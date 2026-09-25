package com.example.floatpad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object Textures {

    const val NONE = "none"

    /** 底层贴图（touchpad 底盘） */
    val BASE_IDS = listOf(NONE, "base_ring", "base_soft", "base_square")
    val BASE_NAMES = listOf("无", "圆环", "柔光", "圆角方")

    /** 上层贴图（图标层） */
    val ICON_IDS = listOf(
        NONE, "icon_arrow_left", "icon_arrow_down", "icon_arrow_up",
        "icon_arrow_right", "icon_a", "icon_b", "icon_star"
    )
    val ICON_NAMES = listOf("无", "←", "↓", "↑", "→", "A", "B", "★")

    /**
     * 随包图片放这里：app/src/main/assets/pad/
     * assets 不是资源，文件名大小写、空格都不限制，原版那批 A.png / LEFT.png 可以原名丢进去。
     */
    private const val ASSET_DIR = "pad"
    private const val ASSET_PREFIX = "asset:"

    /** 放进 res/drawable 的图，必须全小写且以 pad_ 开头 */
    private const val RES_PREFIX = "res:"
    private const val RES_NAME_PREFIX = "pad_"

    /** 动态字母图标：letter:a ~ letter:z，不用为每个字母准备一张图 */
    private const val LETTER_PREFIX = "letter:"

    private const val DIR_PREFIX = "dir_"

    private val cache = HashMap<String, Bitmap?>()
    private val resDrawables = LinkedHashMap<String, Drawable>()
    private val assetNames = mutableListOf<String>()
    private var resReady = false
    private var filesDirPath: String? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        filesDirPath = ctx.filesDir.absolutePath
        if (resReady) return
        resReady = true

        // 1) assets/pad/ 下的图（名字随便起）
        runCatching {
            assetNames.clear()
            ctx.assets.list(ASSET_DIR)
                ?.filter { isImageName(it) }
                ?.sorted()
                ?.forEach { assetNames.add(it) }
        }

        // 2) res/drawable 下 pad_ 开头的图
        runCatching {
            R.drawable::class.java.fields
                .map { it.name }
                .filter { it.startsWith(RES_NAME_PREFIX) }
                .sorted()
                .forEach { name ->
                    val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
                    if (id != 0) {
                        ctx.getDrawable(id)?.let { resDrawables[RES_PREFIX + name] = it }
                    }
                }
        }
    }

    private fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
    }

    fun resIds(): List<String> = resDrawables.keys.toList()

    fun resNames(): List<String> = resDrawables.keys.map { it.removePrefix(RES_PREFIX) }

    /** assets/pad/ 里的贴图 */
    fun assetIds(): List<String> = assetNames.map { ASSET_PREFIX + it }

    fun assetLabels(): List<String> = assetNames.map { it.substringBeforeLast('.') }

    /** 按原版 json 里的 graphic 名去 assets/pad 找图（大小写不敏感），找不到返回 null */
    fun assetIdForGraphic(graphic: String): String? {
        val want = "$graphic.png".lowercase()
        return assetNames.firstOrNull { it.lowercase() == want }?.let { ASSET_PREFIX + it }
    }

    /** 已经导入过的贴图（相册单张导入 + 文件夹批量导入） */
    fun importedIds(): List<String> {
        val dir = filesDirPath?.let { File(it, "textures") } ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && isImageName(it.name) }
            ?.sortedBy { it.name }
            ?.map { "file:${it.absolutePath}" }
            ?: emptyList()
    }

    fun importedNames(): List<String> =
        importedIds().map { File(it.removePrefix("file:")).nameWithoutExtension }

    fun isImported(id: String) = id.startsWith("file:")

    fun isAsset(id: String) = id.startsWith(ASSET_PREFIX)

    fun isRes(id: String) = id.startsWith(RES_PREFIX)

    fun displayName(id: String): String {
        if (id == NONE) return "无"
        if (id.startsWith(LETTER_PREFIX)) return id.removePrefix(LETTER_PREFIX).uppercase()
        if (isAsset(id)) return id.removePrefix(ASSET_PREFIX).substringBeforeLast('.')
        if (isImported(id)) return File(id.removePrefix("file:")).nameWithoutExtension
        if (isRes(id)) return id.removePrefix(RES_PREFIX)
        val b = BASE_IDS.indexOf(id)
        if (b >= 0) return BASE_NAMES[b]
        val i = ICON_IDS.indexOf(id)
        return if (i >= 0) ICON_NAMES[i] else "?"
    }

    /** 把用户选的 PNG 拷进私有目录，返回 file: 开头的 id；失败返回 null */
    fun importFrom(ctx: Context, uri: Uri): String? = runCatching {
        val dir = File(ctx.filesDir, "textures").apply { mkdirs() }
        val file = File(dir, "tex_${System.currentTimeMillis()}.png")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val id = "file:${file.absolutePath}"
        cache.remove(id)
        id
    }.getOrNull()

    /**
     * 整个文件夹批量导入。选原版仓库的 assets/mobile/images/touchpad 或 virtualpad 目录，
     * 里面的图会全部拷进来，文件名保留，直接在贴图选项里出现。
     */
    fun importFolder(ctx: Context, treeUri: Uri): Int {
        var count = 0
        runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
            val dest = File(ctx.filesDir, "textures").apply { mkdirs() }
            ctx.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val docId = cursor.getString(1) ?: continue
                    if (!isImageName(name)) continue
                    runCatching {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val out = File(dest, "$DIR_PREFIX$name")
                        ctx.contentResolver.openInputStream(docUri)!!.use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                        cache.remove("file:${out.absolutePath}")
                        count++
                    }
                }
            }
        }
        return count
    }

    /** assets 和已导入的贴图都走这里，带缓存 */
    private fun imageBitmap(id: String): Bitmap? {
        if (cache.containsKey(id)) return cache[id]
        val bmp = runCatching {
            when {
                isAsset(id) -> {
                    val name = id.removePrefix(ASSET_PREFIX)
                    appCtx?.assets?.open("$ASSET_DIR/$name")?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                isImported(id) -> BitmapFactory.decodeFile(id.removePrefix("file:"))
                else -> null
            }
        }.getOrNull()
        cache[id] = bmp
        return bmp
    }

    /**
     * 画一层贴图。paint 上带的 colorFilter 对内置图形、字母、随包图、导入图同时生效，
     * 所以“是否上色”一个开关就能同时管两层。
     */
    fun draw(canvas: Canvas, id: String, cx: Float, cy: Float, r: Float, paint: Paint) {
        if (id == NONE || r <= 0f) return

        if (id.startsWith(LETTER_PREFIX)) {
            letter(canvas, id.removePrefix(LETTER_PREFIX).uppercase(), cx, cy, r, paint)
            return
        }

        if (isRes(id)) {
            resDrawables[id]?.let { d ->
                d.setBounds((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
                d.colorFilter = paint.colorFilter
                d.setAlpha(paint.alpha)
                d.draw(canvas)
            }
            return
        }

        if (isAsset(id) || isImported(id)) {
            imageBitmap(id)?.let { bmp ->
                canvas.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), paint)
            }
            return
        }

        when (id) {
            "base_ring" -> {
                val p = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.16f
                }
                canvas.drawCircle(cx, cy, r * 0.84f, p)
            }
            "base_soft" -> {
                val p = Paint(paint).apply { style = Paint.Style.FILL }
                canvas.drawCircle(cx, cy, r, p)
            }
            "base_square" -> {
                val p = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.14f
                }
                val d = r * 0.84f
                canvas.drawRoundRect(
                    RectF(cx - d, cy - d, cx + d, cy + d), r * 0.28f, r * 0.28f, p
                )
            }
            "icon_arrow_left" -> arrow(canvas, cx, cy, r, -1f, 0f, paint)
            "icon_arrow_right" -> arrow(canvas, cx, cy, r, 1f, 0f, paint)
            "icon_arrow_up" -> arrow(canvas, cx, cy, r, 0f, -1f, paint)
            "icon_arrow_down" -> arrow(canvas, cx, cy, r, 0f, 1f, paint)
            "icon_a" -> letter(canvas, "A", cx, cy, r, paint)
            "icon_b" -> letter(canvas, "B", cx, cy, r, paint)
            "icon_star" -> star(canvas, cx, cy, r, paint)
        }
    }

    private fun arrow(canvas: Canvas, cx: Float, cy: Float, r: Float, dx: Float, dy: Float, paint: Paint) {
        val p = Paint(paint).apply { style = Paint.Style.FILL }
        val tip = r * 0.62f
        val back = r * 0.34f
        val half = r * 0.58f
        val path = Path()
        if (dx != 0f) {
            path.moveTo(cx + dx * tip, cy)
            path.lineTo(cx - dx * back, cy - half)
            path.lineTo(cx - dx * back, cy + half)
        } else {
            path.moveTo(cx, cy + dy * tip)
            path.lineTo(cx - half, cy - dy * back)
            path.lineTo(cx + half, cy - dy * back)
        }
        path.close()
        canvas.drawPath(path, p)
    }

    private fun letter(canvas: Canvas, text: String, cx: Float, cy: Float, r: Float, paint: Paint) {
        val p = Paint(paint).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = r * 1.35f
            isFakeBoldText = true
        }
        canvas.drawText(text, cx, cy + p.textSize / 3f, p)
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val p = Paint(paint).apply { style = Paint.Style.FILL }
        val outer = r * 0.72f
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val px = cx + (radius * Math.cos(angle)).toFloat()
            val py = cy + (radius * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, p)
    }
}

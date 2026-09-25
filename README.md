# FloatingPad

可自定义的安卓悬浮虚拟按键，用来给没有触屏支持的游戏（比如 Friday Night Funkin'）加一层自己摆的按键。

原理：`WindowManager` 悬浮窗（每个按键一个独立小窗）+ `AccessibilityService.dispatchGesture` 注入点击，**无需 root**。

## 能改什么

- **按键数量**：不限，编辑器里随时增删
- **两层贴图**：每个按键分底层（底盘）与上层（图标），可分别选图
- **贴图来源**：内置代码绘制 / 字母动态绘制 / `assets/pad/` 随包图片 / `res/drawable` 的 `pad_*.png` / 相册导入 / 文件夹批量导入
- **Hitbox 样式**：圆形 / 圆角方
- **是否上色**：可开关，开了用色板颜色给两层套色
- **按键映射**：按下位置和触发位置可以分开

## 放自己的贴图（重点）

**推荐：放到 `app/src/main/assets/pad/`**

assets 不是安卓资源，**文件名大小写、空格都不限制**，原版那批 `A.png`、`LEFT.png`、`bg.png` 可以原名直接丢进去，不用改任何名字。重新编译后自动出现在贴图选项里。

```
app/src/main/assets/pad/
├── A.png
├── LEFT.png
├── UP.png
├── bg.png
└── ...
```

**不推荐：放 `res/drawable/`**

安卓对资源文件名有硬性限制：**只能用小写字母、数字、下划线**。`A.png` 这种大写名会让编译直接报错（`invalid file name`）。非要放这里的话，必须全改成小写、以 `pad_` 开头（如 `pad_left.png`）。

**运行时导入（不用重新编译）**

- 单张：编辑器里点「从相册选底层/上层贴图」
- 批量：点「从文件夹批量导入贴图」，直接选图片所在目录，里面所有图一次拷进来，原名保留

> 内置贴图（圆环、柔光、圆角方、四方向箭头、A/B/★）和字母（`letter:a` ~ `letter:z`）都是代码画的，一张图都不放也能跑。

## 应用图标

图标是**矢量 XML**，不是 PNG 图片，所以你在 drawable 里找不到“图标图片”：

- `app/src/main/res/drawable/ic_launcher_foreground.xml` —— 图形本体（四个方向箭头 + 中心圆点，用 FNF 四键色）
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` —— 自适应图标壳
- `app/src/main/res/values/colors.xml` —— 图标底色 `#12121A`

因为 `minSdk = 26`，自适应图标已经够用，**不需要任何 PNG**。用 Android Studio 打开工程，双击那几个 xml 就能预览效果。

## 原版 mobile 预设

编辑器底部内置了原版 `assets/mobile` 里的全部 15 个按键模式，坐标和颜色按原数据换算：

**ActionModes（右侧动作键）**：A、A_B、A_B_C、A_B_C_D_V_X_Y_Z、A_B_C_X_Y_Z、A_B_M_E、A_B_X_Y、B、B_C、P、Z

**DPadModes（左侧方向键）**：LEFT_FULL、LEFT_RIGHT、RIGHT_FULL、UP_DOWN

点模式名字 = 整套替换；点 `＋` = 追加到当前布局。所以你可以先加一个 ActionMode，再追加一个 DPadMode，跟原版组合逻辑一致。

坐标换算说明：原版是 1280×720 虚拟分辨率、按键图形 124px，且给的是图形**左上角**坐标（左键 x=0、P 键 y=2 都不会超出屏幕），本工程换算成中心点比例。

## 按键映射

音游的判定点在游戏自己的位置，但你的拇指想放的地方可能不一样。映射就是把这个拆开：

- 未映射时：按下按键 → 在按键自己的位置注入点击
- 已映射时：按下按键 → 在映射点注入点击

设置方法：回到游戏 → 点左上角齿轮进编辑模式 → **长按要设的按键**（约 0.6 秒）→ 屏幕变黄框，点一下游戏里那个键的位置，自动保存。

编辑模式里，已设映射的按键右上角会有一个蓝色十字靶标。要取消就在编辑器里点「清除映射」。

## 构建

推到 GitHub 后，Actions 会自动编译并上传 APK；打 `v*` 标签还会自动发 Release。

本地构建：用 Android Studio 打开本目录直接 Run，或

```bash
gradle assembleDebug
```

（仓库未包含 `gradle/wrapper`，本地有 Gradle 的话可以先跑 `gradle wrapper` 补上。）

## 使用

1. 装好后打开应用，依次点「授予悬浮窗权限」和「开启无障碍服务」。
2. 点「3. 启动悬浮按键」，屏幕上出现按键和左上角齿轮。
3. 点齿轮进编辑模式（按键变黄色虚线框、带序号），拖动改位置，松手自动存。
4. 回控制页点「编辑按键」可以增减数量、换贴图、改样式、开关上色、选原版预设、清映射。

## 已知限制

- 无障碍注入手势要过系统手势管道，单次延迟在几十毫秒量级且会波动。对判定严格的音游可能偏软；游戏若自带触屏按键，优先用游戏自带的。
- 映射的是「点击坐标」，不是键盘按键。免 root 的情况下，无障碍服务无法向其他应用发送按键事件（那是 `INJECT_EVENTS` 权限，只有系统应用能拿）。
- `Textures` 靠反射扫 `R.drawable` 找 `pad_` 贴图，所以 `buildTypes` 里不要开 `minifyEnabled`（现在默认就是关的）。
- 部分机型会在后台清理服务，建议给本应用开「允许后台运行」。

## 目录

```
app/src/main/
├── assets/pad/                   随包贴图（文件名随便起）
├── res/drawable/                 内置矢量图标 + pad_ 开头的贴图
├── res/mipmap-anydpi-v26/        自适应启动图标
└── java/com/example/floatpad/
    ├── PadConfig.kt              按键模型 + 持久化 + 预设（含原版 15 个模式）
    ├── Textures.kt               内置贴图 / 字母绘制 + assets / res / 相册 / 文件夹四种来源
    ├── PadAccessibilityService.kt  手势注入
    ├── PadButtonView.kt          按键渲染与触摸（含齿轮 ToggleView）
    ├── FloatingPadService.kt     悬浮窗管理 + 取映射点
    ├── MainActivity.kt           控制页
    └── PadEditorActivity.kt      按键编辑器
```

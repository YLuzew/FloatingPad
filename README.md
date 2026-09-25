# FloatingPad

可自定义的安卓悬浮虚拟按键，给没有触屏支持的游戏（比如 Friday Night Funkin'）加一层自己摆的、能绑键盘键的按键。

## 原理（关键）

**悬浮窗 + 输入法**，两个身份配合：

- **悬浮窗**（`TYPE_APPLICATION_OVERLAY`）负责显示按钮、接收触摸；每个按键一个独立小窗，按键以外的区域全部透传给游戏。
- **输入法**（`InputMethodService`）负责把键盘事件真正送进游戏。这是免 root 下发按键的唯一正路：`INJECT_EVENTS` 是系统签名权限，普通 App 拿不到；无障碍服务只能注入触摸手势，发不了 Enter / ESC。

这套架构是拆开参考应用「游戏键盘」（`com.locnet.gamekeyboard2`）后确定的：它声明了 `android.view.InputMethod` 服务（`BIND_INPUT_METHOD`）+ `SYSTEM_ALERT_WINDOW`，没有无障碍服务。

**代价**：玩之前要把当前输入法切成「悬浮按键」，跟用「游戏键盘」一样。

## 三种可切换布局

控制页和编辑器顶部都能切，点一下整套换：

- **十字键**：四个方向，绑方向键
- **十字键 + 控制键**：十字键 + A（默认绑 Enter）+ B（默认绑 Esc）
- **Hitbox**：四个方向排成一排，适合音游轨道

## 按键绑定

每个按键可以绑一个键盘键（←→↑↓ / Enter / Esc / Space / Tab / Shift / Ctrl / Alt / Del / Back / A~Z / 0~9 / F1~F12）。

- **绑了键** → 按下时由输入法发出对应的键盘事件
- **不绑（未绑定）** → 退回触摸点击，需要开无障碍服务

按钮下方会直接标出它代表的键（触摸模式的标「触摸」），一眼能看出哪个是哪个。

## 位置：居中 16:9 区域

所有按键都建在屏幕居中那块 **16:9** 矩形里：先算出 `min(屏宽, 屏高×16/9) × min(屏高, 屏宽×9/16)` 的居中矩形，按键坐标和大小都按这个矩形的比例算。所以换机型、转屏，相对位置都不会跑。

## 贴图

每个按键分底层（底盘）与上层（图标），可分别选图。三种来源：

1. **随包图片**：放到 `app/src/main/assets/pad/`。assets 不是资源，文件名大小写、空格都不限制。
2. **`res/drawable` 下的 `pad_*.png`**：注意安卓限制资源名只能小写。
3. **运行时导入**：相册单张选，或「从文件夹批量导入贴图」。

内置贴图（圆环、柔光、圆角方、四方向箭头、A/B/★）和字母（`letter:a`~`letter:z`）都是代码画的，一张图不放也能跑。

## 构建

推到 GitHub 后 Actions 自动编译并上传 APK；打 `v*` 标签会自动发 Release。

本地：用 Android Studio 打开直接 Run，或 `gradle assembleDebug`（仓库未含 gradle wrapper，本地有 Gradle 可先跑 `gradle wrapper`）。

## 手机上怎么用

1. 装好后打开应用，点「授予悬浮窗权限」。
2. 点「启用本应用的输入法」，在系统设置里把它勾上。
3. 点「把当前输入法切成「悬浮按键」」，选它。
4. 点「启动悬浮按键」。
5. 回游戏即可。点左上角齿轮进编辑模式可以拖按键；回控制页可切布局。

> 如果某个按键按下去弹出「发不出按键」，说明输入法没切成这个应用。

## 已知限制

- 发键盘事件靠 `InputConnection`，需要当前有输入焦点。游戏若没有焦点窗口，键盘事件发不出去（这时用未绑定的触摸模式）。
- 部分机型会在后台清理服务，建议给本应用开「允许后台运行」。
- `Textures` 靠反射扫 `R.drawable` 找 `pad_` 贴图，`buildTypes` 里不要开 `minifyEnabled`（现在默认就是关的）。

## 目录

```
app/src/main/
├── assets/pad/                   随包贴图（文件名随便起）
├── res/drawable/                 内置矢量图标 + pad_ 开头的贴图
├── res/mipmap-anydpi-v26/        自适应启动图标
├── res/xml/                      输入法配置 + 无障碍配置
└── java/com/example/floatpad/
    ├── PadConfig.kt              按键模型 + 三种布局 + 键位表 + 16:9 计算
    ├── Textures.kt               内置贴图 / 字母绘制 + assets / res / 相册 / 文件夹四种来源
    ├── PadInputMethodService.kt  输入法：真正发键盘事件
    ├── PadAccessibilityService.kt 无障碍：只负责触摸模式的点击注入
    ├── PadButtonView.kt          按键渲染与触摸（含齿轮 ToggleView）
    ├── FloatingPadService.kt     悬浮窗管理 + 16:9 定位 + 按下分发
    ├── MainActivity.kt           控制页
    └── PadEditorActivity.kt      按键编辑器
```

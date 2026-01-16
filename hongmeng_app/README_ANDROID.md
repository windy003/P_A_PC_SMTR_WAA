# Screenshot Monitor - Android 应用说明

## 应用功能

这是一个 Android Widget 应用，用于监控局域网内 PC 的截图状态。

## 构建步骤

### 1. 环境要求

- Android Studio Arctic Fox 或更高版本
- Android SDK 26+
- Gradle 8.0+
- Kotlin 1.9+

### 2. 打开项目

1. 启动 Android Studio
2. 选择 "Open an Existing Project"
3. 选择 `Android_app` 文件夹
4. 等待 Gradle 同步完成

### 3. 配置本地 SDK 路径

编辑 `local.properties` 文件，设置正确的 SDK 路径：

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

或者让 Android Studio 自动生成此文件。

### 4. 构建应用

#### 方式一：使用 Android Studio
1. 点击 "Build" → "Make Project"
2. 连接 Android 设备或启动模拟器
3. 点击 "Run" 按钮

#### 方式二：使用命令行
```bash
cd Android_app

# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug

# 生成的 APK 位于:
# app/build/outputs/apk/debug/app-debug.apk
```

## 应用使用

### 首次配置

1. **打开应用**
   - 首次打开会看到设置界面

2. **输入 PC IP 地址**
   - 在 PC 上运行: `ipconfig` (Windows) 或 `ifconfig` (Linux/Mac)
   - 找到 IPv4 地址，例如: `192.168.1.100`
   - 在应用中输入此 IP 地址

3. **保存配置**
   - 点击"保存"按钮
   - 应用会自动开始调度定时任务

4. **添加 Widget**
   - 长按主屏幕
   - 选择"小部件"或"Widgets"
   - 找到"Screenshot Monitor"
   - 拖动到主屏幕

### Widget 说明

Widget 会显示以下内容：

#### 状态：无 (none)
```
┌─────────────────┐
│   12-5 5:10    │  ← 上次检查时间
│  上次检查时间   │  ← 提示文字
└─────────────────┘
```

#### 状态：有 (has)
```
┌─────────────────┐
│   [图片显示]    │  ← has.png
│ 最后检查: 12-5  │  ← 检查时间
│      5:10       │
└─────────────────┘
```

### 更新机制

- **自动更新**: 每小时的 10 分（±5 分钟）
- **手动更新**: 在应用中点击"保存"后立即更新
- **开机恢复**: 设备重启后自动恢复定时任务

## 权限说明

应用需要以下权限：

| 权限 | 用途 |
|------|------|
| INTERNET | 访问 PC 的 Web API |
| ACCESS_NETWORK_STATE | 检查网络连接状态 |
| WAKE_LOCK | WorkManager 后台任务 |
| RECEIVE_BOOT_COMPLETED | 开机自启动 |

所有权限都是必需的，不会用于其他目的。

## 目录结构

```
app/
├── src/main/
│   ├── AndroidManifest.xml              # 应用配置
│   ├── java/com/screenshot/monitor/
│   │   ├── MainActivity.kt              # 主界面（IP 配置）
│   │   ├── widget/
│   │   │   └── ScreenshotWidgetProvider.kt  # Widget 核心逻辑
│   │   ├── worker/
│   │   │   └── UpdateWorker.kt          # 后台定时任务
│   │   ├── api/
│   │   │   └── ApiService.kt            # HTTP 请求处理
│   │   ├── model/
│   │   │   └── StatusResponse.kt        # JSON 数据模型
│   │   └── receiver/
│   │       └── BootReceiver.kt          # 开机启动接收器
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml        # 主界面布局
│   │   │   └── widget_screenshot.xml    # Widget 布局
│   │   ├── xml/
│   │   │   └── screenshot_widget_info.xml  # Widget 元数据
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   ├── colors.xml
│   │   │   └── themes.xml
│   │   └── drawable/
│   │       ├── widget_background.xml
│   │       └── widget_preview.xml
│   └── assets/
│       └── has.png                      # "有"状态图片
└── build.gradle                         # 应用构建配置
```

## 核心组件说明

### MainActivity
- 用户配置界面
- 保存 PC IP 地址到 SharedPreferences
- 调度 WorkManager 任务

### ScreenshotWidgetProvider
- 接收 Widget 更新请求
- 调用 ApiService 获取状态
- 更新 Widget UI

### UpdateWorker
- WorkManager 任务
- 每小时执行一次
- 检查当前时间是否在 5-15 分之间
- 触发所有 Widget 实例更新

### ApiService
- 使用 OkHttp 发起 HTTP 请求
- 解析 JSON 响应
- 错误处理和超时控制

### BootReceiver
- 监听系统开机广播
- 重新调度 WorkManager 任务

## 依赖库

```gradle
dependencies {
    // Android Core
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'

    // WorkManager - 后台任务
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    // OkHttp - 网络请求
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // Gson - JSON 解析
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

## 调试技巧

### 查看日志

```bash
# 查看应用日志
adb logcat | grep -i screenshot

# 查看 WorkManager 任务
adb shell dumpsys jobscheduler | grep screenshot

# 查看 Widget 状态
adb shell dumpsys appwidget
```

### 手动触发更新

```bash
# 发送广播触发 Widget 更新
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE \
  -n com.screenshot.monitor/.widget.ScreenshotWidgetProvider
```

### 测试网络连接

```bash
# 从 Android 设备 ping PC
adb shell ping 192.168.1.100

# 测试 HTTP 连接
adb shell curl http://192.168.1.100:5000/api/status
```

## 常见问题

### Q: Widget 不显示？
**A**:
1. 检查是否已在应用中保存 IP 配置
2. 重新添加 Widget
3. 查看日志: `adb logcat | grep Screenshot`

### Q: Widget 显示"未配置"？
**A**:
1. 打开应用
2. 输入 PC IP 地址
3. 点击"保存"

### Q: Widget 显示"连接失败"？
**A**:
1. 确认 PC 和手机在同一 WiFi
2. 测试连接: `adb shell ping <PC_IP>`
3. 检查 PC 防火墙设置
4. 验证 PC Web 服务器正在运行

### Q: Widget 不自动更新？
**A**:
1. 检查电池优化设置
   - 设置 → 应用 → Screenshot Monitor → 电池 → 不优化
2. 检查后台运行权限
3. 重新配置并保存

### Q: 如何修改图片？
**A**:
替换 `app/src/main/assets/has.png` 文件，然后重新构建应用。

## 自定义修改

### 修改检查时间

编辑 `UpdateWorker.kt`:

```kotlin
// 当前：每小时 5-15 分
if (currentMinute in 5..15) {
    // ...
}

// 修改为其他时间，例如每小时 30 分
if (currentMinute in 25..35) {
    // ...
}
```

### 修改时间格式

编辑 `ScreenshotWidgetProvider.kt`:

```kotlin
// 当前格式: "12-5 5:10" (月-日 时:分)
val dateFormat = SimpleDateFormat("M-d H:mm", Locale.getDefault())

// 修改为其他格式，例如: "2025-12-05 05:10"
val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
```

### 修改 Widget 样式

编辑 `res/layout/widget_screenshot.xml` 和 `res/drawable/widget_background.xml`

## 发布应用

### 生成签名 APK

1. Build → Generate Signed Bundle / APK
2. 选择 APK
3. 创建或选择密钥库
4. 选择 release 构建变体
5. 点击 Finish

### 混淆配置

已在 `proguard-rules.pro` 中配置，确保：
- Gson 类不被混淆
- OkHttp 类不被混淆
- WorkManager 类不被混淆

## 技术细节

### WorkManager 调度策略

```kotlin
PeriodicWorkRequestBuilder<UpdateWorker>(
    repeatInterval = 1 小时,
    flexTimeInterval = 15 分钟  // 允许在 10±5 分钟执行
)
```

### SharedPreferences 存储

```
settings.xml:
  - pc_ip: String (PC IP 地址)
  - last_check_time: String (最后检查时间)
```

### 网络请求超时

- 连接超时: 10 秒
- 读取超时: 10 秒

## 许可证

本项目仅供个人学习和使用。

## 贡献

欢迎提交问题和改进建议！

# 澎湃记 (Hyper Note)

## 项目概述

澎湃记是一款 Android 应用，主要功能是通过截图自动识别取餐码/取件码，支持外卖、饮品、快递等多种类型。应用通过屏幕截图服务监听系统截图事件，利用 ML Kit 进行文字识别和条码扫描，自动提取关键信息并生成通知提醒。

### 核心功能

- **截图识别**：监听系统截图，自动识别取餐码/取件码
- **文字识别 (OCR)**：支持中文文字识别，提取取餐码、品牌名、取货地点
- **条码扫描**：识别二维码信息
- **订单管理**：记录、管理、完成订单
- **通知提醒**：支持高级通知，点击可直接跳转
- **快捷设置磁贴**：快速启动截图识别

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建工具 | Gradle 9.0.1 + KSP |
| Kotlin 版本 | 2.0.21 |
| 数据库 | Room |
| ML | ML Kit (Text Recognition, Barcode Scanning) |
| 特权操作 | Shizuku API |
| 图片加载 | Coil |
| 异步 | Kotlin Coroutines |
| 导航 | Navigation Compose |

## 项目结构

```
app/src/main/java/com/Badnng/moe/
├── MainActivity.kt           # 主入口，Compose UI 宿主
├── HomeScreen.kt             # 主页面，包含导航和底部弹窗
├── CaptureScreen.kt          # 截图识别页面
├── CaptureTileService.kt     # 快捷设置磁贴服务
├── ScreenCaptureService.kt   # 屏幕截图前台服务
├── ScreenshotHelper.kt       # 截图辅助工具 (MediaProjection)
├── ShizukuScreenshotHelper.kt # Shizuku 截图辅助
├── TextRecognitionHelper.kt  # OCR 和条码识别核心逻辑
├── OrderEntity.kt            # 订单数据实体
├── OrderDao.kt               # Room DAO 接口
├── OrderDatabase.kt          # Room 数据库定义
├── OrderRepository.kt        # 数据仓库层
├── OrderViewModel.kt         # ViewModel 层
├── NotificationHelper.kt     # 通知管理
├── NotificationReceiver.kt   # 通知广播接收器
├── PermissionActivity.kt     # 权限申请透明 Activity
├── LogManager.kt             # 日志管理
├── SettingsScreen.kt         # 设置页面
└── screens/
    ├── LogScreen.kt          # 日志查看页面
    └── OrderDetailScreen.kt  # 订单详情页面
```

## 构建与运行

### 环境要求

- Android SDK 36 (compileSdk)
- Android SDK 35 (minSdk)
- JDK 11
- Android Studio (推荐最新版)

### 构建命令

```bash
# 调试版构建
./gradlew assembleDebug

# 发布版构建 (启用混淆和资源压缩)
./gradlew assembleRelease

# 清理构建
./gradlew clean

# 运行测试
./gradlew test
./gradlew connectedAndroidTest
```

### 安装运行

```bash
# 安装调试版到设备
./gradlew installDebug

# 或直接运行 (需要连接设备)
./gradlew runDebug
```

## 开发约定

### 代码风格

- 使用 Kotlin 官方代码风格
- Compose 函数使用 PascalCase 命名
- 状态变量使用 `var` + `by mutableStateOf` 或 `remember`

### 架构模式

- **MVVM 架构**：ViewModel + Repository + Room
- **单向数据流**：StateFlow 作为 UI 状态源
- **Compose UI**：声明式 UI，无 XML 布局

### 依赖管理

- 使用 `libs.versions.toml` 进行版本目录管理
- 使用 KSP 处理注解处理器 (Room)
- 使用阿里云镜像加速依赖下载

### 关键配置

- **混淆**：Release 版本启用 ProGuard 混淆和资源压缩
- **NDK**：仅支持 arm64-v8a 架构
- **Java 兼容性**：Java 11

## 功能模块详解

### 1. 截图识别流程

1. 用户截图触发 `ScreenCaptureService`
2. 服务获取截图并通过 `TextRecognitionHelper` 处理
3. 识别取餐码、品牌、取货地点等信息
4. 保存到 Room 数据库并显示通知

### 2. 文字识别策略 (`TextRecognitionHelper.kt`)

- 支持品牌识别：麦当劳、肯德基、瑞幸、星巴克、喜茶等
- 支持类型判断：餐食、饮品、快递
- 快递取件码格式：数字码、连字符码 (A-2-7261)
- 取餐码格式：字母数字混合

### 3. 通知系统

- 支持高级通知 (POST_PROMOTED_NOTIFICATIONS)
- 点击通知可跳转到二维码详情页
- 完成订单自动取消通知

### 4. Shizuku 集成

- 用于需要特权的截图操作
- 通过 `ShizukuProvider` 提供服务

## 注意事项

1. **权限**：应用需要以下权限
   - `POST_NOTIFICATIONS` (Android 13+)
   - `FOREGROUND_SERVICE_MEDIA_PROJECTION`
   - `PACKAGE_USAGE_STATS` (获取截图来源应用)

2. **Shizuku**：部分功能依赖 Shizuku 服务，需用户授权

3. **签名**：Release 版本需要配置签名密钥

4. **ProGuard 规则**：ML Kit 和 Room 相关类需要保留

## 常见问题排查

- **截图识别不工作**：检查 MediaProjection 权限是否授权
- **通知不显示**：检查通知权限和通知渠道
- **Shizuku 失败**：确保 Shizuku 应用已安装并授权

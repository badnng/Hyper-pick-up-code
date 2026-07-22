# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目简介

**澎湃记（Hyper Note）** — 一款 Android 应用，使用本地 OCR（PaddleOCR ncnn）和 ML Kit 条码扫描，从截图中自动识别餐饮外卖取餐码和快递单号。界面语言为中文。

## 构建命令

```bash
./gradlew assembleDebug       # 调试版构建
./gradlew assembleRelease     # 发布版构建（混淆 + ProGuard）
./gradlew installDebug        # 安装调试版 APK 到已连接设备
./gradlew test                # 单元测试（JUnit 4）
./gradlew connectedAndroidTest # 仪器化测试
./gradlew clean               # 清理构建
```

发布签名从 `local.properties` 或环境变量中读取：`KEY_STORE_PATH`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 架构

**MVVM 架构，单模块扁平包结构**，位于 `com.Badnng.moe` 包下。

```
Compose UI → StateFlow ← OrderViewModel → Repository → DAO → Room DB
```

- **Room 数据库**（v5，含迁移路径 2→3→4→5）：`OrderEntity`（orders 表）和 `OrderGroup`（order_groups 表），外键设置级联删除
- **PaddleOcrHelper**：单例（双重检查锁定），封装 PaddleOCR ncnn 用于中文文字识别
- **TextRecognitionHelper**：核心 OCR + 条码逻辑，`recognizeMultipleCodes()` 可从单张截图中返回多个取餐码
- **前台服务**：`ScreenCaptureService`（MediaProjection）、`ShareRecognitionService`（共享图片）、`ProcessTextRecognitionService`（文本选择）
- **Shizuku 集成**：通过 `ShizukuScreenshotHelper` 进行特权截图操作，并提供 `RootHelper` 作为 root 备用方案

## UI 兼容层

应用使用 `LocalAppUi` CompositionLocal 实现 UI 抽象，支持 MD3E / Miuix 双 UI 切换。

- **抽象接口**：`ui/AppUi.kt` — `AppUi` data class + `LocalAppUi`
- **MD3E 实现**：`ui/Md3eAppUi.kt` — 包装 `SettingsComponents.kt` 中的组件
- **Miuix 实现**：`ui/MiuixAppUi.kt` — 占位，fallback 到 md3eAppUi
- **提供**：`ui/theme/Theme.kt` 中 `CompositionLocalProvider(LocalAppUi provides md3eAppUi)` 包裹主题

**新功能必须通过 `LocalAppUi.current.xxx()` 调用组件**，不能直接使用 Material3 组件，确保切换 UI 时自动兼容。

### Miuix 模糊系统

- `rememberMiuixBackdrop()` 创建 `LayerBackdrop`，`layerBackdrop(backdrop)` 应用到内容层
- `textureBlur(backdrop)` 采样 backdrop 做毛玻璃效果
- **⚠️ 严禁递归渲染**：`layerBackdrop` 和 `textureBlur(backdrop)` 不能在同一渲染子树中。底栏等覆盖层必须作为 Scaffold 的兄弟节点，不能放在 Scaffold 内部的 `bottomBar` 中使用 `textureBlur`
- 模糊需要 `isRenderEffectSupported()` / `isRuntimeShaderSupported()` 检查
- `BlurState` 全局单例管理 BottomSheet 模糊进度，通过 `snapshotFlow { blurProgress.value }.collect` 同步

## 关键约定

- **100% Compose UI** — 无 XML 布局文件
- **Kotlin 2.2.10**，Java 17 兼容
- **minSdk 35**（Android 15+），compileSdk 37，targetSdk 36
- **NDK**：仅支持 arm64-v8a
- **版本目录**：依赖版本统一在 `gradle/libs.versions.toml` 中管理
- **KSP** 用于 Room 注解处理
- **Compose 状态**：本地状态使用 `var ... by mutableStateOf()` 或 `remember`；共享状态使用 ViewModel 的 `StateFlow`
- **Compose 函数命名**：使用 PascalCase（大驼峰）
- **数据库迁移**：添加列或表前须先检查是否已存在（使用 `PRAGMA table_info()`），以防升级时崩溃
- **按钮圆角统一**: 一律遵循15dp圆角数值
- **震动反馈**: 可以交互的都需要震动模块
- **最新改动**: 每次改动之前都以目前的代码进行修改，对功能修改不要连带其他功能，改什么就是什么
- **编译**: 如无特殊说明，不自动编译，需要编译时会明确说明
- **触发方式**: 如果更改规则识别相关代码时，请确保所有触发方式全部覆盖，而不只是识屏相关代码兼容
- **中文**: 请使用中文回答
- **正则**: 请使用里面的 `default_rules.json` 来修改正则以便匹配相关关键词，请在有必要的时候动代码，因为这是个有自定义正则的app，用户可以自行修改json来写自己的规则
- **手势线沉浸**: 每个页面的手势线都必须沉浸，如遇到问题，请按照常见问题来修复
- **MotionScheme**: 透明度/颜色动画用 `defaultEffectsSpec<Float>()`，尺寸/位置动画用 `defaultSpatialSpec<IntSize>()`，不能混用
- **Material Colors**: 使用 `com.google.android.material:material` 中的 `CorePalette`（字段被混淆：`a1`=primary, `a2`=secondary, `a3`=tertiary, `n1`=neutral, `n2`=neutralVariant）

## ProGuard

Keep 规则对运行时至关重要 — ML Kit（`com.google.mlkit.**`）、PaddleOCR ncnn（`com.equationl.ncnnandroidppocr.**`）以及 ncnn 原生库（`org.ncnn.**`）不得被混淆。

## CI

GitHub Actions（`.github/workflows/Build and Release.yml`）：仅在推送 tag（`v*`）时触发构建，使用 JDK 17，通过 `KEYSTORE_BASE64` secret 解码签名。分支推送不触发任何 workflow。

## 常见问题

- 实体字段与迁移列类型不匹配会导致升级时崩溃 — 务必确认 `OrderEntity.groupId` 为 `Long?`，与 `OrderGroup.id: Long` 类型一致
- Android 14+ 的提升通知需要 `POST_PROMOTED_NOTIFICATIONS` 权限
- Shizuku 特权操作需要用户已安装并授权 Shizuku
- 建议将应用加入电池优化白名单，以保障后台服务稳定运行
- 如果页面手势线未沉浸时，问题是外层 `Column` 有 `windowInsetsPadding` 挡住了底部导航栏区域，需要去掉，改由 `LazyColumn` 的 `contentPadding` 处理
- Miuix BottomSheet 模糊使用 `Animatable` + `snapshotFlow` 同步，`onDismissFinished` 统一调用 `BlurState.hide()`，不要在 `LaunchedEffect(show=false)` 中重复调用

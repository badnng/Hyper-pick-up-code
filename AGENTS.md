# 澎湃记 (Hyper Note)

## 项目概述

澎湃记是一款 Android 应用，主要功能是通过截图自动识别取餐码/取件码，支持外卖、饮品、快递等多种类型。应用通过屏幕截图服务监听系统截图事件，利用 PaddleOCR 进行文字识别和 ML Kit 进行条码扫描，自动提取关键信息并生成通知提醒。

### 核心功能

- **截图识别**：监听系统截图，自动识别取餐码/取件码
- **文字识别 (OCR)**：支持中文文字识别，提取取餐码、品牌名、取货地点
- **条码扫描**：识别二维码信息
- **订单管理**：记录、管理、完成订单
- **订单组管理**：将一次识别的多个取件码分组管理
- **通知提醒**：支持高级通知，点击可直接跳转
- **快捷设置磁贴**：快速启动截图识别
- **划词识别**：选择文字后识别取件码
- **分享识别**：通过分享图片识别取件码
- **备份恢复**：支持数据备份和恢复

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建工具 | Gradle 9.0.1 + KSP |
| Kotlin 版本 | 2.0.21 |
| 数据库 | Room |
| ML | PaddleOCR (文字识别) + ML Kit (条码扫描) |
| 特权操作 | Shizuku API |
| 图片加载 | Coil |
| 异步 | Kotlin Coroutines |
| 导航 | Navigation Compose |
| 网络 | OkHttp |
| 模糊效果 | Kyant Backdrop |

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
├── PaddleOcrHelper.kt        # PaddleOCR 辅助类
├── OrderEntity.kt            # 订单数据实体
├── OrderGroup.kt             # 订单组实体
├── OrderDao.kt               # Room DAO 接口
├── OrderGroupDao.kt          # 订单组 DAO 接口
├── OrderDatabase.kt          # Room 数据库定义
├── OrderRepository.kt        # 数据仓库层
├── OrderGroupRepository.kt   # 订单组仓库层
├── OrderViewModel.kt         # ViewModel 层
├── NotificationHelper.kt     # 通知管理
├── NotificationReceiver.kt   # 通知广播接收器
├── PermissionActivity.kt     # 权限申请透明 Activity
├── ProcessTextActivity.kt    # 处理文字 Activity
├── ProcessTextRecognitionService.kt # 处理文字识别服务
├── ShareReceiverActivity.kt  # 分享接收 Activity
├── ShareRecognitionService.kt # 分享识别服务
├── OrderQuickViewActivity.kt # 订单快速查看 Activity
├── LogManager.kt             # 日志管理
├── SettingsScreen.kt         # 设置页面
├── OnboardingScreen.kt       # 引导页面
├── UpdateDialog.kt           # 更新对话框
├── UpdateHelper.kt           # 更新辅助工具
├── BackupHelper.kt           # 备份辅助工具
└── screens/
    ├── LogScreen.kt          # 日志查看页面
    ├── OrderDetailScreen.kt  # 订单详情页面
    └── GroupDetailScreen.kt  # 组详情页面
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

- 使用 PaddleOCR 进行中文文字识别
- 保留 ML Kit 进行条码扫描
- 支持品牌识别：麦当劳、肯德基、瑞幸、星巴克、喜茶、古茗、蜜雪冰城等
- 支持类型判断：餐食、饮品、快递
- 快递取件码格式：数字码、连字符码 (A-2-7261)
- 取餐码格式：字母数字混合
- 支持多取件码识别：`recognizeMultipleCodes()` 方法

### 3. 通知系统

- 支持高级通知 (POST_PROMOTED_NOTIFICATIONS)
- 点击通知可跳转到二维码详情页
- 完成订单自动取消通知
- 支持组通知：`showGroupNotification()` 方法

### 4. Shizuku 集成

- 用于需要特权的截图操作
- 通过 `ShizukuProvider` 提供服务
- 支持免授权截图识别

### 5. 订单组功能

- 用于将一次识别的多个取件码分组管理
- 支持组详情查看、批量完成、批量删除
- 数据库版本 5，包含外键约束

### 6. 备份恢复功能

- 支持备份订单数据和设置到压缩包
- 支持从备份文件恢复数据
- 使用 `BackupHelper` 类实现

### 7. 更新功能

- 支持联网检查更新
- 支持下载和安装更新包
- 支持正式版和测试版更新通道
- 使用 `UpdateHelper` 类实现

## 注意事项

1. **权限**：应用需要以下权限
   - `POST_NOTIFICATIONS` (Android 13+)
   - `POST_PROMOTED_NOTIFICATIONS` (Android 14+)
   - `FOREGROUND_SERVICE_MEDIA_PROJECTION`
   - `FOREGROUND_SERVICE_SPECIAL_USE`
   - `PACKAGE_USAGE_STATS` (获取截图来源应用)
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

2. **Shizuku**：部分功能依赖 Shizuku 服务，需用户授权

3. **签名**：Release 版本需要配置签名密钥

4. **ProGuard 规则**：ML Kit、Room 和 PaddleOCR 相关类需要保留

## 常见问题排查

- **截图识别不工作**：检查 MediaProjection 权限是否授权
- **通知不显示**：检查通知权限和通知渠道
- **Shizuku 失败**：确保 Shizuku 应用已安装并授权
- **电池优化**：建议加入电池优化白名单以确保后台正常运行

## 开发记录

### 2026-03-24: 订单组功能实现

#### 功能需求
实现"订单组"功能，用于将一次识别的多个取件码分组管理。

#### 新增文件

1. **OrderGroup.kt** - 订单组实体类
   - 使用 Room 数据库，表名 `order_groups`
   - 主键 `id` 为 Long 类型，自动生成
   - 包含字段：name, orderType, brandName, screenshotPath, sourceApp, sourcePackage, recognizedText, createdAt, isCompleted, completedAt, orderCount

2. **OrderGroupDao.kt** - 订单组数据访问对象
   - `insertGroup()` - 插入订单组
   - `updateGroup()` - 更新订单组
   - `deleteGroup()` - 删除订单组
   - `getGroupById()` - 根据 ID 获取订单组
   - `getAllGroups()` - 获取所有订单组（Flow）
   - `getIncompleteGroups()` - 获取未完成的订单组
   - `getCompletedGroups()` - 获取已完成的订单组
   - `getOrdersByGroupId()` - 获取组中的订单
   - `markGroupAsCompleted()` - 标记组为已完成
   - `markAllOrdersInGroupCompleted()` - 标记组中所有订单为已完成

3. **OrderGroupRepository.kt** - 订单组仓库类
   - 封装 OrderGroupDao 的操作
   - 提供订单组的 CRUD 操作

4. **screens/GroupDetailScreen.kt** - 组详情页面
   - 显示订单组信息
   - 显示组中的订单列表
   - 支持标记单个订单或全部订单为已完成
   - 支持查看截图和复制取件码

#### 修改的文件

1. **OrderDatabase.kt**
   - 在 `@Database` 注解中添加 `OrderGroup::class` 实体
   - 添加 `orderGroupDao()` 抽象方法
   - 添加 `MIGRATION_4_5` 迁移，创建 `order_groups` 表
   - 为 `orders` 表添加 `groupId` 列

2. **OrderEntity.kt**
   - 添加 `groupId: Long?` 字段，关联到 OrderGroup
   - 添加外键约束，删除组时级联删除订单
   - 添加索引 `index_orders_groupId`

3. **OrderViewModel.kt**
   - 添加 `orderGroupDao` 和 `groupRepository` 属性
   - 添加 `_orderGroups`, `_incompleteGroups`, `_completedGroups` 状态流
   - 添加 `insertGroup()`, `getOrdersByGroupId()`, `markGroupAsCompleted()`, `markAllOrdersInGroupCompleted()`, `deleteGroup()`, `deleteCompletedGroups()` 方法

4. **ScreenCaptureService.kt**
   - 修改 `recognizeAndStop()` 方法，支持创建订单组
   - 当识别到多个取件码时，创建 OrderGroup 并关联订单
   - 修复 `groupId` 类型，从 `Long` 直接赋值（移除不必要的 `toString()`）

5. **ShareRecognitionService.kt**
   - 修改 `processImage()` 方法，支持创建订单组
   - 当识别到多个取件码时，创建 OrderGroup 并关联订单
   - 修复 `groupId` 类型，从 `Long` 直接赋值
   - 修复 `NotificationHelper.showGroupNotification()` 调用，传入完整的 OrderGroup 对象

6. **NotificationHelper.kt**
   - 添加 `showGroupNotification()` 方法，显示组通知
   - 添加 `cancelGroupNotification()` 方法，取消组通知

7. **CaptureScreen.kt**
   - 修改 UI 支持显示订单组
   - 添加 `OrderGroupCard` 组合函数，显示订单组卡片
   - 支持展开/收起订单组详情

#### 数据库兼容性修复

**问题**：旧版数据库无法打开，APP 崩溃

**原因**：
1. `MIGRATION_4_5` 中添加 `groupId` 列时未检查列是否已存在
2. `OrderEntity.groupId` 类型定义为 `String?`，但迁移代码添加的是 `INTEGER` 类型

**修复**：
1. 修改 `MIGRATION_4_5`，先检查 `groupId` 列是否存在，只在不存在时添加
2. 将 `OrderEntity.groupId` 类型从 `String?` 改为 `Long?`，与 OrderGroup.id 类型一致
3. 修复所有使用 `groupId.toString()` 的地方，直接使用 `groupId`

#### 类型不匹配修复

**问题**：
- `OrderEntity.groupId` 定义为 `String?`
- `OrderGroup.id` 定义为 `Long`
- 迁移代码添加 `INTEGER` 类型的列
- 导致类型不匹配错误

**修复**：
- `OrderEntity.groupId` 改为 `Long?`
- `ScreenCaptureService.kt` 中移除 `groupId.toString()`
- `ShareRecognitionService.kt` 中移除 `orderIdGroup.toString()`
- `ShareRecognitionService.kt` 中 `NotificationHelper.showGroupNotification()` 传入 OrderGroup 对象而不是 Long

#### 技术要点

1. **Room 外键约束**
   - 使用 `ForeignKey` 定义 orders 表与 order_groups 表的关联
   - 设置 `onDelete = ForeignKey.CASCADE`，删除组时自动删除关联的订单

2. **数据库迁移**
   - 使用 `PRAGMA table_info()` 检查表结构
   - 先检查列是否存在，避免重复添加导致的崩溃

3. **类型一致性**
   - 确保实体类字段类型与数据库列类型一致
   - 主键使用 Long 类型，外键也使用 Long? 类型

4. **Compose UI**
   - 使用 `LazyColumn` 显示订单列表
   - 使用 `Card` 组件显示订单组卡片
   - 使用 `AlertDialog` 显示全屏截图

#### 测试要点

1. **数据库迁移测试**
   - 测试从旧版本升级到新版本
   - 验证 groupId 列正确添加
   - 验证数据完整性

2. **订单组功能测试**
   - 测试识别多个取件码时创建订单组
   - 测试订单组的 CRUD 操作
   - 测试标记订单组为已完成

3. **通知功能测试**
   - 测试组通知正确显示
   - 测试点击通知跳转到组详情
   - 测试完成订单后通知取消

4. **UI 测试**
   - 测试订单组卡片显示
   - 测试展开/收起详情
   - 测试复制取件码功能

## 版本信息

- 当前版本：26.3.23.C01
- 版本代码：20260323_01
- 最低 SDK：35
- 目标 SDK：36
- 编译 SDK：36
# Hyper Pick-up Code （澎湃记）🍴

**这个项目完全使用vibe coding，如果介意请勿使用**
<p align="left">
  <img src="https://img.shields.io/badge/平台-Android-3DDC84?style=flat-square" />
  <img src="https://img.shields.io/github/license/badnng/Hyper-pick-up-code?style=flat-square" />
  <img src="https://img.shields.io/github/stars/badnng/Hyper-pick-up-code?style=flat-square" />
  <img src="https://img.shields.io/github/forks/badnng/Hyper-pick-up-code?style=flat-square" />
  <img src="https://img.shields.io/github/downloads/badnng/Hyper-pick-up-code/total?style=flat-square" />

  <!-- 最新版本下载 -->
  <a href="https://github.com/badnng/Hyper-pick-up-code/releases/latest">
    <img src="https://img.shields.io/badge/下载-最新版本-blue?style=flat-square&logo=github" />
  </a>

  <!-- Release 版本 -->
  <img src="https://img.shields.io/github/v/release/badnng/Hyper-pick-up-code?style=flat-square" />

  <!-- Build 状态（需要 GitHub Actions） -->
  <img src="https://img.shields.io/github/actions/workflow/status/badnng/Hyper-pick-up-code/Build%20and%20Release.yml?style=flat-square&label=构建" />
</p>

> 🚀 快速 · 🔒 隐私 · ⚡ 离线  
> 一个专注于 **取餐码 / 取件码识别** 的高效 Android 工具

---

## 📌 项目简介

**澎湃记** 是一款面向 Android 15 及更高版本的本地识别工具，用于从截图、分享图片、通知和文本中提取并管理外卖取餐码、快递取件码等信息。应用使用 PP-OCRv6 Tiny 与 ONNX Runtime 进行本地文字识别，并通过 ML Kit 处理条码内容。

相比依赖云端识别的方案，本应用**完全本地运行**，具备：

- ⚡ 更快的识别速度  
- 🔒 更高的隐私安全  
- 📶 核心识别无需网络

在线规则更新和应用更新为可选功能，可在设置中关闭。

### 运行要求

- Android 15（API 35）或更高版本
- 64 位 ARM 设备（arm64-v8a）
- 部分截图方式需要 MediaProjection、Shizuku 或 Root 权限
- 通知、短信和无障碍触发方式需要用户单独授权

---

## 🌳 项目结构

```
app/src/main/
├── java/com/Badnng/moe/
│   ├── HyperNoteApp.kt                    # Application 入口
│   ├── activity/
│   │   ├── MainActivity.kt                # 主 Activity
│   │   ├── OnboardingContentActivity.kt   # 初始设置内容
│   │   ├── OnboardingCompleteActivity.kt  # 初始设置完成页
│   │   ├── OrderQuickViewActivity.kt      # 通知点击快速查看
│   │   ├── PermissionActivity.kt          # 权限/截图触发
│   │   ├── ProcessTextActivity.kt         # 划词识别入口
│   │   └── ShareReceiverActivity.kt       # 分享接收入口
│   ├── data/
│   │   ├── db/
│   │   │   ├── OrderEntity.kt             # 订单实体
│   │   │   ├── OrderGroup.kt              # 订单组实体
│   │   │   ├── OrderDao.kt                # 订单 DAO
│   │   │   ├── OrderGroupDao.kt           # 组 DAO
│   │   │   └── OrderDatabase.kt           # Room 数据库
│   │   └── repository/
│   │       ├── OrderRepository.kt         # 订单仓库
│   │       └── OrderGroupRepository.kt    # 组仓库
│   ├── helper/
│   │   ├── BrandIconResolver.kt           # 品牌图标解析
│   │   ├── NotificationHelper.kt          # 通知构建
│   │   ├── NotificationScheduler.kt       # 定时通知调度
│   │   ├── ScreenshotHelper.kt            # 截图辅助
│   │   ├── ShizukuScreenshotHelper.kt     # Shizuku 特权截图
│   │   ├── SuperIslandHelper.kt           # 小米超级岛
│   │   ├── UpdateHelper.kt                # 应用更新
│   │   └── BackupHelper.kt                # 备份恢复
│   ├── ocr/
│   │   ├── PaddleOcrHelper.kt             # PP-OCRv6 Tiny 官方 Android SDK 封装
│   │   └── TextRecognitionHelper.kt       # 核心识别逻辑
│   ├── receiver/
│   │   ├── SmsRecognitionReceiver.kt      # 短信广播接收
│   │   └── ScheduledNotificationReceiver.kt
│   ├── rules/
│   │   ├── RecognitionRuleEngine.kt       # 规则引擎核心
│   │   ├── RuleModels.kt                  # 规则数据模型
│   │   ├── RuleRepository.kt              # 规则仓库
│   │   └── RuleOnlineUpdater.kt           # 在线规则更新
│   ├── service/
│   │   ├── KeepAliveService.kt            # 前台保活服务
│   │   ├── ScreenCaptureService.kt        # 屏幕截图服务
│   │   ├── ShareRecognitionService.kt      # 分享图片识别
│   │   ├── ProcessTextRecognitionService.kt # 划词识别
│   │   ├── SmsRecognitionService.kt       # 短信识别服务
│   │   ├── CaptureTileService.kt          # 快捷设置磁贴
│   │   ├── NotificationListenerRecognitionService.kt
│   │   └── VolumeShortcutAccessibilityService.kt
│   ├── ui/
│   │   ├── AppUi.kt                       # UI 兼容层接口
│   │   ├── Md3eAppUi.kt                   # MD3E 实现
│   │   ├── MiuixAppUi.kt                  # Miuix 实现
│   │   ├── component/
│   │   │   ├── SettingsComponents.kt      # 设置组件
│   │   │   └── UpdateDialog.kt            # 更新弹窗
│   │   ├── screen/
│   │   │   ├── HomeScreen.kt              # 主页（Pager 容器）
│   │   │   ├── CaptureScreen.kt           # 识别记录页
│   │   │   ├── RulesScreen.kt             # 规则管理页
│   │   │   ├── GroupDetailScreen.kt       # 组详情页
│   │   │   ├── OrderDetailScreen.kt       # 订单详情页
│   │   │   └── settings/                  # 设置子页面
│   │   │       ├── SettingsScreen.kt      # 设置主页
│   │   │       ├── SettingsPreference.kt  # 偏好设置
│   │   │       ├── SettingsPermission.kt  # 权限管理
│   │   │       └── ...
│   │   ├── miuix/                         # Miuix UI 实现
│   │   │   ├── MiuixHomeScreen.kt
│   │   │   ├── MiuixCaptureScreen.kt
│   │   │   └── ...
│   │   ├── oobe/                          # 初始设置界面与动画
│   │   └── theme/
│   │       ├── Theme.kt                   # 主题定义
│   │       ├── ColorGenerator.kt          # 种子色→色调盘生成
│   │       └── Color.kt                   # 预设色定义
│   └── viewmodel/
│       └── OrderViewModel.kt              # ViewModel
├── assets/
│   └── default_rules.json                 # 内置识别规则
└── res/
    └── ...
```

## ✨ 核心特性

### 🔍 智能识别
- PP-OCRv6 Tiny 本地多语言文字识别
- ML Kit 条码与二维码扫描
- 自动提取取餐码、取件码和快递单号
- 支持从单张图片中识别多个号码
- 内置规则并支持自定义识别规则

### 🔒 隐私优先
- 所有数据仅在本地处理
- 不上传任何用户信息
- 无埋点 / 无追踪

### ⚡ 多入口触发
- 📸 截图识别
- 📤 分享识别
- ✂️ 划词识别
- 🔘 快捷开关（磁贴）
- 🔊 无障碍双音量键快捷触发
- 🔔 通知识别与应用范围管理
- 💬 短信识别

### 🖼️ 截图方式
- 系统 MediaProjection 截图
- Shizuku 特权截图
- Root 截图备用方案

### 🎨 现代 UI
- 默认使用 Miuix UI
- 可切换 Material 3 Expressive UI
- 两套 UI 使用各自的组件与配色设置
- 支持跟随系统、深浅色与莫奈取色
- Miuix 支持默认和 iOS-like 悬浮底栏
- 针对手机、大屏和折叠设备调整设置与底栏布局
- 首次启动提供完整的 OOBE 初始设置引导

### 📦 本地管理
- Room 本地数据库保存识别记录和分组
- 支持记录分组、完成状态和定时通知
- 支持数据备份、恢复、日志导出和空间清理

---

## 📷 动图演示
<p align="center">
  <img src="./README/20260409-205359-6d37e5.gif" width="30%" />
  <img src="./README/20260409-205349-68461c.gif" width="30%" />
  <img src="./README/20260409-205352-2849ac.gif" width="30%" />
</p>

---

## 🙏 致谢

感谢以下开源项目及其贡献者：

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android 声明式 UI 框架
- [Miuix](https://github.com/compose-miuix-ui/miuix) - Miuix UI 组件、效果与设计实现
- [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) - OOBE 视觉效果与动画实现参考，详细归属见 [NOTICE](./NOTICE)
- [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) - PP-OCRv6 Tiny 模型与 Android 部署实现
- [Google ML Kit](https://developers.google.com/ml-kit) - 设备端条码识别
- [Shizuku](https://github.com/RikkaApps/Shizuku) - 系统 API 权限能力
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) - 液态玻璃与模糊效果参考
- [ZXing](https://github.com/zxing/zxing) - 二维码相关能力
- [Room](https://developer.android.com/training/data-storage/room) - 本地数据库
- [Coil](https://coil-kt.github.io/coil/) - Compose 图片加载

同时感谢所有提交反馈、规则和代码贡献的用户与开发者。

---

## 📄 开源许可

本项目当前整体以 [GNU Affero General Public License v3.0](./LICENSE)（AGPL-3.0-only）分发，允许使用、修改、赞助、收费服务及商业分发。分发修改版，或通过网络向用户提供修改版功能时，必须依照 AGPL 提供对应源码并保留许可证与归属声明。

“澎湃记”、Hyper Note 及项目 Logo 用于标识官方项目。第三方修改版或商业分发版必须明确标注为非官方版本，不得暗示获得官方背书。OOBE 中移植和改编的 HyperCeiler 内容详见 [NOTICE](./NOTICE)。

---

## ❤️ 赞助

感谢您使用我的项目，项目制作花费的时间精力很大
如果您觉得这个项目对您有帮助，欢迎赞助支持！🙏
官方版本免费提供；赞助是自愿支持，不会购买专有授权。第三方可以依照 AGPL 进行商业使用和商业分发，但必须开放对应源码、保留归属信息并明确标注为非官方版本。
这个项目大量使用 vibe coding，如果介意请勿使用；部分 UI 与动画参考来源见应用致谢和 NOTICE。

<p align="center">
  <img src="./README/Alipay.jpg" width="30%" />
  <img src="./README/Wechat.png" width="50%" />
</p>

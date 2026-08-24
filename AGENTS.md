# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目简介

**澎湃记（Hyper Note）** — 一款 Android 应用，使用本地 OCR（PP-OCRv6 Tiny + ONNX Runtime）和 ML Kit 条码扫描，从截图中自动识别餐饮外卖取餐码和快递单号。界面语言为中文。

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
- **PaddleOcrHelper**：单例，封装 PaddleOCR 官方 Android SDK 与 PP-OCRv6 Tiny ONNX 模型用于本地文字识别
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

## Miuix UI 规范
- **修改Miuix UI相关代码前**，请先阅读 [docs/Miuix-ui-guidelines.md](docs/Miuix-ui-guidelines.md)，以符合Miux的规范，其中十余条属于「不读就会写错、写错了编译器不报错」的情况，**如无特殊说明，不要动大屏适配**

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
- **构建缓存与临时目录**: Gradle 缓存统一使用环境变量 `GRADLE_USER_HOME`（固定为 `D:\GradleCache`）；npm 等依赖缓存使用系统默认目录。**严禁在工作区（仓库）内创建任何缓存目录**（如 `.gradle-home`、`.tmpgradle`、`.gradle-isolated*`、`.gradle-user`、`.npm-cache` 等），也禁止在工作区存放临时克隆/脚本（如 `.tmp-*`、`.search-*`），用完立即删除；确需缓存时直接使用 `GRADLE_USER_HOME` 指向的目录
- **触发方式**: 如果更改规则识别相关代码时，请确保所有触发方式全部覆盖，而不只是识屏相关代码兼容
- **中文**: 请使用中文回答
- **正则**: 请使用里面的 `default_rules.json` 来修改正则以便匹配相关关键词，请在有必要的时候动代码，因为这是个有自定义正则的app，用户可以自行修改json来写自己的规则
- **手势线沉浸**: 每个页面的手势线都必须沉浸，如遇到问题，请按照常见问题来修复
- **MotionScheme**: 透明度/颜色动画用 `defaultEffectsSpec<Float>()`，尺寸/位置动画用 `defaultSpatialSpec<IntSize>()`，不能混用
- **Material Colors**: 使用 `com.google.android.material:material` 中的 `CorePalette`（字段被混淆：`a1`=primary, `a2`=secondary, `a3`=tertiary, `n1`=neutral, `n2`=neutralVariant）

## ProGuard

Keep 规则对运行时至关重要 — ML Kit（`com.google.mlkit.**`）、PaddleOCR SDK（`com.paddle.ocr.**`）以及 ONNX Runtime（`ai.onnxruntime.**`）不得被混淆。

## CI

GitHub Actions（`.github/workflows/Build and Release.yml`）：仅在推送 tag（`v*`）时触发构建，使用 JDK 17，通过 `KEYSTORE_BASE64` secret 解码签名。分支推送不触发任何 workflow。

## 手表端 Vela 快应用（app\wear）

**澎湃记手表端工程位于 `app\wear` 目录**，用于开发小米手表 S5（VelaOS 5.0）上的取餐码同步快应用，与手机端通过「设备通信 interconnect」双向通信（包名 + 签名必须与澎湃记一致）。

- **⚠️ 开发手表端代码前必须先读 `app/wear/doc/.claude/CLAUDE.md`**，其中包含 VelaOS 平台硬约束（组件/API 白名单、禁止第三方库、布局规范等），不读就会写出编译不通过或运行崩溃的代码
- **知识库位置**（由 `npx create-vela-workflow` 生成）：
  - 核心开发指南：`app/wear/doc/.claude/knowledge/vela-js-app.md`（1072 行，含项目结构/manifest/UX/组件/API 全量知识）
  - 平台规则：`app/wear/doc/.claude/rules/`（vela-platform / vela-quality / vela-layout / vela-css / vela-format / vela-coding-convention / vela-design-driven / vela-figma-mcp / project-init）
  - 组件/API 参考：`app/wear/doc/.claude/prompts/`（vela-components / vela-apis / vela-best-practices / vela-dev-guide）
  - 工作流：`app/wear/doc/.workflow/`（S1 PRD → S2 技术方案 → S3 代码，`workflow_starter.md` 入口）、`app/wear/doc/.github/agents/`（Copilot 方式）、`app/wear/doc/.kiro/`（Kiro 方式）
- **Vela 关键约束速查**：组件白名单（div/list/text/image/scroll/swiper/switch/slider/progress/picker/stack/span/marquee/barcode/qrcode/chart/image-animator/a）；API 白名单（router/app/fetch/storage/device/audio/prompt/sensor/vibrator/network/brightness/volume/battery/geolocation/record/file/crypto/configuration/interconnect/messagecenter，用前须在 manifest.json features 声明）；禁第三方库（axios/lodash/echarts/Vue/React 等）；构建仅用 aiot-toolkit；`.ux` 文件 template 仅一个根节点；onDestroy 必须清理定时器
- **模拟器**：AIoT-IDE 内置，镜像 `vela-miwear-watch-5.0`（对应 S5 的 VelaOS 5.0，466×466 圆形）；真机调试官方仅支持 S4，S5 用社区工具（AstroBox/表盘自定义工具）安装 rpk 验证

## MCP 服务器（exa 搜索）

本仓库开发环境通过 DSH（DeepSeek Harness）接入 **exa MCP 服务器** 进行网络搜索，配置位于 `C:\Users\wsj31\.dsh\profiles\web\cordis.patch.yml`：

```yaml
- id: mcp-exa
  name: '@deepseek-ai/dsh-mcp-client'
  config:
    serverName: exa
    transport: streamable-http
    url: https://mcp.exa.ai/mcp
    headers:
      x-api-key: <EXA_API_KEY>   # 实际 key 见 DSH 配置，勿提交到仓库
```

- **提供工具**：`web_search_exa`（语义化搜索，返回结构化结果）、`web_fetch_exa`（抓取网页全文为 Markdown）
- **使用提示**：搜索时使用「描述理想页面」的自然语言而非关键词堆砌；`web_search_exa` 结果摘要不足时用 `web_fetch_exa` 跟进抓取具体 URL
- **注意**：内置 `web_search` 工具依赖 DeepSeek 原生搜索，若报 `Authentication Fails` 属正常（该 key 仅用于 exa MCP），请改用 exa MCP 的搜索工具
- **⚠️ 工具未挂载时的兜底方案（Node 直连 exa MCP）**：若当前会话工具列表里没有 `web_search_exa`/`web_fetch_exa`（MCP 客户端插件未加载），可直接用 Node 走 streamable-http JSON-RPC 调用。已验证：`pwsh` 的 `Invoke-RestMethod`/`Invoke-WebRequest` 和 `curl.exe` 会因 schannel 凭据问题失败（`SEC_E_NO_CREDENTIALS`/`SSL connection could not be established`），**必须用 Node 内置 `https` 模块**。在 `D:\Hypernotesuper` 下运行：

  ```bash
  node -e "
  const https = require('https');
  const KEY = '<EXA_API_KEY>'; // 从 C:\Users\wsj31\.dsh\profiles\web\cordis.patch.yml 读取，勿提交仓库
  let sessionId = null;
  function call(method, params, sid) {
    return new Promise((resolve, reject) => {
      const body = JSON.stringify({jsonrpc:'2.0',id:1,method,params});
      const headers = {'Content-Type':'application/json','Accept':'application/json, text/event-stream','x-api-key':KEY,'Content-Length':Buffer.byteLength(body)};
      if (sid) headers['Mcp-Session-Id'] = sid;
      const req = https.request('https://mcp.exa.ai/mcp', {method:'POST', headers}, (res) => {
        let data=''; res.on('data',c=>data+=c); res.on('end',()=>{ resolve({status:res.statusCode, sid:res.headers['mcp-session-id']||null, body:data}); });
      });
      req.on('error', reject);
      req.write(body); req.end();
    });
  }
  (async () => {
    const init = await call('initialize', {protocolVersion:'2025-03-26',capabilities:{},clientInfo:{name:'dsh-search',version:'1.0'}});
    sessionId = init.sid;
    await call('notifications/initialized', {}, sessionId);
    const r = await call('tools/call', {name:'web_search_exa', arguments:{query:'<自然语言描述理想页面>', numResults:10}}, sessionId);
    console.log(r.body);
  })().catch(e => console.log('ERR', e.message));
  "
  ```

  - **协议要点**：先 `initialize` 拿到响应头 `Mcp-Session-Id`，再发 `notifications/initialized`，之后每次 `tools/call` 都带 `Mcp-Session-Id` 头；响应是 SSE 流（`event: message\ndata: {...}`），`data:` 行内是 JSON。
  - **参数名坑**：`web_fetch_exa` 的参数是 `urls`（字符串数组，如 `urls:['https://…']`）**不是** `url`；`web_search_exa` 用 `query`（自然语言）+ 可选 `numResults`；`web_fetch_exa` 可选 `maxCharacters` 截断。
  - **结果解析**：正文在 `result.content[0].text` 里，可用 `console.log(JSON.parse(r.body.split('\ndata: ')[1]).result.content[0].text)` 或直接截取 `r.body.slice(0, N)` 查看。

## 常见问题

- 实体字段与迁移列类型不匹配会导致升级时崩溃 — 务必确认 `OrderEntity.groupId` 为 `Long?`，与 `OrderGroup.id: Long` 类型一致
- Android 14+ 的提升通知需要 `POST_PROMOTED_NOTIFICATIONS` 权限
- Shizuku 特权操作需要用户已安装并授权 Shizuku
- 建议将应用加入电池优化白名单，以保障后台服务稳定运行
- 如果页面手势线未沉浸时，问题是外层 `Column` 有 `windowInsetsPadding` 挡住了底部导航栏区域，需要去掉，改由 `LazyColumn` 的 `contentPadding` 处理
- Miuix BottomSheet 模糊使用 `Animatable` + `snapshotFlow` 同步，`onDismissFinished` 统一调用 `BlurState.hide()`，不要在 `LaunchedEffect(show=false)` 中重复调用

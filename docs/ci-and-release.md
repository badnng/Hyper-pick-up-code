# CI、发布与 Telegram 分发

## 触发与产物

| 操作 | 构建 | 分发 | 更新 App 内版本索引 |
| --- | --- | --- | --- |
| 本仓库 master / dev 分支 push | 与 tag 发布相同的正式签名 Release APK | Actions；启用后发送 Telegram | 否 |
| 其他分支 push、PR、fork | 未签名 Release APK，仅构建检查 | Actions | 否 |
| 手动运行 CI | 按所选分支采用以上规则 | Actions；master/dev 可发 Telegram | 否 |
| 推送 `v*` tag | 正式签名 Release APK + 手表 RPK | GitHub Release | 是 |

GitHub Actions 在代码 **push 到 GitHub** 时触发，不会感知本地 commit。如果一次 push 包含多个 commit，构建的是这次 push 的最后一个提交；需要每个 commit 都有包时，逐次 push。普通 push 不取消旧构建；PR 的旧构建会被新提交取消。同仓库人工分支同时有 PR 时，两种事件都会检查；`dependabot/**` 分支只执行 PR 检查，不重复执行 push 构建。

Actions 列表中，`Dependabot Updates` 是 GitHub 检查 Action 依赖并提出升级 PR 的任务，不构建或发布 App。它创建 PR 后会触发一次 `CI`；`Build and Release` 只在推送 `v*` tag 时运行。共用步骤里的 `release` 是 Android 构建类型，不代表创建 GitHub Release。

CI 与 tag 发布的 Android 构建步骤共用 `.github/actions/build-android-release/action.yml`：相同 JDK/SDK、Gradle Wrapper、`:app:assembleRelease`、R8 混淆、资源压缩、arm64 配置；master/dev 分发包也使用相同的签名密钥和证书校验。CI 包就是供用户安装测试的 Release 包，**不会创建 GitHub Release**。两个 workflow 保留各自的触发和分发步骤，手表 RPK 仍随 tag 发布。

CI APK 和独立的 R8 mapping artifact 保存 14 天，便于测试用户交付包时还原崩溃堆栈。APK 文件名包含版本、CI 运行编号、Git 短提交号和构建类型，同时保存 `SHA256SUMS` 与 `build-info.json`。App 已有 Git 提交号展示，不改正式版本号规则。

master/dev CI 包与正式包签名一致，满足 Android 的版本限制时可以覆盖安装；版本号不会为每次 CI 自动递增，旧版本号的包不能覆盖较新的安装包。其他分支/PR/fork 不注入正式密钥，使用同一 Release 构建但生成未签名 APK，不能直接安装，也不会发到 Telegram。

交付包构建只执行 `:app:assembleRelease`，不附加 `testReleaseUnitTest` 或 `testDebugUnitTest`。当前项目没有前者，之前同时请求它会让 Gradle 在选择任务时直接失败。仓库现有的 Android 单元测试代码保留；分发脚本的 Python 离线测试继续在共用步骤中运行。

## 无需自建更新服务器

保留现有 `master/Stable.json`、`master/Dev.json` 及客户端读取格式。CI 不写这些文件。

发布流程为：构建和校验 → 上传草稿 Release 的 APK/RPK → 发布 Release → 提交版本 JSON 到 master。版本信息来自 APK 的 `output-metadata.json`，下载地址指向该 tag 的 Release 附件。这样不会在 APK 尚未上传时提前提示更新。

更新索引是独立的 `update_index` job。若 Release 已成功而索引提交失败，修复问题后选择 **Re-run failed jobs**，直接使用保留 90 天的索引 artifact 恢复，不重新构建。已发布的 tag 禁止重跑完整发布流程来覆盖附件；源码有变动时递增版本并创建新 tag。未发布的草稿可继续重试。

更新索引的写入使用独立临时检出目录，遇到并发提交会重新读取 master 并重试，不强推；较旧或相同 `versionCode` 不覆盖现有索引。发布前必须递增 `app/build.gradle.kts` 的 `versionCode`。Stable 同步更新 Stable/Dev，但不会把 Dev 中较高的版本降级。master 分支保护必须允许此 Actions 身份写入这些索引，否则 Release 已发布但索引提交会失败。

客户端仍使用已有 `badnng.dpdns.org` 代理地址；本次没有移除或改变该网络依赖。源数据和安装包均托管在 GitHub，无需新增后端。Actions artifact 需登录 GitHub 且会过期，因此只用于 CI 分发，不作为 App 自动更新地址。

## Tag 规则

通道由 tag 后缀确定，避免通过“提交所在分支”的不确定顺序猜测。

| 示例 | 通道 | GitHub 状态 |
| --- | --- | --- |
| `v26.10.19-C01` | Stable，同时更新 Dev | 正式 Release |
| `v26.10.19-C01-Dev`、`v26.10.19-C01-beta.1` | Dev | Pre-release |
| `v26.10.19-C01-DevUI` | DevUI | Pre-release |

Dev 还接受 `alpha`、`rc`、`preview`；后缀大小写不敏感，可带数字。不要移动已经发布的 tag。手表端沿用 `npm ci` / `npm run release`，包内版本必须与其 manifest 一致。

## Telegram 接入

1. 在 Telegram 打开官方 [@BotFather](https://t.me/BotFather)，发送 `/newbot` 创建机器人，保存得到的 Token。
2. 创建用于 CI 分发的频道，将机器人添加为管理员并授予“发布消息”权限。
3. 在 GitHub 仓库 → **Settings → Secrets and variables → Actions → Secrets** 添加：
   - `TELEGRAM_BOT_TOKEN`：BotFather 提供的 Token。
   - `TELEGRAM_CHAT_ID`：公开频道可直接用 `@频道用户名`；私有频道使用 `-100…` 数字 ID。
   - `TELEGRAM_API_ID`（可选）：登录 [my.telegram.org/apps](https://my.telegram.org/apps)，在 **API development tools** 创建应用，复制数字 `api_id`。
   - `TELEGRAM_API_HASH`（可选）：同一应用页面的 `api_hash`。这两个是运行官方 Bot API Server 的应用凭据，不是 BotFather Token；已有的 Bot Token 和频道 ID 继续使用。两项均填写时使用 local 模式，任一缺失、为空或只有空白时自动回退到普通云端 Bot API。
4. 在同页面 **Variables** 中添加 `TELEGRAM_ENABLED`，值为 `true`。未开启时不运行 Telegram 任务。
5. 推送 master/dev，或在 Actions → CI → Run workflow 选择该分支。成功后，频道将收到 APK 或下载链接通知，附版本、构建号、分支、提交 hash、提交说明，以及 Actions 下载/构建详情按钮。

私有频道 ID 可通过 Bot API 的 `getUpdates` 获取：机器人加入频道后，在频道发一条消息，读取返回的 `channel_post.chat.id`。已有 webhook 的机器人不能同时使用 `getUpdates`，建议为 CI 创建专用机器人。不要提交 Token，也不要把带 Token 的 API URL 放进公开日志或截图。

现有签名 secrets 继续复用：`KEYSTORE_BASE64`、`KEY_ALIAS`、`KEY_PASSWORD`、`STORE_PASSWORD`；tag 发布还需要 `RPK_CERT_BASE64`、`RPK_PRIVATE_KEY_BASE64`。PR 不注入发布签名密钥，fork 不向本项目频道推送。

应用注册表可填写：App title `HyperNote CI`、Short name `hypernoteci`、URL 为仓库地址或留空、Platform 选择 **Bot / Server**、Description 填写 `Upload HyperNote CI APK builds to my channel using a temporary local Bot API server in GitHub Actions.`。CI 不需要你的手机号、登录验证码、账户密码或用户会话文件。

Telegram job 先校验配置并输出所选模式。App API ID/Hash 完整时，在 GitHub 托管的 Ubuntu 24.04 runner 上临时运行官方 **Local Bot API Server**：恢复服务程序缓存（未命中则编译官方固定提交及其 TDLib 子模块）→ 在 `127.0.0.1:8081` 启动 local 服务 → 从云端 Bot API 注销并验证本地机器人登录 → 直接上传 APK → 从本地服务注销并停止进程。无需自建常驻服务器，不开放公网端口。Telegram job 不编译手机 App，直接下载前面 Android job 的同一份 APK。

local 模式下超过 50 MB 的文件仍以附件发送，保留富文本、Git hash、提交说明和 Actions 下载按钮；最高支持 2000 MB，脚本采用 2,000,000,000 字节的保守上限，不会拆分 APK 或因上传失败改发链接。使用本地文件 URI 交给服务读取，不把整个 APK 载入 Python 内存。

App API ID/Hash 任一为空时，跳过服务程序的缓存恢复、编译、缓存保存及本地服务启动，直接使用 Bot Token 调用 `https://api.telegram.org`，也不会执行 `logOut`。APK 不超过 50,000,000 字节时以附件发送；超过这个阈值时发送构建信息和 Actions 下载按钮，并在通知和日志中明确说明普通 Bot API 的 50 MB 限制。Actions 下载需要登录 GitHub，产物保留 14 天。

仅缓存无凭据的服务程序；首次编译较慢，后续命中缓存即可复用。机器人会话保存在 runner 的临时目录，任务结束时清理，不进入 cache/artifact。Telegram job 使用同一个并发组串行发送并最多排队 100 个任务；Android 构建仍可并行。此机器人应专用于 CI：迁移会调用 `logOut`，退出云端后 10 分钟内不能登录回官方托管 Bot API，但可以立即使用 local 服务。

Bot Token / Chat ID 仍为必填；两项 App API 均非空但格式错误时仍明确报错。自动回退只针对 App API 凭据不完整，不会掩盖鉴权、网络或发送失败。Telegram 发送失败不会影响已完成的 Android 构建和 Actions 产物，可以重跑失败的 Telegram job；上传超时时脚本不会自动重复发包，手动重跑前先检查频道是否已收到附件。此前使用过 local 模式的机器人若仍处于云端登录冷却期，需等冷却结束后再重跑云端发送。

## 缓存与检查

- JDK 21 与当前项目 toolchain 对齐；使用仓库 Gradle Wrapper。
- SDK 包使用 Google 仓库中的 `platforms;android-37.0`，Gradle 同时设置 `compileSdk = 37` 和 `compileSdkMinor = 0`；不要简化为不存在的 `platforms;android-37`。
- APK 签名校验固定使用已安装的 Build Tools `36.1.0`，不自动挑选 runner 的最高版本；解析同时兼容旧版 `Signer #1` 和新版 `V2 Signer:` 等格式，读取失败与证书不匹配均会中止分发。
- `setup-gradle` 缓存 wrapper、依赖、转换结果及 Gradle 本地构建缓存。master/dev 可写；PR/其他分支/tag 只读，避免 PR 写入共享构建缓存。
- 不再执行额外的 `gradlew dependencies`，不在 CI 调用 `clean`，APK artifact 关闭重复 ZIP 压缩。
- 不启用未经本项目验证的配置缓存；已有任务构建缓存保持启用。
- Actions 固定完整提交 SHA，由 Dependabot 每周提出更新。
- 本地遵守仓库约定，不自动编译；脚本可运行 `python -B -m unittest discover -s .github/scripts -p 'test_*.py'`，工作流可使用 `actionlint` 检查。完整 APK 构建与签名结果以远程 Actions 为准，安装后的功能仍需实机验证。
- `concurrency.queue: max` 使用 [GitHub 官方排队语法](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idconcurrency)，用于保留待发送任务；actionlint 1.7.12 的旧 schema 尚不识别该字段，检查时只需排除这一条已确认的兼容性提示。

参考：[Gradle Actions 缓存](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)、[GitHub artifact 下载与权限](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)、[官方 Local Bot API Server](https://github.com/tdlib/telegram-bot-api)、[Telegram 应用凭据](https://core.telegram.org/api/obtaining_api_id)。

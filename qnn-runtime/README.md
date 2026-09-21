# QNN Runtime 产物说明（PP-OCRv6 tiny det / V68–V81）

编译工具链：**QAIRT SDK 2.50.0.260828**（`D:\qairt\qairt\2.50.0.260828`）
目标模型：**PP-OCRv6 tiny det**，固定输入 `[1,3,2688,1216]`，输出 `fetch_name_0 [1,1,2688,1216]`

---

## 1. 目录结构

```
qnn-runtime/
├── General/                      # 所有架构共用（放 app 私有目录同一层）
│   ├── libQnnHtp.so              # 3,978,976 B
│   └── libQnnSystem.so           # 4,068,024 B
├── V68/  det_ctx.onnx  libQnnHtpV68Skel.so  libQnnHtpV68Stub.so
├── V69/  det_ctx.onnx  libQnnHtpV69Skel.so  libQnnHtpV69Stub.so
├── V73/  det_ctx.onnx  libQnnHtpV73Skel.so  libQnnHtpV73Stub.so
├── V75/  det_ctx.onnx  libQnnHtpV75Skel.so  libQnnHtpV75Stub.so
├── V79/  det_ctx.onnx  libQnnHtpV79Skel.so  libQnnHtpV79Stub.so
└── V81/  det_ctx.onnx  libQnnHtpV81Skel.so  libQnnHtpV81Stub.so
```

设备端落地时，把 `General/` 两个 + 该机型的 `V<arch>/` 两个 `.so` **平铺到同一个目录**
（`filesDir/npu_libs/arm64-v8a/`），共 **4 个 `.so`**；`det_ctx.onnx` 另行分发。

---

## 2. 各架构 ctx 与体积

| 架构 | 代表 SoC | soc_id | det_ctx.onnx | Skel | Stub |
|---|---|---|---|---|---|
| V68 | SM8350 / SA8295 | 39 | 4,365,238 B | 10,981,860 B | 783,992 B |
| V69 | SM8450 | 36 | 4,365,238 B | 12,383,460 B | 783,992 B |
| V73 | SM8550 | 43 | 3,869,622 B | 12,375,248 B | 791,424 B |
| V75 | SM8650 | 57 | 3,869,622 B | 12,358,868 B | 791,424 B |
| V79 | SM8750 | 69 | 3,709,878 B | 12,559,528 B | 791,424 B |
| V81 | SM8850 | 87 | 3,963,830 B | 13,546,372 B | 816,184 B |

每个 ctx 的 `dspArch` 均经 `qnn-context-binary-utility` 逐个校验，与实际目标架构一致。

---

## 3. 版本绑定（关键约束）

**ctx 的 blob 版本必须 ≤ 运行库版本。** 本次实测：

| 组合 | 结果 |
|---|---|
| ctx 2.50 (blob 4.0.5) + 运行库 2.42 | ✗ `Failed to create context from binary. Error code: 5000` |
| ctx 2.50 (blob 4.0.5) + 运行库 **2.50** | ✓ `backend=NPU strict=true` |
| ctx 2.42 (blob 3.3.4) + 运行库 2.42 | ✓ `backend=NPU strict=true` |

错误码 `5000` = `QNN_MIN_ERROR_CONTEXT`（`QnnCommon.h:81`）。
**因此本目录的运行库与 ctx 一并升级到 2.50，必须配套使用**，不可与旧版 2.42 库混搭。
（旧 2.42 成套库已备份在 `D:\Hypernotesuper\qnn-runtime-2.42-backup\`。）

---

## 4. 数值一致性（与 demo 实测对齐）

- **精度语义**：QNN HTP 要求图 IO 为 **float32**，内部自动以 fp16 数学执行、输出转回 float32
  （官方文档：`QNN HTP Precision`，`QNN_HTP_GRAPH_CONFIG_OPTION_PRECISION` 自 2.35 起废弃）。
  本 ctx 的 `x` / `fetch_name_0` 均为 `QNN_DATATYPE_FLOAT_32`，与 demo 端侧编译产物一致。
  转换时**没有**传 `--float_bitwidth 16`——若传了会得到 `FLOAT_16` 的图 IO，与 app 的 float32 输入不匹配。
- **逐元素比对**（同一输入，demo 原生 ctx vs 本离线 ctx，均在真机 HTP 上执行）：

  | 指标 | 数值 |
  |---|---|
  | 输出元素总数 | 3,268,608 |
  | 完全相等 | 3,268,600（**99.9998%**） |
  | 最大绝对差 | 0.000488281308 = **恰好 1 个 fp16 ULP（2⁻¹¹）** |
  | 平均绝对差 | 1.2e-09 |

  残余差异来自 fp16 加法结合序，硬件决定、不可消除。

---

## 5. 端到端真机验证（小米 houji / SM8650 / HTP v75）

用本目录的 2.50 四件套 + `V75/det_ctx.onnx` 替换 demo 的对应文件后启动：

```
NpuSupport: ADSP_LIBRARY_PATH -> /data/user/0/com.paddle.ocr.demo/files/npu_libs/arm64-v8a
ORTSessionManager: [det] 以缓存的 context 作为模型加载:.../det_b141f13a1e53_ctx.onnx
adsprpc: Successfully opened file .../npu_libs/arm64-v8a/./libQnnHtpV75Skel.so
adsprpc: remote_handle64_open: ... file:///libQnnHtpV75Skel.so?qnn_2_50_0_skel_handle_invoke ... domain 3
ORTSessionManager: 会话就绪 backend=NPU strict=true detShape=1, 3, 2688, 1216 耗时=363ms
                   note=NPU(HTP v75,仅 det 在 HTP 上;rec 留在 CPU 以保精度与速度)
```

`strict=true` 表示 `session.disable_cpu_ep_fallback=1` 下整图仍落在 HTP——零算子回落 CPU。

---

## 6. EPContext 外壳结构

`det_ctx.onnx` 是标准 ORT **EPContext** 模型，结构逐字段复刻真机产物：

```
ir_version = 13
graph.input  = x            float32 [1,3,2688,1216]
graph.output = fetch_name_0 float32 [1,1,2688,1216]
单节点 EPContext (domain=com.microsoft)
  main_context      = 1
  source            = 'QNNExecutionProvider'
  embed_mode        = 1
  max_size          = 0
  ep_cache_context  = <裸 context binary>
  partition_name    = 节点名
  ep_sdk_version    = 'v2.50.0.260828221209'
```

ORT 侧无需改代码：`libQnnSystem.so` 负责读取，加载方式与 demo 原有 ctx 完全相同。

---

## 7. 16 KB 页对齐

| 文件 | LOAD 对齐 |
|---|---|
| `libQnnHtp.so`、`libQnnSystem.so`、全部 `*Stub.so` | `0x4000` ✓ |
| 全部 `*Skel.so` | `0x1000` ⚠ |

Skel 由 **DSP 进程**（`cdsprpcd`）按路径打开，不经过 app 链接器，`0x1000` 无影响。

---

## 8. 复现命令

```powershell
# 1) onnx -> DLC（保持 float32，勿加 --float_bitwidth 16）
python <SDK>/bin/x86_64-windows-msvc/qairt-converter `
  --input_network det.onnx --output_path det_fp32.dlc `
  --export_format DLC_DEFAULT -d x 1,3,2688,1216

# 2) DLC -> context binary（按 SoC 指定，dsp_arch 与 soc_id 同时给）
#    config: { "graphs":[{"graph_names":["det_fp32"]}],
#              "devices":[{"dsp_arch":"v75","soc_id":57}] }
qnn-context-binary-generator.exe `
  --backend <SDK>/lib/x86_64-windows-msvc/QnnHtp.dll `
  --dlc_path det_fp32.dlc --binary_file ctx_v75 `
  --config_file be_cfg.json --output_dir out

# 3) 校验真实架构
qnn-context-binary-utility.exe --context_binary ctx_v75.bin --json_file info.json

# 4) 封装 EPContext（脚本已备）
python wrap_epctx.py
```

关键坑：`soc_id` **会覆盖** `dsp_arch`，两者必须属于同一架构，否则产物 `dspArch` 与预期不符
（例如 v68 配 SM8550=43 会得到 v73）。转换脚本与校验脚本见 `D:\qairt\build\`。

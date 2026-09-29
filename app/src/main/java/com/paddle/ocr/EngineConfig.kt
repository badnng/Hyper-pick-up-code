// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr

/**
 * 推理后端选择。
 *
 * 默认是 [CPU],与改造前行为完全一致;NPU 必须显式开启,且要同时满足
 * SoC 白名单、打包策略、模型资源三项条件(见 `com.paddle.ocr.npu.NpuSupport`)。
 */
enum class AccelBackend {
    /** ONNX Runtime 默认 CPU EP。所有设备可用。 */
    CPU,

    /** Android NNAPI EP。零额外体积,在高通平台可能被驱动下沉到 Hexagon,但只对量化模型有效。 */
    NNAPI,

    /** 高通 QNN HTP 后端,真正跑在 Hexagon NPU 上。 */
    NPU,
}

/**
 * QNN(HTP)后端参数。字段名与 ONNX Runtime 的 provider option / session config key 一一对应,
 * 便于对照官方文档排查。
 */
data class NpuOptions(
    /**
     * `htp_performance_mode`。可选:default / burst / balanced / high_performance /
     * sustained_high_performance / power_saver / high_power_saver / low_balanced / low_power_saver。
     *
     * 默认 sustained_high_performance:持续负载下比 burst 更稳,适合连续识别。
     */
    val performanceMode: String = "sustained_high_performance",

    /**
     * `enable_htp_fp16_precision`。默认开启:fp32 模型会以 fp16 精度在 HTP 上推理,
     * 这样不量化也能跑;若模型已做 QDQ 量化,该选项不影响量化部分。
     */
    val enableFp16Precision: Boolean = true,

    /**
     * `htp_graph_finalization_optimization_mode`。0=默认,1=准备快但图较差,
     * 2=更优,3=准备最慢但图最优。端侧只编译一次并缓存,所以默认取 2。
     */
    val graphFinalizationOptimizationMode: Int = 2,

    /** `vtcm_mb`,留空表示由 QNN 自行决定。 */
    val vtcmMb: Int? = null,

    /**
     * `session.disable_cpu_ep_fallback`。true 时模型必须完整落在 HTP 上,否则建会话失败。
     * 默认为 false:允许部分算子回退 CPU,保证可用性。
     */
    val disableCpuEpFallback: Boolean = false,

    /**
     * 是否**校验 QNN 真的接管了整图**(默认 true),而不是默默回落到 CPU。
     *
     * ## 为什么需要它
     *
     * QNN 建图失败时,**ORT 不会让建会话失败** —— 它只是把算子分回 CPU EP。
     * 于是代码以为 NPU 可用、界面显示 `backend=NPU`,实际却在 CPU 上跑。
     * 真机实测过这个陷阱(小米 houji / SM8650):det 推理 400ms ≈ CPU 的 379ms,
     * 而日志里其实是 `QNN SetupBackend failed / QNN_DEVICE_ERROR_INVALID_CONFIG`。
     *
     * 开启后会用 `session.disable_cpu_ep_fallback=1` 建一次会话:
     * 只要有任何节点落到 CPU,建会话即失败 → 自动降级为非严格模式重建,
     * 并把 [backendNote] 标成「部分算子回落 CPU」,不再谎报。
     *
     * 代价是 NPU 路径多一次建会话(实测几十毫秒,且仅首次)。
     */
    val verifyFullOffload: Boolean = true,

    /**
     * 检测模型是否放到 HTP 上。
     *
     * det 是这套流水线里**唯一值得上 NPU 的模型**:输入
     * `[1,3,2688,1216]` 计算量大,HTP 能把它从 359ms 压到 67ms(5.4×)。
     */
    val detOnHtp: Boolean = true,

    /**
     * 识别模型是否也放到 HTP 上。**默认 false,这是实测结论不是保守设定。**
     *
     * ## 骁龙 8 Gen 3 真机四组对照(统一输入 shape,预热 3 次取 9 次中位数)
     *
     * | 模型 | 纯 CPU | 严格 HTP | 胜出 |
     * |---|---|---|---|
     * | det `[1,3,2688,1216]` | 359 ms | **67 ms** | HTP 快 **5.4×** |
     * | rec `[1,3,48,320]` | **3 ms** | 6 ms | CPU 快 2.0× |
     * | rec `[1,3,48,640]` | **7 ms** | 13 ms | CPU 快 1.9× |
     * | rec `[1,3,48,1280]` | **15 ms** | 25 ms | CPU 快 1.7× |
     *
     * rec 在**所有**宽度上 CPU 都更快,没有任何交叉点,所以不存在
     * "换个宽度就能让 HTP 反超"的配置。原因有两条:
     *
     * 1. **计算量太小**,摊不平 HTP 单次图启动的固定开销(~20ms 量级);
     * 2. **输出张量太大**:rec 输出 `[1,T,6906]`,W=1280 时 T=160,
     *    即 160×6906×4B ≈ **4.2MB**,每次推理都要从 DSP 拷回 CPU,
     *    这部分开销在 CPU 上根本不存在。
     *
     * ## 关掉它同时解决精度问题
     *
     * rec 走 CPU 就用回**原版动态 shape 模型**,不再受 `[1,3,48,W]`
     * 的宽度上限约束,长文本行不会被横向压扁 ——
     * 精度回到原版水平,**速度还更快**。两个目标不再互斥。
     *
     * 想强制全 HTP(例如为了省电或对比测试)把它设成 true 即可。
     */
    val recOnHtp: Boolean = false,

    /** 是否启用 QNN context binary 缓存(端侧编译一次后落盘,后续直接加载)。 */
    val contextCacheEnabled: Boolean = true,

    /** context 缓存目录名,位于 app 私有 filesDir 下。 */
    val contextCacheDirName: String = "npu_cache",

    /**
     * 检测是否用**原生分辨率分块推理**(仅 NPU 路径有效,**默认关**)。
     *
     * ## 为什么默认关:矩形画布更快且一样准
     *
     * 固定 shape 的 det 模型有两个用法:
     *
     * | 做法 | 推理次数 | 实测逐行 IoU |
     * |---|---|---|
     * | **矩形画布单次**(默认) | **1** | **0.476 ~ 0.488** |
     * | 分块 960(本开关打开) | 6 ~ 10 | 0.406 ~ 0.435 |
     * | CPU 原生(参考) | 1 | 0.393 ~ 0.400 |
     *
     * 关键前提是**画布形状要匹配屏幕比例**(手机竖屏约 0.45),
     * 而不是正方形。早期用 960×960 正方形画布时,1200×2670 的截屏被压成
     * 横向 0.80×、纵向 0.36×(横纵差 2.2 倍),文字压扁、框错位 ——
     * 那才是"框标注不准"的真正原因,与量化无关。
     *
     * 换成匹配比例的矩形画布(如 896×1984)后,整图缩放不产生形变,
     * 单次推理即可,精度反而高于 CPU 基准。
     *
     * ## 什么时候才需要打开分块
     *
     * 图像内容远小于整屏、或字特别小、且矩形画布下采样过多时
     * (例如把 3200×1440 的长图塞进 896×1984)。分块在**原生分辨率**上跑,
     * 字高零损失,代价是推理次数按块数倍增。
     *
     * 注意:画布尺寸由模型固化决定(`tools/qnn/convert_to_npu.py --det-size`),
     * H 与 W **都必须是 32 的倍数**,否则固化会报 ShapeInferenceError。
     */
    val detTiledInference: Boolean = false,

    /**
     * 相邻分块的重叠比例(0~0.5)。重叠是为了避免文字正好被切在块边界上而漏检:
     * 0.25 表示每块与邻块重叠 25%,即步长 = 75% × 画布。
     *
     * 注意:分块的**块尺寸恒等于模型固化的输入尺寸**,不可配置 ——
     * 固定 shape 的会话只接受那一个尺寸,传别的尺寸会直接抛异常。
     */
    val detTileOverlap: Double = 0.25,

    /**
     * 分块合并出的概率图**像素总数**上限,超出则按整数倍降采样,默认 400 万。
     * 仅在 [detTiledInference] 打开时生效。
     *
     * 常规手机截屏 1200×2670 = 320 万像素,低于上限 → 不降采样,零损失。
     * 长截图(如 1080×20000 = 2160 万像素)若按原分辨率累计 float 缓冲要 86 MB,
     * 降采样后只需几 MB。**坐标不受影响** —— DB 后处理用
     * `原图宽 / 概率图宽` 把框线性放大回原图(与 CPU 路径同一机制)。
     *
     * 设为 0 或负数表示不限制(有 OOM 风险,长截图慎用)。
     */
    val maxProbPixels: Int = 4_000_000,

    /**
     * 画布**宽高比**与图像宽高比的偏差超过该阈值时,即使关闭了分块,
     * 也自动改用分块推理(避免把长图压进不匹配的画布导致框错位)。
     *
     * 例:画布 896×1984(比例 0.4516)遇上 4:3 的图片(比例 0.75),
     * 偏差 0.30 > 0.12,会自动分块,而不是强行拉伸。
     * 设为 <= 0 表示不检查(始终信任画布)。
     */
    val aspectGuardTolerance: Double = 0.12,

    /** NPU 建会话失败时是否自动回退到 CPU。 */
    val allowFallbackToCpu: Boolean = true,
)

data class EngineConfig(
    val numThreads: Int = 4,

    /** 推理后端。默认 CPU,改动此值才会启用 NPU。 */
    val accel: AccelBackend = AccelBackend.CPU,

    /** NPU 参数,仅在 [accel] 为 [AccelBackend.NPU] 时生效。 */
    val npu: NpuOptions = NpuOptions(),
)

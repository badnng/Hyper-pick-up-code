package com.Badnng.moe.npu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.ocr.PaddleOcrHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「空闲释放后再识别,NPU 仍可用」端到端真机验证 —— 即缺陷修复是否生效。
 *
 * ## 被验证的缺陷(真机 912a4148 已确诊)
 *
 * 同进程内 NPU 会话被「空闲 60 秒自动释放」销毁后,QNN **无法重建**且**永久失败**,
 * NPU 静默降级为 CPU:
 * ```
 * apps_dev_init failed for domain 3, errno File exists
 * open_dev (-1) failed for domain 3
 * remote_handle64_open failed for file:///libQnnHtpV69Skel.so?...&_dom=cdsp
 *   → QNN_DEVICE_ERROR_INVALID_CONFIG: Invalid config values
 * ```
 * 机理:FastRPC domain 3 的设备 fd 每进程只建立一次、`domain_deinit` 不真正释放,
 * 而 QNN 主机库经 `System.load` 后进程内不可卸载 ⇒ **销毁即不可逆**。
 *
 * ## 验证逻辑(两个 run 之间隔着一次真实的空闲释放定时器)
 *
 * ```
 * RUN A  initAsync() → 断言 isNpuActive()==true          (NPU 基线必须成立)
 *         recognizeAsync(bitmap) → 启动 60s 空闲释放定时器
 * sleep   65s(比 IDLE_RELEASE_DELAY_MS=60_000 多留 5s 余量)
 * RUN B  recognizeAsync(bitmap) → 断言 isNpuActive()==true  ← 本次修复的核心断言
 *                                  断言 isUnusableForProcess()==false
 *                                  断言 RUN B 返回非 null(识别链路没坏)
 * ```
 *
 * **修复生效的直接证据**有两条,缺一不可:
 * 1. RUN B 后 [PaddleOcrHelper.isNpuActive] 仍为 `true`(修复前为 `false`,永久降级);
 * 2. logcat 出现 `跳过会话释放（保留 NPU 常驻）: reason=idle-60000ms`(tag `PaddleOcrHelper`)——
 *    这条日志的触发前提是 [PaddleOcrHelper.releaseResourcesLocked] 判定
 *    `!allowNpuDestroy && currentOnNpu && NpuAccelerator.isEnabled(context)`,而
 *    `allowNpuDestroy=false` **只**由 [PaddleOcrHelper.scheduleIdleRelease] 传入,
 *    因此它出现即证明「空闲释放路径确实跳过了 NPU 会话销毁」。
 *    本测试无法直接读到该日志对象,须配合 `logcat -s NpuIdleReuseTest:V PaddleOcrHelper:V ...` 同步观察。
 *
 * ## ⚠️ 本文件的三条硬约束(均为真机踩过的坑)
 *
 * 1. **`@Test` 必须是块体** `fun x() { ... }`,**禁止** `fun x() = runBlocking { ... }`。
 *    `Log.i` 返回 `Int`,表达式体会把方法签名推断为返回 `Int`,JUnit4 直接报
 *    `InvalidTestClassError: Method x() should be void`,**整个测试类一个用例都跑不了**。
 * 2. **禁止调用 `NpuLibLoader.clear()`** 或任何删除 `filesDir/npu_libs/` 下 QNN 运行时的方法 ——
 *    那会删掉用户真机上已下载的约 21MB 资源(`libQnnHtp.so` / `libQnnSystem.so` /
 *    `libQnnHtpV69Stub.so` / `libQnnHtpV69Skel.so`)与 `models/npu/ctx/69/det_ctx.onnx`,
 *    且删除后**无法恢复**,用户必须重新下载。资源缺失一律走 `assumeTrue` **跳过**。
 * 3. **不做任何 mock**:这是真机端到端验证,必须走真实 QNN/FastRPC 路径。
 *
 * ## 时长与超时
 *
 * 单个用例耗时约 70s(65s 等待 + 两次真实推理)。65s **不可缩短** ——
 * `IDLE_RELEASE_DELAY_MS = 60_000L` 是 `private const`,测试改不了,少于 60s 就测不到空闲释放。
 * AndroidJUnitRunner 对普通 JUnit4 用例**没有**单用例超时限制(`@Test(timeout=...)` 未使用),
 * 因此 65s 等待安全;但 `am instrument -w` 与 CI 侧不得设置 < 120s 的命令超时。
 */
@RunWith(AndroidJUnit4::class)
class NpuIdleReleaseReuseTest {

    /**
     * 核心用例:空闲释放后再识别,NPU 仍须可用。
     *
     * 块体写法是硬要求,理由见类头 KDoc 第 1 条。
     */
    @Test
    fun npuRemainsActiveAfterIdleRelease() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        // ---------- 前置:打开 NPU 开关(直接写 prefs,与 NpuAccelerator.KEY_NPU_ENABLED 同名) ----------
        // 用 commit() 而非 apply():随后 initAsync() 会同步读该值,
        // apply() 是异步落盘,存在读到旧值的窗口。
        val switched = context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("npu_acceleration_enabled", true)
            .commit()
        Log.i(TAG, "前置: npu_acceleration_enabled=true 写入=$switched")

        // ---------- 前置清理:清进程级不可逆标记(测试专用重置入口,生产零调用) ----------
        // NpuAccelerator.reset() 会清 unusableForProcess / initAttempted / hostLibsLoaded,
        // 让本用例从"本进程从未销毁过 QNN 会话"的干净状态起步。
        NpuAccelerator.reset()
        logState(context, "after-reset")

        // ---------- 前置清理:确保单例未被别的用例预热 ----------
        // 注意:此刻 instance 通常为 null(测试进程全新启动、Application.onCreate 也不预热 OCR),
        // 因此这一步是 no-op,不会销毁任何 QNN 会话。若确有残留实例则清掉,保证 RUN A 是冷启动。
        val released = PaddleOcrHelper.releaseIfCreated("test-setup")
        Log.i(TAG, "前置: releaseIfCreated(\"test-setup\")=$released")

        val helper = PaddleOcrHelper.getInstance(context)

        // 变量在 runBlocking 外声明,便于 runBlocking 结束后继续断言(断言放在块外更清晰)
        var runAOk = false
        var runANpuActive = false
        var runBResultNull = true
        var runBNpuActive = false
        var runBUnusable = true
        var runALastError: String? = null
        var runBLastError: String? = null
        var arch: Int? = null
        var runBElapsedMs = -1L
        var runAResultNull = true

        val bitmap = buildTextBitmap()

        try {
            runBlocking {
                // ================= RUN A:建立 NPU 基线 =================
                // 先解析 HTP 架构(唯一决策入口:白名单优先、设备自报兜底),
                // 架构解析不出来说明本机不该有 NPU,属于环境不满足 → 跳过而非判失败。
                arch = NpuSupport.resolveHtpArch(NpuDeviceQuery.query())
                Log.i(TAG, "RUN A 前置: resolved HTP arch=$arch")

                val t0 = SystemClock.elapsedRealtime()
                runAOk = helper.initAsync()
                val initElapsed = SystemClock.elapsedRealtime() - t0
                runANpuActive = helper.isNpuActive()
                runALastError = NpuAccelerator.getLastError()
                logState(context, "RUN A after-initAsync (initAsync=$runAOk, ${initElapsed}ms)")

                // NPU 起不来就跳过,不判失败:设备资源缺失 / 非高通 / 架构解析失败
                // 都属"环境不满足",不该让修复验证变成假红。跳过原因里带上真实错误文案。
                if (!runANpuActive) {
                    val why = "NPU 不可用,跳过(资源缺失或本机不支持): " +
                        "arch=$arch initAsync=$runAOk lastError=$runALastError"
                    Log.w(TAG, why)
                    assumeTrue(why, false)
                    return@runBlocking
                }
                assertTrue("RUN A 应建立 NPU 基线,实际 isNpuActive=false", runANpuActive)

                // ================= RUN A 推理:启动空闲释放定时器 =================
                // recognizeAsync 的 finally 里会 scheduleIdleRelease(),60s 后触发
                // releaseResources(reason="idle-60000ms", allowNpuDestroy=false)。
                val t1 = SystemClock.elapsedRealtime()
                val runA = helper.recognizeAsync(bitmap)
                runAResultNull = runA == null
                Log.i(
                    TAG,
                    "RUN A 识别完成: ${SystemClock.elapsedRealtime() - t1}ms " +
                        "result=${if (runA == null) "null" else "fullText='${runA.fullText}' " +
                            "blocks=${runA.textBlocks.size}"} " +
                        "npuActive=${helper.isNpuActive()}",
                )

                // ================= 等待空闲释放定时器触发 =================
                // 不可缩短:IDLE_RELEASE_DELAY_MS=60_000 是 private const,
                // 少于 60s 定时器根本没触发,整个用例就失去意义。
                Log.i(TAG, "等待 65s 让空闲释放定时器(IDLE_RELEASE_DELAY_MS=60_000)触发 ...")
                val sleepStart = SystemClock.elapsedRealtime()
                Thread.sleep(IDLE_WAIT_MS)
                Log.i(
                    TAG,
                    "等待结束,实际睡了 ${SystemClock.elapsedRealtime() - sleepStart}ms;" +
                        "此时应已出现 PaddleOcrHelper 的『跳过会话释放（保留 NPU 常驻）: reason=idle-60000ms』",
                )
                logState(context, "after-idle-wait")
            }

            // ================= RUN B:关键断言 =================
            // 单独一个 runBlocking:语义上 RUN B 是一次全新的识别调用
            // (它会 cancelIdleRelease() → 复用仍在的会话 → finally 重新排期)。
            runBlocking {
                val t2 = SystemClock.elapsedRealtime()
                val runB = helper.recognizeAsync(bitmap)
                runBElapsedMs = SystemClock.elapsedRealtime() - t2
                runBResultNull = runB == null
                runBNpuActive = helper.isNpuActive()
                runBUnusable = NpuAccelerator.isUnusableForProcess()
                runBLastError = NpuAccelerator.getLastError()

                Log.i(
                    TAG,
                    "RUN B 识别完成: ${runBElapsedMs}ms " +
                        "result=${if (runB == null) "null" else "fullText='${runB.fullText}' " +
                            "blocks=${runB.textBlocks.size}"}",
                )
                logState(context, "RUN B")
            }
        } finally {
            // 注意:这里**不**调用 helper.close()。故意不销毁会话 ——
            // close() 会置位 unusableForProcess(销毁不可逆),那既是"制造条件",
            // 也会让最终上报的设备状态被污染。进程随 am instrument 结束而退出,无需手工清理。
            bitmap.recycle()
        }

        // ---------- 汇总日志(便于真机排查) ----------
        Log.i(
            TAG,
            "===== 结果汇总 =====\n" +
                "  RUN A: initAsync=$runAOk isNpuActive=$runANpuActive " +
                "resultNull=$runAResultNull lastError=$runALastError\n" +
                "  RUN B: isNpuActive=$runBNpuActive isUnusableForProcess=$runBUnusable " +
                "resultNull=$runBResultNull elapsed=${runBElapsedMs}ms lastError=$runBLastError\n" +
                "  arch=$arch npuEnabled=${NpuAccelerator.isEnabled(context)}",
        )

        // ---------- 关键断言 ----------
        // ① 修复的核心:空闲释放后 NPU 仍是活跃的(修复前此处为 false 且永久为 false)
        assertTrue(
            "空闲释放后 NPU 应仍活跃(修复前会被销毁并永久降级为 CPU): " +
                "isNpuActive=$runBNpuActive isUnusableForProcess=$runBUnusable lastError=$runBLastError",
            runBNpuActive,
        )
        // ② 空闲释放路径不该置位进程级不可逆标记(只有"不得不销毁"的三条路径才置位)
        assertFalse(
            "空闲释放不应置位进程级不可逆标记 isUnusableForProcess",
            runBUnusable,
        )
        // ③ RUN B 的识别链路本身必须没坏(返回非 null;文本内容不作断言,识别结果可为空)
        assertNotNull(
            "RUN B 应返回非 null 识别结果(链路没坏);若为 null 说明会话已不可用",
            if (runBResultNull) null else "ok",
        )
    }

    /**
     * 程序化生成一张带中文文字的位图(800×400 白底 + 黑色大字「取件码 A-123」)。
     *
     * 不依赖任何外部图片文件,保证测试自包含。识别结果不参与断言,
     * 这里只要求「有内容可跑一次真实推理」,所以字足够大、对比度足够高即可。
     */
    private fun buildTextBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 96f
            isAntiAlias = true
            isFakeBoldText = true
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("取件码 A-123", 40f, 210f, paint)
        canvas.drawText("SF1234567890", 40f, 340f, paint)
        return bitmap
    }

    /** 统一打点:把修复相关的三个可观测值一次性打进 logcat,便于 `-s NpuIdleReuseTest:V` 排查。 */
    private fun logState(context: Context, stage: String) {
        Log.i(
            TAG,
            "[$stage] isNpuActive(PaddleOcrHelper)=${PaddleOcrHelper.getInstance(context).isNpuActive()} " +
                "isNpuActive(NpuAccelerator)=${NpuAccelerator.isNpuActive()} " +
                "isUnusableForProcess=${NpuAccelerator.isUnusableForProcess()} " +
                "npuEnabled=${NpuAccelerator.isEnabled(context)} " +
                "lastError=${NpuAccelerator.getLastError()}",
        )
    }

    companion object {
        private const val TAG = "NpuIdleReuseTest"

        /**
         * 等待时长 = IDLE_RELEASE_DELAY_MS(60_000) + 5s 余量。
         *
         * ⚠️ **禁止缩短**:少于 60s 时 `PaddleOcrHelper.scheduleIdleRelease()` 的
         * `delay(IDLE_RELEASE_DELAY_MS)` 尚未到期,空闲释放根本没发生,
         * 本用例会退化成"连续两次识别"——那测不出这个缺陷。
         */
        private const val IDLE_WAIT_MS = 65_000L
    }
}

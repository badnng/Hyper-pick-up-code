package com.Badnng.moe.npu

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NPU 加速器 androidTest。
 *
 * 需要真机运行(模拟器上没有 QNN 运行时)。
 *
 * 验证:
 * 1. NpuDeviceQuery 能正确识别设备架构
 * 2. NpuSupport.detect 返回合理状态
 * 3. NpuAccelerator.tryInitNpu 在资源缺失时优雅降级
 *
 * ⚠️ 两条硬约束(均为真机踩过的坑,改本文件前务必先读):
 * - 测试**不得删除**用户已下载的 NPU 资源(`filesDir/npu_libs/<abi>/` 下约 21MB 的 QNN 运行时),
 *   即禁止使用 `NpuLibLoader.clear()`。缺资源场景一律**只读**构造,
 *   见 [testNpuAcceleratorInitWithoutLibs]。
 * - 所有 `@Test` 方法必须是**块体**(`fun x() { ... }`):`= runBlocking { ... }` 这类表达式体
 *   会把 lambda 末句的类型(如 `Log.i` 返回的 `Int`)推断成方法返回类型,而 JUnit4 要求
 *   测试方法返回 void,否则测试类根本实例化不了
 *   (`InvalidTestClassError: Method xxx() should be void`)。
 */
@RunWith(AndroidJUnit4::class)
class NpuAcceleratorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testDeviceQuery() {
        val info = NpuDeviceQuery.query()
        Log.i(TAG, "Device query: ${info.summary}")
        Log.i(TAG, "rawDump:\n${NpuDeviceQuery.rawDump()}")

        // 骁龙设备应该能查到架构
        if (info.isQualcomm) {
            assertNotNull("高通设备应能识别 HTP 架构", info.htpArch)
            assertTrue("架构应在 68-81 之间", info.htpArch!! in 68..81)
        }

        // 诊断:设备自报值只是证据,决策口径是 resolveHtpArch(白名单优先)。
        // 真机 SM8475 上两者可能不一致(设备自报 v68 / 白名单 v69),这里把两者都打出来便于对比。
        Log.i(
            TAG,
            "resolveHtpArch=${NpuSupport.resolveHtpArch(info)} (设备自报 htpArch=${info.htpArch}, " +
                "archFromDevice=${info.archFromDevice}, ambiguous=${info.archAmbiguous})",
        )
    }

    @Test
    fun testNpuSupportDetect() {
        val cap = NpuSupport.detect(context)
        Log.i(TAG, "NPU capability: status=${cap.status}, message=${cap.message}")

        // 不应该抛出异常
        assertNotNull(cap.status)
    }

    /**
     * 验证 QNN 运行时**就绪**时 [NpuAccelerator.tryInitNpu] 能成功初始化。
     *
     * ## 为什么本测试不再"制造缺资源场景"(真机数据安全)
     *
     * 原实现先执行 `NpuLibLoader.clear(context)` 把外载目录删干净,以此断言
     * "缺资源时初始化返回 false"。但这会**真实删除**用户真机上已下载的约 21MB QNN 运行时
     * (`libQnnHtp.so` / `libQnnSystem.so` / `libQnnHtpV{arch}Stub.so` / `libQnnHtpV{arch}Skel.so`),
     * 跑一次测试用户就得重新下载;且被删的文件**无法从内存恢复**,
     * "先备份后 finally 还原"的方案(仅记录 [NpuLibLoader.presentLibs] / [NpuLibLoader.usedBytes])
     * 也救不回来。因此这里采用**方案 C**:
     *
     * - 资源未就绪 → `Assume.assumeTrue` **跳过**测试(而不是清空用户文件);
     * - 资源已就绪 → 走真实初始化路径,断言不抛异常、`isNpuActive()` 与返回值一致、
     *   失败时必有 `getLastError()` 错误文案。
     *
     * "缺资源时优雅降级"的覆盖由以下两点保证,不依赖本测试破坏数据:
     * 1. 缺资源分支是真实生产路径:`NpuLibLoader.isReady` 为 false 时 [NpuAccelerator.tryInitNpu]
     *    只写 `lastInitError` 后返回 false(不抛异常),该分支在未下载库的设备上必然被走到;
     * 2. 新增生产代码时须保持 `tryInitNpu` 内部 `runCatching`/`try` 语义,任何一步失败都降级而非抛出。
     * 若将来需要主动覆盖该场景,应给 [NpuAccelerator.tryInitNpu] 增加可注入目录的参数,
     * 而不是在测试里删除用户数据。
     */
    @Test
    fun testNpuAcceleratorInitWithoutLibs() {
        runBlocking {
            // 决策走唯一入口:白名单优先、设备自报兜底(设备自报值不可用于决策)。
            val arch = NpuSupport.resolveHtpArch(NpuDeviceQuery.query())
            if (arch == null) {
                Log.w(TAG, "无法解析本机 HTP 架构,跳过初始化测试")
                assumeTrue("无法解析本机 HTP 架构(白名单与设备自报均无结论)", false)
                return@runBlocking
            }

            val ready = NpuLibLoader.isReady(context, arch)
            Log.i(
                TAG,
                "Init precondition: arch=$arch ready=$ready " +
                    "usedBytes=${NpuLibLoader.usedBytes(context)} " +
                    "present=${NpuLibLoader.presentLibs(context)}",
            )
            // ⚠️ 这里**绝不调用 NpuLibLoader.clear(context)**:那会删除用户真机上已下载的
            // QNN 运行时(约 21MB)。资源缺失时直接跳过,不制造"缺资源"假象。
            // 但无论是否就绪都先 reset():tryInitNpu 有幂等守卫(initAttempted),被其它用例
            // 或 Application.onCreate 提前置位时会直接返回缓存值,断言会变得没有意义。
            NpuAccelerator.reset()

            // 用语句 + 变量而不是 try 表达式:Java 的 Assert.fail 在 Kotlin 里是 Unit,
            // 写成 `try { Boolean } catch { fail(...) }` 会让表达式类型被推成 Any,断言编译不过。
            var result = false
            var thrown: Throwable? = null
            try {
                result = NpuAccelerator.tryInitNpu(context, arch)
            } catch (t: Throwable) {
                thrown = t
            }

            Log.i(
                TAG,
                "Init with libs: arch=$arch ready=$ready result=$result " +
                    "thrown=${thrown?.javaClass?.name} " +
                    "active=${NpuAccelerator.isNpuActive()} error=${NpuAccelerator.getLastError()}",
            )

            assertNull("tryInitNpu 不应抛出异常,实际抛出: $thrown", thrown)
            if (ready) {
                assertTrue("库文件齐备时 NPU 初始化应成功,error=${NpuAccelerator.getLastError()}", result)
                assertTrue("初始化成功后 isNpuActive 应为 true", NpuAccelerator.isNpuActive())
                assertNull("初始化成功后不应有错误信息", NpuAccelerator.getLastError())
            } else {
                // 未就绪:生产代码应优雅降级(返回 false + 给出错误文案),而不是抛异常
                assertFalse("库文件缺失时 NPU 初始化应优雅降级为 false", result)
                assertFalse("降级后 isNpuActive 应为 false", NpuAccelerator.isNpuActive())
                assertNotNull("降级时应给出错误信息", NpuAccelerator.getLastError())
            }
        }
    }

    @Test
    fun testNpuAcceleratorReset() {
        NpuAccelerator.reset()
        assertFalse(NpuAccelerator.isNpuActive())
        assertNull(NpuAccelerator.getLastError())
    }

    @Test
    fun testNpuLibLoaderPaths() {
        val dir = NpuLibLoader.externalDir(context)
        Log.i(TAG, "External dir: ${dir.absolutePath}")

        // 查缺失清单属**决策**场景,必须走唯一入口 resolveHtpArch(白名单优先、设备自报兜底),
        // 不能用设备自报的 htpArch:多套 Skel 并存时它会随目录列举顺序漂移。
        val arch = NpuSupport.resolveHtpArch(NpuDeviceQuery.query())
        Log.i(TAG, "Resolved htpArch: $arch")
        if (arch == null) {
            assumeTrue("无法解析本机 HTP 架构,跳过缺失清单检查", false)
            return
        }
        val missing = NpuLibLoader.missingLibs(context, arch)
        Log.i(TAG, "Missing libs: $missing")

        // 不应该抛出异常
        assertNotNull(missing)
    }

    companion object {
        private const val TAG = "NpuAcceleratorTest"
    }
}

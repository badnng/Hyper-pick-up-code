package com.Badnng.moe.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleRuleTemplateCompilerTest {
    @Test
    fun digitsOnlyCodeDoesNotConsumeLetters() {
        val regex = SimpleRuleTemplateCompiler.compile("取餐号{{code}}已使用", codeDigitsOnly = true)

        assertEquals("2", regex.find("取餐号2已使用")?.groupValues?.get(1))
        assertNull(regex.find("取餐号A2已使用"))
    }

    @Test
    fun templateWithoutSpacesMatchesOcrLinesJoinedBySpaces() {
        val regex = SimpleRuleTemplateCompiler.compile("取茶码{{code}}制作中")
        val match = regex.find("取茶码 0165 制作中，请您耐心等待")

        assertNotNull(match)
        assertEquals("0165", match?.groups?.get(SimpleRuleTemplateCompiler.codeGroupIndex("取茶码{{code}}制作中"))?.value)
    }

    @Test
    fun locationAndCodeCanCrossLogicalLineBoundaries() {
        val regex = SimpleRuleTemplateCompiler.compile("{{location}}{{code}}取餐码")
        val match = regex.find("示例门店 731 取餐码")

        assertNotNull(match)
        assertEquals("731", match?.groups?.get(SimpleRuleTemplateCompiler.codeGroupIndex("{{location}}{{code}}取餐码"))?.value)
        assertEquals("示例门店", match?.groups?.get(SimpleRuleTemplateCompiler.locationGroupIndex("{{location}}{{code}}取餐码")!!) ?.value?.trim())
    }

    @Test
    fun variableLinkCanBeSkippedAndCodeLengthCanBeFixed() {
        val template = "【示例平台】包裹已放{{location}}，打开{{any}}或使用{{code:digits:4}}取件"
        val regex = SimpleRuleTemplateCompiler.compile(template)
        val match = regex.find("【示例平台】包裹已放示例存放点，打开 example.test/path 或使用2468取件")

        assertNotNull(match)
        assertEquals("2468", match?.groups?.get(SimpleRuleTemplateCompiler.codeGroupIndex(template))?.value)
        assertEquals(
            "示例存放点",
            match?.groups?.get(SimpleRuleTemplateCompiler.locationGroupIndex(template)!!)?.value?.trim(),
        )
    }

    @Test
    fun parameterizedCodeRejectsWrongLength() {
        val regex = SimpleRuleTemplateCompiler.compile("取件码{{code:digits:4}}")

        assertEquals("1969", regex.find("取件码1969")?.groupValues?.get(1))
        assertNull(regex.find("取件码196"))
        assertNull(regex.find("取件码A969"))
    }

    @Test
    fun runtimeReturnsEveryOccurrenceOfTheSameTemplate() {
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.EXPRESS,
                        name = "菜鸟驿站",
                        keywords = listOf("菜鸟驿站"),
                        templates = listOf(
                            SimpleTemplateRule(
                                name = "多取件码",
                                template = "取件码{{code:digits:4}}待取件",
                            )
                        ),
                    )
                )
            )
        )

        val matches = SimpleRuleRuntime.recognizeCurrent(
            "菜鸟驿站 取件码1234待取件 其他内容 取件码5678待取件",
            SimpleRuleSource.IMAGE,
        )

        assertEquals(listOf("1234", "5678"), matches.map { it.code })
    }

    @Test
    fun runtimeDeduplicatesRepeatedCodesAcrossTemplates() {
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.DRINK,
                        name = "测试饮品",
                        keywords = listOf("测试饮品"),
                        templates = listOf(
                            SimpleTemplateRule(name = "模板一", template = "取餐码{{code:digits:4}}"),
                            SimpleTemplateRule(name = "模板二", template = "号码{{code:digits:4}}"),
                        ),
                    )
                )
            )
        )

        val matches = SimpleRuleRuntime.recognizeCurrent(
            "测试饮品 取餐码2468 号码2468",
            SimpleRuleSource.IMAGE,
        )

        assertEquals(listOf("2468"), matches.map { it.code })
    }

    @Test
    fun runtimeMatchesMultipleCourierBrandsInDocumentOrder() {
        val entries = listOf(
            Triple("甲快递", "12-3-4567", "甲快递"),
            Triple("乙快递", "23-4-5678", "乙快递"),
            Triple("甲快递", "34-5-6789", "甲快递"),
        )
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = entries.map { (brand, _, keyword) ->
                    SimpleBrandRule(
                        category = SimpleRuleCategory.EXPRESS,
                        name = brand,
                        keywords = listOf(keyword),
                        templates = listOf(SimpleTemplateRule(template = "取件码{{code}}$keyword")),
                    )
                }.distinctBy { it.name },
            ),
        )
        val rawText = entries.joinToString("\n") { (brand, code, _) -> "取件码$code\n$brand 123456789012345" }

        val matches = SimpleRuleRuntime.recognizeCurrent(rawText, SimpleRuleSource.IMAGE)

        assertEquals(entries.map { it.second }, matches.map { it.code })
        assertEquals(entries.map { it.first }, matches.map { it.brand })
    }

    @Test
    fun builtInLuckinQrStillExcludesOtherBrands() {
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.DRINK,
                        name = LuckinQrRule.BRAND_NAME,
                        keywords = listOf("瑞幸"),
                        templates = listOf(SimpleTemplateRule(template = "取餐码{{code:digits:4}}")),
                    ),
                    SimpleBrandRule(
                        category = SimpleRuleCategory.FOOD,
                        name = "其他品牌",
                        keywords = listOf("其他品牌"),
                        templates = listOf(SimpleTemplateRule(template = "号码{{code:digits:4}}")),
                    ),
                )
            )
        )

        val matches = SimpleRuleRuntime.recognizeCurrent(
            rawText = "瑞幸 取餐码1234 其他品牌 号码5678",
            source = SimpleRuleSource.IMAGE,
            qrData = "a1234567AA.=",
        )

        assertEquals(listOf("1234"), matches.map { it.code })
        assertEquals(listOf(LuckinQrRule.BRAND_NAME), matches.map { it.brand })
    }

    @Test
    fun runtimeMatchesDrinkBrandFromVariableOcrText() {
        val rawText = listOf(
            "状态栏随机内容",
            "取茶码",
            "0427",
            "制作中，请稍候",
            "示例茶饮品牌",
            "活动文案与门店信息均可变化",
        ).joinToString("\n")
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.DRINK,
                        name = "示例茶饮",
                        keywords = listOf("示例茶饮品牌"),
                        templates = listOf(SimpleTemplateRule(name = "通用模板", template = "取茶码{{code}}制作中")),
                    ),
                ),
            ),
        )

        val match = SimpleRuleRuntime.recognizeCurrent(rawText, SimpleRuleSource.IMAGE).single()

        assertEquals("示例茶饮", match.brand)
        assertEquals("0427", match.code)
        assertEquals(SimpleRuleCategory.DRINK, match.category)
    }

    @Test
    fun runtimePrefersNearestCodeWhenAnySpansAcrossPreviousOrder() {
        val firstCode = "12-3-4567"
        val secondCode = "56-7-8901"
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.EXPRESS,
                        name = "甲快递",
                        keywords = listOf("甲快递"),
                        templates = listOf(SimpleTemplateRule(template = "甲快递{{code:alnum:9}}收件人{{any}}甲快递")),
                    ),
                    SimpleBrandRule(
                        category = SimpleRuleCategory.EXPRESS,
                        name = "乙邮政",
                        keywords = listOf("乙邮政"),
                        templates = listOf(SimpleTemplateRule(template = "{{code:alnum:9}}收件人{{any}}乙邮政")),
                    ),
                ),
            ),
        )
        val rawText = listOf(
            "甲快递${firstCode}收件人信息",
            "甲快递 123456789012345",
            secondCode,
            "收件人信息",
            "乙邮政 987654321098765",
        ).joinToString("\n")

        val matches = SimpleRuleRuntime.recognizeCurrent(rawText, SimpleRuleSource.IMAGE)

        assertEquals(listOf(firstCode, secondCode), matches.map { it.code })
        assertEquals(listOf("甲快递", "乙邮政"), matches.map { it.brand })
    }
    @Test
    fun runtimeKeepsFullLocationWhenTextContainsEllipsis() {
        SimpleRuleRuntime.replace(
            SimpleRulePack(
                brands = listOf(
                    SimpleBrandRule(
                        category = SimpleRuleCategory.EXPRESS,
                        name = "示例快递",
                        keywords = listOf("示例快递"),
                        templates = listOf(
                            SimpleTemplateRule(template = "{{location}} 绿色公益\n示例快递{{code}}本人"),
                        ),
                    ),
                ),
            ),
        )
        val rawText = "示例地点一号驿站...绿色公益\n示例快递12-3-4567本人"

        val match = SimpleRuleRuntime.recognizeCurrent(rawText, SimpleRuleSource.IMAGE).single()

        assertEquals("示例地点一号驿站...", match.location)
    }
}

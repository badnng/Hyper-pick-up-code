package com.Badnng.moe.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandFeatureMatcherTest {
    private val mixue = rule("蜜雪冰城", "雪王", "冰鲜柠檬水")

    private fun rule(name: String, vararg words: String) = BrandFeatureRule(
        name = name, words = words.map(::BrandFeatureWord),
    )

    @Test
    fun moreDistinctWordsWinEvenWhenOneWordIsShared() {
        val other = rule("古茗", "冰鲜柠檬水")
        val matches = BrandFeatureMatcher.rank("雪王 冰鲜柠檬水 取餐码1234", "饮品", listOf(other, mixue))
        assertEquals("蜜雪冰城", BrandFeatureMatcher.winner(matches)?.name)
        assertEquals(2, matches.first().hitCount)
        assertEquals(1, matches.last().hitCount)
    }

    @Test
    fun moreWordsWinBeforeDirectBrandNameTieBreaker() {
        val matches = BrandFeatureMatcher.rank("雪王 冰鲜柠檬水 古茗", "餐食", listOf(mixue), listOf("古茗"))
        assertEquals("蜜雪冰城", BrandFeatureMatcher.winner(matches)?.name)
    }

    @Test
    fun brandNameCountsAsOneFeatureAlongsideOtherWords() {
        val match = BrandFeatureMatcher.rank("蜜雪冰城 雪王", "饮品", listOf(mixue)).single()
        assertEquals(2, match.hitCount)
        assertTrue(match.directName)
        assertTrue(match.matchedWords.containsAll(listOf("蜜雪冰城", "雪王")))
    }

    @Test
    fun equallyManyWordsPreferBrandNameMention() {
        val matches = BrandFeatureMatcher.rank(
            "蜜雪冰城 雪王 芝士奶盖 清香乌龙", "饮品",
            listOf(rule("古茗", "芝士奶盖", "清香乌龙"), mixue),
        )
        assertEquals(listOf(2, 2), matches.map { it.hitCount })
        assertEquals("蜜雪冰城", BrandFeatureMatcher.winner(matches)?.name)
    }

    @Test
    fun equalCountsWithoutNamesCannotBeResolvedByAlphabetOrWordLength() {
        val first = rule("A品牌", "短词", "香气")
        val second = rule("Z品牌", "超长特征词汇", "浓郁香味")
        val text = "短词 香气 超长特征词汇 浓郁香味"
        val ranked = BrandFeatureMatcher.rank(text, "餐食", listOf(first, second))
        assertNull(BrandFeatureMatcher.winner(ranked))
        assertEquals(ranked, BrandFeatureMatcher.rank(text, "餐食", listOf(second, first)))
    }

    @Test
    fun equalCountsWithBothBrandNamesAlsoRemainUnresolved() {
        val matches = BrandFeatureMatcher.rank("蜜雪冰城 古茗", "饮品", emptyList(), listOf("蜜雪冰城", "古茗"))
        assertNull(BrandFeatureMatcher.winner(matches))
    }

    @Test
    fun repeatedTextAndDuplicateOrContainedFeaturesCountOnlyOnce() {
        val match = BrandFeatureMatcher.rank(
            "雪王 雪王 雪王驾到", "饮品", listOf(rule("蜜雪冰城", "雪王", "雪 王", "雪王驾到")),
        ).single()
        assertEquals(1, match.hitCount)
        assertEquals(listOf("雪王驾到"), match.matchedWords)
    }

    @Test
    fun explicitlyConfiguredBrandNameDoesNotDoubleCountAutomaticName() {
        val match = BrandFeatureMatcher.rank(
            "蜜雪冰城 雪王", "饮品", listOf(rule("蜜雪冰城", "蜜雪冰城", "蜜 雪冰城", "雪王")),
        ).single()
        assertEquals(2, match.hitCount)
    }

    @Test
    fun nameOnlyWorksAcrossOcrWhitespace() {
        val match = BrandFeatureMatcher.rank("蜜 雪\n冰城", "饮品", listOf(rule("蜜雪冰城"))).single()
        assertEquals(1, match.hitCount)
        assertTrue(match.directName)
    }

    @Test
    fun expressAndOtherTypesNeverParticipate() {
        for (type in listOf("快递", "其他", "")) {
            assertTrue(BrandFeatureMatcher.rank("蜜雪冰城 雪王", type, listOf(mixue)).isEmpty())
        }
    }

    @Test
    fun disabledAndUnmatchedRulesProduceNoWinner() {
        assertTrue(BrandFeatureMatcher.rank("雪王", "饮品", listOf(mixue.copy(enabled = false))).isEmpty())
        assertNull(BrandFeatureMatcher.winner(BrandFeatureMatcher.rank("取餐码 1234", "餐食", listOf(mixue))))
    }

    @Test
    fun matchingIsLiteralAndCaseInsensitive() {
        val custom = rule("测试品牌", "A+B")
        assertEquals(1, BrandFeatureMatcher.rank("a+b", "饮品", listOf(custom)).single().hitCount)
        assertTrue(BrandFeatureMatcher.rank("aaab", "饮品", listOf(custom)).isEmpty())
    }

    @Test
    fun emptyWordsAndGenericCategoryNamesAreIgnored() {
        assertTrue(BrandFeatureMatcher.rank("餐食取餐码", "餐食", listOf(rule("品牌", " ")), listOf("", "餐食")).isEmpty())
    }

    @Test
    fun luckinNamesAndImportedLuckinFeaturesAreExcluded() {
        for (name in listOf("瑞幸", "瑞幸咖啡", "luckin", "Luckin Coffee")) {
            assertTrue(BrandFeatureMatcher.usesDedicatedRecognition(name))
            assertTrue(BrandFeatureMatcher.rank("$name 小蓝杯 生椰拿铁", "饮品", listOf(rule(name, "小蓝杯", "生椰拿铁")), listOf(name)).isEmpty())
        }
        assertFalse(BrandFeatureMatcher.usesDedicatedRecognition("蜜雪冰城"))
    }

    @Test
    fun legacyWeightsAreIgnoredWhileAllWordsArePreserved() {
        val legacy = BrandFeatureRule.fromJson(JSONObject("""
            {"name":"蜜雪冰城","words":[{"word":"雪王","confidence":1},{"word":"柠檬水","confidence":1}]}
        """))
        val other = BrandFeatureRule.fromJson(JSONObject("""
            {"name":"古茗","words":[{"word":"柠檬水","confidence":99}]}
        """))
        assertEquals("蜜雪冰城", BrandFeatureMatcher.winner(BrandFeatureMatcher.rank("雪王 柠檬水", "饮品", listOf(other, legacy)))?.name)
        assertFalse(legacy.toJson().getJSONArray("words").getJSONObject(0).has("confidence"))
    }

    @Test
    fun rulesRoundTripAndOlderPacksStillLoad() {
        val pack = PickupWordRulePack(brandFeatures = listOf(mixue.copy(enabled = false)))
        val loaded = PickupWordRulePack.fromJson(JSONObject(pack.toJson().toString()))
        assertEquals(pack.brandFeatures, loaded.brandFeatures)
        assertEquals(pack.foodKeywords, loaded.foodKeywords)
        val old = PickupWordRulePack.fromJson(pack.toJson().apply { remove("brand_features") })
        assertTrue(old.brandFeatures.isEmpty())
        assertEquals(pack.foodKeywords, old.foodKeywords)
    }
}

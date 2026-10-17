package com.Badnng.moe.recognition

import com.Badnng.moe.rules.BrandFeatureRule
import com.Badnng.moe.rules.PickupWordRulePack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandFeatureRecognitionTest {
    @Test
    fun preserveLuckinResultsFromBrandOrExistingQrAlgorithm() {
        assertTrue(BrandFeatureRecognition.preservesDedicatedResult("瑞幸", null))
        assertTrue(BrandFeatureRecognition.preservesDedicatedResult("瑞幸咖啡", null))
        assertTrue(BrandFeatureRecognition.preservesDedicatedResult(null, "a1234567AA.="))
        assertFalse(BrandFeatureRecognition.preservesDedicatedResult("蜜雪冰城", null))
    }

    @Test
    fun listOmitsLuckinEvenWhenImportedAsCustomBrand() {
        val pack = PickupWordRulePack(brandFeatures = listOf(BrandFeatureRule(name = "瑞幸咖啡")))
        assertFalse(BrandFeatureRecognition.editableNames(pack).contains("瑞幸咖啡"))
        assertTrue(BrandFeatureRecognition.editableNames(pack).contains("蜜雪冰城"))
    }
}

package com.Badnng.moe.rules

/**
 * 瑞幸二维码属于应用既有的高可信品牌校正规则，不能随用户规则包清空而消失。
 * 用户规则仍负责提取取餐码和位置；这里只负责锁定品牌与类型。
 */
object LuckinQrRule {
    const val BRAND_NAME = "瑞幸"
    const val CATEGORY = "饮品"
    const val PACKAGE_NAME = "com.lucky.luckyclient"
    const val PATTERN = "^a.{7}AA.=$"

    private val compiledPattern = Regex(PATTERN)

    fun matches(value: String?): Boolean =
        value?.trim()?.takeIf(String::isNotEmpty)?.let(compiledPattern::matches) == true
}

package mm.nomadratessentinel.model

import java.math.BigDecimal

data class ParsedRate(
    val code: CurrencyCode,
    val nominal: Int,
    val rate: BigDecimal,
    val source: RateSource,
)

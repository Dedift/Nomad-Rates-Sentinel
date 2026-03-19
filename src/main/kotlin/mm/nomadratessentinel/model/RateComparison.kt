package mm.nomadratessentinel.model

import java.math.BigDecimal
import java.time.Instant

data class RateComparison(
    val code: CurrencyCode,
    val nbkRate: BigDecimal,
    val xeRate: BigDecimal,
    val delta: BigDecimal,
    val comparedAt: Instant,
)
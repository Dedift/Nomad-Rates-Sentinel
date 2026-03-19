package mm.nomadratessentinel.model

import java.math.BigDecimal
import java.time.Instant

data class CurrencyRate(
    val code: CurrencyCode,
    val rate: BigDecimal,
    val source: RateSource,
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    init {
        require(rate >= BigDecimal.ZERO) { "rate must be non-negative" }
    }
}
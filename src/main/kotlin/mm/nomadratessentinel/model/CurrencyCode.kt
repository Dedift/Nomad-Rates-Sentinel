package mm.nomadratessentinel.model

enum class CurrencyCode {
    BYN,
    USD,
    EUR,
    RUB,
    KZT,
    GBP,
    CNY;

    companion object {
        fun fromExternalCode(value: String): CurrencyCode? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
package mm.nomadratessentinel.adapter

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.ParsedRate
import mm.nomadratessentinel.model.RateSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.regex.Pattern

@Component
class XeRateAdapter(
    @Value($$"${rates.adapters.xe.url:https://www.xe.com/currencyconverter/convert/?Amount=1&From=USD&To=KZT}")
    private val url: String,
) : RateAdapter {

    private val jsonRatePattern = Pattern.compile("midMarketRate\\s*[:=]\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?")
    private val pairPattern = Pattern.compile("From=([A-Z]{3}).*To=([A-Z]{3})", Pattern.CASE_INSENSITIVE)
    private val displayedRatePattern = Pattern.compile(
        "([0-9]+(?:\\.[0-9]+)?)\\s+([A-Z]{3})\\s*=\\s*([0-9]+(?:[.,][0-9]+)?)\\s+([A-Z]{3})",
        Pattern.CASE_INSENSITIVE
    )

    override fun fetchRates(): List<ParsedRate> {
        val configuredPair = extractPair(url) ?: return emptyList()
        val target = configuredPair.second

        return CurrencyCode.entries.mapNotNull { source ->
            if (source == target) {
                return@mapNotNull ParsedRate(
                    code = source,
                    nominal = 1,
                    rate = BigDecimal.ONE,
                    source = RateSource.XE,
                )
            }

            val requestUrl = buildUrl(source, target)
            val document = fetchDocument(requestUrl)
            parse(document).firstOrNull()
        }
    }

    fun parse(document: Document): List<ParsedRate> {
        val pair = extractPair(document.location()) ?: return emptyList()
        val rate = extractRate(document, pair) ?: return emptyList()

        return listOf(
            ParsedRate(
                code = pair.first,
                nominal = 1,
                rate = rate,
                source = RateSource.XE,
            )
        )
    }

    private fun extractPair(location: String): Pair<CurrencyCode, CurrencyCode>? {
        val matcher = pairPattern.matcher(location)
        if (!matcher.find()) {
            return null
        }

        val from = CurrencyCode.fromExternalCode(matcher.group(1)) ?: return null
        val to = CurrencyCode.fromExternalCode(matcher.group(2)) ?: return null
        return from to to
    }

    private fun extractRate(document: Document, pair: Pair<CurrencyCode, CurrencyCode>): BigDecimal? {
        val documentRate = extractRateFromRawText(document.html())
        if (documentRate != null) {
            return documentRate
        }

        val scriptRate = document.select("script")
            .asSequence()
            .flatMap { script ->
                sequenceOf(
                    script.data(),
                    script.html(),
                    script.text(),
                    script.outerHtml(),
                )
            }
            .filter { it.isNotBlank() }.firstNotNullOfOrNull(::extractRateFromRawText)

        if (scriptRate != null) {
            return scriptRate
        }

        val textCandidates = sequenceOf(
            document.selectFirst("[data-testid=conversion]")?.text(),
            document.selectFirst("[data-testid=converter-result]")?.text(),
            document.body().text(),
        )

        return textCandidates.firstNotNullOfOrNull { extractRateFromText(it, pair) }
    }

    private fun extractRateFromRawText(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) {
            return null
        }

        val matcher = jsonRatePattern.matcher(raw)
        if (matcher.find()) {
            return matcher.group(1)?.toBigDecimalOrNull()
        }

        return null
    }

    private fun extractRateFromText(raw: String?, pair: Pair<CurrencyCode, CurrencyCode>): BigDecimal? {
        if (raw.isNullOrBlank()) {
            return null
        }

        val matcher = displayedRatePattern.matcher(raw)
        while (matcher.find()) {
            val nominal = matcher.group(1)?.toBigDecimalOrNull() ?: continue
            val from = matcher.group(2)?.let(CurrencyCode::fromExternalCode) ?: continue
            val rate = matcher.group(3)?.replace(",", "")?.toBigDecimalOrNull() ?: continue
            val to = matcher.group(4)?.let(CurrencyCode::fromExternalCode) ?: continue

            if (from == pair.first && to == pair.second && nominal.compareTo(BigDecimal.ONE) == 0) {
                return rate
            }
        }

        return null
    }

    private fun buildUrl(from: CurrencyCode, to: CurrencyCode): String =
        url.replace(Regex("([?&]From=)[A-Z]{3}", RegexOption.IGNORE_CASE), "$1${from.name}")
            .replace(Regex("([?&]To=)[A-Z]{3}", RegexOption.IGNORE_CASE), "$1${to.name}")

    protected open fun fetchDocument(requestUrl: String): Document =
        Jsoup.connect(requestUrl).get()
}

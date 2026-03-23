package mm.nomadratessentinel.adapter

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.ParsedRate
import mm.nomadratessentinel.model.RateSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern
import kotlin.system.measureTimeMillis
import org.slf4j.LoggerFactory

@Component
class NbkRateAdapter(
    @Value($$"${rates.adapters.nbk.url:https://nationalbank.kz/en/exchangerates/ezhednevnye-oficialnye-rynochnye-kursy-valyut}")
    private val url: String,
    @Value($$"${rates.adapters.request-timeout-ms:10000}")
    private val requestTimeoutMs: Int
) : RateAdapter {

    private data class ParsedRow(
        val code: CurrencyCode,
        val nominal: Int,
        val rateValue: BigDecimal,
    )

    private val leadingNominalPattern = Pattern.compile("^(\\d+)\\b")
    private val pairCodePattern = Pattern.compile("^([A-Z]{3})\\s*/\\s*[A-Z]{3}$")

    override fun fetchRates(): List<ParsedRate> {
        lateinit var rates: List<ParsedRate>
        val elapsedMs = measureTimeMillis {
            rates = parse(fetchDocument(url))
        }
        logger.info("NBK fetch completed with {} rates in {} ms", rates.size, elapsedMs)
        return rates
    }

    protected fun fetchDocument(requestUrl: String): Document =
        Jsoup.connect(requestUrl)
            .timeout(requestTimeoutMs)
            .get()

    fun parse(document: Document): List<ParsedRate> =
        document.select("table tbody tr")
            .mapNotNull { row ->
                val cells = row.select("td").map { it.text().trim() }
                val parsedRow = parseRow(cells) ?: return@mapNotNull null

                ParsedRate(
                    code = parsedRow.code,
                    nominal = parsedRow.nominal,
                    rate = parsedRow.rateValue.divide(
                        BigDecimal.valueOf(parsedRow.nominal.toLong()),
                        8,
                        RoundingMode.HALF_UP,
                    ),
                    source = RateSource.NBK,
                )
            }

    private fun parseRow(cells: List<String>): ParsedRow? {
        if (cells.size < 4) {
            return null
        }

        val nominal = parseNominal(cells[1]) ?: return null
        val code = parseCodeFromPair(cells[2]) ?: return null
        val rateValue = parseDecimal(cells[3]) ?: return null

        return ParsedRow(
            code = code,
            nominal = nominal,
            rateValue = rateValue,
        )
    }

    private fun parseNominal(raw: String): Int? =
        leadingNominalPattern.matcher(raw.trim())
            .takeIf { it.find() }
            ?.group(1)
            ?.toIntOrNull()

    private fun parseCodeFromPair(raw: String): CurrencyCode? =
        pairCodePattern.matcher(raw.trim())
            .takeIf { it.matches() }
            ?.group(1)
            ?.let(CurrencyCode.Companion::fromExternalCode)

    private fun parseDecimal(raw: String): BigDecimal? =
        raw.replace(" ", "")
            .replace(",", ".")
            .takeIf { candidate -> candidate.any { it.isDigit() } }
            ?.toBigDecimalOrNull()

    companion object {
        private val logger = LoggerFactory.getLogger(NbkRateAdapter::class.java)
    }
}

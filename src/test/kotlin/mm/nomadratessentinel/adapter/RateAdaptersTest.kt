package mm.nomadratessentinel.adapter

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.RateSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RateAdaptersTest {

    @Test
    fun `nbk adapter parses html table and normalizes nominal`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <table>
                  <tbody>
                    <tr>
                      <td></td>
                      <td>1 US DOLLAR</td>
                      <td>USD / KZT</td>
                      <td>510,12</td>
                      <td></td>
                    </tr>
                    <tr>
                      <td></td>
                      <td>100 RUSSIAN RUBLE</td>
                      <td>RUB / KZT</td>
                      <td>560,00</td>
                      <td></td>
                    </tr>
                  </tbody>
                </table>
              </body>
            </html>
            """.trimIndent()
        )

        val rates = NbkRateAdapter("https://example.test").parse(document)

        assertEquals(2, rates.size)
        assertEquals(CurrencyCode.USD, rates[0].code)
        assertEquals(BigDecimal("510.12000000"), rates[0].rate)
        assertEquals(BigDecimal("5.60000000"), rates[1].rate)
        assertEquals(RateSource.NBK, rates[1].source)
    }

    @Test
    fun `xe adapter parses rate from xe page html`() {
        val document = Jsoup.parse(
            """
            <html>
              <head>
                <script>
                  window.__INITIAL_STATE__ = {"midMarketRate":"512.34"};
                </script>
              </head>
              <body>
                <main>
                  <div>1.00 USD = 512.34 KZT</div>
                  <div>Mid-market rate at 07:03 UTC</div>
                </main>
              </body>
            </html>
            """.trimIndent(),
            "https://www.xe.com/currencyconverter/convert/?Amount=1&From=USD&To=KZT"
        )

        val rates = XeRateAdapter("https://example.test").parse(document)

        assertEquals(1, rates.size)
        assertEquals(CurrencyCode.USD, rates.single().code)
        assertEquals(BigDecimal("512.34"), rates.single().rate)
        assertEquals(RateSource.XE, rates.single().source)
    }

    @Test
    fun `xe adapter falls back to displayed page text`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <main>
                  <div>1.00 USD = 483.36987942 KZT</div>
                  <div>Mid-market rate at 07:03 UTC</div>
                </main>
              </body>
            </html>
            """.trimIndent(),
            "https://www.xe.com/currencyconverter/convert/?Amount=1&From=USD&To=KZT"
        )

        val rates = XeRateAdapter("https://example.test").parse(document)

        assertEquals(1, rates.size)
        assertEquals(CurrencyCode.USD, rates.single().code)
        assertEquals(BigDecimal("483.36987942"), rates.single().rate)
    }

    @Test
    fun `xe adapter fetches configured currency set against target currency`() {
        val adapter = object : XeRateAdapter("https://www.xe.com/currencyconverter/convert/?Amount=1&From=USD&To=KZT") {
            override fun fetchDocument(requestUrl: String): Document =
                when {
                    requestUrl.contains("From=USD") -> xeDocument("USD", "480.10")
                    requestUrl.contains("From=EUR") -> xeDocument("EUR", "520.20")
                    requestUrl.contains("From=RUB") -> xeDocument("RUB", "5.30")
                    requestUrl.contains("From=GBP") -> xeDocument("GBP", "620.40")
                    requestUrl.contains("From=CNY") -> xeDocument("CNY", "66.50")
                    requestUrl.contains("From=BYN") -> xeDocument("BYN", "147.70")
                    else -> error("Unexpected URL: $requestUrl")
                }
        }

        val rates = adapter.fetchRates()

        assertEquals(
            listOf(CurrencyCode.BYN, CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.RUB, CurrencyCode.KZT, CurrencyCode.GBP, CurrencyCode.CNY),
            rates.map { it.code }
        )
        assertEquals(BigDecimal("1"), rates.first { it.code == CurrencyCode.KZT }.rate)
        assertEquals(BigDecimal("480.10"), rates.first { it.code == CurrencyCode.USD }.rate)
        assertEquals(BigDecimal("147.70"), rates.first { it.code == CurrencyCode.BYN }.rate)
        assertEquals(RateSource.XE, rates.first { it.code == CurrencyCode.GBP }.source)
    }

    private fun xeDocument(from: String, rate: String): Document =
        Jsoup.parse(
            """
            <html>
              <head>
                <script>
                  window.__INITIAL_STATE__ = {"midMarketRate":"$rate"};
                </script>
              </head>
              <body>
                <main>
                  <div>1.00 $from = $rate KZT</div>
                </main>
              </body>
            </html>
            """.trimIndent(),
            "https://www.xe.com/currencyconverter/convert/?Amount=1&From=$from&To=KZT"
        )
}

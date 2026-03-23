package mm.nomadratessentinel.repository

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.model.RateSource
import org.springframework.context.annotation.Profile
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

@Repository
@Profile("reactive")
class ReactiveCurrencyRateRepository(
    private val databaseClient: DatabaseClient,
) {

    fun saveAll(currencyRates: Collection<CurrencyRate>): Flux<CurrencyRate> {
        val rates = currencyRates.toList()
        if (rates.isEmpty()) {
            return Flux.empty()
        }

        return databaseClient.inConnectionMany { connection ->
            val statement = connection.createStatement(
                """
                INSERT INTO currency_rates (code, rate, source_id, updated_at, version)
                VALUES ($1, $2, $3, $4, 0)
                ON CONFLICT (code, source_id) DO UPDATE
                SET rate = EXCLUDED.rate,
                    updated_at = EXCLUDED.updated_at,
                    version = currency_rates.version + 1
                """.trimIndent()
            )

            rates.forEachIndexed { index, currencyRate ->
                statement
                    .bind("$1", currencyRate.code.name)
                    .bind("$2", currencyRate.rate)
                    .bind("$3", currencyRate.source.name)
                    .bind("$4", currencyRate.updatedAt)

                if (index < rates.lastIndex) {
                    statement.add()
                }
            }

            Flux.from(statement.execute())
                .flatMap { result -> result.rowsUpdated }
        }.thenMany(Flux.fromIterable(rates))
    }

    fun findDeltaBetweenSources(code: CurrencyCode): Mono<RateComparison> =
        databaseClient.sql(
            """
            SELECT
                nbk.code AS code,
                nbk.rate AS nbk_rate,
                xe.rate AS xe_rate,
                xe.rate - nbk.rate AS delta,
                GREATEST(nbk.updated_at, xe.updated_at) AS compared_at
            FROM currency_rates nbk
            JOIN currency_rates xe
              ON xe.code = nbk.code
            WHERE nbk.code = :code
              AND nbk.source_id = :nbkSource
              AND xe.source_id = :xeSource
            """.trimIndent()
        )
            .bind("code", code.name)
            .bind("nbkSource", RateSource.NBK.name)
            .bind("xeSource", RateSource.XE.name)
            .map { row, _ ->
                RateComparison(
                    code = CurrencyCode.valueOf(row.get("code", String::class.java)!!),
                    nbkRate = row.get("nbk_rate", BigDecimal::class.java)!!,
                    xeRate = row.get("xe_rate", BigDecimal::class.java)!!,
                    delta = row.get("delta", BigDecimal::class.java)!!,
                    comparedAt = row.get("compared_at", Instant::class.java) ?: Instant.now(),
                )
            }
            .one()

    fun findAllDeltasBetweenSources(): Flux<RateComparison> =
        databaseClient.sql(
            """
            SELECT
                nbk.code AS code,
                nbk.rate AS nbk_rate,
                xe.rate AS xe_rate,
                xe.rate - nbk.rate AS delta,
                GREATEST(nbk.updated_at, xe.updated_at) AS compared_at
            FROM currency_rates nbk
            JOIN currency_rates xe
              ON xe.code = nbk.code
            WHERE nbk.source_id = :nbkSource
              AND xe.source_id = :xeSource
            ORDER BY nbk.code
            """.trimIndent()
        )
            .bind("nbkSource", RateSource.NBK.name)
            .bind("xeSource", RateSource.XE.name)
            .map { row, _ ->
                RateComparison(
                    code = CurrencyCode.valueOf(row.get("code", String::class.java)!!),
                    nbkRate = row.get("nbk_rate", BigDecimal::class.java)!!,
                    xeRate = row.get("xe_rate", BigDecimal::class.java)!!,
                    delta = row.get("delta", BigDecimal::class.java)!!,
                    comparedAt = row.get("compared_at", Instant::class.java) ?: Instant.now(),
                )
            }
            .all()

}

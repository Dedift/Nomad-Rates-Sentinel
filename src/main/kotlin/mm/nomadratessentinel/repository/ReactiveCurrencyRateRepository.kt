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

    fun save(currencyRate: CurrencyRate): Mono<CurrencyRate> =
        upsert(currencyRate)

    fun saveAll(currencyRates: Collection<CurrencyRate>): Flux<CurrencyRate> =
        Flux.fromIterable(currencyRates).concatMap(::save)

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

    private fun upsert(currencyRate: CurrencyRate): Mono<CurrencyRate> =
        databaseClient.sql(
            """
            INSERT INTO currency_rates (code, rate, source_id, updated_at, version)
            VALUES (:code, :rate, :sourceId, :updatedAt, 0)
            ON CONFLICT (code, source_id) DO UPDATE
            SET rate = EXCLUDED.rate,
                updated_at = EXCLUDED.updated_at,
                version = currency_rates.version + 1
            RETURNING code, rate, source_id, updated_at, version
            """.trimIndent()
        )
            .bind("code", currencyRate.code.name)
            .bind("rate", currencyRate.rate)
            .bind("sourceId", currencyRate.source.name)
            .bind("updatedAt", currencyRate.updatedAt)
            .map { row, _ ->
                CurrencyRate(
                    code = CurrencyCode.valueOf(row.get("code", String::class.java)!!),
                    rate = row.get("rate", BigDecimal::class.java)!!,
                    source = RateSource.valueOf(row.get("source_id", String::class.java)!!),
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    version = row.get("version", Long::class.java)!!,
                )
            }
            .one()
            .switchIfEmpty(Mono.error(IllegalStateException("Failed to upsert rate for ${currencyRate.code}/${currencyRate.source}")))
}

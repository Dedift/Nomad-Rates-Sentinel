package mm.nomadratessentinel.repository

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.model.RateSource
import org.springframework.context.annotation.Profile
import org.springframework.dao.OptimisticLockingFailureException
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
        findByCodeAndSource(currencyRate.code, currencyRate.source)
            .flatMap { update(currencyRate.copy(version = it.version)) }
            .switchIfEmpty(insert(currencyRate))

    fun saveAll(currencyRates: Collection<CurrencyRate>): Flux<CurrencyRate> =
        Flux.fromIterable(currencyRates).concatMap(::save)

    fun findByCodeAndSource(code: CurrencyCode, source: RateSource): Mono<CurrencyRate> =
        databaseClient.sql(
            """
            SELECT code, rate, source_id, updated_at, version
            FROM currency_rates
            WHERE code = :code AND source_id = :sourceId
            """.trimIndent()
        )
            .bind("code", code.name)
            .bind("sourceId", source.name)
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

    private fun insert(currencyRate: CurrencyRate): Mono<CurrencyRate> {
        val persistedRate = currencyRate.copy(version = 0)
        return databaseClient.sql(
            """
            INSERT INTO currency_rates (code, rate, source_id, updated_at, version)
            VALUES (:code, :rate, :sourceId, :updatedAt, :version)
            """.trimIndent()
        )
            .bind("code", persistedRate.code.name)
            .bind("rate", persistedRate.rate)
            .bind("sourceId", persistedRate.source.name)
            .bind("updatedAt", persistedRate.updatedAt)
            .bind("version", persistedRate.version)
            .fetch()
            .rowsUpdated()
            .handle { rowsUpdated, sink ->
                if (rowsUpdated == 1L) {
                    sink.next(persistedRate)
                } else {
                    sink.error(IllegalStateException("Failed to insert rate for ${persistedRate.code}/${persistedRate.source}"))
                }
            }
    }

    private fun update(currencyRate: CurrencyRate): Mono<CurrencyRate> {
        val updatedRate = currencyRate.copy(version = currencyRate.version + 1)
        return databaseClient.sql(
            """
            UPDATE currency_rates
            SET rate = :rate, updated_at = :updatedAt, version = :nextVersion
            WHERE code = :code AND source_id = :sourceId AND version = :currentVersion
            """.trimIndent()
        )
            .bind("rate", updatedRate.rate)
            .bind("updatedAt", updatedRate.updatedAt)
            .bind("nextVersion", updatedRate.version)
            .bind("code", updatedRate.code.name)
            .bind("sourceId", updatedRate.source.name)
            .bind("currentVersion", currencyRate.version)
            .fetch()
            .rowsUpdated()
            .flatMap { rowsUpdated ->
                if (rowsUpdated == 1L) {
                    Mono.just(updatedRate)
                } else {
                    Mono.error(
                        OptimisticLockingFailureException(
                            "Currency rate for ${currencyRate.code}/${currencyRate.source} was modified concurrently"
                        )
                    )
                }
            }
    }
}
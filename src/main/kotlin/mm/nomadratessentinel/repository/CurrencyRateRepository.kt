package mm.nomadratessentinel.repository

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.model.RateSource
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlin.collections.firstOrNull

@Repository
class CurrencyRateRepository(
    private val jdbcTemplate: JdbcTemplate,
) {

    fun save(currencyRate: CurrencyRate): CurrencyRate =
        findByCodeAndSource(currencyRate.code, currencyRate.source)
            ?.let { update(currencyRate.copy(version = it.version)) }
            ?: insert(currencyRate)

    fun saveAll(currencyRates: Collection<CurrencyRate>): List<CurrencyRate> =
        currencyRates.map(::save)

    fun findByCodeAndSource(code: CurrencyCode, source: RateSource): CurrencyRate? =
        jdbcTemplate.query(
            """
            SELECT code, rate, source_id, updated_at, version
            FROM currency_rates
            WHERE code = ? AND source_id = ?
            """.trimIndent(),
            rowMapper,
            code.name,
            source.name,
        ).firstOrNull()

    fun findAllByCode(code: CurrencyCode): List<CurrencyRate> =
        jdbcTemplate.query(
            """
            SELECT code, rate, source_id, updated_at, version
            FROM currency_rates
            WHERE code = ?
            ORDER BY updated_at DESC
            """.trimIndent(),
            rowMapper,
            code.name,
        )

    fun findDeltaBetweenSources(code: CurrencyCode): RateComparison? =
        jdbcTemplate.query(
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
            WHERE nbk.code = ?
              AND nbk.source_id = ?
              AND xe.source_id = ?
            """.trimIndent(),
            comparisonRowMapper,
            code.name,
            RateSource.NBK.name,
            RateSource.XE.name,
        ).firstOrNull()

    fun findAllDeltasBetweenSources(): List<RateComparison> =
        jdbcTemplate.query(
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
            WHERE nbk.source_id = ?
              AND xe.source_id = ?
            ORDER BY nbk.code
            """.trimIndent(),
            comparisonRowMapper,
            RateSource.NBK.name,
            RateSource.XE.name,
        )

    private fun insert(currencyRate: CurrencyRate): CurrencyRate {
        val persistedRate = currencyRate.copy(version = 0)
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO currency_rates (code, rate, source_id, updated_at, version)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            persistedRate.code.name,
            persistedRate.rate,
            persistedRate.source.name,
            Timestamp.from(persistedRate.updatedAt),
            persistedRate.version,
        )

        check(inserted == 1) { "Failed to insert rate for ${persistedRate.code} from ${persistedRate.source}" }
        return persistedRate
    }

    private fun update(currencyRate: CurrencyRate): CurrencyRate {
        val nextVersion = currencyRate.version + 1
        val updatedRate = currencyRate.copy(version = nextVersion)
        val updated = jdbcTemplate.update(
            """
            UPDATE currency_rates
            SET rate = ?, updated_at = ?, version = ?
            WHERE code = ? AND source_id = ? AND version = ?
            """.trimIndent(),
            updatedRate.rate,
            Timestamp.from(updatedRate.updatedAt),
            updatedRate.version,
            updatedRate.code.name,
            updatedRate.source.name,
            currencyRate.version,
        )

        if (updated == 0) {
            throw OptimisticLockingFailureException(
                "Currency rate for ${currencyRate.code}/${currencyRate.source} was modified concurrently"
            )
        }

        return updatedRate
    }

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        CurrencyRate(
            code = CurrencyCode.valueOf(rs.getString("code")),
            rate = rs.getBigDecimal("rate"),
            source = RateSource.valueOf(rs.getString("source_id")),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version"),
        )
    }

    private val comparisonRowMapper = RowMapper { rs: ResultSet, _: Int ->
        RateComparison(
            code = CurrencyCode.valueOf(rs.getString("code")),
            nbkRate = rs.getBigDecimal("nbk_rate"),
            xeRate = rs.getBigDecimal("xe_rate"),
            delta = rs.getBigDecimal("delta"),
            comparedAt = rs.getTimestamp("compared_at")?.toInstant() ?: Instant.now(),
        )
    }
}

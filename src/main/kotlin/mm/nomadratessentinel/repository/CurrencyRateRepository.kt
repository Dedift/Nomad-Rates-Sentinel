package mm.nomadratessentinel.repository

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.model.RateSource
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
        upsert(currencyRate)

    fun saveAll(currencyRates: Collection<CurrencyRate>): List<CurrencyRate> =
        currencyRates.map(::save)

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

    private fun upsert(currencyRate: CurrencyRate): CurrencyRate {
        val persistedRate = jdbcTemplate.queryForObject(
            """
            INSERT INTO currency_rates (code, rate, source_id, updated_at, version)
            VALUES (?, ?, ?, ?, 0)
            ON CONFLICT (code, source_id) DO UPDATE
            SET rate = EXCLUDED.rate,
                updated_at = EXCLUDED.updated_at,
                version = currency_rates.version + 1
            RETURNING code, rate, source_id, updated_at, version
            """.trimIndent(),
            rowMapper,
            currencyRate.code.name,
            currencyRate.rate,
            currencyRate.source.name,
            Timestamp.from(currencyRate.updatedAt),
        )

        return persistedRate
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

package mm.nomadratessentinel.config

import com.zaxxer.hikari.HikariDataSource
import mm.nomadratessentinel.controller.RateController
import mm.nomadratessentinel.repository.CurrencyRateRepository
import mm.nomadratessentinel.service.RateSyncService
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

@Configuration
@Profile("!reactive")
@ImportAutoConfiguration(
    exclude = [
        R2dbcAutoConfiguration::class,
        R2dbcTransactionManagerAutoConfiguration::class,
        DataR2dbcAutoConfiguration::class,
        DataR2dbcRepositoriesAutoConfiguration::class,
    ]
)
@Import(
    RateController::class,
    RateSyncService::class,
    CurrencyRateRepository::class,
)
class JdbcRuntimeConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    fun dataSourceProperties(): DataSourceProperties =
        DataSourceProperties()

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    fun dataSource(dataSourceProperties: DataSourceProperties): DataSource =
        dataSourceProperties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate =
        JdbcTemplate(dataSource)

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}

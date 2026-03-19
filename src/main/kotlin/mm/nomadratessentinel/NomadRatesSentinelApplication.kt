package mm.nomadratessentinel

import mm.nomadratessentinel.controller.RateController
import mm.nomadratessentinel.repository.CurrencyRateRepository
import mm.nomadratessentinel.service.RateSyncService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                RateController::class,
                RateSyncService::class,
                CurrencyRateRepository::class,
            ]
        )
    ]
)
class NomadRatesSentinelApplication

fun main(args: Array<String>) {
    runApplication<NomadRatesSentinelApplication>(*args)
}

package mm.nomadratessentinel.controller

import mm.nomadratessentinel.service.ReactiveRateSyncService
import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.RateComparison
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping
@Profile("reactive")
class ReactiveRateController(
    private val reactiveRateSyncService: ReactiveRateSyncService,
) {

    @PostMapping("/sync-and-compare")
    fun syncAndCompare(): Flux<RateComparison> =
        reactiveRateSyncService.syncAndCompare()

    @GetMapping("/compare/{code}")
    fun compare(@PathVariable code: CurrencyCode): Mono<RateComparison> =
        reactiveRateSyncService.compare(code)
            .switchIfEmpty(Mono.error(ReactiveRateNotFoundException(code)))
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class ReactiveRateNotFoundException(code: CurrencyCode) :
    RuntimeException("No comparable rates found for $code")

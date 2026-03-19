package mm.nomadratessentinel.controller

import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.service.RateSyncService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class RateController(
    private val rateSyncService: RateSyncService,
) {

    @PostMapping("/sync-and-compare")
    fun syncAndCompare(): List<RateComparison> =
        rateSyncService.syncAndCompare()

    @GetMapping("/compare/{code}")
    fun compare(@PathVariable code: CurrencyCode): RateComparison =
        rateSyncService.compare(code)
            ?: throw RateNotFoundException(code)
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class RateNotFoundException(code: CurrencyCode) :
    RuntimeException("No comparable rates found for $code")

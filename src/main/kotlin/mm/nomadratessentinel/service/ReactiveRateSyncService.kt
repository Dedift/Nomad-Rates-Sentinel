package mm.nomadratessentinel.service

import mm.nomadratessentinel.adapter.ReactiveNbkRateClient
import mm.nomadratessentinel.adapter.ReactiveXeRateClient
import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.ParsedRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.repository.ReactiveCurrencyRateRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
@Profile("reactive")
class ReactiveRateSyncService(
    private val reactiveNbkRateClient: ReactiveNbkRateClient,
    private val reactiveXeRateClient: ReactiveXeRateClient,
    private val reactiveCurrencyRateRepository: ReactiveCurrencyRateRepository,
    transactionManager: R2dbcTransactionManager,
) {
    private val transactionalOperator = TransactionalOperator.create(transactionManager)
    private val inFlightSync = AtomicReference<Mono<List<RateComparison>>?>()
    private val sourceRetrySpec = Retry.backoff(3, Duration.ofMillis(300))
        .maxBackoff(Duration.ofSeconds(3))
    private val persistenceRetrySpec = Retry.backoff(3, Duration.ofMillis(200))
        .maxBackoff(Duration.ofSeconds(2))
        .filter(::isConcurrencyFailure)

    fun syncAndCompare(): Flux<RateComparison> {
        val runningSync = inFlightSync.updateAndGet { current ->
            current ?: buildSyncMono()
        }!!

        return runningSync.flatMapMany { Flux.fromIterable(it) }
    }

    fun compare(code: CurrencyCode): Mono<RateComparison> =
        reactiveCurrencyRateRepository.findDeltaBetweenSources(code)
            .doOnSubscribe { logger.info("Starting reactive compare for {}", code) }
            .doOnSuccess { logger.info("Reactive compare for {} returned {}", code, if (it == null) "no data" else "a result") }

    private fun ParsedRate.toCurrencyRate(): CurrencyRate =
        CurrencyRate(
            code = code,
            rate = rate,
            source = source,
            updatedAt = Instant.now(),
        )

    private fun isConcurrencyFailure(ex: Throwable): Boolean =
        ex is OptimisticLockingFailureException ||
                ex is PessimisticLockingFailureException || ex is TransientDataAccessException

    companion object {
        private val logger = LoggerFactory.getLogger(ReactiveRateSyncService::class.java)
    }

    private fun buildSyncMono(): Mono<List<RateComparison>> {
        logger.info("Starting reactive sync-and-compare")
        val nbkRates = reactiveNbkRateClient.fetchRates()
            .retryWhen(sourceRetrySpec)
            .onErrorResume {
                logger.warn("Reactive NBK fetch failed", it)
                Flux.empty()
            }

        val xeRates = reactiveXeRateClient.fetchRates()
            .retryWhen(sourceRetrySpec)
            .onErrorResume {
                logger.warn("Reactive XE fetch failed", it)
                Flux.empty()
            }

        val pipeline = Mono.zip(
            nbkRates.collectList(),
            xeRates.collectList()
        )
            .flatMapIterable { tuple -> tuple.t1 + tuple.t2 }
            .map { it.toCurrencyRate() }
            .collectList()
            .flatMapMany { reactiveCurrencyRateRepository.saveAll(it).thenMany(reactiveCurrencyRateRepository.findAllDeltasBetweenSources()) }

        return transactionalOperator.transactional(pipeline)
            .retryWhen(persistenceRetrySpec)
            .collectList()
            .doOnNext { logger.info("Finished reactive sync-and-compare with {} compared rates", it.size) }
            .doFinally { inFlightSync.set(null) }
            .cache()
    }
}

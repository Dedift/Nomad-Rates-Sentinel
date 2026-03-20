package mm.nomadratessentinel.service

import mm.nomadratessentinel.adapter.NbkRateAdapter
import mm.nomadratessentinel.adapter.XeRateAdapter
import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.ParsedRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.repository.CurrencyRateRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class RateSyncService(
    private val nbkRateAdapter: NbkRateAdapter,
    private val xeRateAdapter: XeRateAdapter,
    private val currencyRateRepository: CurrencyRateRepository,
    private val transactionTemplate: TransactionTemplate,
    @Value($$"${rates.adapters.request-timeout-ms:10000}")
    private val requestTimeoutMs: Int,
) {

    fun syncAndCompare(): List<RateComparison> {
        logger.info("Starting sync-and-compare")
        val parsedRates = fetchRatesConcurrently()
        val result = persistWithRetry(parsedRates.map { it.toCurrencyRate() })
        logger.info("Finished sync-and-compare with {} compared rates", result.size)
        return result
    }

    fun compare(code: CurrencyCode): RateComparison? =
        currencyRateRepository.findDeltaBetweenSources(code).also {
            logger.info("Compare for {} returned {}", code, if (it == null) "no data" else "a result")
        }

    private fun fetchRatesConcurrently(): List<ParsedRate> =
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val nbkFuture = executor.submit(Callable { nbkRateAdapter.fetchRates() })
            val xeFuture = executor.submit(Callable { xeRateAdapter.fetchRates() })
            val sourceResults = listOf(
                awaitSource("NBK", nbkFuture),
                awaitSource("XE", xeFuture),
            )

            val successfulRates = sourceResults
                .filterIsInstance<SourceFetchResult.Success>()
                .flatMap { it.rates }

            if (successfulRates.isEmpty()) {
                val failedSources = sourceResults
                    .filterIsInstance<SourceFetchResult.Failure>()
                    .joinToString(", ") { it.source }
                throw ExternalSourceFetchException("all sources ($failedSources)", IllegalStateException("No sources responded successfully"))
            }

            successfulRates
        }

    private fun awaitSource(source: String, future: Future<List<ParsedRate>>): SourceFetchResult =
        try {
            val rates = future.get(requestTimeoutMs.toLong() + FUTURE_WAIT_BUFFER_MS, TimeUnit.MILLISECONDS)
            logger.info("Fetched {} rates from {}", rates.size, source)
            SourceFetchResult.Success(source, rates)
        } catch (ex: TimeoutException) {
            future.cancel(true)
            logger.warn("Timed out while fetching rates from {}", source, ex)
            SourceFetchResult.Failure(source, ExternalSourceTimeoutException(source, requestTimeoutMs, ex))
        } catch (ex: Exception) {
            logger.warn("Failed to fetch rates from {}", source, ex)
            SourceFetchResult.Failure(source, ExternalSourceFetchException(source, ex))
        }

    private fun persistWithRetry(currencyRates: List<CurrencyRate>): List<RateComparison> {
        var attempt = 0
        var lastFailure: RuntimeException? = null

        while (attempt < MAX_PERSIST_RETRIES) {
            try {
                return transactionTemplate.execute {
                    currencyRateRepository.saveAll(currencyRates)
                    currencyRateRepository.findAllDeltasBetweenSources()
                }
            } catch (ex: RuntimeException) {
                if (!isConcurrencyFailure(ex) || attempt == MAX_PERSIST_RETRIES - 1) {
                    throw ex
                }

                lastFailure = ex
                Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                attempt++
            }
        }

        throw lastFailure ?: IllegalStateException("Failed to persist currency rates after retries")
    }

    private fun isConcurrencyFailure(ex: RuntimeException): Boolean =
        ex is OptimisticLockingFailureException ||
                ex is PessimisticLockingFailureException || ex is TransientDataAccessException

    private fun ParsedRate.toCurrencyRate(): CurrencyRate =
        CurrencyRate(
            code = code,
            rate = rate,
            source = source,
            updatedAt = Instant.now(),
        )

    companion object {
        private val logger = LoggerFactory.getLogger(RateSyncService::class.java)
        private const val MAX_PERSIST_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 200L
        private const val FUTURE_WAIT_BUFFER_MS = 1_000L
    }
}

class ExternalSourceTimeoutException(
    source: String,
    timeoutMs: Int,
    cause: Throwable,
) : RuntimeException("Timed out while fetching rates from $source after ${timeoutMs}ms", cause)

class ExternalSourceFetchException(
    source: String,
    cause: Throwable,
) : RuntimeException("Failed to fetch rates from $source", cause)

sealed interface SourceFetchResult {
    val source: String

    data class Success(
        override val source: String,
        val rates: List<ParsedRate>,
    ) : SourceFetchResult

    data class Failure(
        override val source: String,
        val cause: RuntimeException,
    ) : SourceFetchResult
}

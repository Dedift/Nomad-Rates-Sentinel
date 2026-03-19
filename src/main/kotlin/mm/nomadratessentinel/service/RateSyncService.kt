package mm.nomadratessentinel.service

import mm.nomadratessentinel.adapter.NbkRateAdapter
import mm.nomadratessentinel.adapter.XeRateAdapter
import mm.nomadratessentinel.model.CurrencyCode
import mm.nomadratessentinel.model.CurrencyRate
import mm.nomadratessentinel.model.ParsedRate
import mm.nomadratessentinel.model.RateComparison
import mm.nomadratessentinel.repository.CurrencyRateRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Service
class RateSyncService(
    private val nbkRateAdapter: NbkRateAdapter,
    private val xeRateAdapter: XeRateAdapter,
    private val currencyRateRepository: CurrencyRateRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    fun syncAndCompare(): List<RateComparison> {
        val parsedRates = fetchRatesConcurrently()
        return persistWithRetry(parsedRates.map { it.toCurrencyRate() })
    }

    fun compare(code: CurrencyCode): RateComparison? =
        currencyRateRepository.findDeltaBetweenSources(code)

    private fun fetchRatesConcurrently(): List<ParsedRate> =
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val nbkFuture = executor.submit(Callable { nbkRateAdapter.fetchRates() })
            val xeFuture = executor.submit(Callable { xeRateAdapter.fetchRates() })

            nbkFuture.get() + xeFuture.get()
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
        private const val MAX_PERSIST_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 200L
    }
}

package mm.nomadratessentinel.controller

import mm.nomadratessentinel.service.ExternalSourceFetchException
import mm.nomadratessentinel.service.ExternalSourceTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ExternalSourceTimeoutException::class)
    fun handleTimeout(ex: ExternalSourceTimeoutException): ProblemDetail {
        logger.error("External source timeout", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.message ?: "External source timeout")
    }

    @ExceptionHandler(ExternalSourceFetchException::class)
    fun handleExternalFetch(ex: ExternalSourceFetchException): ProblemDetail {
        logger.error("External source fetch failed", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "External source fetch failed")
    }

    @ExceptionHandler(
        CannotGetJdbcConnectionException::class,
        DataAccessResourceFailureException::class,
    )
    fun handleDatabaseUnavailable(ex: Exception): ProblemDetail {
        logger.error("Database is unavailable", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database is unavailable")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}

package mm.nomadratessentinel.adapter

import mm.nomadratessentinel.model.ParsedRate
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Duration

@Component
@Profile("reactive")
class ReactiveXeRateClient(
    webClientBuilder: WebClient.Builder,
    private val xeRateAdapter: XeRateAdapter,
    @Value($$"${rates.adapters.xe.url}")
    private val url: String,
    @Value($$"${rates.adapters.request-timeout-ms:10000}")
    private val requestTimeoutMs: Int,
) {
    private val webClient = webClientBuilder.build()

    fun fetchRates(): Flux<ParsedRate> =
        webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono<String>()
            .timeout(Duration.ofMillis(requestTimeoutMs.toLong()))
            .map { Jsoup.parse(it, url) }
            .flatMapMany { Flux.fromIterable(xeRateAdapter.parse(it)) }
}

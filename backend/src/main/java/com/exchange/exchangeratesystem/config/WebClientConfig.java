package com.exchange.exchangeratesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

/**
 * Exposes a single WebClient bean pre-configured for Fixer.io, per plan.md: used
 * only for that one outbound integration, backed by the JDK's HttpClient (no
 * Netty/reactor-netty) so this stays a plain servlet application that happens to
 * use WebClient as a client, not a reactive server.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient fixerWebClient(
            @Value("${fixer.base-url}") String baseUrl,
            @Value("${fixer.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new JdkClientHttpConnector())
                .filter(addAccessKey(apiKey))
                .build();
    }

    /**
     * Fixer.io's free-tier API authenticates via an access_key query parameter on
     * every request. Attaching it here once means FixerClient never has to
     * know about authentication at all.
     */
    private ExchangeFilterFunction addAccessKey(String apiKey) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> Mono.just(
                ClientRequest.from(request)
                        .url(UriComponentsBuilder.fromUri(request.url())
                                .queryParam("access_key", apiKey)
                                .build(true)
                                .toUri())
                        .build()));
    }
}

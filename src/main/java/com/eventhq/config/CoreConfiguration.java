package com.eventhq.config;

import java.time.Clock;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class CoreConfiguration {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder b, EventHqProperties p) {
    return b.connectTimeout(p.httpTimeout()).readTimeout(p.httpTimeout()).build();
  }

  @Bean
  public WebClient webClient(WebClient.Builder b, EventHqProperties p) {
    return b.clientConnector(
            new ReactorClientHttpConnector(
                HttpClient.create()
                    .option(
                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(p.httpTimeout().toMillis()))
                    .responseTimeout(p.httpTimeout())))
        .codecs(c -> c.defaultCodecs().maxInMemorySize(262144))
        .build();
  }
}

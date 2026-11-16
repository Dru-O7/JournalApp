package io.github.druo7.journal.service;

import io.github.druo7.journal.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ExternalInfoClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExternalInfoClient client = new ExternalInfoClient(builder.build(), new AppProperties(
            new AppProperties.Jwt("x", Duration.ofMinutes(1), "i"), "k", new AppProperties.Cors(List.of()),
            new AppProperties.Weather("https://weather.test/current", "KEY", "New Delhi"),
            new AppProperties.Quote("https://quote.test/q"),
            new AppProperties.LoginRateLimit(1, 1, Duration.ofMinutes(1))));

    @Test
    void parsesWeatherAndEncodesCityAsQueryParam() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://weather.test/current")))
                .andExpect(queryParam("query", "S%C3%A3o%20Paulo%20%26x%3D1"))
                .andRespond(withSuccess("""
                        {"location":{},"current":{"temperature":20,"feelslike":19,"weather_descriptions":["Sunny"]}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.weather("São Paulo &x=1")).isEqualTo(new ExternalInfoClient.WeatherInfo(19, "Sunny"));
    }

    @Test
    void weatherstackErrorBodyWithHttp200YieldsNull() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://weather.test/current")))
                .andRespond(withSuccess("{\"success\":false,\"error\":{\"code\":104}}", MediaType.APPLICATION_JSON));
        assertThat(client.weather("Pune")).isNull();
    }

    @Test
    void upstreamFailureYieldsNull() {
        server.expect(requestTo("https://quote.test/q")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(client.quote()).isNull();
    }

    @Test
    void parsesQuote() {
        server.expect(requestTo("https://quote.test/q"))
                .andRespond(withSuccess("{\"data\":{\"author\":\"Seneca\",\"quote\":\"Luck is...\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.quote()).isEqualTo(new ExternalInfoClient.QuoteInfo("Luck is...", "Seneca"));
    }
}

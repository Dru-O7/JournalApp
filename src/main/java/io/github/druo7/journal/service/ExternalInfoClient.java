package io.github.druo7.journal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import io.github.druo7.journal.config.AppProperties;
import io.github.druo7.journal.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Weather and quote lookups. Both are best-effort: any failure (timeout, HTTP error, unexpected or
 * error-shaped body) returns null and the greeting is shown without that part.
 */
@Slf4j
@Component
public class ExternalInfoClient {

    public record WeatherInfo(Integer feelsLike, String description) {
    }

    public record QuoteInfo(String quote, String author) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WeatherApiResponse(Current current) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Current(Integer feelslike, @JsonProperty("weather_descriptions") List<String> weatherDescriptions) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteApiResponse(Data data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(String quote, String author) {
        }
    }

    private final RestClient http;
    private final AppProperties props;

    public ExternalInfoClient(RestClient externalRestClient, AppProperties props) {
        this.http = externalRestClient;
        this.props = props;
    }

    /** Cached per city for 5 minutes; the cache key is a hash so city names are not visible in Redis. */
    @Cacheable(cacheNames = CacheConfig.WEATHER, keyGenerator = "hashedKeyGenerator", unless = "#result == null")
    public WeatherInfo weather(String city) {
        String apiKey = props.weather().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            var uri = UriComponentsBuilder.fromUriString(props.weather().baseUrl())
                    .queryParam("access_key", apiKey)
                    .queryParam("query", city)
                    .encode().build().toUri();
            WeatherApiResponse body = http.get().uri(uri).retrieve().body(WeatherApiResponse.class);
            // weatherstack reports errors (bad key, quota) as HTTP 200 with no "current" object.
            if (body == null || body.current() == null || body.current().feelslike() == null) {
                log.warn("Weather API returned no data for the requested city");
                return null;
            }
            List<String> d = body.current().weatherDescriptions();
            return new WeatherInfo(body.current().feelslike(), d == null || d.isEmpty() ? null : d.get(0));
        } catch (RestClientException e) {
            log.warn("Weather API call failed: {}", e.getMessage());
            return null;
        }
    }

    /** Social-media residue the quote source sometimes appends, e.g. "… unconcerned. @" or "#stoic". */
    private static final Pattern TRAILING_TAGS = Pattern.compile("(\\s+[@#]\\S*)+$");

    /** Tidies third-party quote text for display: drops trailing @handles/#tags and extra whitespace. */
    static String cleanQuote(String raw) {
        String s = raw.strip().replaceAll("\\s+", " ");
        s = TRAILING_TAGS.matcher(s).replaceAll("");
        if (s.startsWith("@") || s.startsWith("#")) {
            s = "";
        }
        return s.isBlank() ? null : s;
    }

    @Cacheable(cacheNames = CacheConfig.QUOTE, key = "'daily'", unless = "#result == null")
    public QuoteInfo quote() {
        try {
            QuoteApiResponse body = http.get().uri(props.quote().url()).retrieve().body(QuoteApiResponse.class);
            if (body == null || body.data() == null || body.data().quote() == null) {
                return null;
            }
            String quote = cleanQuote(body.data().quote());
            if (quote == null) {
                return null;
            }
            String author = body.data().author() == null ? null : cleanQuote(body.data().author());
            return new QuoteInfo(quote, author);
        } catch (RestClientException e) {
            log.warn("Quote API call failed: {}", e.getMessage());
            return null;
        }
    }
}

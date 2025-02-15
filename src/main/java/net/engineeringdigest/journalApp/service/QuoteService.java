package net.engineeringdigest.journalApp.service;

import net.engineeringdigest.journalApp.api.QuoteResponse;
import net.engineeringdigest.journalApp.cache.AppCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class QuoteService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AppCache appCache;

    @Autowired
    private RedisService redisService;

    public QuoteResponse getQuote() {
        // Try to get the quote from Redis first
        QuoteResponse cachedQuote = redisService.get("quote_api_cache", QuoteResponse.class);
        if (cachedQuote != null) {
            return cachedQuote;
        }

        // If not found in cache, fetch it using the API URL from the app cache
        String apiUrl = appCache.appCache.get("quote_api");
        ResponseEntity<QuoteResponse> response = restTemplate.exchange(apiUrl, HttpMethod.GET, null, QuoteResponse.class);
        QuoteResponse quote = response.getBody();

        // Cache the response for 300 seconds if not null
        if (quote != null) {
            redisService.set("quote_api_cache", quote, 86400L);
        }

        return quote;
    }
}

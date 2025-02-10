package net.engineeringdigest.journalApp.service;

import net.engineeringdigest.journalApp.api.QuoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class QuoteService {
    @Autowired
    private RestTemplate restTemplate;
    private static final String API="https://stoic.tekloon.net/stoic-quote";

    public QuoteResponse getQuote(){
        ResponseEntity<QuoteResponse> response = restTemplate.exchange(API, HttpMethod.GET, null, QuoteResponse.class);
        QuoteResponse body = response.getBody();
        return body;
    }
}

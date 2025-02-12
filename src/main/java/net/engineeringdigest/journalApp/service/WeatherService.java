package net.engineeringdigest.journalApp.service;


import net.engineeringdigest.journalApp.api.WeatherResponse;
import net.engineeringdigest.journalApp.cache.AppCache;
import net.engineeringdigest.journalApp.repository.ConfigJournalAppRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WeatherService {

    @Value("${weather.api.key}")
    private String apiKey;

//    private static final String API="http://api.weatherstack.com/current?access_key=KEY&query=CITY";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AppCache appCache;

    public WeatherResponse getWeather(String city){
        String finalApi=appCache.appCache.get("weather_api").replace("<city>",city).replace("<apiKey>",apiKey);
        ResponseEntity<WeatherResponse> response = restTemplate.exchange(finalApi, HttpMethod.GET, null, WeatherResponse.class);
        WeatherResponse body = response.getBody();
        return body;

    }
}

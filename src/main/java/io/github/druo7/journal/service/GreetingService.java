package io.github.druo7.journal.service;

import io.github.druo7.journal.config.AppProperties;
import io.github.druo7.journal.dto.AuthDtos.GreetingResponse;
import io.github.druo7.journal.dto.AuthDtos.MeResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GreetingService {
    private final UserService userService;
    private final ExternalInfoClient external;
    private final AppProperties props;

    public GreetingService(UserService userService, ExternalInfoClient external, AppProperties props) {
        this.userService = userService;
        this.external = external;
        this.props = props;
    }

    public GreetingResponse greet(UUID userId) {
        MeResponse me = userService.me(userId);
        String city = me.city() != null ? me.city() : props.weather().defaultCity();
        ExternalInfoClient.WeatherInfo weather = external.weather(city);
        ExternalInfoClient.QuoteInfo quote = external.quote();
        return new GreetingResponse("Hi " + me.username(),
                weather == null ? null : weather.feelsLike(),
                weather == null ? null : weather.description(),
                quote == null ? null : quote.quote(),
                quote == null ? null : quote.author());
    }
}

package net.engineeringdigest.journalApp.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import net.engineeringdigest.journalApp.api.QuoteResponse;
import net.engineeringdigest.journalApp.api.WeatherResponse;
import net.engineeringdigest.journalApp.entity.User;
import net.engineeringdigest.journalApp.repository.UserRepository;
import net.engineeringdigest.journalApp.service.QuoteService;
import net.engineeringdigest.journalApp.service.UserService;
import net.engineeringdigest.journalApp.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "2. User APIs",description = "Greetings, Update and Delete User")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private QuoteService quoteService;


    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = authentication.getName();
        User userInDb = userService.finduserByUserName(userName);
            userInDb.setUserName(user.getUserName());
            userInDb.setPassword(user.getPassword());
            userService.saveNewUser(userInDb);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);

    }

    @DeleteMapping
    public ResponseEntity<?> deleteUserById(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String name = authentication.getName();
        userRepository.deleteByUserName(name);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping
    public ResponseEntity<?> greeting(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.finduserByUserName(authentication.getName());
        String city = (user != null && user.getCity() != null) ? user.getCity() : "New Delhi";

        WeatherResponse weatherResponse = weatherService.getWeather(city);
        QuoteResponse quoteResponse= quoteService.getQuote();

        String greetings="";
        if(weatherResponse!=null){
            greetings="\n Weather feels like: "+weatherResponse.getCurrent().getFeelslike()+"\n \n Quote of the day: \n"+quoteResponse.getData().getQuote();
        }


        return new ResponseEntity<>("Hi " + authentication.getName()+greetings,HttpStatus.OK);
    }

}

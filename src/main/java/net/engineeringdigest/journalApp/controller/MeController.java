package net.engineeringdigest.journalApp.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.engineeringdigest.journalApp.dto.AuthDtos.*;
import net.engineeringdigest.journalApp.security.AuthenticatedUser;
import net.engineeringdigest.journalApp.service.GreetingService;
import net.engineeringdigest.journalApp.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own account. There is no way to address another user's account here. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account", description = "Profile, credentials, greeting, account deletion")
public class MeController {
    private final UserService userService;
    private final GreetingService greetingService;

    public MeController(UserService userService, GreetingService greetingService) {
        this.userService = userService;
        this.greetingService = greetingService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return userService.me(me.id());
    }

    @PatchMapping
    public MeResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(me.id(), req);
    }

    @PutMapping("/credentials")
    public AuthResponse changeCredentials(@AuthenticationPrincipal AuthenticatedUser me,
                                          @Valid @RequestBody ChangeCredentialsRequest req, HttpServletRequest http) {
        return userService.changeCredentials(me.id(), req, http.getRemoteAddr());
    }

    @PostMapping("/logout-everywhere")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutEverywhere(@AuthenticationPrincipal AuthenticatedUser me) {
        userService.logoutEverywhere(me.id());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ReauthRequest req,
                              HttpServletRequest http) {
        userService.deleteAccount(me.id(), req, http.getRemoteAddr());
    }

    @GetMapping("/greeting")
    public GreetingResponse greeting(@AuthenticationPrincipal AuthenticatedUser me) {
        return greetingService.greet(me.id());
    }
}

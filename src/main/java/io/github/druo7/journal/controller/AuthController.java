package io.github.druo7.journal.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.github.druo7.journal.dto.AuthDtos.AuthResponse;
import io.github.druo7.journal.dto.AuthDtos.LoginRequest;
import io.github.druo7.journal.dto.AuthDtos.SignupRequest;
import io.github.druo7.journal.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign up and log in")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        return userService.signup(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return userService.login(req, http.getRemoteAddr());
    }
}

package io.github.druo7.journal.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.github.druo7.journal.dto.AuthDtos.AdminUserView;
import io.github.druo7.journal.dto.AuthDtos.SignupRequest;
import io.github.druo7.journal.dto.PageResponse;
import io.github.druo7.journal.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Administration. Admins see account metadata only (username, roles, creation date); there is
 * intentionally no admin access to journal entries, and entries are ciphertext anyway.
 * Admins change their own credentials through /api/v1/me/credentials like everyone else.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "User list and admin creation")
public class AdminController {
    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public PageResponse<AdminUserView> users(@RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return userService.listUsers(page, size);
    }

    /** Body is produced by the admin UI, which derives the new admin's keys from the chosen password. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView createAdmin(@Valid @RequestBody SignupRequest req) {
        return userService.createAdmin(req);
    }
}

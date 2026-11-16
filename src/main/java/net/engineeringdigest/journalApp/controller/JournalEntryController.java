package net.engineeringdigest.journalApp.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import net.engineeringdigest.journalApp.dto.EntryDtos.*;
import net.engineeringdigest.journalApp.dto.PageResponse;
import net.engineeringdigest.journalApp.security.AuthenticatedUser;
import net.engineeringdigest.journalApp.service.JournalEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The caller's own (encrypted) journal entries. */
@RestController
@RequestMapping("/api/v1/entries")
@Tag(name = "Journal entries", description = "Encrypted entries of the signed-in user")
public class JournalEntryController {
    private final JournalEntryService service;

    public JournalEntryController(JournalEntryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EntryResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(me.id(), page, size);
    }

    @GetMapping("/{id}")
    public EntryResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CreateEntryRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public EntryResponse update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                @Valid @RequestBody UpdateEntryRequest req) {
        return service.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        service.delete(me.id(), id);
    }
}

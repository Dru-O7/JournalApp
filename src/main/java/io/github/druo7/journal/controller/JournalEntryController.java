package io.github.druo7.journal.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.github.druo7.journal.dto.EntryDtos.*;
import io.github.druo7.journal.dto.PageResponse;
import io.github.druo7.journal.security.AuthenticatedUser;
import io.github.druo7.journal.service.JournalEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The caller's own (encrypted) daily journal: one entry per day, editable until that day ends.
 * There is intentionally no endpoint to delete an entry.
 */
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

    /** The days written on over the last year, for the activity heatmap. */
    @GetMapping("/activity")
    public ActivityResponse activity(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.activity(me.id());
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
}

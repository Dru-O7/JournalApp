package net.engineeringdigest.journalApp.security;

import java.util.UUID;

/** The principal placed in the SecurityContext for a request with a valid token. */
public record AuthenticatedUser(UUID id, String username) {
}

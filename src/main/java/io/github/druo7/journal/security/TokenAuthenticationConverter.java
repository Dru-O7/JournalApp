package io.github.druo7.journal.security;

import io.github.druo7.journal.entity.User;
import io.github.druo7.journal.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Turns a signature-verified JWT into an authentication. Roles are read from the database (not the
 * token) so revoking a role takes effect immediately; a deleted user or stale token version yields 401.
 */
@Component
public class TokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public TokenAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        User user = users.findById(id).orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        Object tv = jwt.getClaim(JwtService.TOKEN_VERSION_CLAIM);
        if (!(tv instanceof Number n) || n.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token revoked");
        }
        var authorities = user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.getId(), user.getUsername()), null, authorities);
    }
}

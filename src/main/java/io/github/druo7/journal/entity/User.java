package io.github.druo7.journal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "auth_hash", nullable = false, length = 100)
    private String authHash;

    @Column(name = "wrapped_key", nullable = false, length = 200)
    private String wrappedKey;

    @Column(name = "wrapped_key_iv", nullable = false, length = 24)
    private String wrappedKeyIv;

    @Column(name = "kdf_version", nullable = false)
    private short kdfVersion = 1;

    @Column(name = "email_enc", columnDefinition = "text")
    private String emailEnc;

    @Column(name = "city_enc", columnDefinition = "text")
    private String cityEnc;

    @Column(name = "weekly_report", nullable = false)
    private boolean weeklyReport;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public User(String username, String authHash, String wrappedKey, String wrappedKeyIv, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.authHash = authHash;
        this.wrappedKey = wrappedKey;
        this.wrappedKeyIv = wrappedKeyIv;
        this.roles = EnumSet.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public void bumpTokenVersion() {
        tokenVersion++;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

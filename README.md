# Journal App

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Dru-O7_JournalApp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Dru-O7_JournalApp)

A private journal with **end-to-end encrypted entries**. Spring Boot 4 + PostgreSQL backend, React frontend.
Journal entries are encrypted in the browser; the server and database only ever hold ciphertext, so
neither administrators nor anyone with database access can read them.

## Security model

| Data | Who can read it | How |
|---|---|---|
| Entry title, text, mood | **Only the owner**, in their browser | AES-256-GCM with a per-user data key that the server never sees |
| Password | Nobody | Never sent to the server. The browser sends a derived *auth key*; the server stores `bcrypt(authKey)` |
| Email, city | The running application (to send email / fetch weather) | AES-256-GCM at rest with a server key (`APP_DATA_KEY`). A database dump alone reveals nothing |
| Mood for the weekly email | The running application, **only if the user opts in** | Server-key encrypted copy; deleted when the user opts out |
| Username, roles, timestamps | Admins / operators | Plaintext (needed for login and administration) |

Key hierarchy (see [frontend/src/crypto.ts](frontend/src/crypto.ts)):

```
password ──PBKDF2-SHA256, 600k iterations, salt = app+username──▶ master key
master key ──HKDF "auth"──▶ auth key   → sent at login, stored as bcrypt
master key ──HKDF "kek" ──▶ KEK        → never leaves the browser
KEK wraps a random data key (DEK)      → server stores only the wrapped DEK
DEK encrypts each entry (entry id bound as associated data)
```

Consequences:
- **A forgotten password means the entries are unrecoverable.** That is by design; there is no reset.
- Changing the password re-wraps the same data key, so existing entries stay readable.
- The decrypted key lives only in memory; after a page reload the journal is locked until the password is re-entered.
- Admins can list accounts and create admins. There is no API that returns another user's entries.

Other protections: per-user data isolation on every query (foreign ids return 404), stateless JWT auth
(HS256, key from env, revocable via a token version), login rate limiting, request size limits,
optimistic locking on edits, strict CSP on the built frontend, and no server sessions or cookies.

## Running locally

Prerequisites: Java 17+, Node 22+, PostgreSQL (or Docker), optionally Redis.

```sh
# 1. Dependencies (or point at your own PostgreSQL)
docker compose up -d

# 2. Backend config
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
#    then replace the two REPLACE_WITH values with: openssl rand -base64 32

# 3. Backend on :8081
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# 4. Frontend on :5173 (proxies /api to :8081)
cd frontend && npm install && npm run dev
```

Swagger UI (local profile only): http://localhost:8081/swagger-ui/index.html

## Configuration (production)

All secrets come from environment variables; the app refuses to start if a required one is missing or too short.

| Variable | Required | Description |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | PostgreSQL JDBC URL and credentials |
| `JWT_SECRET` | yes | Base64, ≥ 32 bytes (`openssl rand -base64 32`). Rotating it signs everyone out |
| `APP_DATA_KEY` | yes | Base64, exactly 32 bytes. **Losing it makes stored emails/cities unreadable; back it up** |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | no | Cache for weather/quote; the app works without Redis |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | no | SMTP for the weekly email; skipped if unset |
| `WEATHER_API_KEY`, `WEATHER_BASE_URL` | no | weatherstack. Default URL is HTTPS; weatherstack's free plan may require `http://`, which exposes the key in transit |
| `CORS_ALLOWED_ORIGINS` | no | Only if the frontend is served from a different origin |
| `REPORT_ZONE` | no | Time zone for the Sunday 09:00 weekly email (default `UTC`) |
| `PORT` | no | HTTP port (default 8081) |

Serve `frontend/dist` from the same origin as the API (reverse proxy `/api` → backend), behind HTTPS.
The built `index.html` carries a strict CSP, but browsers ignore `frame-ancestors` in a `<meta>` tag, so have the
web server add `Content-Security-Policy: frame-ancestors 'none'` and `X-Frame-Options: DENY` to the page response
to block clickjacking.
Health checks: `/actuator/health/liveness` and `/actuator/health/readiness`.

### Creating the first admin

Sign up normally, then grant the role in the database:

```sql
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE lower(username) = lower('your-username');
```

Further admins can be created from the Admin page.

## API (v1)

All endpoints are under `/api/v1`. Errors use `application/problem+json`.

| Method | Path | Description |
|---|---|---|
| POST | `/auth/signup` | Create account (client-derived `authKey`, wrapped data key) |
| POST | `/auth/login` | Returns JWT + profile (incl. wrapped data key) |
| GET / PATCH | `/me` | Own profile / update email, city, weekly-report opt-in |
| PUT | `/me/credentials` | Change username and/or password (re-wrapped key); revokes other tokens |
| POST | `/me/logout-everywhere` | Revoke all tokens |
| DELETE | `/me` | Delete account and all entries (requires `authKey`) |
| GET | `/me/greeting` | Greeting with weather and quote (best effort) |
| GET | `/entries?page=&size=` | Own entries, newest first, paginated |
| GET / PUT / DELETE | `/entries/{id}` | Own entry; `PUT` requires the last-seen `version` (409 if stale) |
| POST | `/entries` | Create (client-generated UUID, ciphertext, iv) |
| GET / POST | `/admin/users` | Admin: list accounts (metadata only) / create admin |

## Tests

```sh
./mvnw verify                 # backend: unit + integration tests on an embedded PostgreSQL (no Docker)
cd frontend && npm test       # crypto tests
# Optional end-to-end check against a running backend:
E2E_BASE_URL=http://localhost:8081 npx vitest run src/e2e.test.ts
```

## Migrating from the MongoDB version

The previous version stored entries in plaintext in MongoDB with server-side password hashing. Because
the new scheme derives keys from each user's password in the browser, existing data cannot be converted
server-side. The MongoDB data is left untouched; migrating real users would need an "upgrade on first
login" flow (the browser re-encrypts the user's old entries after they sign in once).

## License

MIT

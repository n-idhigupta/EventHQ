# EventHQ

Java 17 / Spring Boot 3.5.16 backend for assigned-seat event booking. Maven Wrapper is included. Local execution uses H2; the MySQL profile uses MySQL 8.4. Payments and notification delivery are simulated integrations.

## Build and run

From this directory, on Windows PowerShell:

```powershell
.\run.cmd -B verify
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = 'Choose-a-strong-password-123'
.\run.cmd spring-boot:run '-Dspring-boot.run.arguments=--eventhq.seed=true'
```

On Linux/macOS:

```sh
chmod +x mvnw
./mvnw -B verify
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='Choose-a-strong-password-123' ./mvnw spring-boot:run -Dspring-boot.run.arguments=--eventhq.seed=true
```

After packaging: `java -jar target/eventhq-1.0.0.jar --eventhq.seed=true`. The API listens on port 8080. `GET /actuator/health` is public. The optional dev seed creates three venues, STANDARD/VIP seats, and three published future events; it creates no customer or default administrator. H2 data is ephemeral. Bootstrap administrator credentials must be supplied together; existing customer accounts are never promoted. Remove bootstrap credentials after provisioning a persistent database.

Java 25 can compile this project: Maven explicitly enables Lombok annotation processing and targets Java 17 bytecode. Maven Wrapper needs network access on its first run. `verify` runs H2/API tests and MySQL Testcontainers scenarios; MySQL tests are explicitly skipped when Docker is unavailable. For a release gate that requires MySQL, start Docker and run `./mvnw -B verify -Dit.test=MySqlBookingIT`, checking that no integration tests were skipped.

## MySQL and containers

Copy `.env.example` to `.env` and replace all example secrets. Start Docker, then run:

```sh
docker compose up --build -d
```

Compose supplies the `mysql` profile, database credentials, and JWT secret to the app. The database volume persists data. Alternatively run `docker compose up -d mysql`, export `SPRING_PROFILES_ACTIVE=mysql`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` into your shell, and launch the JAR. `DB_URL` defaults to `jdbc:mysql://localhost:3306/eventhq?connectionTimeZone=UTC`.

Spring does not automatically load `.env` files; Docker Compose does. Non-container deployments must inject environment variables themselves. The MySQL profile requires credentials and a JWT secret of at least 32 characters. Generate a random secret through your secret manager. Flyway owns the schema; Hibernate validates it. Always back up the database before deploying migrations. Deploy with TLS at your ingress and restrict database access to the application network.

## API walkthrough

All JSON requests use `Content-Type: application/json`. Authenticate using `Authorization: Bearer <accessToken>`. Registration always creates CUSTOMER accounts; administrator roles come only from explicit bootstrap configuration. Passwords accept 12?72 printable ASCII characters, matching BCrypt's byte limit. JWTs expire after 15 minutes; log in again to obtain a new token.

1. `POST /api/v1/auth/register` with `{"email":"customer@example.com","password":"Customer-password-123"}`. The response contains `accessToken`, `tokenType`, and `expiresIn`.
2. `POST /api/v1/auth/login` accepts the same shape. Log in as the bootstrap administrator to manage catalog data.
3. `GET /api/v1/events?page=0&size=20` lists published events. `GET /api/v1/events/{id}/seats` returns event-specific inventory IDs and current availability. Use its `id` values for booking; physical `seatId` values are different identifiers.
4. `POST /api/v1/bookings` with `{"eventId":1,"eventSeatIds":[1,2]}` creates a ten-minute hold. The response snapshots item prices and total. Requests accept 1?10 distinct seats from one event.
5. `POST /api/v1/bookings/{id}/payments` with header `Idempotency-Key: customer-order-0001` and body `{"gateway":"SIMULATED","token":"tok_approved"}` confirms the booking and issues tickets. Use `tok_declined` for a failed payment.
6. `GET /api/v1/bookings/{id}` returns an owned booking. `GET /api/v1/tickets` returns the authenticated user's tickets.
7. `DELETE /api/v1/bookings/{id}` releases an unpaid hold. Paid bookings cannot be cancelled in this version.

Payment declines return HTTP 200 with `outcome=DECLINED`, leaving the hold usable until expiry. A new payment attempt requires a new key. Repeating the same key and payload returns the saved outcome; changing its payload or using it for another booking returns 409. A confirmed booking rejects further attempts with different keys. Expired holds cannot be paid; a payment request that first observes expiry returns `outcome=EXPIRED`, while already closed bookings return 409. Unknown and non-owned booking IDs both return 404.

### Catalog administration

| Method | Path under `/api/v1` | Body / behavior |
|---|---|---|
| GET | `/venues`, `/venues/{id}`, `/venues/{id}/seats` | Public venue catalog |
| POST | `/admin/venues` | `{"name":"Hall","address":"Delhi","latitude":28.6,"longitude":77.2}` |
| PUT / DELETE | `/admin/venues/{id}` | Update metadata / delete unused venue |
| POST | `/admin/venues/{id}/seats` | `{"label":"A1","tier":"STANDARD"}`; tier also supports VIP |
| PUT / DELETE | `/admin/venues/{id}/seats/{seatId}` | Edit / remove a physical seat |
| GET / POST | `/admin/sponsors` | List / create with `{"name":"Sponsor"}` |
| PUT / DELETE | `/admin/sponsors/{id}` | Update / delete; referenced sponsors cannot be deleted |
| GET / POST | `/admin/events` | List drafts and published / create draft |
| GET / PUT / DELETE | `/admin/events/{id}` | Inspect / edit / delete draft |
| POST | `/admin/events/{id}/publish` | Freeze event and create event inventory; idempotent |
| GET | `/events/{id}` | Published event details |

Event input example (use future dates):

```json
{"name":"Concert","venueId":1,"startsAt":"2030-12-01T18:00:00Z","earlyBirdUntil":"2030-11-01T00:00:00Z","basePrice":1000.00,"sponsorIds":[]}
```

Seating freezes as soon as any event references a venue, including drafts. Event updates cannot change venue. Published events cannot be edited or deleted. Venue metadata can still be updated. A venue needs at least one seat before creating an event. Pricing uses VIP ?1.50 first, otherwise EARLY_BIRD ?0.80 strictly before cutoff, otherwise STANDARD. Prices round HALF_UP to two decimals. Currency defaults to INR and is fixed when creating an event.

### External integrations and legacy metrics

- `GET /api/v1/integrations/currency?base=INR&target=USD&amount=100.00` uses RestTemplate and a bounded TTL cache. Configure `CURRENCY_URL` to a trusted endpoint accepting `base`/`symbols` and returning `{"rates":{"USD":0.012}}`.
- `GET /api/v1/integrations/events/{id}/weather` uses WebClient and returns a reactive result. Configure `WEATHER_URL` to a trusted endpoint accepting `latitude`, `longitude`, and `current=temperature_2m,weather_code`, returning `{"current":{"temperature_2m":24.5,"weather_code":3}}`. JPA coordinate lookup runs on boundedElastic; the HTTP exchange is nonblocking.
- Blank integration URLs produce structured 502 errors; same-currency conversion works locally. No credentials or paid providers are required. Connect/read/response timeouts default to five seconds. These adapters implement the documented response contracts; configure a matching provider or adapter.
- `GET /admin/legacy/metrics` is a Jakarta Servlet returning escaped HTML. Supply an ADMIN bearer token. `/actuator/metrics` also requires ADMIN. H2 console is dev-only and ADMIN-protected; use an HTTP client capable of supplying bearer headers.

## Configuration

| Property / environment | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` H2 or `mysql` persistent database |
| `JWT_SECRET` | dev-only development value | HMAC signing key; required outside dev |
| `eventhq.jwt-lifetime` | `15m` | Access-token lifetime |
| `eventhq.hold-duration` | `10m` | Reservation lifetime |
| `eventhq.currency` | `INR` | New-event currency |
| `eventhq.vip-multiplier` / `eventhq.early-bird-multiplier` | `1.50` / `0.80` | Dynamic pricing |
| `eventhq.cache-size` / `eventhq.cache-ttl` | `256` / `5m` | Currency cache limit and expiry |
| `eventhq.http-timeout` | `5s` | Upstream timeout |
| `CURRENCY_URL` / `WEATHER_URL` | empty | Trusted upstream endpoints |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | empty | Optional explicit admin provisioning |
| `eventhq.seed` | `false` | Opt-in sample catalog, dev profile only |
| `eventhq.expiry-poll-ms` / `eventhq.notification-poll-ms` | `30000` / `10000` | Background processing intervals |

## Architecture and guarantees

Spring singleton services use constructor injection. `NotificationWorker` demonstrates optional setter injection for a `NotificationSink`; the legacy servlet demonstrates limited field injection. `CurrencyCache` demonstrates singleton scope and `@PostConstruct`/`@PreDestroy`. Configuration records validate property binding. Public APIs use DTOs; JPA relationships also demonstrate Jackson managed/back references and ignores without exposing credentials.

Hold creation locks selected event-seat rows in increasing inventory ID order within a READ_COMMITTED transaction. That order is stable relative to physical seat ID because publication creates inventory in physical ID order. Payment, cancellation, and expiry lock their booking first, then its inventory in the same order. Hold reclamation reads old owners without locking them, avoiding an inverse booking/seat lock order. No application-local cache governs inventory. Expired reservations become reclaimable immediately, independent of cleanup timing; cleanup only releases rows still owned by its booking. Availability responses are advisory snapshots, and bookings validate availability again under locks.

Payment attempts are serialized by booking locks and protected by unique idempotency keys. Ticket issuance, payment approval, booking state, and an outbox record commit together. The gateway is synchronous and simulated; integrating a real provider requires a separate authorization/webhook/reconciliation workflow rather than placing network calls inside this transaction.

Booking-confirmed observers run after commit. A scheduled outbox poller recovers delivery failures with capped exponential backoff. Delivery is at least once across crashes; replacement `NotificationSink` implementations must deduplicate the stable `booking-confirmed-{id}` key. The default sink logs a simulated delivery without logging recipient email. Monitor pending outbox records and errors when connecting real notification delivery.

Errors contain timestamp, HTTP status, stable code, safe message, path, correlation ID, and field errors. Supply `X-Correlation-ID` (letters/digits/hyphens, up to 64 characters) or receive a generated UUID. Application logs use structured JSON. Unauthenticated requests return 401; unauthorized roles return 403; seat/data conflicts return 409. Internal exception details stay in server logs.

## Verification and operational limits

Tests exercise H2 schema startup, pricing boundaries and rounding, JWT expiry/signature/issuer validation, auth and role checks, booking ownership, rollback on mixed availability, expiry/reclamation, simultaneous holds, duplicate payment races, expiry/payment races, notification retries, upstream parsing/caching/errors/timeouts, and reactive weather. The same booking scenarios run against a real MySQL 8.4 container when Docker is available.

Use `/actuator/health/readiness` and `/actuator/health/liveness` for orchestration; restrict detailed metrics to operators. Use MySQL backups and an external secret manager in deployments. Rate limiting, password recovery, account verification, refresh tokens, real payment processing, real email delivery, refunds, ticket transfers, and event cancellation are outside this version. The stateless JWT design does not provide immediate token revocation. Configure ingress rate limits and TLS before public exposure.

## Requirement-to-code map

| Requirement | Implementation |
|---|---|
| IoC, lifecycle, singleton | `config/CoreConfiguration`, `integration/CurrencyCache` |
| Constructor, setter, field DI | Services, `notification/NotificationWorker`, `legacy/AdminMetricsServlet` |
| Entity relationships and Jackson | `venue`, `user`, `event`, `booking`, `ticket` entities |
| Transactions and inventory locks | `booking/BookingService`, inventory/booking repositories |
| REST and validation | Auth, catalog, booking, ticket, integration controllers and DTO records |
| Structured exception handling | `exception/GlobalExceptionHandler`, security entry/denied handlers |
| Strategy and factory | `pricing/*`, `payment/*` |
| Observer and durable outbox | `notification/*` |
| Synchronous / reactive HTTP | `integration/ExternalApiService` |
| Servlet interoperability | `legacy/*` |
| Schema portability | `resources/db/migration/V1__schema.sql` |
| Local / container execution | Maven Wrapper, Dockerfile, compose.yaml |

See [the complete source tree](docs/PROJECT_STRUCTURE.md). The project uses the standard Maven layout. Browse `src/main/java/com/eventhq` for the complete module-organized implementation and `src/test/java/com/eventhq` for acceptance scenarios.

## Startup warnings on Java 25

A log ending in `Started EventHqApplication` means startup succeeded. The `System::load` and `sun.misc.Unsafe` messages come from Maven and Netty libraries on newer JDKs. On Windows, run `run.cmd` to select an installed Java 21 or 17 JDK locally; this does not change your system Java installation. Use `run.cmd -B verify` for builds. Set `EVENTHQ_JAVA_HOME` if your preferred JDK is installed in a custom location. The wrapper `mvnw.cmd` still respects your ordinary `JAVA_HOME`.

Entity Instant fields explicitly map to SQL TIMESTAMP, keeping the existing UTC schema without the incubating Hibernate preference. The dev profile uses readable console logs; the MySQL profile retains structured JSON logging.

## Web interface

On Windows, run `run.cmd`, then open **http://localhost:8080/**. The launcher enables the opt-in dev sample catalog for a ready-to-browse local experience. On other platforms, use `./mvnw spring-boot:run -Dspring-boot.run.arguments=--eventhq.seed=true`. No Node.js install or separate frontend server is needed. Stop any earlier EventHQ instance with Ctrl+C before starting the new version.

The responsive interface includes event search, venue filtering, sorting, registration/login, live STANDARD/VIP seat selection, expiring reservations, simulated payment confirmation, ticket retrieval, and role-based catalog management. Your sign-in token and active reservation are saved in sessionStorage for this browser tab. Administrative navigation appears after signing in with an explicitly bootstrapped administrator account; no administrator password is embedded in the frontend.

To provision a local administrator, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` before running the application as described above. The admin interface can create and edit venues, add seats before event creation, manage sponsors, and create/edit/publish/delete draft events. Published events and referenced records retain the backend's editing/deletion restrictions.

### Optional browser verification

The app needs no Node.js runtime, but the optional `scripts/browser-smoke.cjs` acceptance suite uses Playwright. Install it with `npm install --prefix target/browser-qa --no-save playwright`; install Chromium using that Playwright installation or set `BROWSER_EXECUTABLE` to an installed Chromium executable. Start an isolated seeded application, set `EVENTHQ_TEST_URL`, `EVENTHQ_TEST_ADMIN_EMAIL`, and `EVENTHQ_TEST_ADMIN_PASSWORD` for that test instance, then run `node scripts/browser-smoke.cjs`. The suite creates test customers/catalog records and should be used only against disposable test data. Screenshots are written under `target/`.

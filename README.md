# Task Management API

A RESTful backend for personal task management built with **Spring Boot 3**, **Spring Security (JWT)** and **Spring Data JPA**. Users register, log in, and manage their own tasks; nobody can see or modify anybody else's.

- **Auth:** stateless JWT (HS256), passwords hashed with **BCrypt**
- **Persistence:** JPA/Hibernate - H2 file DB out of the box, PostgreSQL via environment variables / Docker Compose
- **Validation:** Bean Validation on every request body, explicit checks on query parameters
- **Errors:** one consistent JSON error shape for every failure (including 401s raised inside the security filter chain)
- **Docs:** this README + interactive Swagger UI at `/swagger-ui.html`
- **Tests:** end-to-end MockMvc integration tests (auth, CRUD, ownership isolation, pagination, validation)

---

## Quick start

**Requirements:** Java 17+ and Maven 3.9+ (or Docker).

```bash
mvn spring-boot:run
```

The API is now on `http://localhost:8080`. Data is stored in `./data/taskdb` (H2 file database), so it survives restarts.

| URL | What |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive API docs (use **Authorize** after logging in) |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| [`postman/TaskManagerAPI.postman_collection.json`](postman/TaskManagerAPI.postman_collection.json) | Postman collection |

### Postman

Import `postman/TaskManagerAPI.postman_collection.json`, then run **Auth -> Register**, **Auth -> Login** (saves the JWT into `{{token}}` automatically) and any request under **Tasks**. The **Error cases** folder demonstrates 400 / 401 / 404 responses.

### Run the tests

```bash
mvn test
```

### Run with Docker + PostgreSQL

```bash
export JWT_SECRET=$(openssl rand -base64 48)
docker compose up --build
```

### Configuration

All settings are environment variables with sensible local defaults (`src/main/resources/application.yml`).

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | dev-only value | HMAC signing key, **>= 32 characters**. The app refuses to start with a shorter one. **Always override in production.** |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Token lifetime |
| `DB_URL` | `jdbc:h2:file:./data/taskdb` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/taskdb` |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / *(empty)* | Database credentials |
| `DDL_AUTO` | `update` | Hibernate schema strategy; use `validate` in production with a migration tool |

---

## Project structure

```
src/main/java/com/example/taskmanager
├── TaskManagerApplication.java
├── config/            SecurityConfig (filter chain, BCrypt), WebConfig (enum query-param converters), OpenApiConfig
├── controller/        AuthController, TaskController      -> HTTP only, no business logic
├── service/           AuthService, TaskService            -> business rules, ownership checks, transactions
├── repository/        UserRepository, TaskRepository, TaskSpecifications (dynamic filters)
├── entity/            User, Task, TaskStatus, TaskPriority
├── dto/               Request/response records           -> entities are never exposed directly
├── security/          JwtService, JwtAuthenticationFilter, 401/403 JSON handlers
└── exception/         Domain exceptions + GlobalExceptionHandler (@RestControllerAdvice)
```

Layering is strictly `controller -> service -> repository`. Controllers translate HTTP to method calls; services own the rules; entities are mapped to DTOs so the password hash can never leak and the API can evolve independently of the schema.

---

## Database schema

```
┌────────────────────────┐          ┌─────────────────────────────┐
│ users                  │          │ tasks                       │
├────────────────────────┤          ├─────────────────────────────┤
│ id            PK       │◄────────┐│ id            PK            │
│ name          varchar  │  1    * ││ title         varchar(200)  │
│ email         UNIQUE   │         ││ description   varchar(2000) │
│ password_hash varchar  │         ││ status        enum          │
│ created_at    tstz     │         ││ priority      enum          │
└────────────────────────┘         └┤ user_id       FK -> users   │
                                    │ created_at    tstz          │
                                    │ updated_at    tstz          │
                                    └─────────────────────────────┘
```

- `users.email` has a **unique constraint** (the real guard against duplicate registrations, even under concurrency). Emails are stored trimmed and lower-cased.
- `tasks.user_id` is a non-null **foreign key** with `ON DELETE CASCADE` semantics in the reference DDL.
- Indexes on `(user_id, created_at)` and `(user_id, status)` serve the listing endpoint (default sort and the status filter).
- `status` and `priority` are stored as strings (not ordinals), so reordering enum constants can never corrupt data.
- Full reference DDL: [`docs/schema.sql`](docs/schema.sql).

---

## API reference

Base path: `/api`. All request and response bodies are JSON with **snake_case** field names. All endpoints except `/api/auth/*` require:

```
Authorization: Bearer <access_token>
```

### Authentication

#### `POST /api/auth/register`

```json
{ "name": "Jane Doe", "email": "jane@example.com", "password": "Secret123" }
```

**201 Created**
```json
{ "id": 1, "name": "Jane Doe", "email": "jane@example.com", "created_at": "2026-10-11T10:15:30.123456Z" }
```

| Field | Rules |
|---|---|
| `name` | required, <= 100 chars |
| `email` | required, valid email, <= 255 chars, unique (case-insensitive) |
| `password` | required, 8-72 chars, at least one letter and one digit |

Errors: `400` validation, `409` email already registered.

#### `POST /api/auth/login`

```json
{ "email": "jane@example.com", "password": "Secret123" }
```

**200 OK**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "user": { "id": 1, "name": "Jane Doe", "email": "jane@example.com", "created_at": "..." }
}
```

Errors: `400` validation, `401` invalid credentials (the same message for "unknown email" and "wrong password").

### Tasks

Task representation:

```json
{
  "id": 7,
  "title": "Write report",
  "description": "Q4 numbers",
  "status": "pending",
  "priority": "medium",
  "created_at": "2026-10-11T10:20:00.000000Z",
  "updated_at": "2026-10-11T10:20:00.000000Z"
}
```

`status`: `pending` | `in_progress` | `completed` `priority`: `low` | `medium` | `high`

#### `POST /api/tasks` -> **201 Created** (+ `Location` header)

| Field | Rules |
|---|---|
| `title` | **required**, not blank, <= 200 chars |
| `description` | optional, <= 2000 chars |
| `status` | optional, default `pending` |
| `priority` | optional, default `medium` |

#### `GET /api/tasks` -> **200 OK** (paginated)

| Query param | Default | Notes |
|---|---|---|
| `page` | `0` | zero-based, >= 0 |
| `size` | `10` | 1-100 |
| `status` | - | optional filter |
| `priority` | - | optional filter |
| `sort` | `created_at` | `created_at`, `updated_at`, `title` |
| `order` | `desc` | `asc` or `desc` |

```json
{
  "content": [ { "id": 7, "title": "Write report", "...": "..." } ],
  "page": 0,
  "size": 10,
  "total_elements": 1,
  "total_pages": 1,
  "first": true,
  "last": true
}
```

Only the logged-in user's tasks are ever returned.

#### `GET /api/tasks/{id}` -> **200 OK**
#### `PUT /api/tasks/{id}` -> **200 OK**

`title` is required. `description` is replaced by the value sent (omit/blank to clear it). `status` and `priority` are optional - when omitted they keep their current value.

#### `DELETE /api/tasks/{id}` -> **200 OK**
```json
{ "message": "Task deleted successfully" }
```

### Status codes

| Code | When |
|---|---|
| `200` | Successful read / update / delete / login |
| `201` | Resource created (register, create task) |
| `400` | Validation failure, malformed JSON, bad enum value, bad query parameter or path id |
| `401` | Missing, invalid or expired token; bad login credentials |
| `404` | Task doesn't exist **or belongs to someone else**; unknown route |
| `405` / `415` | Unsupported HTTP method / content type |
| `409` | Email already registered |
| `500` | Unexpected server error (details are logged, never returned to the client) |

### Error format

Every error has the same shape; `details` appears only for field-level validation errors.

```json
{
  "timestamp": "2026-10-11T10:25:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tasks",
  "details": { "title": "Title is required" }
}
```

```json
{
  "timestamp": "2026-10-11T10:26:00.456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid value 'done' for field 'status'. Allowed values: [pending, in_progress, completed]",
  "path": "/api/tasks"
}
```

---

## Try it with curl

```bash
# 1. Register
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Jane Doe","email":"jane@example.com","password":"Secret123"}'

# 2. Login and capture the token
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

# 3. Create a task
curl -s -X POST localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Write report","description":"Q4 numbers","priority":"high"}'

# 4. List (page 0, 5 per page, only in-progress, oldest first)
curl -s "localhost:8080/api/tasks?page=0&size=5&status=in_progress&sort=created_at&order=asc" \
  -H "Authorization: Bearer $TOKEN"

# 5. Update
curl -s -X PUT localhost:8080/api/tasks/1 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Write final report","status":"completed"}'

# 6. Delete
curl -s -X DELETE localhost:8080/api/tasks/1 -H "Authorization: Bearer $TOKEN"
```

---

## Security design

- **Password storage:** BCrypt (per-password random salt, work factor 10). Raw passwords are never stored or logged, and the hash is never serialised (responses use DTOs).
- **Authentication:** `JwtAuthenticationFilter` verifies signature and expiry on each request and puts an `AuthenticatedUser(id, email)` in the security context. Sessions are disabled (`STATELESS`), so CSRF protection is intentionally off - there are no cookies to forge.
- **Authorization / data isolation:** every task query is scoped by the authenticated user's id (`findByIdAndUserId`, `ownedBy(userId)`); the user id comes **from the token, never from the request**. Accessing someone else's task returns **404** rather than 403, so task IDs can't be probed.
- **User enumeration:** login returns the same 401 for unknown email and wrong password, and runs a dummy BCrypt comparison for unknown emails so response times don't differ.
- **Secrets:** the JWT secret comes from the environment; startup fails if it is shorter than 256 bits.
- **Injection:** all queries are parameterised through JPA / Criteria; sort fields are mapped from a whitelist, never taken from user input directly.
- **Information leakage:** unexpected exceptions return a generic 500 message; the stack trace goes to the server log only.

## Design decisions & trade-offs

- **Snake_case JSON** (`created_at`, `total_elements`) is applied globally via Jackson, matching the required field names without per-field annotations.
- **Lowercase enum values in the API** (`in_progress`) via `@JsonValue`, plus converters so the same values work in query strings. Invalid values give a 400 that lists the allowed ones.
- **`PUT` semantics:** `title` is mandatory; `description` is replaced; `status`/`priority` are optional so a client can edit text without resending them. For partial updates of just one field, a `PATCH` endpoint would be the natural extension.
- **Pagination** is capped at 100 items per page and uses a stable secondary sort on `id`, so pages never skip or duplicate rows when many tasks share a timestamp.
- **DELETE returns 200 with a message** (instead of 204) to stay within the status codes in the spec and give clients an explicit confirmation body.
- **Tokens** are access tokens only (no refresh/revocation). For production, add short-lived access tokens + refresh tokens, and rate limiting on `/api/auth/*`.
- **Schema management:** `ddl-auto=update` keeps local setup zero-config. Use Flyway/Liquibase and `DDL_AUTO=validate` for real deployments.

## Possible extensions

Refresh tokens and logout/revocation · rate limiting and account lockout · due dates, tags, full-text search · `PATCH` for partial updates · soft delete · Flyway migrations · Testcontainers tests against PostgreSQL.

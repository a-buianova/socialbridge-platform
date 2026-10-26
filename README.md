# SocialBridge Platform

SocialBridge is an open-source CRM and fundraising platform for nonprofit organizations. This repository currently contains one Spring Boot application, **`crm-service`**, in a Maven monorepo. Fundraising, identity integration, consent, communication and a frontend are planned.

**Development only: the API has no authentication, authorization or organization isolation. Use fictional data on an isolated local machine. Do not expose its ports publicly or import real donor records.** Contact creation does not grant communication consent. See [security and privacy requirements](docs/SECURITY_PRIVACY.md).

## Stack and repository

| Component | Version / responsibility |
| --- | --- |
| Java | JDK 21, including `javac` |
| Maven | 3.9+ |
| Spring Boot | 4.1.2 |
| PostgreSQL | 16 |
| Persistence | Spring Data JPA, Hibernate, Flyway; Hibernate validates the migrated schema |
| Tests | JUnit, MockMvc, Mockito and Testcontainers with real PostgreSQL |
| API documentation | springdoc 3.2.0; `/v3/api-docs` and `/swagger-ui/index.html` |

The original unverified scaffold used Spring Boot 3.3.5. This first development slice moves to the maintained Boot 4.1 line and its matching springdoc release, while retaining Java 21 and the existing contact routes. The Boot parent manages compatible library and build-plugin versions. This is an intentional early dependency migration, not a claim that the application is production-ready.

`crm-service` owns its database and Flyway migrations. Future services must integrate through explicit APIs or events, without sharing JPA entities or reading another service's database. The [architecture](docs/ARCHITECTURE.md) and [ADRs](docs/adr/) explain these boundaries.

## Prerequisites

Install JDK 21, Maven 3.9+ and Docker Desktop with Compose v2. Start Docker Desktop before running either the integration tests or Compose. Check your terminal tools:

```bash
java -version
javac -version
mvn -version
docker info
docker compose version
```

Maven must report Java 21. A Java runtime without `javac` cannot compile the project. Initial builds need access to Maven Central and Docker Hub. No external API keys are needed for this local CRM slice.

Run all commands below from the repository root, the directory containing the parent `pom.xml`.

## Verify the project

```bash
mvn -B verify
```

The tests start disposable PostgreSQL 16 containers, apply the real Flyway migrations and validate the JPA mappings. Docker is required; tests do not silently fall back to H2 or skip when Docker is missing. Testcontainers uses dynamically allocated ports and does not require the Compose database to be running. Test reports are written under `crm-service/target/surefire-reports/`.

This command compiles, tests and packages the executable application. The Docker image build packages the application with tests skipped; run `mvn -B verify` separately before relying on an image.

## Start everything with Docker Compose

```bash
docker compose up --build -d
docker compose ps
curl --fail http://127.0.0.1:8081/actuator/health
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

The application may still be starting immediately after `up` returns. Retry the health request until it reports `UP`. If startup fails, inspect the local startup logs:

```bash
docker compose logs --tail=100 crm-db crm-service
```

The database is published at `127.0.0.1:5433`; the API is at `127.0.0.1:8081`. Compose binds both published ports to host loopback. The application listens on all container interfaces only so that Docker can forward the local host port.

```bash
# Stop services while retaining the local database volume.
docker compose down
```

The named database volume retains demo contacts between starts. `docker compose down --volumes` deletes this local database; use it only when you intend to discard those records. Changing a PostgreSQL initialization password after the volume exists does not change the database role's existing password.

## IntelliJ IDEA on macOS

1. Install a JDK 21 distribution and select it under **File → Project Structure → Project SDK**. If several JDKs are installed, `/usr/libexec/java_home -v 21` locates the macOS JDK 21 installation.
2. Choose **Open** and select this repository's root directory. Import/reload the root Maven project; `crm-service` must appear as a module.
3. Under **Settings → Build, Execution, Deployment → Build Tools → Maven**, select Maven 3.9+ or a compatible bundled Maven, and JDK 21 for the importer and runner.
4. Start Docker Desktop. Run `mvn -B verify` from the IntelliJ terminal to verify the same workflow used by CI.
5. Start only the local database:

   ```bash
   docker compose up -d crm-db
   ```

6. Run `org.socialbridge.crm.CrmApplication` in IntelliJ with JDK 21. Local database defaults already match Compose. The native application binds to `127.0.0.1:8081`.
7. Check `/actuator/health/readiness`, then exercise the API below. Stop the IntelliJ application before starting the full Compose stack; both would otherwise compete for port 8081.

The equivalent terminal workflow is:

```bash
docker compose up -d crm-db
mvn -pl crm-service spring-boot:run
```

Alternatively, after `mvn -B verify`, run:

```bash
java -jar crm-service/target/crm-service-0.1.0-SNAPSHOT.jar
```

## Local configuration

| Variable | Local default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/socialbridge_crm` | Native application database URL |
| `DB_USER` | `socialbridge` | Local database role |
| `DB_PASSWORD` | `local_only_change_me` | Explicitly local dummy password |
| `SERVER_PORT` | `8081` | Native HTTP port |
| `SERVER_ADDRESS` | `127.0.0.1` | Native bind address; Compose overrides it inside the container |

Compose supplies its own internal database URL. Its `.env` support is for Compose interpolation; IntelliJ does not automatically load `.env` into a Java run configuration. When overriding the local password, set the same value in Compose and the IntelliJ run configuration. Keep `.env` ignored and never commit real credentials. These defaults are not a deployment configuration.

## Try the CRM API

The examples use synthetic contacts and a single relationship `type`: `DONOR`, `VOLUNTEER`, `PARTNER` or `SUPPORTER`. The target person/organization model and multiple categories are not implemented yet.

```bash
# Create: 201, with a Location header and the created contact's UUID.
curl -i -X POST http://127.0.0.1:8081/api/v1/contacts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Example","email":"ADA@example.org","type":"DONOR"}'
```

Copy the returned `id` into a shell variable; the value must be the UUID returned by your own request:

```bash
CONTACT_ID=replace-with-returned-contact-uuid

# Read and list: 200.
curl "http://127.0.0.1:8081/api/v1/contacts/$CONTACT_ID"
curl 'http://127.0.0.1:8081/api/v1/contacts?type=DONOR&page=0&size=20'

# Replace all editable fields: 200.
curl -i -X PUT "http://127.0.0.1:8081/api/v1/contacts/$CONTACT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Example Updated","email":"ada@example.org","type":"SUPPORTER"}'

# Archive: 204. Repeating DELETE for the same existing contact is also 204.
curl -i -X DELETE "http://127.0.0.1:8081/api/v1/contacts/$CONTACT_ID"

# Archived contacts are hidden from GET, PUT and lists: 404 for this GET.
curl -i "http://127.0.0.1:8081/api/v1/contacts/$CONTACT_ID"
```

Email is optional for offline contacts: omit `email`, use `null` or send a blank string. Supplied emails are trimmed and lowercased before validation. A non-null email is reserved across both active and archived contacts; archiving does not free it for reuse. A repeated create with `ada@example.org` therefore returns `409`, even after the example contact has been archived. See [ADR-0002](docs/adr/0002-contact-consistency.md) for this policy and its planned tenant migration.

```bash
# Valid offline contact: 201, email is null in the response.
curl -i -X POST http://127.0.0.1:8081/api/v1/contacts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Offline Example","type":"DONOR"}'

# Invalid body and pagination: 400.
curl -i -X POST http://127.0.0.1:8081/api/v1/contacts \
  -H 'Content-Type: application/json' \
  -d '{"name":" ","email":"invalid","type":"DONOR"}'
curl -i 'http://127.0.0.1:8081/api/v1/contacts?size=101'
curl -i http://127.0.0.1:8081/api/v1/contacts/not-a-uuid
```

Lists return an explicit page shape, sorted by `createdAt DESC, id DESC`. Page size is limited to 1–100 and `page * size` must fit JPA's integer offset limit; excessive offsets return `400`:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Errors use `application/problem+json` with `title`, `status`, `detail`, `instance` and `traceId`. The optional `type` defaults to `about:blank` under RFC 9457 and is omitted by the current serializer. Validation errors additionally contain `errors`, an array of `{field,message}` objects without rejected input values. A valid UUID `X-Request-ID` is retained; absent or invalid values are replaced with a generated UUID. The response header and error `traceId` carry that same identifier. See the [current API contract](docs/API_CONTRACTS.md#current-crm-contract).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Maven reports a compiler/JDK error | `mvn -version` and `javac -version` must use JDK 21. Check IntelliJ's Maven runner JDK separately. |
| Testcontainers cannot find Docker | Start Docker Desktop and verify `docker info` succeeds from the same terminal. |
| Dependency or image download fails | Check access to Maven Central and Docker Hub, including the proxy's trusted CA. Keep TLS and artifact verification enabled. |
| Port 8081 is already in use | Stop either the IntelliJ/native application or the Compose application. |
| Database connection or password fails | Check `docker compose ps`, database logs, DB variables and whether a pre-existing volume uses another password. |
| Flyway checksum/schema validation fails | Preserve the database and investigate its migration history. Do not edit an applied migration or use Hibernate `ddl-auto=update` to bypass it. |

Managed cloud environments may need Maven proxy settings and a trusted proxy CA during image builds. The Dockerfile supports optional BuildKit secrets named `maven_settings` and `build_ca`; mount those from local files through a Compose override. They are used only in the build stage and are not copied into the application image. Ordinary Docker Desktop builds do not require them. Never disable TLS verification or commit credential-bearing settings.

## Next milestone

Implement **authenticated, tenant-isolated CRM** before real data or fundraising: OIDC token validation, verified organization membership, scoped repositories and uniqueness, role checks, cross-tenant tests, and an audit/consent foundation. The [identity ADR](docs/adr/0003-tenant-identity-contract.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md) define the sequence and acceptance criteria.

The current optimistic version protects overlapping database transactions; it is not an HTTP `If-Match` contract for stale clients. Restore/merge, full audit history, consent, retention/export/deletion workflows, backups and a deployment security review remain outstanding. No milestone label here claims GDPR compliance or production readiness.

Collaborators should read [`CLAUDE.md`](CLAUDE.md), the [product requirements](docs/PRODUCT_REQUIREMENTS.md), architecture, security requirements and delivery plan before changing domain boundaries.

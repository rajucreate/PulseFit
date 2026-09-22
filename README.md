# PulseFit Backend

PulseFit is a Spring Boot microservice platform for multi-facility fitness membership and attendance tracking. It is organized as six independent Maven projects and is compatible with Spring Tool Suite.

## Architecture

```text
                               +----------------+
                               |  API Gateway   |
                               | localhost:8080 |
                               +-------+--------+
                                       |
                               Eureka discovery
                                       |
        +------------------+-----------+-----------+------------------+
        |                  |                       |                  |
        v                  v                       v                  v
   Auth Service      Member Service      Subscription Service   Attendance Service
  localhost:8084     localhost:8081         localhost:8082        localhost:8083
        |                  |                       |                  |
     auth DB          members DB            subscriptions DB    attendance DB
        ^                  ^                       ^                  |
        |                  |  OpenFeign            |  OpenFeign       |
        +------------------+-----------------------+------------------+
                           (via Eureka)            (via Eureka)

                               Eureka Server :8761
```

## Services and ports

| Service | Port | Responsibility |
|---|---:|---|
| `eureka-server` | 8761 | Service registry and dashboard |
| `api-gateway` | 8080 | Discovery-backed external routing, token validation, identity propagation |
| `auth-service` | 8084 | User authentication, JWT issuance, profile claim, admin safeguards, audit logging |
| `member-service` | 8081 | Member profile CRUD (enforces unique email and profile ownership) |
| `subscription-service` | 8082 | Membership plan & subscription CRUD, member validation, validity checks |
| `attendance-service` | 8083 | Check-ins (access audit log) and attendance history |

## Technology

Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 17, Maven, Spring Data JPA, PostgreSQL, Eureka, Spring Cloud Gateway, Spring Security with Asymmetric RSA JWT tokens, and OpenFeign.

Each business service owns its own database. Default PostgreSQL databases are `pulsefit_auth`, `pulsefit_members`, `pulsefit_subscriptions`, and `pulsefit_attendance`. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`, and `EUREKA_URL` as environment variables. Credentials and private keys are not hardcoded in Java source files.

## Gateway routes

- `/api/auth/**` -> `AUTH-SERVICE`
- `/api/members/**` -> `MEMBER-SERVICE`
- `/api/plans/**` and `/api/subscriptions/**` -> `SUBSCRIPTION-SERVICE`
- `/api/attendance/**` -> `ATTENDANCE-SERVICE`

## Inter-Service Communication & Check-In Behavior

- **Auth Service** validates and provisions member profiles with `MEMBER-SERVICE` via OpenFeign during registration and claim completion flows.
- **Subscription Service** validates member existence with `MEMBER-SERVICE` via OpenFeign prior to creating/updating subscriptions and validates date ordering (`startDate <= expiryDate`).
- **Attendance Service** queries `/api/subscriptions/member/{memberId}/valid` via OpenFeign. The response returns `{"memberId": 1, "valid": true, "subscriptionId": 10}`.
- **Attendance Check-In (`POST /api/attendance/check-in`):** Creates and persists an attendance attempt/access record returning **HTTP 201 Created** for all attempts. 
  - If valid: `accessStatus` is `GRANTED`, `subscriptionId` is populated, and `denialReason` is `null`.
  - If invalid: `accessStatus` is `DENIED`, `subscriptionId` is `null`, and `denialReason` contains the failure explanation.

## Run

Open six terminals from the repository root and run:

```text
cd eureka-server && mvn spring-boot:run
cd auth-service && mvn spring-boot:run
cd member-service && mvn spring-boot:run
cd subscription-service && mvn spring-boot:run
cd attendance-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

Use `http://localhost:8761` to inspect registered clients.

For a standard workflow:
1. Register or login via `POST http://localhost:8080/api/auth/login` or `POST http://localhost:8080/api/auth/register`.
2. Create a member at `POST http://localhost:8080/api/members`.
3. Create a plan at `POST http://localhost:8080/api/plans`.
4. Create a subscription at `POST http://localhost:8080/api/subscriptions`.
5. Check in at `POST http://localhost:8080/api/attendance/check-in`.

Example payloads:

```json
{"email":"user@example.com","password":"Password123!","firstName":"Jane","lastName":"Doe","contact":"555-0100"}
```

```json
{"name":"Ava Patel","email":"ava@example.com","contact":"555-0100","status":"ACTIVE"}
```

```json
{"planName":"Monthly","durationInDays":30,"price":29.99,"description":"Standard access","active":true}
```

```json
{"memberId":1,"planId":1,"startDate":"2026-09-20","expiryDate":"2026-10-20","status":"ACTIVE"}
```

```json
{"memberId":1,"facilityId":10}
```

Run all tests with `mvn test` in each service directory. Test scope uses Mockito and does not require a running PostgreSQL instance.

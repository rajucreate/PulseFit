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
| `facility-service` | 8085 | Gym branches. Delete deactivates a facility and keeps the row |
| `payment-service` | 8086 | Simulated membership payments and refunds |
| `notification-service` | 8087 | Notification history. Email is sent only when SMTP is configured |
| `trainer-service` | 8088 | Trainer profiles and member assignments |
| `reporting-service` | 8089 | Read-only aggregates through other services' APIs |

## Technology

Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 17, Maven, Spring Data JPA, PostgreSQL, Eureka, Spring Cloud Gateway, Spring Security with Asymmetric RSA JWT tokens, and OpenFeign.

Each business service owns its own database. Default PostgreSQL databases are `pulsefit_auth`, `pulsefit_members`, `pulsefit_subscriptions`, `pulsefit_attendance`, `pulsefit_facilities`, `pulsefit_payments`, `pulsefit_notifications`, and `pulsefit_trainers`. Reporting service has no database. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`, and `EUREKA_URL` as environment variables. Local `application.properties` files include the same development database password already used by the original services. Do not commit a production password. The JWT private signing key stays in `auth-service/config/secrets/` and is not committed.

## Gateway routes

- `/api/auth/**` -> `AUTH-SERVICE`
- `/api/members/**` -> `MEMBER-SERVICE`
- `/api/plans/**` and `/api/subscriptions/**` -> `SUBSCRIPTION-SERVICE`
- `/api/attendance/**` -> `ATTENDANCE-SERVICE`
- `/api/facilities/**` -> `FACILITY-SERVICE`
- `/api/payments/**` -> `PAYMENT-SERVICE`
- `/api/notifications/**` -> `NOTIFICATION-SERVICE`
- `/api/trainers/**` -> `TRAINER-SERVICE`
- `/api/reports/**` -> `REPORTING-SERVICE`

## Inter-Service Communication & Check-In Behavior

- **Auth Service** validates and provisions member profiles with `MEMBER-SERVICE` via OpenFeign during registration and claim completion flows.
- **Subscription Service** validates member existence with `MEMBER-SERVICE` via OpenFeign prior to creating/updating subscriptions and validates date ordering (`startDate <= expiryDate`).
- **Attendance Service** queries `/api/subscriptions/member/{memberId}/valid` via OpenFeign. The response returns `{"memberId": 1, "valid": true, "subscriptionId": 10}`.
- **Attendance Check-In (`POST /api/attendance/check-in`):** Creates and persists an attendance attempt/access record returning **HTTP 201 Created** for all attempts. 
  - If the facility id is unknown or inactive, `accessStatus` is `DENIED`. If the facility service is down, check-in still uses the subscription decision so existing clubs keep working.
  - If valid: `accessStatus` is `GRANTED`, `subscriptionId` is populated, and `denialReason` is `null`.
  - If invalid: `accessStatus` is `DENIED`, `subscriptionId` is `null`, and `denialReason` contains the failure explanation.
- **Payment Service** reads the plan price from Subscription Service. The client cannot set the amount. `SIMULATED_CARD` and `SIMULATED_UPI` succeed. `SIMULATED_FAIL` fails. Repeating the same idempotency key returns the original row. A successful payment calls `POST /api/subscriptions/internal/{id}/activate`. No card data is stored. Responses set `simulated: true`.
- **Notification Service** defaults to `NOTIFICATION_MODE=log`. Those rows stay `PENDING` and say the email was not sent. Set `NOTIFICATION_MODE=smtp` plus `SMTP_HOST` to send mail. Only a completed SMTP send is marked `SENT`.
- **Trainer Service** checks Facility Service and Member Service before creating a trainer or assignment. Delete deactivates the trainer.
- **Reporting Service** calls the other services with the caller's token. Each section is `{ "available": true, "data": ... }` or `{ "available": false, "error": "..." }`. Revenue sums `SUCCESS` payments once per transaction id. Failed and refunded payments are counted separately and are not added to revenue.

## Frontend

```text
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to `http://localhost:8080`.

Spring Tool Suite 4: import the repository with **File → Import → Maven → Existing Maven Projects**, use Java 17, and run each `*Application` class with **Run As → Spring Boot App**. Start Eureka first. The auth service working directory should be the `auth-service` project so it finds `config/secrets/jwt-private.pem`.

Windows scripts: `start-all.ps1` and `stop-all.ps1`.

Create the new databases before the first start:

```sql
CREATE DATABASE pulsefit_facilities;
CREATE DATABASE pulsefit_payments;
CREATE DATABASE pulsefit_notifications;
CREATE DATABASE pulsefit_trainers;
```

## New API summary

| Method | Path | Who |
|---|---|---|
| GET | `/api/facilities` | Authenticated. Filters: `name`, `location`, `status` |
| POST/PUT | `/api/facilities` | STAFF or ADMIN |
| DELETE | `/api/facilities/{id}` | STAFF or ADMIN. Deactivates; does not delete the row |
| POST | `/api/payments/checkout` | Signed-in account with a linked member id |
| POST | `/api/payments/verify` | Same member. Activates the membership only after Razorpay verification |
| POST | `/api/payments/checkout/cancel` | Same member. Leaves the membership inactive |
| POST | `/api/payments` | STAFF or ADMIN simulated ledger only |
| GET | `/api/payments/member/{memberId}` | Owner, STAFF, or ADMIN |
| POST | `/api/payments/{id}/retry` | STAFF or ADMIN, and only for a failed simulated payment |
| POST | `/api/payments/{id}/refund` | STAFF or ADMIN |
| GET | `/api/notifications` | STAFF or ADMIN. Filters: `type`, `status`, `memberId` |
| GET | `/api/notifications/member/{memberId}` | Owner, STAFF, or ADMIN |
| GET/POST/PUT | `/api/trainers` | GET is authenticated. Writes are STAFF or ADMIN |
| POST/DELETE | `/api/trainers/{trainerId}/members/{memberId}` | STAFF or ADMIN |
| GET | `/api/reports/dashboard` | STAFF or ADMIN. Query: `from`, `to`, `facilityId` |

## Known limits

Member purchases no longer become active from the Add Subscription form. A member chooses a plan, and Payment Service opens a Razorpay **test** order. The subscription stays `PENDING` until `POST /api/payments/verify` checks the Razorpay signature and the captured amount. Failed or closed checkouts stay inactive and do not block a later purchase. An unexpired `ACTIVE` membership blocks another plan until the expiry date.

Set these environment variables before checkout. Do not commit them.

```text
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
```

The catalog amount is charged in INR paise. Razorpay Checkout shows UPI first. In test mode use UPI id `success@razorpay` for a captured payment and `failure@razorpay` for a failure. Neither moves real money. Checkout returns HTTP 503 until the two environment variables are set, and it does not create an active membership. The old simulated ledger at `POST /api/payments` is staff-only and stays labeled simulated.

On startup, Subscription Service widens the PostgreSQL `subscriptions_status_check` constraint so `PENDING` can be stored. Hibernate does not update that check when the status enum grows.

Kafka is not used; services call each other with OpenFeign and can later publish the same commands as events.

## Run

Open six terminals from the repository root and run:

```text
cd eureka-server && mvn spring-boot:run
cd auth-service && mvn spring-boot:run
cd member-service && mvn spring-boot:run
cd subscription-service && mvn spring-boot:run
cd attendance-service && mvn spring-boot:run
cd facility-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd trainer-service && mvn spring-boot:run
cd reporting-service && mvn spring-boot:run
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

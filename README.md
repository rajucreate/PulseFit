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
| POST | `/api/auth/invitations` | ADMIN. Creates a 24-hour member invitation |
| POST | `/api/auth/invitations/{id}/resend` | ADMIN |
| POST | `/api/auth/invitations/accept` | Public. Sets the invited member's password |
| POST | `/api/auth/password/forgot` | Public. Always returns a generic message |
| POST | `/api/auth/password/reset` | Public. Consumes a 30-minute reset link |
| POST | `/api/payments/checkout` | Signed-in member. Opens a pending demo checkout |
| POST | `/api/payments/checkout/demo` | Same member. Records the simulated card payment and activates the membership |
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

Public registration is closed. An administrator opens **Invitations**, enters a name and email, and Auth Service stores only a hash of a 24-hour single-use token. The member opens `/invite/{token}`, chooses a password, and then signs in. Forgot password is `/forgot`; the reset link is `/reset/{token}` and expires after 30 minutes. Resetting a password increments the token version so older JWTs stop working.

Invitation and password-reset mail is sent by the Node mailer on port 8090 (`mailer-service`, Nodemailer). Put the SMTP values in `mailer-service/.env` (copy `.env.example`). That file is not committed. Start the mailer with `npm start` in `mailer-service`, then restart it after changing `.env`. Auth Service calls `http://127.0.0.1:8090` and does not send mail itself. When an administrator uses **Add member**, the profile is saved and the same invitation email goes to that address. If `SMTP_HOST` is empty, the invitation is saved and the screen says the email was not sent. In that development case the response also includes the invite link. Do not treat that link as a sent email.

The login page shows two local accounts: admin `admin@pulsefit.com` / `Admin@PulseFit2026`, and trainee `trainee@pulsefit.com` / `Trainee@PulseFit2026`. The trainee role is read-only and sees only members assigned in the trainer service. Payments, checkout, and subscription changes stay with members, staff, and administrators. The catalog seeds Weekly, Quarterly, Half Year, and Annual plans alongside any plan already stored, including Monthly.

```text
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=noreply@pulsefit.local
PUBLIC_APP_URL=http://localhost:5173
```

Member checkout is a **demo card payment**. The card number, expiry, and CVV on the page are fixed sample values and are not submitted. `POST /api/payments/checkout/demo` sends only the payment id. The server prices the plan, marks the payment simulated, and activates the pending membership. Cancelling checkout does not activate it. An unexpired `ACTIVE` membership blocks another plan. No Razorpay, UPI, or real card charge is used. The staff ledger at `POST /api/payments` remains labeled simulated.

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

Frontend routes: `/login`, `/forgot`, `/reset/:token`, `/invite/:token`, `/invitations` (admin), `/plans`, `/checkout/:planId`, and `/checkout/result`. `/register` redirects to login. Public `POST /api/auth/register` returns 403.

To try the flows locally:

1. Sign in as `admin@pulsefit.com`. Open **Invitations**, enter a name and email, and submit.
2. If SMTP is not configured, copy the invite link from the success message. Open it, set a password of at least 8 characters, then sign in on `/login`.
3. On the login page choose **Forgot password?**, enter the member email, and confirm the generic response. With SMTP configured, open the emailed `/reset/{token}` link and set a new password. Without SMTP the reset link is not returned, so the service tests cover token acceptance.
4. As that member, open **Plans**, continue to checkout, and choose **Pay Now (Demo)**. The confirmation shows a `demo-` transaction reference and states that no money moved. Cancel instead, and the membership stays inactive. An unexpired active membership blocks another purchase.

Desk staff can still create a member profile and issue a claim link. Members do not create their own accounts.

Example payloads:

```json
{"fullName":"Jane Doe","email":"jane@example.com"}
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

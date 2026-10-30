# PULSEFIT — Complete Postman API Testing Guide

**College evaluation companion (40 marks · four rubrics)**  
**Source of truth:** existing PulseFit backend code (no project code was modified to produce this document)  
**Base URL (API Gateway):** `http://localhost:8080`  
**Eureka dashboard / registry:** `http://localhost:8761`  
**Branch / stack:** Spring Boot 3.3.5 microservices + Spring Cloud Gateway + Eureka + RS256 JWT

> **Response accuracy legend**  
> - **Verified pattern** — matches DTO/controller + prior live verification of the same contract  
> - **Inferred from source** — field names/status codes taken from Java DTOs/controllers; live values (IDs, timestamps) will differ on your machine  

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Prerequisites and backend startup](#2-prerequisites-and-backend-startup)
3. [Postman environment setup](#3-postman-environment-setup)
4. [Complete API endpoint summary table](#4-complete-api-endpoint-summary-table)
5. [Missing APIs vs rubric expectations](#5-missing-apis-vs-rubric-expectations)
6. [Detailed Postman instructions (every gateway-facing endpoint)](#6-detailed-postman-instructions)
7. [JWT authentication testing (deep dive)](#7-jwt-authentication-testing-deep-dive)
8. [API Gateway and service discovery testing](#8-api-gateway-and-service-discovery-testing)
9. [CRUD testing by module](#9-crud-testing-by-module)
10. [Complete college review sequence](#10-complete-college-review-sequence)
11. [Rubric-wise demonstration checklist](#11-rubric-wise-demonstration-checklist)

---

## 1. Project overview

PulseFit is a **SOA-style fitness club backend**. Clients (Postman / React) talk **only** to the **API Gateway** on port **8080**. Behind the gateway:

| Service | Port | Responsibility |
|---|---:|---|
| `eureka-server` | 8761 | Service discovery registry + dashboard |
| `api-gateway` | 8080 | Single entry, JWT validation, routing via Eureka |
| `auth-service` | 8084 | Login, JWT issue/refresh, invitations, claim, admin users |
| `member-service` | 8081 | Member profile CRUD + member→subscription alias |
| `subscription-service` | 8082 | Plans + subscriptions |
| `attendance-service` | 8083 | Check-in (GRANTED / DENIED) |
| `facility-service` | 8085 | Gym facilities |
| `payment-service` | 8086 | Checkout / payments |
| `notification-service` | 8087 | Notification records |
| `trainer-service` | 8088 | Trainers + member assignments + trainee roster |
| `reporting-service` | 8089 | Aggregated reports |

**Roles:** `ADMIN`, `STAFF`, `MEMBER`, `TRAINEE`.

**Demo accounts (local bootstrap):**

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@pulsefit.com` | `Admin@PulseFit2026` |
| TRAINEE | `trainee@pulsefit.com` | `Trainee@PulseFit2026` |

Public self-registration is **closed** (`POST /api/auth/register` returns **403**). New members are created by staff, then claim an account; staff/admin can also be invited.

**Internal APIs** (`/api/.../internal/...`) are used by Feign between services. The gateway **blocks** any path containing `/internal` (**403**). Do **not** use them in the college Postman demo via `:8080`.

---

## 2. Prerequisites and backend startup

### 2.1 Prerequisites

1. **Java 17+** installed (`java -version`)
2. **Maven** available for each service build (or use project scripts)
3. **PostgreSQL** running with databases expected by each service’s `application.properties`
4. **Postman** Desktop installed
5. Optional: import existing collection `postman/PulseFit-Evaluation.postman_collection.json`

### 2.2 Start the backend

From the project root `C:\Users\Abhir\Desktop\PulseFit`:

```powershell
.\start-all.ps1
```

Or double-click `start-all.bat`.

Wait until Eureka shows instances and gateway health is UP (about 1–2 minutes on first start).

### 2.3 Stop the backend

```powershell
.\stop-all.ps1
```

### 2.4 Quick health check before Postman

1. Browser: `http://localhost:8761` → Eureka dashboard should list services.
2. Browser or Postman: `GET http://localhost:8080/actuator/health` → `{ "status": "UP" }`.

If gateway returns connection refused, services are not up yet.

---

## 3. Postman environment setup

### 3.1 Create environment `PulseFit-Local`

| Variable | Initial value |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `eurekaUrl` | `http://localhost:8761` |
| `adminEmail` | `admin@pulsefit.com` |
| `adminPassword` | `Admin@PulseFit2026` |
| `traineeEmail` | `trainee@pulsefit.com` |
| `traineePassword` | `Trainee@PulseFit2026` |
| `accessToken` | *(empty — filled by Login test script)* |
| `memberId` | `1` |
| `planId` | `1` |
| `subscriptionId` | `1` |
| `trainerId` | `1` |
| `facilityId` | `1` |
| `paymentId` | `1` |
| `invitationId` | `1` |

Select this environment in the top-right of Postman before testing.

### 3.2 How to save JWT automatically

On the **Login** request, **Scripts → Post-response**:

```javascript
pm.test("Login returned token", () => {
  pm.response.to.have.status(200);
  const body = pm.response.json();
  pm.expect(body.accessToken).to.be.a("string");
  pm.environment.set("accessToken", body.accessToken);
});
```

Then for protected requests: **Authorization → Type: Bearer Token → Token: `{{accessToken}}`**.

---

## 4. Complete API endpoint summary table

All URLs below are via the **gateway** unless marked *direct*.

### Rubric 1 — Microservices, discovery, configuration

| Method | Endpoint | Auth | Service |
|---|---|---|---|
| GET | `/actuator/health` | Public | Gateway |
| GET | `/actuator/info` | Public | Gateway |
| GET | `http://localhost:8084/actuator/health` | Public | Auth *(direct)* |
| GET | `http://localhost:8081/actuator/health` | Public | Member *(direct)* |
| GET | `http://localhost:8082/actuator/health` | Public | Subscription *(direct)* |
| GET | `http://localhost:8083/actuator/health` | Public | Attendance *(direct)* |
| GET | `http://localhost:8761/eureka/apps` | Public | Eureka |
| GET | `/api/services/status` | Bearer | Gateway (discovery) |
| GET | `/api/services/registry` | Bearer | Gateway (discovery) |

### Rubric 2 — JWT authentication and security

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Issues JWT |
| POST | `/api/auth/register` | Public | Always **403** (closed) |
| GET | `/api/auth/me` | Bearer | Current user |
| POST | `/api/auth/refresh` | Bearer | New JWT |
| POST | `/api/auth/logout` | Bearer | **204** |
| POST | `/api/auth/password/forgot` | Public | |
| POST | `/api/auth/password/reset` | Public | |
| POST | `/api/auth/claim/initiate` | Public | |
| POST | `/api/auth/claim/complete` | Public | **201** |
| POST | `/api/auth/invitations` | ADMIN | |
| GET | `/api/auth/invitations` | ADMIN | |
| POST | `/api/auth/invitations/{id}/resend` | ADMIN | |
| GET | `/api/auth/invitations/preview?token=` | Public | |
| POST | `/api/auth/invitations/accept` | Public | |
| POST | `/api/auth/users/staff` | ADMIN | |
| POST | `/api/auth/users/admin` | ADMIN | |
| PUT | `/api/auth/users/{id}/role` | ADMIN | |
| PUT | `/api/auth/users/{id}/status` | ADMIN | |
| GET | `/api/auth/audit-logs` | ADMIN | |

### Rubric 3 — API Gateway and service integration

Routed paths: `/api/auth/**`, `/api/members/**`, `/api/plans/**`, `/api/subscriptions/**`, `/api/attendance/**`, `/api/facilities/**`, `/api/payments/**`, `/api/notifications/**`, `/api/trainers/**`, `/api/reports/**`, `/api/services/**`.  
Cross-service demos: `POST/GET /api/members/{id}/subscriptions` (Member → Subscription via Feign), attendance check-in (Attendance → subscription validity).

### Rubric 4 — Testing, validation, demonstration (CRUD + status codes)

| Module | Create | Read | Update | Delete |
|---|---|---|---|---|
| Members | POST `/api/members` **201** | GET list/id | PUT | DELETE **204** ADMIN |
| Plans | POST `/api/plans` | GET (public) | PUT | DELETE |
| Subscriptions | POST `/api/subscriptions` | GET | PUT | DELETE |
| Member subscriptions | POST `.../subscriptions` | GET | — | — |
| Attendance | POST `/api/attendance/check-in` | GET | — | — |
| Facilities | POST | GET | PUT | DELETE (soft) |
| Trainers | POST | GET | PUT | DELETE |
| Trainer↔Member | POST assign | GET | — | DELETE unassign |
| Payments | POST / checkout | GET | retry/refund | — |
| Notifications | POST | GET | retry | — |
| Reports | — | GET only | — | — |

---

## 5. Missing APIs vs rubric expectations

| Typical rubric expectation | PulseFit reality |
|---|---|
| Open public registration | **`POST /api/auth/register` always returns 403** — show this as intentional security design; use invitation or claim instead |
| Admin list-all-users | **No `GET /api/auth/users`** — only create staff/admin + role/status |
| Attendance check-out | **Not implemented** — only check-in |
| Class/session booking | **Not implemented** |
| Equipment inventory | **Not implemented** |
| Social / OAuth login | **Not implemented** |
| Internal APIs via gateway | **Blocked by design** (correct) |

Use implemented endpoints below for the viva; explain closed registration clearly if asked.

---

## 6. Detailed Postman instructions

> **Convention:** Replace `{{baseUrl}}` with `http://localhost:8080` if you are not using an environment.  
> Path parameters like `{id}` become concrete numbers (e.g. `3`) after you create records.

### 6.A Rubric group map

| API range | Focus |
|---|---|
| Early health/Eureka/status | Rubric 1 |
| Auth/JWT/RBAC | Rubric 2 |
| Feign subscriptions, gateway errors, routing | Rubric 3 |
| CRUD modules + reports | Rubric 4 |


### API 1 — Gateway Health

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove the API Gateway process is alive and its health indicator is UP.

**Microservice:** api-gateway (local actuator)

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
- Example *(verified pattern)*:
```json
{
  "status": "UP"
}
```
- Important field: `status` must be `UP` before other demos.

**Step 7 — Negative Test:**
- Stop the gateway (`stop-all` or kill the gateway process) and retry.
- Expected: connection error in Postman (not an HTTP body), or if something else is bound incorrectly, non-200.
- This proves health depends on a running gateway JVM.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("status UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Show faculty the browser/Postman **200 UP** on the gateway port **8080**. Rubric: service is running and observable.

### API 2 — Gateway Info

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Show build/app metadata exposed by Spring Boot Actuator info.

**Microservice:** api-gateway

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/actuator/info`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
- Example *(inferred from `application.yml` `info.app`)*:
```json
{
  "app": {
    "name": "PulseFit API Gateway",
    "description": "Routes authenticated requests to PulseFit microservices through Eureka",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

**Step 7 — Negative Test:**
- Call a non-exposed actuator endpoint such as `GET http://localhost:8080/actuator/env` (not in `include`).
- Expected: **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show that configuration/metadata is available without logging in — observability for microservices.

### API 3 — auth-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `auth-service` is independently running on its own port (not only via gateway).

**Microservice:** auth-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8084/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `auth-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `auth-service` is registered and healthy.

### API 4 — member-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `member-service` is independently running on its own port (not only via gateway).

**Microservice:** member-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8081/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `member-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `member-service` is registered and healthy.

### API 5 — subscription-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `subscription-service` is independently running on its own port (not only via gateway).

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8082/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `subscription-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `subscription-service` is registered and healthy.

### API 6 — attendance-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `attendance-service` is independently running on its own port (not only via gateway).

**Microservice:** attendance-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8083/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `attendance-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `attendance-service` is registered and healthy.

### API 7 — facility-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `facility-service` is independently running on its own port (not only via gateway).

**Microservice:** facility-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8085/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `facility-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `facility-service` is registered and healthy.

### API 8 — payment-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `payment-service` is independently running on its own port (not only via gateway).

**Microservice:** payment-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8086/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `payment-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `payment-service` is registered and healthy.

### API 9 — notification-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `notification-service` is independently running on its own port (not only via gateway).

**Microservice:** notification-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8087/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `notification-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `notification-service` is registered and healthy.

### API 10 — trainer-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `trainer-service` is independently running on its own port (not only via gateway).

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8088/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `trainer-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `trainer-service` is registered and healthy.

### API 11 — reporting-service Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `reporting-service` is independently running on its own port (not only via gateway).

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8089/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `reporting-service` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `reporting-service` is registered and healthy.

### API 12 — eureka-server Health (direct)

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** Prove `eureka-server` is independently running on its own port (not only via gateway).

**Microservice:** eureka-server

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8761/actuator/health`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "status": "UP" }
```
*(Direct actuator — not routed through gateway.)*

**Step 7 — Negative Test:**
- If `eureka-server` is stopped: connection refused.
- Shows each microservice is a separate deployable process.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("UP", () => pm.expect(pm.response.json().status).to.eql("UP"));
```

**Step 9 — Review Demonstration:**
Open Eureka then hit this URL to show `eureka-server` is registered and healthy.

### API 13 — Eureka Applications Registry

**Rubric:** 1 — Microservices, Service Discovery and Configuration

**Purpose:** List all applications registered with Netflix Eureka.

**Microservice:** eureka-server

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8761/eureka/apps`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

Without `Accept: application/json`, Eureka may return XML.

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
- JSON body contains `applications.application` array with names like `AUTH-SERVICE`, `MEMBER-SERVICE`, `API-GATEWAY`, etc.
- *(Verified pattern — exact instance list depends on what you started.)*

**Step 7 — Negative Test:**
- Call while Eureka is down → connection refused.
- Call `http://localhost:8761/eureka/apps/DOES-NOT-EXIST` → may return empty application or 404 depending on Eureka version.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show the faculty that services are **discovered dynamically**, not hard-coded IPs in the client.

### API 14 — Services Status (via Gateway Discovery)

**Rubric:** 1 & 3 — Discovery + Gateway integration

**Purpose:** Gateway asks Eureka which expected PulseFit services are UP/DOWN and returns a summary.

**Microservice:** api-gateway (`ServiceStatusController`) using `DiscoveryClient`

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/services/status`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
- Example *(inferred from `StatusResponse`)*:
```json
{
  "checkedAt": "2026-10-30T17:30:00.123+00:00",
  "expectedServices": 10,
  "availableServices": 10,
  "unavailableServices": 0,
  "services": [
    {
      "serviceId": "AUTH-SERVICE",
      "available": true,
      "status": "UP",
      "instanceCount": 1,
      "instances": [
        {
          "instanceId": "...",
          "host": "192.168.x.x",
          "port": 8084,
          "uri": "http://192.168.x.x:8084"
        }
      ]
    }
  ]
}
```

**Step 7 — Negative Test:**
- Remove Bearer token.
- Expected HTTP **401** with gateway error shape:
```json
{
  "timestamp": "2026-10-30T17:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "AUTH_MISSING_TOKEN",
  "message": "Missing or malformed Authorization header.",
  "path": "/api/members"
}
```
*(Inferred from `SecurityContextFilter` — exact timestamp/path vary.)*

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("has services", () => pm.expect(pm.response.json().services.length).to.be.above(0));
```

**Step 9 — Review Demonstration:**
Best single screen for Rubric 1+3: gateway + discovery + JWT together.

### API 15 — Services Registry Snapshot

**Rubric:** 1 & 3 — Discovery + Gateway

**Purpose:** Return raw discovered application names and instances from the gateway’s DiscoveryClient.

**Microservice:** api-gateway

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/services/registry`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{
  "checkedAt": "2026-10-30T17:30:00.123+00:00",
  "appCount": 11,
  "applications": [
    { "name": "AUTH-SERVICE", "instances": [ { "instanceId": "...", "host": "...", "port": 8084, "uri": "..." } ] }
  ]
}
```
*(Inferred from `RegistryResponse`.)*

**Step 7 — Negative Test:**
- Use TRAINEE token (allowed) vs no token (**401**).
- Role ADMIN/STAFF/MEMBER/TRAINEE are all allowed by gateway filter for `/api/services/**`.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("appCount", () => pm.expect(pm.response.json().appCount).to.be.above(0));
```

**Step 9 — Review Demonstration:**
Complement to Eureka UI — proves the gateway itself can query the registry.

### API 16 — Login (valid admin)

**Rubric:** 2 — JWT Authentication and Security

**Purpose:** Authenticate and receive a signed RS256 JWT access token.

**Microservice:** auth-service (routed by gateway)

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/login`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
Body → **raw** → **JSON**:
```json
{
  "email": "admin@pulsefit.com",
  "password": "Admin@PulseFit2026"
}
```
```

**Step 6 — Expected Success Response:**
- HTTP **200**
- Example *(verified AuthResponse shape)*:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "userId": 1,
    "email": "admin@pulsefit.com",
    "firstName": "System",
    "lastName": "Administrator",
    "role": "ADMIN",
    "memberId": null,
    "enabled": true,
    "tokenVersion": 1
  }
}
```
- Copy `accessToken` into environment variable `accessToken`. `expiresIn` is seconds (default 15 minutes = 900).

**Step 7 — Negative Test:**
Body:
```json
{
  "email": "admin@pulsefit.com",
  "password": "WrongPassword!"
}
```
- Expected HTTP **401** Unauthorized.

**Step 8 — Postman Test Script:**
```javascript
pm.test("200 login", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("token present", () => pm.expect(body.accessToken).to.be.a("string"));
pm.test("Bearer type", () => pm.expect(body.tokenType).to.eql("Bearer"));
pm.environment.set("accessToken", body.accessToken);
```

**Step 9 — Review Demonstration:**
Core JWT demo — show token issued after password check.

### API 17 — Login (valid trainee)

**Rubric:** 2 — JWT + RBAC

**Purpose:** Obtain a TRAINEE JWT for role-based demos.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/login`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "email": "trainee@pulsefit.com",
  "password": "Trainee@PulseFit2026"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** with `user.role` = `"TRAINEE"`.
- Save token to a separate variable if needed, e.g. `traineeToken`.

**Step 7 — Negative Test:**
Same as invalid login → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("200", () => pm.response.to.have.status(200));
pm.test("TRAINEE", () => pm.expect(pm.response.json().user.role).to.eql("TRAINEE"));
pm.environment.set("traineeToken", pm.response.json().accessToken);
```

**Step 9 — Review Demonstration:**
Use this token later to show TRAINEE cannot POST members/payments (403).

### API 18 — Register (closed — expected failure)

**Rubric:** 2 — JWT Authentication and Security

**Purpose:** Demonstrate that public signup is intentionally disabled.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/register`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "email": "new.user@example.com",
  "password": "Password123!",
  "firstName": "New",
  "lastName": "User",
  "contact": "555-0100",
  "dateOfBirth": "1995-01-01"
}
```

**Step 6 — Expected Success Response:**
- There is **no success path** for public register in this project.
- Expected HTTP **403 Forbidden** with a message that invitation/claim is required.
- *(Controller annotates 201, but service rejects — live status is 403.)*

**Step 7 — Negative Test:**
This request *is* the negative/security test. Also try missing email → **400** validation.

**Step 8 — Postman Test Script:**
```javascript
pm.test("403 closed register", () => pm.response.to.have.status(403));
```

**Step 9 — Review Demonstration:**
Explain to faculty: security design — users join via invitation or member claim OTP.

### API 19 — Current User (me)

**Rubric:** 2 — JWT

**Purpose:** Validate the JWT and return the authenticated user profile.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/me`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{
  "userId": 1,
  "email": "admin@pulsefit.com",
  "firstName": "System",
  "lastName": "Administrator",
  "role": "ADMIN",
  "memberId": null,
  "enabled": true,
  "tokenVersion": 1
}
```
*(Verified UserSummary shape.)*

**Step 7 — Negative Test:**
- No Auth → **401**.
- Tampered token (change last characters) → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("role", () => pm.expect(pm.response.json().role).to.be.a("string"));
```

**Step 9 — Review Demonstration:**
Prove JWT is accepted by gateway and auth-service.

### API 20 — Refresh JWT

**Rubric:** 2 — JWT

**Purpose:** Issue a new access token for the same user without re-entering password.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/refresh`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** — same shape as login `AuthResponse`.
- Replace `accessToken` with the new value.

**Step 7 — Negative Test:**
- Missing token → **401**.
- Disabled user → **403** *(inferred from AuthService).*

**Step 8 — Postman Test Script:**
```javascript
pm.test("200 refresh", () => pm.response.to.have.status(200));
pm.environment.set("accessToken", pm.response.json().accessToken);
```

**Step 9 — Review Demonstration:**
Show token lifecycle management.

### API 21 — Logout

**Rubric:** 2 — JWT

**Purpose:** Invalidate session by bumping token version (old JWTs stop working).

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/logout`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **204 No Content**
- Empty body.

**Step 7 — Negative Test:**
- After logout, call `/api/auth/me` with the **old** token → **401**.
- Missing token on logout may still return **204**/empty depending on filter; prefer always sending Bearer.

**Step 8 — Postman Test Script:**
```javascript
pm.test("204 logout", () => pm.response.to.have.status(204));
```

**Step 9 — Review Demonstration:**
Do this **last** in a sequence, or re-login afterward.

### API 22 — Forgot Password

**Rubric:** 2 — JWT / account recovery

**Purpose:** Start password reset (always returns generic success message).

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/password/forgot`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "email": "admin@pulsefit.com"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** with a message object (does not reveal whether email exists).

**Step 7 — Negative Test:**
- Invalid JSON / missing email → **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show account recovery endpoint exists; actual email depends on mailer config.

### API 23 — Reset Password

**Rubric:** 2 — JWT / account recovery

**Purpose:** Complete reset using token from email.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/password/reset`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "token": "paste-reset-token-from-email-or-logs",
  "password": "NewPassword123!"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** `MessageResponse` when token is valid.
- **Cannot be fully demoed without a real reset token** — if token invalid → **400**.

**Step 7 — Negative Test:**
```json
{ "token": "invalid", "password": "short" }
```
- Expected **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("handled", () => pm.expect([200,400]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Explain flow; show **400** with bad token if no email token available.

### API 24 — Claim Initiate

**Rubric:** 2 — Secure member onboarding

**Purpose:** Send OTP so an existing member can claim a login account.

**Microservice:** auth-service (+ member lookup + mailer)

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/claim/initiate`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "email": "existing.member@pulsefit.local"
}
```
Use an email of a member you created via `POST /api/members` that does **not** already have a user account.

**Step 6 — Expected Success Response:**
- HTTP **200**
- Fields include `message`, `expiresAt`; `devOtp` only if SMTP is not configured.
- If SMTP works, OTP is emailed (not always in JSON).

**Step 7 — Negative Test:**
- Unknown email → **404**.
- Email already claimed → **409**.
- Too many requests → **400** rate limit.

**Step 8 — Postman Test Script:**
```javascript
pm.test("claim initiate status", () => pm.expect([200,404,409,400]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Show alternative to public register.

### API 25 — Claim Complete

**Rubric:** 2 — Secure member onboarding

**Purpose:** Verify OTP and create MEMBER user; returns JWT.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/claim/complete`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "email": "existing.member@pulsefit.local",
  "otp": "123456",
  "password": "MemberPass1!"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** + `AuthResponse` (same as login).

**Step 7 — Negative Test:**
- Wrong OTP → **400**.
- Missing fields → **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("claim complete", () => pm.expect([201,400,404,409]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
End-to-end claim only if you have a fresh OTP.

### API 26 — Create Invitation

**Rubric:** 2 — Admin-controlled onboarding

**Purpose:** Admin invites a new user by email.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/invitations`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "fullName": "Review Guest",
  "email": "review.guest@example.com"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** `InvitationView` (id, email, status, token fields as implemented).
- Save `id` → `invitationId`.

**Step 7 — Negative Test:**
- STAFF/TRAINEE token → **403**.
- Missing Bearer → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201", () => pm.response.to.have.status(201));
if (pm.response.code === 201) pm.environment.set("invitationId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Show admin-only user provisioning.

### API 27 — List Invitations

**Rubric:** 2 — Admin

**Purpose:** List outstanding invitations.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/invitations`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON array of invitations.

**Step 7 — Negative Test:**
- Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Pair with create invitation.

### API 28 — Resend Invitation

**Rubric:** 2 — Admin

**Purpose:** Resend invitation email for an existing invitation id.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/invitations/{{invitationId}}/resend`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `InvitationView`.

**Step 7 — Negative Test:**
- Unknown id → **404**. Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("resend", () => pm.expect([200,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Show operational admin tooling.

### API 29 — Invitation Preview

**Rubric:** 2 — Public invite accept flow

**Purpose:** Validate invitation token before accept.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/invitations/preview?token=PASTE_TOKEN_HERE`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "valid": true, "reason": null, "email": "review.guest@example.com", "fullName": "Review Guest" }
```
*(Field names inferred from InvitationPreview.)*

**Step 7 — Negative Test:**
- Bad token → `valid: false` with reason (still often **200**).

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Only if you have a real invitation token from DB/email.

### API 30 — Accept Invitation

**Rubric:** 2 — Public invite accept flow

**Purpose:** Set password and activate invited account.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/invitations/accept`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Step 5 — Body:**
```json
{
  "token": "PASTE_TOKEN_HERE",
  "password": "InvitePass1!"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** message when token valid.

**Step 7 — Negative Test:**
- Bad/expired token → **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("accept", () => pm.expect([200,400]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Skip live success if no token; show request shape.

### API 31 — Create Staff User

**Rubric:** 2 — RBAC admin

**Purpose:** Admin creates a STAFF account.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/users/staff`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "email": "staff.review@pulsefit.local",
  "temporaryPassword": "StaffPass1!",
  "firstName": "Desk",
  "lastName": "Staff"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** `UserSummary` with role STAFF.

**Step 7 — Negative Test:**
- Duplicate email → error **409/400**. Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("staff create", () => pm.expect([201,409,400]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Show role separation.

### API 32 — Create Admin User

**Rubric:** 2 — RBAC admin

**Purpose:** Admin creates another ADMIN account.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/users/admin`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "email": "admin2.review@pulsefit.local",
  "temporaryPassword": "AdminPass1!",
  "firstName": "Second",
  "lastName": "Admin"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** `UserSummary` role ADMIN.

**Step 7 — Negative Test:**
- Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("admin create", () => pm.expect([201,409,400,403]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Optional — avoid creating many admins during review.

### API 33 — Update User Role

**Rubric:** 2 — RBAC

**Purpose:** Change a user’s role.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/users/{{userId}}/role`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "role": "STAFF"
}
```
Allowed enum values: `ADMIN`, `STAFF`, `MEMBER`, `TRAINEE`.

**Step 6 — Expected Success Response:**
- HTTP **200** updated `UserSummary`.

**Step 7 — Negative Test:**
- Invalid role string → **400**. Missing user → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("role update", () => pm.expect([200,400,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Use a userId from create-staff response.

### API 34 — Update User Status

**Rubric:** 2 — Account enable/disable

**Purpose:** Enable or disable a user account.

**Microservice:** auth-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/users/{{userId}}/status`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "enabled": false
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** `UserSummary` with `enabled: false`.

**Step 7 — Negative Test:**
- Disabled user login → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("status", () => pm.expect([200,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Show account lockout control.

### API 35 — Audit Logs

**Rubric:** 2 — Security auditing

**Purpose:** List security audit events (logins, etc.).

**Microservice:** auth-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/auth/audit-logs`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array of audit entries (id, timestamp, actor, action, outcome, …).

**Step 7 — Negative Test:**
- Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show security traceability after a few logins.

### API 36 — Create Member

**Rubric:** 3 & 4 — Gateway routing + CRUD

**Purpose:** Staff/Admin creates a gym member profile.

**Microservice:** member-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/members`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Postman Member",
  "email": "postman.member@pulsefit.local",
  "contact": "555-0200",
  "dateOfBirth": "1998-05-05",
  "status": "ACTIVE"
}
```
`status` enum: `ACTIVE`, `INACTIVE`, `SUSPENDED`.

**Step 6 — Expected Success Response:**
- HTTP **201**
```json
{
  "id": 12,
  "name": "Postman Member",
  "email": "postman.member@pulsefit.local",
  "contact": "555-0200",
  "dateOfBirth": "1998-05-05",
  "status": "ACTIVE",
  "createdAt": "2026-10-30T17:40:00.000Z"
}
```
Save `id` → `memberId`.

**Step 7 — Negative Test:**
```json
{ "name": "", "email": "bad", "contact": "", "status": null }
```
- Expected **400** validation.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201", () => pm.response.to.have.status(201));
pm.environment.set("memberId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Primary Create in CRUD demo.

### API 37 — List Members

**Rubric:** 4 — CRUD Read

**Purpose:** List all members (staff desk).

**Microservice:** member-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/members`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON array of `MemberResponse`.

**Step 7 — Negative Test:**
- No token → **401**.
- TRAINEE token → **403** (list is STAFF/ADMIN only).

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show role restriction with trainee token as negative.

### API 38 — Get Member by ID

**Rubric:** 4 — CRUD Read

**Purpose:** Fetch one member; ownership rules apply for MEMBER/TRAINEE.

**Microservice:** member-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/members/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** single `MemberResponse`.

**Step 7 — Negative Test:**
- `GET /api/members/999999` → **404**.
- MEMBER accessing another id → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show 200 then 404 for missing id.

### API 39 — Update Member

**Rubric:** 4 — CRUD Update

**Purpose:** Update member profile fields.

**Microservice:** member-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/members/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Postman Member Updated",
  "email": "postman.updated@pulsefit.local",
  "contact": "555-0201",
  "dateOfBirth": "1998-05-05",
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** updated `MemberResponse`.

**Step 7 — Negative Test:**
- TRAINEE update → **403**. Missing member → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Complete Update step of CRUD.

### API 40 — Delete Member

**Rubric:** 4 — CRUD Delete

**Purpose:** Hard-delete member (ADMIN only).

**Microservice:** member-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/members/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **204 No Content**.

**Step 7 — Negative Test:**
- STAFF may be forbidden depending on PreAuthorize (ADMIN only) → **403**. Missing id → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("204", () => pm.response.to.have.status(204));
```

**Step 9 — Review Demonstration:**
Do delete on a **test** member created during review, not production data.

### API 41 — List Member Subscriptions (Feign)

**Rubric:** 3 — Inter-service communication

**Purpose:** Member-service calls subscription-service via Feign and returns subscriptions for a member.

**Microservice:** member-service → subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/members/{{memberId}}/subscriptions`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array of:
```json
[{ "id": 1, "memberId": 12, "planId": 1, "startDate": "2026-10-30", "expiryDate": "2026-11-29", "status": "PENDING" }]
```
- If subscription-service is down → **503**.

**Step 7 — Negative Test:**
- No JWT → **401**. Wrong ownership for MEMBER → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("subs", () => pm.expect([200,503,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Best inter-service demo for Rubric 3.

### API 42 — Assign Subscription to Member (Feign)

**Rubric:** 3 & 4 — Integration + Create

**Purpose:** Desk assigns a plan to a member without activating payment (PENDING).

**Microservice:** member-service → subscription-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/members/{{memberId}}/subscriptions`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "planId": 1,
  "startDate": "2026-10-30",
  "expiryDate": "2026-11-29",
  "status": "PENDING"
}
```
Do **not** send `ACTIVE` — rejected by design. `planId` alone is enough (defaults apply).

**Step 6 — Expected Success Response:**
- HTTP **201** `SubscriptionView`. Save `id` → `subscriptionId`.

**Step 7 — Negative Test:**
```json
{ "planId": 1, "status": "ACTIVE" }
```
- Expected **400**. Unknown plan/member → **404**. Conflict → **409**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201 assign", () => pm.response.to.have.status(201));
pm.environment.set("subscriptionId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Show Feign write path + validation.

### API 43 — List Plans (public)

**Rubric:** 3 — Gateway public route

**Purpose:** Anyone can browse membership plans without JWT.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/plans`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array. Seeded plans include Weekly, Quarterly, Half Year, Annual.
```json
[{ "id": 1, "planName": "Weekly", "durationInDays": 7, "price": 9.99, "description": "...", "active": true }]
```
Save a plan `id` → `planId`.

**Step 7 — Negative Test:**
- N/A for auth. Invalid path `/api/plans/abc` may **400/404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("200", () => pm.response.to.have.status(200));
const rows = pm.response.json();
if (rows.length) pm.environment.set("planId", String(rows[0].id));
```

**Step 9 — Review Demonstration:**
Show gateway public GET without token.

### API 44 — Get Plan by ID

**Rubric:** 4 — Plans Read

**Purpose:** Fetch one plan.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/plans/{{planId}}`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `MembershipPlanResponse`.

**Step 7 — Negative Test:**
- Unknown id → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Public catalog detail.

### API 45 — Create Plan

**Rubric:** 4 — Plans Create

**Purpose:** Admin creates a membership plan.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/plans`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "planName": "Review Special",
  "durationInDays": 30,
  "price": 49.99,
  "description": "Created during Postman review",
  "active": true
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** plan object. Save id if desired.

**Step 7 — Negative Test:**
- Missing Bearer → **401**. STAFF may be **403** (ADMIN only).

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
```

**Step 9 — Review Demonstration:**
Admin-only write vs public read.

### API 46 — Update Plan

**Rubric:** 4 — Plans Update

**Purpose:** Admin updates a plan.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/plans/{{planId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "planName": "Review Special Updated",
  "durationInDays": 45,
  "price": 59.99,
  "description": "Updated in review",
  "active": true
}
```

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Unknown plan → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Prefer updating a plan you just created, not seeded catalog, during live delete tests.

### API 47 — Delete Plan

**Rubric:** 4 — Plans Delete

**Purpose:** Admin deletes a plan.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/plans/{{planId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **204**.

**Step 7 — Negative Test:**
- Non-admin → **403**. Prefer deleting only review-created plans.

**Step 8 — Postman Test Script:**
```javascript
pm.test("204", () => pm.response.to.have.status(204));
```

**Step 9 — Review Demonstration:**
Delete the Review Special plan, then restore `planId` from list plans.

### API 48 — Create Subscription

**Rubric:** 4 — Subscriptions CRUD

**Purpose:** Staff/Admin create a subscription row directly.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "memberId": 1,
  "planId": 1,
  "startDate": "2026-10-30",
  "expiryDate": "2026-11-29",
  "status": "PENDING"
}
```
Replace memberId/planId with real IDs. Status enum: `PENDING`, `ACTIVE`, `EXPIRED`, `CANCELLED`.

**Step 6 — Expected Success Response:**
- HTTP **201** `SubscriptionResponse`.

**Step 7 — Negative Test:**
- MEMBER/TRAINEE token → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201", () => pm.response.to.have.status(201));
pm.environment.set("subscriptionId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Alternative to member-subscriptions alias.

### API 49 — List Subscriptions

**Rubric:** 4 — Subscriptions Read

**Purpose:** List all subscriptions (staff).

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- No token → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Staff desk listing.

### API 50 — Get Subscription by ID

**Rubric:** 4 — Subscriptions Read

**Purpose:** Fetch one subscription.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions/{{subscriptionId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**. Wrong owner MEMBER → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Read-by-id.

### API 51 — Subscriptions by Member

**Rubric:** 4 — Subscriptions Read

**Purpose:** List subscriptions for one member.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions/member/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- MEMBER other id → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Same data as Feign alias path, different service entry.

### API 52 — Subscription Validity

**Rubric:** 3 — Cross-service use by attendance

**Purpose:** Check if member currently has a valid subscription.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions/member/{{memberId}}/valid`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**
```json
{ "memberId": 1, "valid": false, "subscriptionId": null }
```
*(Inferred ValidityResponse — `valid` true when an ACTIVE unexpired sub exists.)*
Note: on **direct** port 8082 this path is permitAll; via **gateway** JWT is required.

**Step 7 — Negative Test:**
- No JWT on gateway → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Explain attendance uses validity internally.

### API 53 — Update Subscription

**Rubric:** 4 — Subscriptions Update

**Purpose:** Update subscription fields.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions/{{subscriptionId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "memberId": 1,
  "planId": 1,
  "startDate": "2026-10-30",
  "expiryDate": "2026-12-31",
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- TRAINEE → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show update of expiry/status.

### API 54 — Delete Subscription

**Rubric:** 4 — Subscriptions Delete

**Purpose:** Admin deletes subscription.

**Microservice:** subscription-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/subscriptions/{{subscriptionId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **204**.

**Step 7 — Negative Test:**
- Non-admin → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("204", () => pm.response.to.have.status(204));
```

**Step 9 — Review Demonstration:**
Delete only test data.

### API 55 — Attendance Check-In

**Rubric:** 3 & 4 — Integration + Create

**Purpose:** Record check-in; access GRANTED or DENIED based on subscription validity / facility.

**Microservice:** attendance-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/attendance/check-in`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "memberId": 1,
  "facilityId": 1
}
```

**Step 6 — Expected Success Response:**
- HTTP **201**
```json
{
  "id": 5,
  "memberId": 1,
  "subscriptionId": 2,
  "facilityId": 1,
  "checkInTime": "2026-10-30T18:00:00Z",
  "accessStatus": "GRANTED",
  "denialReason": null
}
```
If no valid sub: `accessStatus` = `DENIED` with reason. TRAINEE blocked at gateway (**403**).

**Step 7 — Negative Test:**
- TRAINEE token → **403**.
- Invalid body → **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
```

**Step 9 — Review Demonstration:**
Show GRANTED vs DENIED by assigning/removing ACTIVE subscription first.

### API 56 — List Attendance

**Rubric:** 4 — Attendance Read

**Purpose:** Staff list all check-ins.

**Microservice:** attendance-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/attendance`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array of `AttendanceResponse`.

**Step 7 — Negative Test:**
- No token → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
After check-in, show the new row.

### API 57 — Get Attendance by ID

**Rubric:** 4 — Attendance Read

**Purpose:** Fetch one attendance row.

**Microservice:** attendance-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/attendance/{{attendanceId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Set `attendanceId` from check-in response.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("get att", () => pm.expect([200,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Use id from check-in.

### API 58 — Attendance by Member

**Rubric:** 4 — Attendance Read

**Purpose:** History for one member (TRAINEE may read assigned members).

**Microservice:** attendance-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/attendance/member/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- Ownership violations → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show history filter.

### API 59 — List Facilities

**Rubric:** 4 — Facilities Read

**Purpose:** List facilities (JWT required on gateway).

**Microservice:** facility-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/facilities`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional query: `?name=&location=&status=`.

**Step 6 — Expected Success Response:**
- HTTP **200** array. Save first id → `facilityId`.

**Step 7 — Negative Test:**
- No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("200", () => pm.response.to.have.status(200));
const rows = pm.response.json();
if (rows.length) pm.environment.set("facilityId", String(rows[0].id));
```

**Step 9 — Review Demonstration:**
Need a facility before check-in.

### API 60 — Get Facility

**Rubric:** 4 — Facilities Read

**Purpose:** Get one facility.

**Microservice:** facility-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/facilities/{{facilityId}}`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Detail view.

### API 61 — Create Facility

**Rubric:** 4 — Facilities Create

**Purpose:** Staff/Admin create facility.

**Microservice:** facility-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/facilities`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Review Gym Floor",
  "address": "123 Pulse Street",
  "location": "Downtown",
  "contact": "555-1000",
  "operatingHours": "06:00-22:00",
  "capacity": 120,
  "status": "ACTIVE"
}
```
Confirm `FacilityStatus` enum values in code if create fails validation.

**Step 6 — Expected Success Response:**
- HTTP **201**. Save id → `facilityId`.

**Step 7 — Negative Test:**
- Invalid capacity → **400**. TRAINEE → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201", () => pm.response.to.have.status(201));
pm.environment.set("facilityId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Create before check-in if none exist.

### API 62 — Update Facility

**Rubric:** 4 — Facilities Update

**Purpose:** Update facility.

**Microservice:** facility-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/facilities/{{facilityId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Review Gym Floor Updated",
  "address": "123 Pulse Street",
  "location": "Downtown",
  "contact": "555-1001",
  "operatingHours": "05:00-23:00",
  "capacity": 150,
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Update step.

### API 63 — Delete Facility (deactivate)

**Rubric:** 4 — Facilities Delete

**Purpose:** Soft-delete/deactivate facility (returns body).

**Microservice:** facility-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/facilities/{{facilityId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** with deactivated `FacilityResponse` (not 204).

**Step 7 — Negative Test:**
- Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Note: returns 200 entity, unlike member delete 204.

### API 64 — List Trainers

**Rubric:** 4 — Trainers Read

**Purpose:** List trainers.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional `?facilityId=&specialization=&status=`.

**Step 6 — Expected Success Response:**
- HTTP **200** array. Save id → `trainerId`.

**Step 7 — Negative Test:**
- No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show trainer catalog.

### API 65 — Trainers by Facility

**Rubric:** 4 — Trainers Read

**Purpose:** Filter trainers by facility.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/facility/{{facilityId}}`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Facility linkage.

### API 66 — Trainee Roster (me)

**Rubric:** 2 & 4 — TRAINEE RBAC

**Purpose:** Logged-in trainee sees assigned members roster.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/me/roster`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth: Bearer Token = `{{traineeToken}}` (login as trainee)
- Role: **TRAINEE** only (`@PreAuthorize`)

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{traineeToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `RosterResponse` with members, subscriptions, attendance summaries.

**Step 7 — Negative Test:**
- Admin token may **403**. No token → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("roster", () => pm.expect([200,403]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Key TRAINEE feature for dashboard viva.

### API 67 — Get Trainer Assignments for Member

**Rubric:** 4 — Assignments Read

**Purpose:** List trainer assignments for a member.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/member/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array of `AssignmentResponse`.

**Step 7 — Negative Test:**
- MEMBER other id → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
After assign member to trainer.

### API 68 — Get Trainer by ID

**Rubric:** 4 — Trainers Read

**Purpose:** Fetch one trainer.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}`

**Step 3 — Authorization:**
- Authentication: **required**
- Postman Auth tab: **Bearer Token** → `{{accessToken}}`
- Role: any authenticated role (ADMIN / STAFF / MEMBER / TRAINEE depending on endpoint)
- Obtain token via login

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `TrainerResponse`.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Detail.

### API 69 — Create Trainer

**Rubric:** 4 — Trainers Create

**Purpose:** Staff/Admin create trainer profile.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Review Coach",
  "specialization": "Strength",
  "experienceYears": 5,
  "facilityId": 1,
  "availability": "Mon-Fri 09:00-17:00",
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201**. Save `trainerId`.

**Step 7 — Negative Test:**
- Invalid facility → error. TRAINEE → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("201", () => pm.response.to.have.status(201));
pm.environment.set("trainerId", String(pm.response.json().id));
```

**Step 9 — Review Demonstration:**
Create trainer for assignment demo.

### API 70 — Update Trainer

**Rubric:** 4 — Trainers Update

**Purpose:** Update trainer.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
PUT

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Review Coach Updated",
  "specialization": "HIIT",
  "experienceYears": 6,
  "facilityId": 1,
  "availability": "Mon-Sat 08:00-18:00",
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Update step.

### API 71 — Delete Trainer

**Rubric:** 4 — Trainers Delete

**Purpose:** Deactivate/delete trainer (returns body).

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `TrainerResponse`.

**Step 7 — Negative Test:**
- Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Prefer not deleting bootstrap trainee’s linked trainer mid-demo.

### API 72 — Assign Member to Trainer

**Rubric:** 3 & 4 — Relationships

**Purpose:** Link member to trainer.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}/members/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **201** `AssignmentResponse`.

**Step 7 — Negative Test:**
- Duplicate assign → conflict error. Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
```

**Step 9 — Review Demonstration:**
Enables trainee roster visibility for that member.

### API 73 — List Trainer Members

**Rubric:** 4 — Assignments Read

**Purpose:** List members assigned to a trainer.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}/members`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
After assign.

### API 74 — Unassign Member from Trainer

**Rubric:** 4 — Assignments Delete

**Purpose:** Remove assignment.

**Microservice:** trainer-service

**Step 1 — HTTP Method:**
DELETE

**Step 2 — Request URL:**
`http://localhost:8080/api/trainers/{{trainerId}}/members/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **204**.

**Step 7 — Negative Test:**
- Missing assignment → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("204", () => pm.response.to.have.status(204));
```

**Step 9 — Review Demonstration:**
Cleanup after demo.

### API 75 — Payment Checkout

**Rubric:** 3 & 4 — Payments

**Purpose:** Start checkout for a plan (requires linked memberId on user for MEMBER).

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/checkout`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "planId": 1
}
```
Admin/Staff checkout may require member context — if **403**, create/login as MEMBER who claimed a profile, or use desk payment create below.

**Step 6 — Expected Success Response:**
- HTTP **200** `CheckoutResponse` with payment/order fields.

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("checkout", () => pm.expect([200,403,400]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Explain Razorpay test / demo mode from response fields.

### API 76 — Payment Verify

**Rubric:** 4 — Payments

**Purpose:** Verify Razorpay payment signature.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/verify`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "razorpayOrderId": "order_xxx",
  "razorpayPaymentId": "pay_xxx",
  "razorpaySignature": "sig_xxx"
}
```
**Hard to demo live without real Razorpay test values.** Prefer demo checkout.

**Step 6 — Expected Success Response:**
- HTTP **200** `PaymentResponse` when signature valid.

**Step 7 — Negative Test:**
- Bad signature → error **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("verify", () => pm.expect([200,400,403]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Show request shape; use demo pay if Razorpay keys unavailable.

### API 77 — Demo Pay

**Rubric:** 4 — Payments (simulation)

**Purpose:** Complete simulated payment for a pending checkout paymentId.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/checkout/demo`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "paymentId": 1
}
```
Use `paymentId` from checkout response.

**Step 6 — Expected Success Response:**
- HTTP **200** `PaymentResponse`.

**Step 7 — Negative Test:**
- Unknown paymentId → error.

**Step 8 — Postman Test Script:**
```javascript
pm.test("demo pay", () => pm.expect([200,400,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Best payment success path for viva without Razorpay UI.

### API 78 — Cancel Checkout

**Rubric:** 4 — Payments

**Purpose:** Cancel a pending checkout order.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/checkout/cancel`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "orderId": "order_xxx"
}
```

**Step 6 — Expected Success Response:**
- HTTP **200** `PaymentResponse`.

**Step 7 — Negative Test:**
- Bad order → error.

**Step 8 — Postman Test Script:**
```javascript
pm.test("cancel", () => pm.expect([200,400,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Optional.

### API 79 — Create Payment (desk)

**Rubric:** 4 — Payments Create

**Purpose:** Staff records a payment against member/subscription.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "memberId": 1,
  "subscriptionId": 1,
  "paymentMethod": "CARD",
  "idempotencyKey": "review-pay-001"
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** `PaymentResponse`. Save `id` → `paymentId`.

**Step 7 — Negative Test:**
- TRAINEE → **403**. Validation → **400**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
```

**Step 9 — Review Demonstration:**
Desk payment CRUD create.

### API 80 — List Payments

**Rubric:** 4 — Payments Read

**Purpose:** List payments.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/payments`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** array.

**Step 7 — Negative Test:**
- No token → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
After create.

### API 81 — Get Payment

**Rubric:** 4 — Payments Read

**Purpose:** Get payment by id.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/{{paymentId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Read-by-id.

### API 82 — Payments by Member

**Rubric:** 4 — Payments Read

**Purpose:** List payments for member.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/member/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Ownership → **403** for wrong MEMBER.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Filter.

### API 83 — Payments by Subscription

**Rubric:** 4 — Payments Read

**Purpose:** List payments for subscription.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/subscription/{{subscriptionId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Filter.

### API 84 — Retry Payment

**Rubric:** 4 — Payments

**Purpose:** Retry a failed payment.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/{{paymentId}}/retry`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `PaymentResponse`.

**Step 7 — Negative Test:**
- Wrong state → error. Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("retry", () => pm.expect([200,400,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Optional.

### API 85 — Refund Payment

**Rubric:** 4 — Payments

**Purpose:** Refund a payment.

**Microservice:** payment-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/payments/{{paymentId}}/refund`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** `PaymentResponse`.

**Step 7 — Negative Test:**
- Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("refund", () => pm.expect([200,400,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Optional — may change financial state.

### API 86 — Create Notification

**Rubric:** 4 — Notifications Create

**Purpose:** Staff creates a notification record.

**Microservice:** notification-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/notifications`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "memberId": 1,
  "type": "EMAIL",
  "recipient": "postman.member@pulsefit.local",
  "subject": "Welcome to PulseFit",
  "message": "Your membership desk record was created."
}
```

**Step 6 — Expected Success Response:**
- HTTP **201** `NotificationResponse`.

**Step 7 — Negative Test:**
- Validation → **400**. TRAINEE → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
```

**Step 9 — Review Demonstration:**
Show notification microservice.

### API 87 — List Notifications

**Rubric:** 4 — Notifications Read

**Purpose:** List notifications; optional filters.

**Microservice:** notification-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/notifications`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional `?memberId=&type=&status=`.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
After create.

### API 88 — Get Notification

**Rubric:** 4 — Notifications Read

**Purpose:** Get by id.

**Microservice:** notification-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/notifications/{{notificationId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Set id from create.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Missing → **404**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("get n", () => pm.expect([200,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Detail.

### API 89 — Notifications by Member

**Rubric:** 4 — Notifications Read

**Purpose:** Member-scoped list.

**Microservice:** notification-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/notifications/member/{{memberId}}`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Wrong MEMBER → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Filter.

### API 90 — Retry Notification

**Rubric:** 4 — Notifications

**Purpose:** Retry failed delivery.

**Microservice:** notification-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/notifications/{{notificationId}}/retry`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200**.

**Step 7 — Negative Test:**
- Non-staff → **403**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("retry n", () => pm.expect([200,400,403,404]).to.include(pm.response.code));
```

**Step 9 — Review Demonstration:**
Optional.

### API 91 — Dashboard Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `dashboard` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/dashboard`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional query: `?from=2026-01-01&to=2026-12-31&facilityId=1`.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 92 — Members Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `members` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/members`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 93 — Subscriptions Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `subscriptions` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/subscriptions`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 94 — Attendance Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `attendance` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/attendance`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional query: `?from=2026-01-01&to=2026-12-31&facilityId=1`.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 95 — Revenue Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `revenue` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/revenue`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional query: `?from=2026-01-01&to=2026-12-31`.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 96 — Payments Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `payments` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/payments`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required. Optional query: `?from=2026-01-01&to=2026-12-31`.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 97 — Facilities Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `facilities` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/facilities`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 98 — Trainers Report

**Rubric:** 4 — Reporting / demonstration

**Purpose:** Aggregated `trainers` metrics for staff dashboards.

**Microservice:** reporting-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/reports/trainers`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- HTTP **200** JSON object/map (structure varies by report).

**Step 7 — Negative Test:**
- TRAINEE → **403**. No JWT → **401**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
```

**Step 9 — Review Demonstration:**
Show reporting microservice aggregates other services.

### API 99 — Protected API without JWT

**Rubric:** 2 & 3 — Security + Gateway

**Purpose:** Prove gateway rejects unauthenticated access to protected routes.

**Microservice:** api-gateway filter → would have been member-service

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/members`

**Step 3 — Authorization:**
- Authentication: **not required**
- Postman Auth tab: **No Auth**
- Role: public / anonymous
- JWT: not used for this request
- Intentionally **omit** Bearer token.

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- **No success.** Expected failure is the demo.

**Step 7 — Negative Test:**
- Expected HTTP **401**
```json
{
  "timestamp": "2026-10-30T17:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "AUTH_MISSING_TOKEN",
  "message": "Missing or malformed Authorization header.",
  "path": "/api/members"
}
```
*(Inferred from `SecurityContextFilter` — exact timestamp/path vary.)*

**Step 8 — Postman Test Script:**
```javascript
pm.test("401 missing JWT", () => pm.response.to.have.status(401));
```

**Step 9 — Review Demonstration:**
Must-show negative test for Rubric 2.

### API 100 — RBAC Forbidden (Trainee creates member)

**Rubric:** 2 — Role-based authorization

**Purpose:** Show TRAINEE cannot perform STAFF/ADMIN writes.

**Microservice:** api-gateway + member-service

**Step 1 — HTTP Method:**
POST

**Step 2 — Request URL:**
`http://localhost:8080/api/members`

**Step 3 — Authorization:**
- Use Bearer `{{traineeToken}}`
- Role TRAINEE is authenticated but not authorized for this write

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{traineeToken}}` |

(Or use Auth tab → Bearer Token so Postman adds `Authorization` automatically.)

**Step 5 — Body:**
```json
{
  "name": "Should Fail",
  "email": "should.fail@pulsefit.local",
  "contact": "555-0000",
  "dateOfBirth": "1990-01-01",
  "status": "ACTIVE"
}
```

**Step 6 — Expected Success Response:**
- **No success.** Expected **403**.

**Step 7 — Negative Test:**
- Expected HTTP **403 Forbidden**.

**Step 8 — Postman Test Script:**
```javascript
pm.test("403 trainee write", () => pm.response.to.have.status(403));
```

**Step 9 — Review Demonstration:**
Pair with successful admin create.

### API 101 — Internal Path Blocked at Gateway

**Rubric:** 3 — Gateway security

**Purpose:** Prove inter-service internal APIs are not exposed publicly.

**Microservice:** api-gateway (blocks before routing)

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/members/internal/summaries`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- **No success via gateway.**

**Step 7 — Negative Test:**
- Expected HTTP **403** even with valid ADMIN JWT.

**Step 8 — Postman Test Script:**
```javascript
pm.test("403 internal", () => pm.response.to.have.status(403));
```

**Step 9 — Review Demonstration:**
Explain Feign uses direct service ports, not the public gateway.

### API 102 — Gateway 404 Unknown Route

**Rubric:** 3 — Gateway errors

**Purpose:** Unknown path through gateway.

**Microservice:** api-gateway

**Step 1 — HTTP Method:**
GET

**Step 2 — Request URL:**
`http://localhost:8080/api/does-not-exist`

**Step 3 — Authorization:**
- Authentication: **required** (except where noted)
- Postman Auth tab: **Bearer Token**
- Role: **ADMIN** (demo account)
- Obtain token: run **API — Login (valid admin)** first, copy `accessToken`, paste into Postman environment variable `accessToken`, then set Auth → Bearer Token → `{{accessToken}}`

**Step 4 — Headers:**
| Key | Value |
|---|---|
| `Accept` | `application/json` |
| `Authorization` | `Bearer {{accessToken}}` |

(Or use Auth tab → Bearer Token.)

**Step 5 — Body:**
No request body required.

**Step 6 — Expected Success Response:**
- N/A

**Step 7 — Negative Test:**
- Expected **404** (gateway error JSON may include `code`).

**Step 8 — Postman Test Script:**
```javascript
pm.test("404", () => pm.response.to.have.status(404));
```

**Step 9 — Review Demonstration:**
Show structured gateway errors.

---

## 7. JWT authentication testing (deep dive)

### 7.1 Can I register a test user?

**Public register is closed.** `POST /api/auth/register` returns **403**.

Practical ways to get users:

1. Use bootstrap **admin** / **trainee** accounts.
2. Admin creates **staff** via `POST /api/auth/users/staff`.
3. Admin **invites** via `POST /api/auth/invitations` → accept with token.
4. Staff creates **member** → member **claims** account with OTP.

### 7.2 Login through Postman

1. Method **POST** `http://localhost:8080/api/auth/login`
2. Body raw JSON:
```json
{
  "email": "admin@pulsefit.com",
  "password": "Admin@PulseFit2026"
}
```
3. Send → Status **200**.

### 7.3 Copy JWT

In response JSON, select the `accessToken` string (starts with `eyJ...`).

### 7.4 Save in environment

- Manually: Environment → `accessToken` → paste.
- Or Post-response script:
```javascript
pm.environment.set("accessToken", pm.response.json().accessToken);
```

### 7.5 Use JWT on protected endpoints

Authorization tab → **Bearer Token** → Token = `{{accessToken}}`  
Or header `Authorization: Bearer {{accessToken}}`.

### 7.6 Invalid credentials

Wrong password → **401**.

### 7.7 Missing JWT

`GET /api/members` with No Auth → **401** `AUTH_MISSING_TOKEN`.

### 7.8 Expired or tampered JWT

- Wait >15 minutes without refresh, or
- Edit one character of the token,
then call `/api/auth/me` → **401**.

### 7.9 Role-based authorization

| Action | ADMIN | STAFF | TRAINEE | MEMBER |
|---|---|---|---|---|
| List members | Yes | Yes | No (403) | No |
| Delete member | Yes | No | No | No |
| List plans | Yes | Yes | Yes (public) | Yes (public) |
| Create plan | Yes | No | No | No |
| Trainee roster | No* | No* | Yes | No |
| Payments write | Yes | Yes | No | Limited |

\*Unless that user also has TRAINEE role.

---

## 8. API Gateway and service discovery testing

### 8.1 Verify gateway port

`GET http://localhost:8080/actuator/health` → **200** `{ "status": "UP" }`.

### 8.2 Send requests through gateway

Always use `http://localhost:8080/api/...` (not raw 8081/8082) for demos unless showing direct health.

### 8.3 Verify routing to each microservice

| Call via gateway | Lands on |
|---|---|
| `/api/auth/**` | AUTH-SERVICE :8084 |
| `/api/members/**` | MEMBER-SERVICE :8081 |
| `/api/plans/**`, `/api/subscriptions/**` | SUBSCRIPTION-SERVICE :8082 |
| `/api/attendance/**` | ATTENDANCE-SERVICE :8083 |
| `/api/facilities/**` | FACILITY-SERVICE :8085 |
| `/api/payments/**` | PAYMENT-SERVICE :8086 |
| `/api/notifications/**` | NOTIFICATION-SERVICE :8087 |
| `/api/trainers/**` | TRAINER-SERVICE :8088 |
| `/api/reports/**` | REPORTING-SERVICE :8089 |
| `/api/services/**` | Handled **inside gateway** |

### 8.4 Communication between services

1. `POST /api/members/{id}/subscriptions` — Member Feign → Subscription.
2. `POST /api/attendance/check-in` — Attendance checks subscription validity remotely.
3. Trainee member access — Member Feign → Trainer assignment check.

### 8.5 Gateway errors

| Case | Status |
|---|---|
| Missing JWT | 401 |
| Bad role | 403 |
| Internal path | 403 |
| Unknown route | 404 |
| Downstream instance missing | **503** (e.g. stop subscription-service, call member subscriptions) |

### 8.6 Verify service discovery

1. Eureka UI `http://localhost:8761`
2. `GET http://localhost:8761/eureka/apps` with `Accept: application/json`
3. `GET http://localhost:8080/api/services/status` with JWT
4. `GET http://localhost:8080/api/services/registry` with JWT

---

## 9. CRUD testing by module

Use the detailed APIs above. Quick matrix:

### Members — full CRUD via gateway

| Op | Method | URL | Auth | Body |
|---|---|---|---|---|
| C | POST | `/api/members` | ADMIN/STAFF | MemberRequest JSON |
| R | GET | `/api/members`, `/api/members/{id}` | list: STAFF/ADMIN | none |
| U | PUT | `/api/members/{id}` | ADMIN/STAFF/own MEMBER | MemberRequest |
| D | DELETE | `/api/members/{id}` | ADMIN | none → **204** |

### Plans — full CRUD (Read public)

| Op | Method | URL | Auth |
|---|---|---|---|
| C | POST | `/api/plans` | ADMIN |
| R | GET | `/api/plans`, `/api/plans/{id}` | Public |
| U | PUT | `/api/plans/{id}` | ADMIN |
| D | DELETE | `/api/plans/{id}` | ADMIN → **204** |

### Subscriptions — full CRUD

See APIs Create/List/Get/Update/Delete Subscription + member alias POST/GET.

### Attendance — Create + Read (no Update/Delete APIs)

Check-in **201**; list/get/member history **200**.

### Facilities / Trainers / Payments / Notifications

See section 6 APIs. Reports are **Read-only**.

### Workouts module

**Not implemented** — do not invent endpoints.

---

## 10. Complete college review sequence

Execute **in order** during the viva (≈15–20 minutes):

| # | What to do | Exact Postman request | Expected |
|---|---|---|---|
| 1 | Service discovery UI | Browser `http://localhost:8761` | Apps registered |
| 2 | Eureka JSON | `GET http://localhost:8761/eureka/apps` + Accept JSON | **200** |
| 3 | Gateway health | `GET http://localhost:8080/actuator/health` | **200** UP |
| 4 | Gateway info | `GET http://localhost:8080/actuator/info` | **200** |
| 5 | Auth service health | `GET http://localhost:8084/actuator/health` | **200** UP |
| 6 | Login admin | `POST /api/auth/login` valid body | **200** + token → save |
| 7 | Invalid login | wrong password | **401** |
| 8 | Closed register | `POST /api/auth/register` | **403** |
| 9 | Me | `GET /api/auth/me` + Bearer | **200** ADMIN |
| 10 | Missing JWT | `GET /api/members` No Auth | **401** |
| 11 | Services status | `GET /api/services/status` + Bearer | **200** |
| 12 | Services registry | `GET /api/services/registry` + Bearer | **200** |
| 13 | List plans (public) | `GET /api/plans` No Auth | **200** |
| 14 | Create member | `POST /api/members` + Bearer | **201** save memberId |
| 15 | Get member | `GET /api/members/{{memberId}}` | **200** |
| 16 | Update member | `PUT /api/members/{{memberId}}` | **200** |
| 17 | Assign subscription | `POST /api/members/{{memberId}}/subscriptions` | **201** (Feign) |
| 18 | List member subs | `GET /api/members/{{memberId}}/subscriptions` | **200** |
| 19 | List trainers | `GET /api/trainers` | **200** |
| 20 | Refresh token | `POST /api/auth/refresh` | **200** new token |
| 21 | Trainee login | login trainee | **200** TRAINEE |
| 22 | RBAC deny | trainee `POST /api/members` | **403** |
| 23 | Internal blocked | `GET /api/members/internal/summaries` + admin JWT | **403** |
| 24 | Member not found | `GET /api/members/999999` | **404** |
| 25 | Optional delete | `DELETE` test member | **204** |
| 26 | Optional logout | `POST /api/auth/logout` | **204** then me→**401** |

If subscription-service is stopped before step 17, show **503** for Feign path (strong Rubric 3 resilience point).

---

## 11. Rubric-wise demonstration checklist

### Rubric 1 — Microservices, Service Discovery and Configuration (marks)

- [ ] Eureka dashboard shows multiple services  
- [ ] `/eureka/apps` returns registry JSON  
- [ ] Each major service `/actuator/health` is UP on its port  
- [ ] Gateway `/actuator/health` and `/actuator/info`  
- [ ] `/api/services/status` + `/api/services/registry` with JWT  

### Rubric 2 — JWT Authentication and Security

- [ ] Valid login → JWT  
- [ ] Invalid login → 401  
- [ ] `/api/auth/me`, refresh, logout  
- [ ] Missing/tampered token → 401  
- [ ] Closed register → 403  
- [ ] Role demo: ADMIN success vs TRAINEE 403  
- [ ] Optional: invitations / claim / audit logs  

### Rubric 3 — API Gateway and Service Integration

- [ ] All business calls via `:8080`  
- [ ] Route table explained (auth/members/plans/…)  
- [ ] Feign: member subscriptions GET/POST  
- [ ] Internal paths blocked  
- [ ] Gateway error codes 401/403/404/503  

### Rubric 4 — Testing, Validation and Demonstration

- [ ] Postman collection or this guide used live  
- [ ] Positive CRUD on members (and plans/subscriptions)  
- [ ] Negative validation **400**, not found **404**  
- [ ] Scripts asserting status codes  
- [ ] Clear narration of request → response  

---

## Appendix A — Internal endpoints (do not demo via gateway)

These exist for Feign only (direct service ports). Gateway returns **403**:

- `GET /api/auth/internal/users/{userId}/token-version`
- `GET /api/members/internal/by-email`, `/summaries`, `/{id}`, `POST /internal/create`
- `POST /api/subscriptions/internal/checkout`, `/{id}/activate`, `/{id}/cancel`
- `GET /api/facilities/internal/{id}`
- `POST /api/notifications/internal`
- `POST /api/trainers/internal/bootstrap`, `GET /internal/assigned`

---

## Appendix B — Import ready collection

File: `postman/PulseFit-Evaluation.postman_collection.json`  
Import into Postman for a shorter automated subset of this guide.

---

*Generated from the PulseFit repository controllers, DTOs, gateway routes, and security configuration. Live numeric IDs and timestamps will differ on your database.*

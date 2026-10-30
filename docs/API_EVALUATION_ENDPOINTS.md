# PulseFit evaluation API map

Base URL for routed APIs: `http://localhost:8080`

| Endpoint | Method | Microservice | Purpose | Expected response |
|---|---|---|---|---|
| `/actuator/health` | GET | Each service + gateway | Liveness/health | `200` `{ "status": "UP" }` |
| `/actuator/info` | GET | Each service + gateway | Service metadata | `200` app info JSON |
| `/eureka/apps` | GET | Eureka (`:8761`) | Registered applications | `200` Eureka XML/JSON registry |
| `/api/services/status` | GET | API Gateway | Backend availability via discovery | `200` service UP/DOWN summary |
| `/api/services/registry` | GET | API Gateway | Discovered instances | `200` registry snapshot |
| `/api/auth/login` | POST | Auth | Issue JWT | `200` token + user |
| `/api/auth/register` | POST | Auth | Public signup (closed) | `403` invitation required |
| `/api/auth/me` | GET | Auth | Current user | `200` user summary |
| `/api/auth/refresh` | POST | Auth | Re-issue JWT | `200` new token |
| `/api/auth/logout` | POST | Auth | Revoke token version | `204` |
| `/api/members` | GET | Member | List members | `200` array (STAFF/ADMIN) |
| `/api/members` | POST | Member | Create member | `201` member |
| `/api/members/{id}` | GET | Member | Get member | `200` / `404` |
| `/api/members/{id}` | PUT | Member | Update member | `200` / `403` / `404` |
| `/api/members/{id}` | DELETE | Member | Delete member | `204` (ADMIN) |
| `/api/trainers` | GET | Trainer | List trainers | `200` array |
| `/api/plans` | GET | Subscription | List plans | `200` array (public) |
| `/api/members/{id}/subscriptions` | GET | Member → Subscription | Member subscriptions | `200` array |
| `/api/members/{id}/subscriptions` | POST | Member → Subscription | Assign plan (desk) | `201` subscription |

## Common status codes for Postman

| Scenario | Status |
|---|---|
| Valid login | `200` |
| Invalid login | `401` |
| Missing/invalid JWT | `401` |
| Insufficient role | `403` |
| Successful create | `201` |
| Successful read | `200` |
| Invalid body | `400` |
| Missing resource | `404` |
| Downstream service down | `503` |

Demo admin: `admin@pulsefit.com` / `Admin@PulseFit2026` (local bootstrap only).

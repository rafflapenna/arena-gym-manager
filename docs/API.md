# API Reference

Base URL: `http://localhost:8080/api/v1`

All responses use `Content-Type: application/json`.
Errors follow [RFC 9457 Problem Detail](https://www.rfc-editor.org/rfc/rfc9457).

All endpoints require a JWT (`Authorization: Bearer <token>`) **except** `/auth/**` and `/actuator/health`.

---

## Authentication & Roles

### Roles

| Role | Manages |
|---|---|
| `ADMIN` | only gym managers and gyms (NOT members/subscriptions/payments) |
| `GYM_MANAGER` | members + staff + trainers of their gym (formerly `INSTRUCTOR`) |
| `STAFF` | members of their gym |
| `TRAINER` | only `/trainer/**` — their own courses and course attendance |
| `MEMBER` | only `/me/**` |

### POST /auth/login

```json
{ "username": "user@example.com", "password": "secret" }
```
**200 OK** → `{ "token": "...", "role": "STAFF", "username": "...", "mustChangePassword": false, ... }`
**401 Unauthorized** — bad credentials (identical for an unknown username — anti user-enumeration).

**429 Too Many Requests** — progressive login throttling (counts only *failed* attempts, so a
gym's shared/NAT'd IP is never blocked by its own successful logins): per account+IP, the first
3 failures are free, then a doubling block (30s → 60s → 120s → ... capped at 15 min; forgotten
after 15 min with no failures; a successful login resets it); per IP across all accounts, 30
failures in 10 minutes blocks that IP for 10 minutes; per account across any IP, 100 consecutive
failures blocks the account for 15 minutes. The response carries a `Retry-After` header (seconds)
and a `retryAfterSeconds` property on the ProblemDetail body for the FE to drive its own
countdown/retry — both always reflect the real remaining time.

### POST /auth/set-password — set/reset password via one-time token

```json
{ "token": "<invite-token>", "newPassword": "newSecret" }
```
**200 OK** → returns a full auth response (auto-login). **400 Bad Request** — invalid/expired/used token.

### POST /auth/forgot-password — self-service recovery (public)

```json
{ "email": "user@example.com" }
```
**Always 204 No Content** (anti user-enumeration). If the email belongs to a non-ADMIN active user, an email with a `/set-password?token=...` link is sent. ADMIN / unknown / inactive emails are silently ignored.

---

## Gyms (ADMIN only)

### GymRequest / GymResponse

| Field | Type | Notes |
|---|---|---|
| `name` | `String` | required |
| `address`, `city`, `postalCode`, `province`, `phone`, `vatNumber`, `contactEmail` | `String` | optional |
| `id`, `createdAt` | — | response only |
| `instructorId`, `instructorEmail` | — | response only (assigned instructor, if any) |
| `staffCount`, `memberCount` | `Number` | response only |

| Method | Endpoint | Response |
|---|---|---|
| `POST` | `/gyms` | 201 GymResponse |
| `GET` | `/gyms` | 200 GymResponse[] |
| `GET` | `/gyms/{id}` | 200 / 404 |
| `PUT` | `/gyms/{id}` | 200 GymResponse |
| `DELETE` | `/gyms/{id}` | 204 / **409** if the gym still has linked users or members |

---

## Users (account management)

Creation follows the role matrix: ADMIN→GYM_MANAGER, GYM_MANAGER→STAFF|TRAINER, STAFF→MEMBER (else **403**).

### POST /users/instructors — ADMIN creates a gym manager (GYM_MANAGER)

```json
{ "email": "manager@gym.com", "firstName": "Mario", "lastName": "Rossi", "gymId": 1 }
```
The gym must already exist (**404** otherwise) and must not already have a gym manager (**409** otherwise). Returns the user + an invite link. The new account sets its password via the emailed `/set-password` token.

### POST /users/staff — GYM_MANAGER creates staff in their own gym

```json
{ "email": "staff@gym.com", "firstName": "Lucia", "lastName": "Bianchi" }
```

### POST /users/trainers — GYM_MANAGER creates a course trainer (TRAINER) in their own gym

```json
{ "email": "trainer@gym.com", "firstName": "Anna", "lastName": "Verdi" }
```
`GET /users/trainers` lists the gym's trainers. A TRAINER is assigned to a course via `trainerId` on `POST/PUT /courses`.

### POST /users/{id}/reset-password — admin/gym manager re-issues an invite link (per role scope).

> Member login accounts are created via `POST /members/{id}/account` (STAFF/GYM_MANAGER only — ADMIN is excluded).

---

## Objects

### PersonalInfo

| Field         | Type     | Description                                     |
|---------------|----------|-------------------------------------------------|
| `firstName`   | `String` | First name                                      |
| `lastName`    | `String` | Last name                                       |
| `email`       | `String` | Email address (unique)                          |
| `phone`       | `String` | Phone number (optional)                         |
| `birthDate`   | `String` | ISO 8601 date: `YYYY-MM-DD`                     |
| `birthPlace`  | `String` | City of birth (optional)                        |
| `fiscalCode`  | `String` | Fiscal code, max 16 chars (optional)            |
| `gender`      | `String` | `M` or `F` (optional)                          |
| `address`     | `String` | Street address (optional)                       |
| `city`        | `String` | City (optional)                                 |
| `postalCode`  | `String` | Postal code (optional)                          |
| `province`    | `String` | Province code, max 5 chars (optional)           |
| `country`     | `String` | Country. Default: `Italia`                      |
| `beltGrade`   | `Lookup` | Belt grade (optional, null if not assigned)     |

### Member

| Field              | Type          | Description                          |
|--------------------|---------------|--------------------------------------|
| `id`               | `Long`        | Unique identifier (auto-generated)   |
| `personalInfo`     | `PersonalInfo`| Personal and contact data            |
| `registrationDate` | `String`      | ISO 8601 date: `YYYY-MM-DD`          |
| `active`           | `Boolean`     | `true` = active member               |
| `notes`            | `String`      | Free-text notes (optional)           |
| `createdAt`        | `String`      | ISO 8601 datetime                    |
| `updatedAt`        | `String`      | ISO 8601 datetime, nullable          |

### Lookup

Used for subscription types, statuses, payment methods, and belt grades.

| Field    | Type      | Description                                      |
|----------|-----------|--------------------------------------------------|
| `id`     | `Long`    | Unique identifier                                |
| `code`   | `String`  | Internal code (e.g. `MONTHLY`, `CASH`, `WHITE`) |
| `label`  | `String`  | Human-readable label                             |
| `months` | `Integer` | Duration in months (only for subscription types) |

### Subscription

| Field           | Type     | Description                                   |
|-----------------|----------|-----------------------------------------------|
| `id`            | `Long`   | Unique identifier                             |
| `memberId`      | `Long`   | Associated member ID                          |
| `memberFullName`| `String` | Member full name                              |
| `type`          | `Lookup` | Subscription type (MONTHLY, QUARTERLY…)       |
| `startDate`     | `String` | ISO 8601 date                                 |
| `endDate`       | `String` | ISO 8601 date (auto-calculated)               |
| `price`         | `Number` | Amount paid                                   |
| `status`        | `Lookup` | Status (ACTIVE, EXPIRED, CANCELLED)           |
| `notes`         | `String` | Free-text notes (optional)                    |
| `createdAt`     | `String` | ISO 8601 datetime                             |

### Payment

| Field           | Type     | Description                        |
|-----------------|----------|------------------------------------|
| `id`            | `Long`   | Unique identifier                  |
| `memberId`      | `Long`   | Associated member ID               |
| `memberFullName`| `String` | Member full name                   |
| `subscriptionId`| `Long`   | Associated subscription ID         |
| `amount`        | `Number` | Amount paid                        |
| `paymentDate`   | `String` | ISO 8601 date                      |
| `paymentMethod` | `Lookup` | Payment method (CASH…)             |
| `description`   | `String` | Free-text description (optional)   |
| `createdAt`     | `String` | ISO 8601 datetime                  |

---

## Members

### GET /members — List members

Returns all members as a plain array, with optional filtering and search.

**Query Parameters**

| Parameter | Type      | Default    | Description                                           |
|-----------|-----------|------------|-------------------------------------------------------|
| `q`       | `String`  | —          | Search across firstName, lastName, email, phone       |
| `active`  | `Boolean` | —          | Filter by status. Omit for all                        |
| `page`    | `Integer` | `0`        | Page index (zero-based)                               |
| `size`    | `Integer` | `20`       | Items per page                                        |
| `sortBy`  | `String`  | `lastName` | Sort field                                            |
| `sortDir` | `String`  | `asc`      | `asc` or `desc`                                       |

**Response — 200 OK**
```json
[
  {
    "id": 1,
    "personalInfo": {
      "firstName": "Mario",
      "lastName": "Rossi",
      "email": "mario.rossi@example.com",
      "phone": "3331234567",
      "birthDate": "1990-05-15",
      "birthPlace": "Roma",
      "fiscalCode": "RSSMRO90E15H501Z",
      "gender": "M",
      "address": "Via Roma 1",
      "city": "Milano",
      "postalCode": "20100",
      "province": "MI",
      "country": "Italia",
      "beltGrade": { "id": 1, "code": "WHITE", "label": "White" }
    },
    "registrationDate": "2024-01-10",
    "active": true,
    "notes": null,
    "createdAt": "2024-01-10T09:00:00",
    "updatedAt": null
  }
]
```

---

### GET /members/{id} — Get member by ID

**Response — 200 OK** — member object  
**Response — 404 Not Found**

---

### POST /members — Create a member

**Request Body**

```json
{
  "personalInfo": {
    "firstName": "Mario",
    "lastName": "Rossi",
    "email": "mario.rossi@example.com",
    "phone": "3331234567",
    "birthDate": "1990-05-15",
    "birthPlace": "Roma",
    "fiscalCode": "RSSMRO90E15H501Z",
    "gender": "M",
    "address": "Via Roma 1",
    "city": "Milano",
    "postalCode": "20100",
    "province": "MI",
    "country": "Italia",
    "beltGradeId": 1
  },
  "registrationDate": "2024-01-10",
  "notes": "Optional notes"
}
```

**`personalInfo` field constraints**

| Field        | Required | Constraints                                  |
|--------------|----------|----------------------------------------------|
| `firstName`  | Yes      | Max 100 characters                           |
| `lastName`   | Yes      | Max 100 characters                           |
| `email`      | Yes      | Valid email, max 150 chars, unique           |
| `phone`      | No       | Max 20 characters                            |
| `birthDate`  | No       | ISO date, must be in the past                |
| `birthPlace` | No       | Max 100 characters                           |
| `fiscalCode` | No       | Max 16 characters                            |
| `gender`     | No       | Max 1 character (`M` or `F`)                 |
| `address`    | No       | Max 255 characters                           |
| `city`       | No       | Max 100 characters                           |
| `postalCode` | No       | Max 10 characters                            |
| `province`   | No       | Max 5 characters                             |
| `country`    | No       | Max 100 characters. Default: `Italia`        |
| `beltGradeId`| No       | ID from `GET /belt-grades`                   |

**Root-level fields**

| Field              | Required | Description                          |
|--------------------|----------|--------------------------------------|
| `registrationDate` | No       | ISO date. Defaults to today          |
| `notes`            | No       | Free text                            |

**Response — 201 Created** — created member object  
**Response — 409 Conflict** — duplicate email  
**Response — 422 Unprocessable Entity** — validation error

---

### PUT /members/{id} — Full update

Replaces all updatable fields. Same body and validation as POST.

**Response — 200 OK** — updated member object

---

### PATCH /members/{id}/activate — Activate a member
### PATCH /members/{id}/deactivate — Deactivate a member

**Response — 200 OK** — updated member object

---

### DELETE /members/{id} — Delete a member

**Response — 204 No Content**  
**Response — 404 Not Found**

---

## Subscriptions

### POST /members/{memberId}/subscriptions — Create a subscription

**Request Body**

| Field       | Type     | Required | Description                                    |
|-------------|----------|----------|------------------------------------------------|
| `typeId`    | `Long`   | Yes      | ID of subscription type (from lookup endpoint) |
| `startDate` | `String` | Yes      | ISO date                                       |
| `price`     | `Number` | Yes      | Amount (min 0.01)                              |
| `notes`     | `String` | No       | Free text                                      |

```json
{
  "typeId": 1,
  "startDate": "2024-01-10",
  "price": 50.00,
  "notes": "First subscription"
}
```

**Response — 201 Created**
```json
{
  "id": 1,
  "memberId": 1,
  "memberFullName": "Mario Rossi",
  "type": { "id": 1, "code": "MONTHLY", "label": "Monthly", "months": 1 },
  "startDate": "2024-01-10",
  "endDate": "2024-02-10",
  "price": 50.00,
  "status": { "id": 1, "code": "ACTIVE", "label": "Active" },
  "notes": "First subscription",
  "createdAt": "2024-01-10T09:00:00"
}
```

---

### GET /members/{memberId}/subscriptions — List member subscriptions

**Response — 200 OK** — array of subscription objects

---

### GET /subscriptions — List all subscriptions

**Query Parameters**

| Parameter | Type     | Description                            |
|-----------|----------|----------------------------------------|
| `status`  | `String` | Filter by status code (e.g. `ACTIVE`)  |

```
GET /api/v1/subscriptions?status=ACTIVE
```

**Response — 200 OK** — array of subscription objects

---

### GET /subscriptions/expiring — Subscriptions expiring soon

| Parameter | Type      | Default | Description             |
|-----------|-----------|---------|-------------------------|
| `days`    | `Integer` | `7`     | Expiring within N days  |

```
GET /api/v1/subscriptions/expiring?days=7
```

**Response — 200 OK** — array of subscription objects

---

### PATCH /subscriptions/{id}/cancel — Cancel a subscription

**Response — 200 OK** — updated subscription with status CANCELLED

---

## Payments

### POST /subscriptions/{subscriptionId}/payments — Register a payment

**Request Body**

| Field             | Type     | Required | Description                                       |
|-------------------|----------|----------|---------------------------------------------------|
| `amount`          | `Number` | Yes      | Amount paid (min 0.01)                            |
| `paymentDate`     | `String` | Yes      | ISO date                                          |
| `paymentMethodId` | `Long`   | Yes      | ID of payment method (from lookup endpoint)       |
| `description`     | `String` | No       | Free text                                         |

```json
{
  "amount": 50.00,
  "paymentDate": "2024-01-10",
  "paymentMethodId": 1,
  "description": "January fee"
}
```

**Response — 201 Created**
```json
{
  "id": 1,
  "memberId": 1,
  "memberFullName": "Mario Rossi",
  "subscriptionId": 1,
  "amount": 50.00,
  "paymentDate": "2024-01-10",
  "paymentMethod": { "id": 1, "code": "CASH", "label": "Cash" },
  "description": "January fee",
  "createdAt": "2024-01-10T09:00:00"
}
```

---

### GET /subscriptions/{subscriptionId}/payments — Payments for a subscription

**Response — 200 OK** — array of payment objects, ordered by date desc

---

### GET /members/{memberId}/payments — All payments for a member

**Response — 200 OK** — array of payment objects, ordered by date desc

---

## Dashboard

### GET /dashboard/stats — Aggregate gym statistics

Returns aggregate data for the **current month**, scoped to the authenticated user's gym. Powers the dashboard cards ("Monthly revenue", "New members"). Restricted to **STAFF/GYM_MANAGER** (ADMIN and MEMBER → 403). Both metrics are aggregated DB-side (a single `SUM` and a single `COUNT`); the gym is derived from the token.

**Request** — no parameters (window is the current calendar month; gym is taken from the JWT).

**Response — 200 OK**

```json
{
  "monthlyRevenue": 350.00,
  "newMembersThisMonth": 4
}
```

- `monthlyRevenue` (number) — sum of payment amounts with `paymentDate` in the current month for the caller's gym. `0` when there are no payments (never `null`).
- `newMembersThisMonth` (integer) — count of members of the caller's gym whose `registrationDate` falls in the current month.

---

## Attendance / Check-in

One check-in per member per day (idempotent: a second registration on the same day returns **200** with `alreadyCheckedIn: true` instead of **201**). Eligibility (active subscription) produces **non-blocking** `warnings` for operators; it only blocks the member's self check-in. `source` ∈ `RECEPTION` | `SELF` | `QR`.

| Method | Path | Notes | Roles |
|---|---|---|---|
| POST | `/members/{id}/check-ins` | reception check-in | STAFF, GYM_MANAGER |
| GET | `/members/{id}/check-ins` | member attendance history | STAFF, GYM_MANAGER |
| GET | `/check-ins?date=YYYY-MM-DD` | day attendance list (defaults to today) | STAFF, GYM_MANAGER |
| POST | `/me/check-ins` | self check-in (blocked without an active subscription) | MEMBER |
| GET | `/me/check-ins` | own attendance | MEMBER |

**Response** (`CheckInResponseDTO`): `{ id, memberId, memberFullName, checkInAt, source, outcome: "OK"|"WARNING", warnings: [...], alreadyCheckedIn }`.

### QR check-in (member identity card)

Every member has an opaque token (256-bit base64url, generated lazily on first read) behind their personal QR; the FE renders the QR image, the BE only exchanges the string.

| Method | Path | Notes | Roles |
|---|---|---|---|
| POST | `/check-ins/qr` | body `{ "token": "..." }` → registers the entry; **404** for unknown or cross-gym tokens (indistinguishable), **422** blank token | STAFF, GYM_MANAGER |
| GET | `/me/qr` | `{ "token": "..." }` — the member displays their own QR | MEMBER |
| GET | `/members/{id}/qr` | member's token, e.g. to print the card | STAFF, GYM_MANAGER |
| POST | `/members/{id}/qr/rotate` | regenerates the token: the previous QR stops working immediately (lost card) | STAFF, GYM_MANAGER |

---

## Free trials / Leads

Tracks who's doing a free trial before signing up (roadmap #8), with one-click conversion to a member. Unpaginated list (low volume expected). Since 2026-10-01 a **member** can also report a friend's trial from `/me/leads` ("prove inserite dai soci") — see the dedicated section below.

### Lead

| Field             | Type     | Description                                                                 |
|-------------------|----------|------------------------------------------------------------------------------|
| `id`              | `Long`   | Unique identifier                                                            |
| `firstName`       | `String` | First name                                                                   |
| `lastName`        | `String` | Last name                                                                    |
| `birthDate`       | `String` | ISO 8601 date, **nullable** (blank only for a member-reported trial, until the reception fills it in) |
| `phone`           | `String` | Phone number                                                                 |
| `email`           | `String` | Email (optional)                                                             |
| `source`          | `String` | Free-text provenance (optional)                                             |
| `interestCourse`  | `{id,name}` or `null` | Course of interest (optional)                                   |
| `trialDate`       | `String` | Trial date, ISO 8601 (optional)                                             |
| `status`          | `String` | `REQUESTED`\|`NEW`\|`TRIAL_SCHEDULED`\|`TRIED`\|`CONVERTED`\|`LOST`          |
| `origin`          | `String` | `STAFF` (logged at the reception) or `MEMBER` (reported by a member)        |
| `notes`           | `String` | Free notes (optional)                                                       |
| `createdAt`       | `String` | ISO 8601 datetime                                                            |
| `convertedMemberId` | `Long` or `null` | Logical reference to the member the conversion created              |
| `referredBy`      | `{id,name}` or `null` | The member who brought this prospect in (whoever reported it, or whoever the reception named) |
| `matchedMemberId` | `Long` or `null` | **Staff only.** Set if the phone matches an existing member of the gym |

### GET /leads — List trials

**Roles**: STAFF, GYM_MANAGER.

**Query string**: `?status=` (one of the values above; invalid value → **400**), `?origin=STAFF|MEMBER` (invalid value → **400**), `?q=` (free-text search on name/phone/email).

**Response — 200 OK** — array of `Lead`, ordered by `createdAt` desc.

### POST /leads — Create a trial (reception)

**Roles**: STAFF, GYM_MANAGER.

**Request**: `firstName`, `lastName`, `phone` required; `birthDate` **required** on this path (`@NotNull @Past`); `email`, `source`, `courseId`, `trialDate`, `notes`, `referredByMemberId` optional. `status` optional — if omitted it's derived from `trialDate` (absent → `NEW`; future → `TRIAL_SCHEDULED`; today or past → `TRIED`); explicitly passing `REQUESTED` → **422** (reserved for member-reported trials).

**Response — 201 Created** — `Lead`.

**Errors**: **404** if `courseId`/`referredByMemberId` belong to another gym; **422** on missing required fields, a future `birthDate`, or `status=REQUESTED`.

### GET/PUT/DELETE /leads/{id}

**Roles**: STAFF, GYM_MANAGER. **404** for a trial belonging to another gym (anti-IDOR).

`PUT` takes the same body as `POST`. For an `origin=MEMBER` trial, `referredByMemberId` in the body is **ignored**: the referrer stays the member who reported it. `status=REQUESTED` in the body is only accepted if the trial is already `REQUESTED` (no status change); use `confirm`/`decline` to move it out of `REQUESTED`, not `PUT`.

### POST /leads/{id}/confirm — Reception confirms a member-reported trial

**Roles**: STAFF, GYM_MANAGER.

**Request**: `{ "trialDate": "YYYY-MM-DD" }` (required, today or in the future).

**Response — 200 OK** — `Lead` with `status: "TRIAL_SCHEDULED"`.

**Errors**: **409** `LEAD_NOT_REQUESTED` if the trial is no longer `REQUESTED`; **422** on a missing or past `trialDate`.

### POST /leads/{id}/decline — Reception declines a member-reported trial

**Roles**: STAFF, GYM_MANAGER. No body.

**Response — 200 OK** — `Lead` with `status: "LOST"`.

**Errors**: **409** `LEAD_NOT_REQUESTED` if the trial is no longer `REQUESTED`.

### POST /leads/{id}/convert — Creates the member from the trial

**Roles**: STAFF, GYM_MANAGER.

**Request**: `{ "email": "...", "birthDate"?: "YYYY-MM-DD" }`. `birthDate` is only required in the body when the trial doesn't already have one of its own (a member-reported trial the reception hasn't completed yet); otherwise it's ignored and the lead's own value is kept.

**Response — 200 OK**
```json
{
  "lead": { "...": "Lead with status CONVERTED" },
  "member": { "...": "newly created Member" }
}
```

**Behavior**:
1. If the trial is already `CONVERTED` → identical response, **no second member created** (idempotent replay — the body's `email`/`birthDate` are ignored).
2. If the trial is still `REQUESTED` (not confirmed by the reception) → **409** `LEAD_NOT_CONFIRMED`.
3. Otherwise creates the `Member` by reusing `MemberService.create` (same validation/normalization) and links the lead, in the same transaction.

**Errors**: **404** trial not found or belonging to another gym; **409** `LEAD_NOT_CONFIRMED` (trial still `REQUESTED`), or a plain 409 if the email already belongs to another member; **422** `errors.email` on a missing/malformed email, `errors.birthDate` if it's missing on both the lead and the body.

---

## Member-reported trials (`/me/leads`)

Since 2026-10-01 a **member** can report a friend's trial without going through the reception. The trial starts as `REQUESTED`, `origin: "MEMBER"`, with `referredBy` **forced to the logged-in member** (never from the body) — it stays non-convertible until the reception confirms or declines it (section above).

### MeLead (member's view — deliberately narrower than `Lead`)

| Field            | Type     | Description                                                              |
|------------------|----------|---------------------------------------------------------------------------|
| `id`             | `Long`   | Unique identifier                                                        |
| `firstName`      | `String` | First name of the reported friend                                       |
| `lastName`       | `String` | Last name of the reported friend                                        |
| `status`         | `String` | `REQUESTED`\|`NEW`\|`TRIAL_SCHEDULED`\|`TRIED`\|`CONVERTED`\|`LOST`      |
| `trialDate`      | `String` or `null` | Trial date, ISO 8601                                            |
| `interestCourse` | `{id,name}` or `null` | Course of interest                                              |
| `rewardEarned`   | `Boolean`| `true` if bringing this person in has already earned the "porta un amico" discount (stays `true` even after the reward becomes `USED`) |
| `createdAt`      | `String` | ISO 8601 datetime                                                        |

No `phone`/`email`/`notes`/`matchedMemberId`/`convertedMemberId`/`referredBy` — the friend's contact details never round-trip onto the member's own screen.

### POST /me/leads — Report a friend's trial

**Roles**: MEMBER.

**Request**: `firstName`, `lastName`, `phone` required; `trialDate` (today or in the future) and `courseId` optional. No `birthDate`/`email`: the reception fills those in at confirm/convert time. Any extra body field (e.g. `referredByMemberId`) is ignored — the referrer is always the authenticated member.

**Response — 201 Created** — `MeLead`, always with `status: "REQUESTED"`.

**Validation order**:
1. **422** `errors.phone` = *"Non puoi inserire il tuo numero di telefono"* (you can't report your own number) — if the phone matches the member's own.
2. **409** `LEAD_PENDING_LIMIT` — if the member already has 5 open trials (`REQUESTED`/`NEW`/`TRIAL_SCHEDULED`) pending, counting only the ones they reported (`origin=MEMBER`).
3. **409** `LEAD_DUPLICATE_PHONE` — if the same number is already on an open trial of the gym (`REQUESTED`/`NEW`/`TRIAL_SCHEDULED`/`TRIED`, any origin). Comparison normalizes the number (digits only, `00`→`+`, `+39` stripped), so `"+39 333 1234567"` and `"3331234567"` are the same number.
4. **404** — if `courseId` belongs to another gym.
5. **No error** if the phone matches an **existing** member (other than the caller): the trial is still created (201); only staff see the match (`matchedMemberId` on `GET /leads`) — anti user-enumeration, the member gets no hint either way.

### GET /me/leads — "My friends"

**Roles**: MEMBER.

**Response — 200 OK** — array of `MeLead`, ordered by `createdAt` desc. Includes **every** trial naming the member as `referredBy`, whether they reported it themselves or the reception logged it with them as referrer.

---

## Courses, disciplines and packages (overview)

Per-gym catalog (STAFF/GYM_MANAGER): `GET/POST/PUT/DELETE /disciplines`, `/courses` (with `disciplineId`, `level`, optional `trainerId`), `/packages` (one or more courses, own price/duration; optional `packageId` on `POST /subscriptions`). TRAINER area: `GET /trainer/courses` (own courses only), `GET/POST /trainer/courses/{id}/attendance` (today's course-scoped attendance). Members see their gym's active courses via `GET /me/courses`.

---

## Lookup Tables

Lookup tables are pre-seeded at startup and can be extended at runtime without restarting the backend.

### GET /subscription-types

Returns all available subscription types.

**Response — 200 OK**
```json
[
  { "id": 1, "code": "MONTHLY",   "label": "Monthly",   "months": 1  },
  { "id": 2, "code": "QUARTERLY", "label": "Quarterly", "months": 3  },
  { "id": 3, "code": "ANNUAL",    "label": "Annual",    "months": 12 }
]
```

### POST /subscription-types — Add a new subscription type

```json
{ "code": "BIENNIAL", "label": "Biennial", "months": 24 }
```

**Response — 201 Created** — created lookup object  
**Response — 409 Conflict** — code already exists

---

### GET /subscription-statuses

```json
[
  { "id": 1, "code": "ACTIVE",    "label": "Active"    },
  { "id": 2, "code": "EXPIRED",   "label": "Expired"   },
  { "id": 3, "code": "CANCELLED", "label": "Cancelled" }
]
```

---

### GET /payment-methods

```json
[
  { "id": 1, "code": "CASH", "label": "Cash" }
]
```

### POST /payment-methods — Add a new payment method

```json
{ "code": "CARD", "label": "Credit Card" }
```

**Response — 201 Created** — created lookup object  
**Response — 409 Conflict** — code already exists

---

### GET /belt-grades

Returns all available belt grades (pre-seeded: WHITE → BLACK).

**Response — 200 OK**
```json
[
  { "id": 1, "code": "WHITE",  "label": "White"  },
  { "id": 2, "code": "YELLOW", "label": "Yellow" },
  { "id": 3, "code": "ORANGE", "label": "Orange" },
  { "id": 4, "code": "GREEN",  "label": "Green"  },
  { "id": 5, "code": "BLUE",   "label": "Blue"   },
  { "id": 6, "code": "BROWN",  "label": "Brown"  },
  { "id": 7, "code": "BLACK",  "label": "Black"  }
]
```

### POST /belt-grades — Add a new belt grade

```json
{ "code": "RED", "label": "Red" }
```

**Response — 201 Created** — created lookup object  
**Response — 409 Conflict** — code already exists

---

## HTTP Status Codes

| Status | Meaning                                  |
|--------|------------------------------------------|
| 200    | OK — request succeeded                   |
| 201    | Created — resource created               |
| 204    | No Content — deletion/recovery succeeded |
| 400    | Bad Request — invalid/expired token      |
| 401    | Unauthorized — missing/invalid JWT       |
| 403    | Forbidden — role not allowed             |
| 404    | Not Found — resource does not exist      |
| 409    | Conflict — duplicate / linked dependency |
| 422    | Unprocessable Entity — validation failed |
| 500    | Internal Server Error                    |

> Note: members, subscriptions, payments, check-ins and course-catalog routes are restricted to **STAFF** and **GYM_MANAGER** (ADMIN is excluded). MEMBER accounts can only access `/me/**`, TRAINER accounts only `/trainer/**`.

---

## curl Examples

```bash
# List all members
curl http://localhost:8080/api/v1/members

# Search active members
curl "http://localhost:8080/api/v1/members?q=mario&active=true"

# Create a member
curl -X POST http://localhost:8080/api/v1/members \
  -H "Content-Type: application/json" \
  -d '{
    "personalInfo": {
      "firstName": "Mario",
      "lastName": "Rossi",
      "email": "mario@example.com",
      "beltGradeId": 1
    }
  }'

# Get belt grades
curl http://localhost:8080/api/v1/belt-grades

# Get subscription types
curl http://localhost:8080/api/v1/subscription-types

# Create a subscription (typeId=1 = MONTHLY)
curl -X POST http://localhost:8080/api/v1/members/1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"typeId":1,"startDate":"2024-01-10","price":50.00}'

# Register a payment (paymentMethodId=1 = CASH)
curl -X POST http://localhost:8080/api/v1/subscriptions/1/payments \
  -H "Content-Type: application/json" \
  -d '{"amount":50.00,"paymentDate":"2024-01-10","paymentMethodId":1}'

# List subscriptions expiring in 7 days
curl http://localhost:8080/api/v1/subscriptions/expiring?days=7

# Cancel a subscription
curl -X PATCH http://localhost:8080/api/v1/subscriptions/1/cancel

# Add a new payment method
curl -X POST http://localhost:8080/api/v1/payment-methods \
  -H "Content-Type: application/json" \
  -d '{"code":"CARD","label":"Credit Card"}'

# Add a new belt grade
curl -X POST http://localhost:8080/api/v1/belt-grades \
  -H "Content-Type: application/json" \
  -d '{"code":"RED","label":"Red"}'
```

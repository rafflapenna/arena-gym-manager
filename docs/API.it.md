# API — Riferimento

URL base: `http://localhost:8080/api/v1`

Tutte le risposte usano `Content-Type: application/json`.
Gli errori seguono il formato [RFC 9457 Problem Detail](https://www.rfc-editor.org/rfc/rfc9457).

Tutti gli endpoint richiedono un JWT (`Authorization: Bearer <token>`) **tranne** `/auth/**` e `/actuator/health`.

---

## Autenticazione e Ruoli

### Ruoli

| Ruolo | Gestisce |
|---|---|
| `ADMIN` | solo capi palestra e palestre (NON membri/abbonamenti/pagamenti) |
| `GYM_MANAGER` | membri + staff + istruttori della propria palestra (capo palestra; ex `INSTRUCTOR`) |
| `STAFF` | membri della propria palestra |
| `TRAINER` | solo `/trainer/**` — i propri corsi e le relative presenze (istruttore dei corsi) |
| `MEMBER` | solo `/me/**` |

### POST /auth/login

```json
{ "username": "utente@esempio.com", "password": "segreto" }
```
**200 OK** → `{ "token": "...", "role": "STAFF", "username": "...", "mustChangePassword": false, ... }`
**401 Unauthorized** — credenziali errate (identico per uno username inesistente — anti user-enumeration).

**429 Too Many Requests** — throttling progressivo del login (conta solo i tentativi *falliti*,
quindi l'IP condiviso/NAT'd di una palestra non viene mai bloccato dai propri login riusciti):
per account+IP i primi 3 fallimenti sono liberi, poi un blocco che raddoppia (30s → 60s → 120s →
... cap a 15 minuti; si dimentica dopo 15 minuti senza fallimenti; un login riuscito lo azzera);
per IP su tutti gli account, 30 fallimenti in 10 minuti bloccano quell'IP per 10 minuti; per
account su qualsiasi IP, 100 fallimenti consecutivi bloccano l'account per 15 minuti. La risposta
porta l'header `Retry-After` (secondi) e la proprietà `retryAfterSeconds` sul body ProblemDetail
perché il FE guidi il proprio countdown/retry — entrambi riflettono sempre il tempo reale rimasto.

### POST /auth/set-password — imposta/reimposta la password tramite token monouso

```json
{ "token": "<token-invito>", "newPassword": "nuovaPwd" }
```
**200 OK** → restituisce una risposta di auth completa (login automatico). **400 Bad Request** — token non valido/scaduto/già usato.

### POST /auth/forgot-password — recupero self-service (pubblico)

```json
{ "email": "utente@esempio.com" }
```
**Sempre 204 No Content** (anti user-enumeration). Se l'email appartiene a un utente attivo non-ADMIN, viene inviata una mail con un link `/set-password?token=...`. Email ADMIN / inesistenti / inattive vengono ignorate silenziosamente.

---

## Palestre (solo ADMIN)

### GymRequest / GymResponse

| Campo | Tipo | Note |
|---|---|---|
| `name` | `String` | obbligatorio |
| `address`, `city`, `postalCode`, `province`, `phone`, `vatNumber`, `contactEmail` | `String` | opzionali |
| `id`, `createdAt` | — | solo risposta |
| `instructorId`, `instructorEmail` | — | solo risposta (istruttore assegnato, se presente) |
| `staffCount`, `memberCount` | `Number` | solo risposta |

| Metodo | Endpoint | Risposta |
|---|---|---|
| `POST` | `/gyms` | 201 GymResponse |
| `GET` | `/gyms` | 200 GymResponse[] |
| `GET` | `/gyms/{id}` | 200 / 404 |
| `PUT` | `/gyms/{id}` | 200 GymResponse |
| `DELETE` | `/gyms/{id}` | 204 / **409** se la palestra ha ancora utenti o membri collegati |

---

## Utenti (gestione account)

La creazione segue la matrice dei ruoli: ADMIN→GYM_MANAGER, GYM_MANAGER→STAFF|TRAINER, STAFF→MEMBER (altrimenti **403**).

### POST /users/instructors — l'ADMIN crea un capo palestra (GYM_MANAGER)

```json
{ "email": "gestore@palestra.com", "firstName": "Mario", "lastName": "Rossi", "gymId": 1 }
```
La palestra deve già esistere (altrimenti **404**) e non deve già avere un capo palestra (altrimenti **409**). Restituisce l'utente + un link di invito. Il nuovo account imposta la password tramite il token `/set-password` ricevuto via email.

### POST /users/staff — il GYM_MANAGER crea staff nella propria palestra

```json
{ "email": "staff@palestra.com", "firstName": "Lucia", "lastName": "Bianchi" }
```

### POST /users/trainers — il GYM_MANAGER crea un istruttore dei corsi (TRAINER) nella propria palestra

```json
{ "email": "istruttore@palestra.com", "firstName": "Anna", "lastName": "Verdi" }
```
`GET /users/trainers` elenca gli istruttori della propria palestra. Un TRAINER si assegna a un corso tramite `trainerId` in `POST/PUT /courses`.

### POST /users/{id}/reset-password — admin/capo palestra rigenera un link di invito (nel proprio ambito).

> Gli account di login dei membri si creano con `POST /members/{id}/account` (solo STAFF/GYM_MANAGER — l'ADMIN è escluso).

---

## Oggetti

### PersonalInfo (Dati anagrafici)

| Campo         | Tipo     | Descrizione                                            |
|---------------|----------|--------------------------------------------------------|
| `firstName`   | `String` | Nome                                                   |
| `lastName`    | `String` | Cognome                                                |
| `email`       | `String` | Indirizzo email (univoco)                              |
| `phone`       | `String` | Numero di telefono (opzionale)                         |
| `birthDate`   | `String` | Data ISO 8601: `YYYY-MM-DD`                            |
| `birthPlace`  | `String` | Luogo di nascita (opzionale)                           |
| `fiscalCode`  | `String` | Codice fiscale, max 16 caratteri (opzionale)           |
| `gender`      | `String` | `M` o `F` (opzionale)                                 |
| `address`     | `String` | Indirizzo (opzionale)                                  |
| `city`        | `String` | Città (opzionale)                                      |
| `postalCode`  | `String` | CAP (opzionale)                                        |
| `province`    | `String` | Sigla provincia, max 5 caratteri (opzionale)           |
| `country`     | `String` | Paese. Default: `Italia`                               |
| `beltGrade`   | `Lookup` | Grado cintura (opzionale, null se non assegnato)       |

### Member (Iscritto)

| Campo              | Tipo          | Descrizione                               |
|--------------------|---------------|-------------------------------------------|
| `id`               | `Long`        | Identificatore univoco (auto-generato)    |
| `personalInfo`     | `PersonalInfo`| Dati anagrafici e di contatto             |
| `registrationDate` | `String`      | Data ISO 8601: `YYYY-MM-DD`               |
| `active`           | `Boolean`     | `true` = iscritto attivo                  |
| `notes`            | `String`      | Note libere (opzionale)                   |
| `createdAt`        | `String`      | Datetime ISO 8601                         |
| `updatedAt`        | `String`      | Datetime ISO 8601, nullable               |

### Lookup

Usato per tipi abbonamento, stati, metodi di pagamento e gradi cintura.

| Campo    | Tipo      | Descrizione                                               |
|----------|-----------|-----------------------------------------------------------|
| `id`     | `Long`    | Identificatore univoco                                    |
| `code`   | `String`  | Codice interno (es. `MONTHLY`, `CASH`, `WHITE`)           |
| `label`  | `String`  | Etichetta leggibile                                       |
| `months` | `Integer` | Durata in mesi (solo per i tipi di abbonamento)           |

### Subscription (Abbonamento)

| Campo           | Tipo     | Descrizione                                    |
|-----------------|----------|------------------------------------------------|
| `id`            | `Long`   | Identificatore univoco                         |
| `memberId`      | `Long`   | ID dell'iscritto associato                     |
| `memberFullName`| `String` | Nome completo dell'iscritto                    |
| `type`          | `Lookup` | Tipo abbonamento (MONTHLY, QUARTERLY…)         |
| `startDate`     | `String` | Data inizio ISO 8601                           |
| `endDate`       | `String` | Data fine ISO 8601 (calcolata automaticamente) |
| `price`         | `Number` | Importo                                        |
| `status`        | `Lookup` | Stato (ACTIVE, EXPIRED, CANCELLED)             |
| `notes`         | `String` | Note libere (opzionale)                        |
| `createdAt`     | `String` | Datetime ISO 8601                              |

### Payment (Pagamento)

| Campo           | Tipo     | Descrizione                              |
|-----------------|----------|------------------------------------------|
| `id`            | `Long`   | Identificatore univoco                   |
| `memberId`      | `Long`   | ID dell'iscritto associato               |
| `memberFullName`| `String` | Nome completo dell'iscritto              |
| `subscriptionId`| `Long`   | ID dell'abbonamento associato            |
| `amount`        | `Number` | Importo pagato                           |
| `paymentDate`   | `String` | Data pagamento ISO 8601                  |
| `paymentMethod` | `Lookup` | Metodo di pagamento (CASH…)              |
| `description`   | `String` | Descrizione libera (opzionale)           |
| `createdAt`     | `String` | Datetime ISO 8601                        |

---

## Iscritti

### GET /members — Lista iscritti

Restituisce tutti gli iscritti come array, con filtri e ricerca opzionali.

**Parametri query**

| Parametro | Tipo      | Default    | Descrizione                                           |
|-----------|-----------|------------|-------------------------------------------------------|
| `q`       | `String`  | —          | Ricerca su firstName, lastName, email, phone          |
| `active`  | `Boolean` | —          | Filtra per stato. Omesso = tutti                      |
| `page`    | `Integer` | `0`        | Indice di pagina (da zero)                            |
| `size`    | `Integer` | `20`       | Elementi per pagina                                   |
| `sortBy`  | `String`  | `lastName` | Campo di ordinamento                                  |
| `sortDir` | `String`  | `asc`      | `asc` o `desc`                                        |

**Risposta — 200 OK**
```json
[
  {
    "id": 1,
    "personalInfo": {
      "firstName": "Mario",
      "lastName": "Rossi",
      "email": "mario.rossi@esempio.com",
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

### GET /members/{id} — Recupera iscritto per ID

**Risposta — 200 OK** — oggetto iscritto  
**Risposta — 404 Not Found**

---

### POST /members — Crea un iscritto

**Corpo della richiesta**

```json
{
  "personalInfo": {
    "firstName": "Mario",
    "lastName": "Rossi",
    "email": "mario.rossi@esempio.com",
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
  "notes": "Note opzionali"
}
```

**Vincoli campi `personalInfo`**

| Campo        | Obbligatorio | Vincoli                                        |
|--------------|--------------|------------------------------------------------|
| `firstName`  | Sì           | Max 100 caratteri                              |
| `lastName`   | Sì           | Max 100 caratteri                              |
| `email`      | Sì           | Formato email valido, max 150 car., univoco    |
| `phone`      | No           | Max 20 caratteri                               |
| `birthDate`  | No           | Data ISO, deve essere nel passato              |
| `birthPlace` | No           | Max 100 caratteri                              |
| `fiscalCode` | No           | Max 16 caratteri                               |
| `gender`     | No           | Max 1 carattere (`M` o `F`)                    |
| `address`    | No           | Max 255 caratteri                              |
| `city`       | No           | Max 100 caratteri                              |
| `postalCode` | No           | Max 10 caratteri                               |
| `province`   | No           | Max 5 caratteri                                |
| `country`    | No           | Max 100 caratteri. Default: `Italia`           |
| `beltGradeId`| No           | ID da `GET /belt-grades`                       |

**Campi radice**

| Campo              | Obbligatorio | Descrizione                           |
|--------------------|--------------|---------------------------------------|
| `registrationDate` | No           | Data ISO. Default: data odierna       |
| `notes`            | No           | Testo libero                          |

**Risposta — 201 Created** — oggetto iscritto creato  
**Risposta — 409 Conflict** — email duplicata  
**Risposta — 422 Unprocessable Entity** — errore di validazione

---

### PUT /members/{id} — Aggiornamento completo

Sostituisce tutti i campi aggiornabili. Stesso corpo e stessa validazione del POST.

**Risposta — 200 OK** — oggetto iscritto aggiornato

---

### PATCH /members/{id}/activate — Attiva un iscritto
### PATCH /members/{id}/deactivate — Disattiva un iscritto

**Risposta — 200 OK** — oggetto iscritto aggiornato

---

### DELETE /members/{id} — Elimina un iscritto

**Risposta — 204 No Content**  
**Risposta — 404 Not Found**

---

## Abbonamenti

### POST /members/{memberId}/subscriptions — Crea un abbonamento

**Corpo della richiesta**

| Campo       | Tipo     | Obbligatorio | Descrizione                                          |
|-------------|----------|--------------|------------------------------------------------------|
| `typeId`    | `Long`   | Sì           | ID del tipo abbonamento (dall'endpoint lookup)       |
| `startDate` | `String` | Sì           | Data inizio ISO                                      |
| `price`     | `Number` | Sì           | Importo (min 0.01)                                   |
| `notes`     | `String` | No           | Testo libero                                         |

```json
{
  "typeId": 1,
  "startDate": "2024-01-10",
  "price": 50.00,
  "notes": "Primo abbonamento"
}
```

**Risposta — 201 Created**
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
  "notes": "Primo abbonamento",
  "createdAt": "2024-01-10T09:00:00"
}
```

---

### GET /members/{memberId}/subscriptions — Lista abbonamenti di un iscritto

**Risposta — 200 OK** — array di abbonamenti

---

### GET /subscriptions — Lista tutti gli abbonamenti

**Parametri query**

| Parametro | Tipo     | Descrizione                                   |
|-----------|----------|-----------------------------------------------|
| `status`  | `String` | Filtra per codice stato (es. `ACTIVE`)        |

```
GET /api/v1/subscriptions?status=ACTIVE
```

**Risposta — 200 OK** — array di abbonamenti

---

### GET /subscriptions/expiring — Abbonamenti in scadenza

| Parametro | Tipo      | Default | Descrizione                            |
|-----------|-----------|---------|----------------------------------------|
| `days`    | `Integer` | `7`     | Abbonamenti in scadenza entro N giorni |

```
GET /api/v1/subscriptions/expiring?days=7
```

**Risposta — 200 OK** — array di abbonamenti

---

### PATCH /subscriptions/{id}/cancel — Cancella un abbonamento

**Risposta — 200 OK** — abbonamento aggiornato con stato CANCELLED

---

## Pagamenti

### POST /subscriptions/{subscriptionId}/payments — Registra un pagamento

**Corpo della richiesta**

| Campo             | Tipo     | Obbligatorio | Descrizione                                          |
|-------------------|----------|--------------|------------------------------------------------------|
| `amount`          | `Number` | Sì           | Importo pagato (min 0.01)                            |
| `paymentDate`     | `String` | Sì           | Data pagamento ISO                                   |
| `paymentMethodId` | `Long`   | Sì           | ID del metodo di pagamento (dall'endpoint lookup)    |
| `description`     | `String` | No           | Testo libero                                         |

```json
{
  "amount": 50.00,
  "paymentDate": "2024-01-10",
  "paymentMethodId": 1,
  "description": "Quota gennaio"
}
```

**Risposta — 201 Created**
```json
{
  "id": 1,
  "memberId": 1,
  "memberFullName": "Mario Rossi",
  "subscriptionId": 1,
  "amount": 50.00,
  "paymentDate": "2024-01-10",
  "paymentMethod": { "id": 1, "code": "CASH", "label": "Cash" },
  "description": "Quota gennaio",
  "createdAt": "2024-01-10T09:00:00"
}
```

---

### GET /subscriptions/{subscriptionId}/payments — Pagamenti di un abbonamento

**Risposta — 200 OK** — array di pagamenti, ordinati per data desc

---

### GET /members/{memberId}/payments — Tutti i pagamenti di un iscritto

**Risposta — 200 OK** — array di pagamenti, ordinati per data desc

---

## Dashboard

### GET /dashboard/stats — Statistiche aggregate della palestra

Ritorna i dati aggregati del **mese corrente** per la palestra dell'utente autenticato. Usato dalle card della dashboard ("Entrate del Mese", "Nuovi Membri"). Riservato a **STAFF/GYM_MANAGER** (ADMIN e MEMBER → 403). Aggregazione fatta interamente in DB (una `SUM` e una `COUNT`), scoping automatico per palestra.

**Richiesta** — nessun parametro (la finestra è il mese solare corrente; la palestra è dedotta dal token).

**Risposta — 200 OK**

```json
{
  "monthlyRevenue": 350.00,
  "newMembersThisMonth": 4
}
```

- `monthlyRevenue` (number) — somma degli importi dei pagamenti con `paymentDate` nel mese corrente per la propria palestra. `0` quando non ci sono pagamenti (mai `null`).
- `newMembersThisMonth` (integer) — numero di iscritti della propria palestra con `registrationDate` nel mese corrente.

---

## Presenze / Check-in

Un ingresso al giorno per socio (idempotente: la seconda registrazione dello stesso giorno risponde **200** con `alreadyCheckedIn: true` invece di **201**). L'idoneità (abbonamento attivo) genera `warnings` **non bloccanti** per gli operatori; blocca solo il self check-in del socio. `source` ∈ `RECEPTION` | `SELF` | `QR`.

| Metodo | Path | Note | Ruoli |
|---|---|---|---|
| POST | `/members/{id}/check-ins` | check-in reception | STAFF, GYM_MANAGER |
| GET | `/members/{id}/check-ins` | storico presenze del socio | STAFF, GYM_MANAGER |
| GET | `/check-ins?date=YYYY-MM-DD` | presenze del giorno (default oggi) | STAFF, GYM_MANAGER |
| POST | `/me/check-ins` | self check-in (bloccato senza abbonamento attivo) | MEMBER |
| GET | `/me/check-ins` | le proprie presenze | MEMBER |

**Risposta** (`CheckInResponseDTO`): `{ id, memberId, memberFullName, checkInAt, source, outcome: "OK"|"WARNING", warnings: [...], alreadyCheckedIn }`.

### Check-in via QR (tessera d'identità del socio)

Ogni socio ha un token opaco (256-bit base64url, generato pigramente alla prima lettura) dietro il proprio QR personale; il QR viene renderizzato dal FE, il BE scambia solo la stringa.

| Metodo | Path | Note | Ruoli |
|---|---|---|---|
| POST | `/check-ins/qr` | body `{ "token": "..." }` → registra l'ingresso; **404** token ignoto o di un'altra palestra (indistinguibili), **422** token vuoto | STAFF, GYM_MANAGER |
| GET | `/me/qr` | `{ "token": "..." }` — il socio mostra il proprio QR | MEMBER |
| GET | `/members/{id}/qr` | token del socio per stampare la tessera | STAFF, GYM_MANAGER |
| POST | `/members/{id}/qr/rotate` | rigenera il token: il QR precedente smette subito di funzionare (tessera smarrita) | STAFF, GYM_MANAGER |

---

## Prove gratuite / Lead

Traccia chi fa una prova prima di iscriversi (roadmap #8), con conversione a socio in un clic. Lista non paginata (volume basso). Dal 2026-10-01 anche il **socio** può segnalare un amico da `/me/leads` ("prove inserite dai soci") — vedi sezione dedicata più sotto.

### Lead

| Campo             | Tipo     | Descrizione                                                                 |
|-------------------|----------|------------------------------------------------------------------------------|
| `id`              | `Long`   | Identificatore univoco                                                      |
| `firstName`       | `String` | Nome                                                                         |
| `lastName`        | `String` | Cognome                                                                      |
| `birthDate`       | `String` | Data ISO 8601, **nullable** (vuota solo per una prova inserita dal socio, finché la reception non la completa) |
| `phone`           | `String` | Numero di telefono                                                           |
| `email`           | `String` | Email (opzionale)                                                            |
| `source`          | `String` | Provenienza libera (opzionale)                                              |
| `interestCourse`  | `{id,name}` o `null` | Corso di interesse (opzionale)                                   |
| `trialDate`       | `String` | Data della prova, ISO 8601 (opzionale)                                      |
| `status`          | `String` | `REQUESTED`\|`NEW`\|`TRIAL_SCHEDULED`\|`TRIED`\|`CONVERTED`\|`LOST`          |
| `origin`          | `String` | `STAFF` (inserita alla reception) o `MEMBER` (segnalata da un socio)         |
| `notes`           | `String` | Note libere (opzionale)                                                     |
| `createdAt`       | `String` | Datetime ISO 8601                                                            |
| `convertedMemberId` | `Long` o `null` | Riferimento logico al socio creato dalla conversione                 |
| `referredBy`      | `{id,name}` o `null` | Il socio che ha portato questo prospect (chi l'ha segnalato o chi la reception ha indicato) |
| `matchedMemberId` | `Long` o `null` | **Solo staff.** Se il telefono coincide con un socio già esistente della palestra |

### GET /leads — Lista prove

**Ruoli**: STAFF, GYM_MANAGER.

**Query string**: `?status=` (uno dei valori sopra; valore non valido → **400**), `?origin=STAFF|MEMBER` (valore non valido → **400**), `?q=` (ricerca libera su nome/cognome/telefono/email).

**Risposta — 200 OK** — array di `Lead`, ordinati per `createdAt` desc.

### POST /leads — Crea una prova (reception)

**Ruoli**: STAFF, GYM_MANAGER.

**Richiesta**: `firstName`, `lastName`, `phone` obbligatori; `birthDate` **obbligatoria** su questo percorso (`@NotNull @Past`); `email`, `source`, `courseId`, `trialDate`, `notes`, `referredByMemberId` opzionali. `status` opzionale — se omesso viene derivato da `trialDate` (assente → `NEW`; futura → `TRIAL_SCHEDULED`; oggi o passata → `TRIED`); se passato esplicitamente a `REQUESTED` → **422** (riservato alle prove inserite dai soci).

**Risposta — 201 Created** — `Lead`.

**Errori**: **404** se `courseId`/`referredByMemberId` appartengono a un'altra palestra; **422** su campi obbligatori mancanti, `birthDate` futura, o `status=REQUESTED`.

### GET/PUT/DELETE /leads/{id}

**Ruoli**: STAFF, GYM_MANAGER. **404** su una prova di un'altra palestra (anti-IDOR).

`PUT` accetta lo stesso corpo di `POST`. Per una prova `origin=MEMBER`, `referredByMemberId` nel body viene **ignorato**: il referrer resta il socio che l'ha segnalata. `status=REQUESTED` nel body è accettato solo se la prova è già `REQUESTED` (nessun cambio di stato); per spostarla fuori da `REQUESTED` usare `confirm`/`decline`, non `PUT`.

### POST /leads/{id}/confirm — La reception conferma una prova segnalata da un socio

**Ruoli**: STAFF, GYM_MANAGER.

**Richiesta**: `{ "trialDate": "YYYY-MM-DD" }` (obbligatoria, oggi o futura).

**Risposta — 200 OK** — `Lead` con `status: "TRIAL_SCHEDULED"`.

**Errori**: **409** `LEAD_NOT_REQUESTED` se la prova non è (più) `REQUESTED`; **422** su `trialDate` mancante o passata.

### POST /leads/{id}/decline — La reception rifiuta una prova segnalata da un socio

**Ruoli**: STAFF, GYM_MANAGER. Nessun corpo.

**Risposta — 200 OK** — `Lead` con `status: "LOST"`.

**Errori**: **409** `LEAD_NOT_REQUESTED` se la prova non è (più) `REQUESTED`.

### POST /leads/{id}/convert — Crea il socio dalla prova

**Ruoli**: STAFF, GYM_MANAGER.

**Richiesta**: `{ "email": "...", "birthDate"?: "YYYY-MM-DD" }`. `birthDate` è richiesta nel body **solo** se la prova non ne ha già una propria (caso: prova segnalata da un socio e non ancora completata); altrimenti viene ignorata e resta quella del lead.

**Risposta — 200 OK**
```json
{
  "lead": { "...": "Lead con status CONVERTED" },
  "member": { "...": "Member appena creato" }
}
```

**Comportamento**:
1. Se la prova è già `CONVERTED` → risposta identica, **nessun secondo socio creato** (replay idempotente — l'`email`/`birthDate` del body vengono ignorate).
2. Se la prova è ancora `REQUESTED` (non confermata dalla reception) → **409** `LEAD_NOT_CONFIRMED`.
3. Altrimenti crea il `Member` riusando `MemberService.create` (stessa validazione/normalizzazione) e collega il lead, nella stessa transazione.

**Errori**: **404** prova inesistente o di un'altra palestra; **409** `LEAD_NOT_CONFIRMED` (prova ancora `REQUESTED`), oppure 409 generico se l'email è già di un altro socio; **422** `errors.email` su email mancante/malformata, `errors.birthDate` se manca sia sul lead che nel body.

---

## Prove inserite dai soci (`/me/leads`)

Dal 2026-10-01 un **socio** può segnalare la prova di un amico senza passare dalla reception. La prova nasce `REQUESTED`, `origin: "MEMBER"` e con `referredBy` **forzato al socio loggato** (mai dal body) — resta non convertibile finché la reception non la conferma o la rifiuta (sezione sopra).

### MeLead (vista del socio — deliberatamente più stretta di `Lead`)

| Campo            | Tipo     | Descrizione                                                              |
|------------------|----------|---------------------------------------------------------------------------|
| `id`             | `Long`   | Identificatore univoco                                                   |
| `firstName`      | `String` | Nome dell'amico segnalato                                                |
| `lastName`       | `String` | Cognome dell'amico segnalato                                             |
| `status`         | `String` | `REQUESTED`\|`NEW`\|`TRIAL_SCHEDULED`\|`TRIED`\|`CONVERTED`\|`LOST`      |
| `trialDate`      | `String` o `null` | Data della prova, ISO 8601                                      |
| `interestCourse` | `{id,name}` o `null` | Corso di interesse                                              |
| `rewardEarned`   | `Boolean`| `true` se portare questa persona ha già fatto maturare lo sconto "porta un amico" (resta `true` anche dopo che il reward diventa `USED`) |
| `createdAt`      | `String` | Datetime ISO 8601                                                        |

Nessun `phone`/`email`/`notes`/`matchedMemberId`/`convertedMemberId`/`referredBy` — i dati di contatto dell'amico non tornano mai sullo schermo del socio.

### POST /me/leads — Segnala la prova di un amico

**Ruoli**: MEMBER.

**Richiesta**: `firstName`, `lastName`, `phone` obbligatori; `trialDate` (oggi o futura) e `courseId` opzionali. Niente `birthDate`/`email`: li completa la reception alla conferma/conversione. Eventuali campi extra nel body (es. `referredByMemberId`) vengono ignorati — il referrer è sempre il socio autenticato.

**Risposta — 201 Created** — `MeLead`, con `status: "REQUESTED"` sempre.

**Ordine delle validazioni**:
1. **422** `errors.phone` = *"Non puoi inserire il tuo numero di telefono"* — se il telefono coincide con quello del socio stesso.
2. **409** `LEAD_PENDING_LIMIT` — se il socio ha già 5 prove aperte (`REQUESTED`/`NEW`/`TRIAL_SCHEDULED`) in attesa, contando solo quelle segnalate da lui (`origin=MEMBER`).
3. **409** `LEAD_DUPLICATE_PHONE` — se lo stesso numero è già su una prova aperta della palestra (`REQUESTED`/`NEW`/`TRIAL_SCHEDULED`/`TRIED`, qualsiasi origine). Il confronto normalizza il numero (solo cifre, `00`→`+`, `+39` tolto), quindi `"+39 333 1234567"` e `"3331234567"` sono lo stesso numero.
4. **404** — se `courseId` appartiene a un'altra palestra.
5. **Nessun errore** se il telefono coincide con un socio **già esistente** (diverso dal chiamante): la prova viene creata comunque (201); solo lo staff vede il collegamento (`matchedMemberId` su `GET /leads`) — anti user-enumeration, il socio non riceve alcun indizio.

### GET /me/leads — "I miei amici"

**Ruoli**: MEMBER.

**Risposta — 200 OK** — array di `MeLead`, ordinati per `createdAt` desc. Include **tutte** le prove che hanno il socio come `referredBy`, sia quelle inserite da lui stesso sia quelle che la reception ha registrato indicandolo come referrer.

---

## Corsi, discipline e pacchetti (cenni)

Catalogo per-palestra (STAFF/GYM_MANAGER): `GET/POST/PUT/DELETE /disciplines`, `/courses` (con `disciplineId`, `level`, `trainerId` opzionale), `/packages` (uno o più corsi, prezzo/durata propri; `packageId` opzionale su `POST /subscriptions`). Area TRAINER: `GET /trainer/courses` (solo i propri corsi), `GET/POST /trainer/courses/{id}/attendance` (presenze di oggi scoped per corso). Il socio vede i corsi attivi della propria palestra con `GET /me/courses`.

---

## Tabelle Lookup

Le tabelle lookup vengono popolate automaticamente all'avvio e possono essere estese a runtime **senza riavviare il backend**.

### GET /subscription-types

Restituisce tutti i tipi di abbonamento disponibili.

**Risposta — 200 OK**
```json
[
  { "id": 1, "code": "MONTHLY",   "label": "Monthly",   "months": 1  },
  { "id": 2, "code": "QUARTERLY", "label": "Quarterly", "months": 3  },
  { "id": 3, "code": "ANNUAL",    "label": "Annual",    "months": 12 }
]
```

### POST /subscription-types — Aggiunge un nuovo tipo

```json
{ "code": "BIENNIAL", "label": "Biennale", "months": 24 }
```

**Risposta — 201 Created** — oggetto lookup creato  
**Risposta — 409 Conflict** — codice già esistente

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

### POST /payment-methods — Aggiunge un nuovo metodo di pagamento

```json
{ "code": "CARD", "label": "Carta di credito" }
```

**Risposta — 201 Created** — oggetto lookup creato  
**Risposta — 409 Conflict** — codice già esistente

---

### GET /belt-grades

Restituisce tutti i gradi cintura disponibili (pre-popolati: WHITE → BLACK).

**Risposta — 200 OK**
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

### POST /belt-grades — Aggiunge un nuovo grado cintura

```json
{ "code": "RED", "label": "Rosso" }
```

**Risposta — 201 Created** — oggetto lookup creato  
**Risposta — 409 Conflict** — codice già esistente

---

## Riepilogo Codici HTTP

| Stato | Significato                                    |
|-------|------------------------------------------------|
| 200   | OK — richiesta completata con successo         |
| 201   | Created — risorsa creata con successo          |
| 204   | No Content — eliminazione/recupero riuscito    |
| 400   | Bad Request — token non valido/scaduto         |
| 401   | Unauthorized — JWT mancante/non valido         |
| 403   | Forbidden — ruolo non autorizzato              |
| 404   | Not Found — risorsa non trovata                |
| 409   | Conflict — duplicato / dipendenza collegata    |
| 422   | Unprocessable Entity — validazione fallita     |
| 500   | Internal Server Error — errore imprevisto      |

> Nota: gli endpoint di membri, abbonamenti, pagamenti, presenze e catalogo corsi sono riservati a **STAFF** e **GYM_MANAGER** (l'ADMIN è escluso). Gli account MEMBER accedono solo a `/me/**`, i TRAINER solo a `/trainer/**`.

---

## Esempi curl

```bash
# Lista tutti gli iscritti
curl http://localhost:8080/api/v1/members

# Cerca iscritti attivi di nome "mario"
curl "http://localhost:8080/api/v1/members?q=mario&active=true"

# Crea un iscritto
curl -X POST http://localhost:8080/api/v1/members \
  -H "Content-Type: application/json" \
  -d '{
    "personalInfo": {
      "firstName": "Mario",
      "lastName": "Rossi",
      "email": "mario@esempio.com",
      "beltGradeId": 1
    }
  }'

# Recupera i gradi cintura
curl http://localhost:8080/api/v1/belt-grades

# Recupera i tipi di abbonamento
curl http://localhost:8080/api/v1/subscription-types

# Crea un abbonamento (typeId=1 = MONTHLY)
curl -X POST http://localhost:8080/api/v1/members/1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"typeId":1,"startDate":"2024-01-10","price":50.00}'

# Registra un pagamento (paymentMethodId=1 = CASH)
curl -X POST http://localhost:8080/api/v1/subscriptions/1/payments \
  -H "Content-Type: application/json" \
  -d '{"amount":50.00,"paymentDate":"2024-01-10","paymentMethodId":1}'

# Lista abbonamenti in scadenza entro 7 giorni
curl http://localhost:8080/api/v1/subscriptions/expiring?days=7

# Cancella un abbonamento
curl -X PATCH http://localhost:8080/api/v1/subscriptions/1/cancel

# Aggiunge un nuovo metodo di pagamento
curl -X POST http://localhost:8080/api/v1/payment-methods \
  -H "Content-Type: application/json" \
  -d '{"code":"CARD","label":"Carta di credito"}'

# Aggiunge un nuovo grado cintura
curl -X POST http://localhost:8080/api/v1/belt-grades \
  -H "Content-Type: application/json" \
  -d '{"code":"RED","label":"Rosso"}'
```

-- Idempotent migrations for columns Hibernate can't add to non-empty tables
ALTER TABLE IF EXISTS app_users ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;
ALTER TABLE IF EXISTS app_users ADD COLUMN IF NOT EXISTS member_id bigint;
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS contact_email varchar(150);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS address varchar(255);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS city varchar(100);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS postal_code varchar(10);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS province varchar(2);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS phone varchar(30);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS vat_number varchar(20);
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS region varchar(50);
ALTER TABLE IF EXISTS personal_infos ADD COLUMN IF NOT EXISTS region varchar(50);
ALTER TABLE IF EXISTS subscription_types ADD COLUMN IF NOT EXISTS default_price numeric(10,2);

-- [Roadmap: per-gym listino] SubscriptionType moves from a global lookup to a per-gym one
-- (SubscriptionTypeSeeder copies the 3 defaults — plus any legacy global codes — into every
-- gym). Existing global rows (gym_id IS NULL) are kept as historical FK targets for past
-- subscriptions; they're hidden from gym-scoped listings and are no longer editable by staff.
ALTER TABLE IF EXISTS subscription_types ADD COLUMN IF NOT EXISTS gym_id bigint;

-- Uniqueness of code moves from global to per-gym. NO DO-blocks here: Spring's script
-- runner splits on every ';' without understanding $$ dollar-quoting, so a DO block gets
-- shredded into invalid fragments that continue-on-error silently swallows. The legacy
-- UNIQUE(code) name is Hibernate-generated but deterministic (hash of table+column), so
-- it is identical on every DB this app ever created and can be dropped by name.
ALTER TABLE IF EXISTS subscription_types DROP CONSTRAINT IF EXISTS uk_gs331ef3ds4e02dpluw1fiko;

CREATE INDEX IF NOT EXISTS ix_subscription_types_gym_id ON subscription_types (gym_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_types_gym_code
    ON subscription_types (gym_id, code) WHERE gym_id IS NOT NULL;

-- [Roadmap #4 Fase 0] INSTRUCTOR -> GYM_MANAGER rename. Must run before the indexes below:
-- idempotent by nature (after the first run zero rows match). continue-on-error=true only
-- protects the CREATE INDEX/ALTER statements on tables that may not exist yet on a fresh
-- DB; this UPDATE always targets an existing table so a real failure here would NOT be
-- silently swallowed the same way — but verify count(role='INSTRUCTOR')=0 on Neon post-deploy.
UPDATE app_users SET role = 'GYM_MANAGER' WHERE role = 'INSTRUCTOR';

-- [Security fix #3 / Roadmap #4] One gym manager per gym: partial unique index enforced at
-- DB level. The application check still runs first to return a clean 409 message; this
-- index is a safety net against concurrent inserts that slip past the application guard.
-- The old index name/predicate is dropped (a new CREATE with a different name would not
-- remove it, and it would otherwise linger in the catalog inert after the UPDATE above).
DROP INDEX IF EXISTS ux_app_users_one_instructor_per_gym;
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_one_gym_manager_per_gym
    ON app_users (gym_id) WHERE role = 'GYM_MANAGER';

-- [Perf fix #5] Query-supporting indexes (all idempotent).
CREATE INDEX IF NOT EXISTS ix_members_gym_id
    ON members (gym_id);

CREATE INDEX IF NOT EXISTS ix_subscriptions_member_id
    ON subscriptions (member_id);

-- Covers the nightly expiry job and the expiring-soon query (status_id + end_date).
CREATE INDEX IF NOT EXISTS ix_subscriptions_status_id_end_date
    ON subscriptions (status_id, end_date);

CREATE INDEX IF NOT EXISTS ix_payments_member_id
    ON payments (member_id);

CREATE INDEX IF NOT EXISTS ix_payments_subscription_id
    ON payments (subscription_id);

-- Covers gym-scoped user lookups (e.g. findByGymIdAndRole, one-instructor check).
CREATE INDEX IF NOT EXISTS ix_app_users_gym_id_role
    ON app_users (gym_id, role);

CREATE INDEX IF NOT EXISTS ix_app_users_member_id
    ON app_users (member_id);

-- Check-ins (Tappa 1 presenze). On a fresh DB the table doesn't exist yet when this runs
-- (defer-datasource-initialization=false), so continue-on-error skips these; Hibernate then
-- creates the table and the indexes are added on the next startup. In prod the table already
-- exists, so they are created normally.
CREATE INDEX IF NOT EXISTS ix_check_ins_member_id
    ON check_ins (member_id);

-- Covers gym-scoped "who's in today" queries (gym_id + day range).
CREATE INDEX IF NOT EXISTS ix_check_ins_gym_id_check_in_at
    ON check_ins (gym_id, check_in_at);

-- [Round 3 #4] One check-in per member per calendar day, enforced at the DB level: a safety
-- net against the daily-idempotency race in CheckInService (two concurrent requests can both
-- pass its SELECT-then-INSERT check before either commits, producing 2 rows). Same
-- continue-on-error protection as the indexes above covers a fresh DB where the table doesn't
-- exist yet.
--
-- The unique index creation fails outright if duplicates already exist, so it's preceded by
-- an idempotent dedup DELETE (a second run finds no more matching pairs, so it's a no-op).
-- Dedup keeps, for each (member_id, day), the "most informative" surviving row: one already
-- carrying a course attribution (training_class_id) wins over one that doesn't — discarding
-- an attributed row here would silently re-introduce the very bug the CheckInService
-- attribution fix (round 2, item 1) closed; if neither/both are attributed, the earliest row
-- (lowest id) wins. Both statements target real, already-duplicate rows only (by the app's
-- own "one check-in per member per day" rule any 2nd row for the same pair is a race
-- artifact) — no legitimate, distinct data is at risk.
DELETE FROM check_ins a
USING check_ins b
WHERE a.member_id = b.member_id
  AND a.check_in_at::date = b.check_in_at::date
  AND a.id <> b.id
  AND (
        (a.training_class_id IS NULL AND b.training_class_id IS NOT NULL)
        OR (a.training_class_id IS NOT DISTINCT FROM b.training_class_id AND a.id > b.id)
      );

CREATE UNIQUE INDEX IF NOT EXISTS ux_check_ins_member_day
    ON check_ins (member_id, (check_in_at::date));

-- [Corsi #4] columns on subscriptions (backward-compatible, nullable).
ALTER TABLE IF EXISTS subscriptions ADD COLUMN IF NOT EXISTS package_id bigint;
ALTER TABLE IF EXISTS subscriptions ADD COLUMN IF NOT EXISTS purchase_price numeric(10,2);
-- Subscriptions to a package have no SubscriptionType (option A.4 "type_id nullable").
ALTER TABLE IF EXISTS subscriptions ALTER COLUMN type_id DROP NOT NULL;

-- Discipline uniqueness per gym (case-insensitive). Primary defense is the 409 check in
-- DisciplineService; this index is the DB-level safety net (same pattern as the gym-manager
-- index above). On a fresh DB (tests) `disciplines` doesn't exist yet when this line runs
-- (defer-datasource-initialization=false) so continue-on-error skips it; Hibernate creates
-- the table and it's added on the 2nd startup.
CREATE UNIQUE INDEX IF NOT EXISTS ux_disciplines_gym_name ON disciplines (gym_id, lower(name));

-- Scope / FK indexes for the Corsi & Pacchetti feature.
CREATE INDEX IF NOT EXISTS ix_disciplines_gym_id ON disciplines (gym_id);
CREATE INDEX IF NOT EXISTS ix_courses_gym_id     ON courses (gym_id);
CREATE INDEX IF NOT EXISTS ix_courses_trainer_id ON courses (trainer_id);
CREATE INDEX IF NOT EXISTS ix_packages_gym_id    ON packages (gym_id);
CREATE INDEX IF NOT EXISTS ix_subscriptions_package_id ON subscriptions (package_id);
-- Join table: FK back-reference for "packages that include course X".
CREATE INDEX IF NOT EXISTS ix_package_courses_course ON package_courses (course_id);

-- [Roadmap #5] QR check-in identity token on members. Nullable + generated lazily on
-- first read (CheckinTokenService), so this backfills every member already in prod on
-- Neon without a data migration script. Partial unique index: the many pre-backfill
-- NULLs never collide (same pattern as ux_app_users_one_gym_manager_per_gym above).
ALTER TABLE IF EXISTS members ADD COLUMN IF NOT EXISTS checkin_token varchar(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_members_checkin_token
    ON members (checkin_token) WHERE checkin_token IS NOT NULL;

-- [Roadmap #5] Widen the check_ins.source CHECK to accept 'QR'. Hibernate generated the
-- constraint back when the enum was RECEPTION/SELF only, and ddl-auto:update never alters
-- an existing CHECK — so on any DB whose check_ins table predates the QR feature (prod
-- included) every QR scan bounced with a 409. Drop+recreate keeps the pair idempotent; on
-- a fresh DB the table doesn't exist yet so both lines are skipped and Hibernate creates
-- it with the full enum list. Keep the values in sync with CheckInSource.
ALTER TABLE IF EXISTS check_ins DROP CONSTRAINT IF EXISTS check_ins_source_check;
ALTER TABLE IF EXISTS check_ins
    ADD CONSTRAINT check_ins_source_check CHECK (source IN ('RECEPTION', 'SELF', 'QR'));

-- [Member Documents] member_documents / member_document_blobs / medical_cert_reminder_logs
-- are created by Hibernate ddl-auto:update; on a fresh DB (tests) they don't exist yet when
-- this runs (defer-datasource-initialization=false), so continue-on-error skips these and
-- Hibernate creates them + these indexes get added on the next startup. In prod they'll exist
-- from the first deploy of this feature onward.
ALTER TABLE IF EXISTS member_documents ADD COLUMN IF NOT EXISTS expiry_date date;
CREATE INDEX IF NOT EXISTS ix_member_documents_member_id ON member_documents (member_id);
CREATE INDEX IF NOT EXISTS ix_member_documents_type_expiry ON member_documents (type, expiry_date);
CREATE UNIQUE INDEX IF NOT EXISTS ux_member_documents_storage_key ON member_documents (storage_key);
CREATE INDEX IF NOT EXISTS ix_medical_cert_reminder_document ON medical_cert_reminder_logs (document_id);

-- [Prove/Lead #8] leads.status: enum-string, il CHECK lo genera Hibernate creando la tabella.
-- Se in futuro si aggiunge uno stato servirà DROP+ADD del CHECK (vedi fix check_ins.source).
-- Il "in futuro" è arrivato il 2026-10-01 con REQUESTED: vedi il blocco "Prove inserite dai
-- soci" in fondo al file.
CREATE INDEX IF NOT EXISTS ix_leads_gym_id_status ON leads (gym_id, status);

-- [Corsi multi-giorno] Un corso può ripetersi su più giorni: courses.day_of_week (singolo,
-- NOT NULL) è sostituito dalla collection table course_days. schema.sql gira PRIMA di
-- Hibernate (defer-datasource-initialization: false), quindi la tabella va creata qui per
-- poter travasare i dati esistenti; su un DB vuoto (test) questi 3 statement falliscono
-- perché "courses" non esiste ancora e continue-on-error li lascia passare — a quel punto è
-- Hibernate a creare course_days dal mapping, con la stessa forma.
-- Il backfill è one-shot grazie al NOT EXISTS: senza, togliere un giorno a un corso lo
-- vedrebbe ricomparire al riavvio, ripescato dalla vecchia colonna rimasta indietro.
CREATE TABLE IF NOT EXISTS course_days (course_id bigint NOT NULL REFERENCES courses(id) ON DELETE CASCADE, day_of_week integer NOT NULL, PRIMARY KEY (course_id, day_of_week));
INSERT INTO course_days (course_id, day_of_week) SELECT c.id, c.day_of_week FROM courses c WHERE c.day_of_week IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_days d WHERE d.course_id = c.id);
ALTER TABLE IF EXISTS courses ALTER COLUMN day_of_week DROP NOT NULL;

-- [Porta un amico] Chi ha portato chi. Colonne nullable: Hibernate saprebbe aggiungerle da
-- solo, ma restano qui per la stessa convenzione delle altre (app_users.member_id ecc.) e
-- perche' cosi' la migrazione e' esplicita anche rileggendo il file. La tabella
-- referral_rewards la crea Hibernate dal mapping.
ALTER TABLE IF EXISTS members ADD COLUMN IF NOT EXISTS referred_by_member_id bigint;
ALTER TABLE IF EXISTS leads ADD COLUMN IF NOT EXISTS referred_by_member_id bigint;
CREATE INDEX IF NOT EXISTS ix_members_referred_by ON members (referred_by_member_id);
CREATE INDEX IF NOT EXISTS ix_referral_rewards_beneficiary ON referral_rewards (beneficiary_member_id, status);

-- [Quote associative e federali] La quota della palestra sta sul gym: NOT NULL con default,
-- quindi va aggiunta qui perche' Hibernate non sa farlo su una tabella gia' popolata.
-- federations / federation_card_types / member_fees le crea Hibernate dal mapping.
ALTER TABLE IF EXISTS gyms ADD COLUMN IF NOT EXISTS membership_fee_amount numeric(10,2) NOT NULL DEFAULT 20.00;
ALTER TABLE IF EXISTS disciplines ADD COLUMN IF NOT EXISTS federation_id bigint;
CREATE INDEX IF NOT EXISTS ix_member_fees_member ON member_fees (member_id, valid_until);
CREATE INDEX IF NOT EXISTS ix_federation_card_types_federation ON federation_card_types (federation_id);

-- [Prove inserite dai soci 2026-10-01] Un MEMBER può segnalare l'amico portato in palestra da
-- /me/leads (anti user-enumeration: niente errore se il numero è già di un socio, solo
-- matched_member_id ad uso staff). Il lead parte REQUESTED e resta non convertibile finché la
-- reception non lo confirma/declina. birth_date diventa nullable perché il socio non la
-- conosce; la reception la completa alla conferma o alla conversione. origin distingue chi ha
-- creato il lead (STAFF/MEMBER). Il CHECK su status va DROP+ADD perché Hibernate
-- ddl-auto:update non altera mai un CHECK esistente (stesso fix di check_ins.source più sopra).
ALTER TABLE IF EXISTS leads ALTER COLUMN birth_date DROP NOT NULL;
ALTER TABLE IF EXISTS leads ADD COLUMN IF NOT EXISTS origin varchar(10) NOT NULL DEFAULT 'STAFF';
ALTER TABLE IF EXISTS leads ADD COLUMN IF NOT EXISTS matched_member_id bigint;
ALTER TABLE IF EXISTS leads DROP CONSTRAINT IF EXISTS leads_status_check;
ALTER TABLE IF EXISTS leads ADD CONSTRAINT leads_status_check CHECK (status IN ('REQUESTED','NEW','TRIAL_SCHEDULED','TRIED','CONVERTED','LOST'));
ALTER TABLE IF EXISTS leads DROP CONSTRAINT IF EXISTS leads_origin_check;
ALTER TABLE IF EXISTS leads ADD CONSTRAINT leads_origin_check CHECK (origin IN ('STAFF','MEMBER'));
CREATE INDEX IF NOT EXISTS ix_leads_referred_by_status ON leads (referred_by_member_id, status);

-- [Scheduler email / outbox, fase 1 - 2026-10-01] email_outbox la crea Hibernate dal mapping
-- (incluso l'indice unico su dedup_key, dichiarato anche via @Table(uniqueConstraints) cosi'
-- esiste dal primo boot pure nei test). Qui solo gli indici di supporto alle query del
-- drainer/dell'osservabilita' futura: su un DB vergine (test) la tabella non esiste ancora e
-- continue-on-error li salta, Hibernate crea la tabella al prossimo avvio senza di loro.
CREATE INDEX IF NOT EXISTS ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_email_outbox_gym_created ON email_outbox (gym_id, created_at DESC);

# Load testing — Gym Manager backend

Script [k6](https://k6.io) per misurare capacità, latenza e stabilità dell'API. Tutti gli script
sono **read-only** (solo GET autenticate): un solo login in `setup()`, token riusato da tutti i
virtual user, soglie con `abortOnFail` che fermano il test se errori o latenza esplodono.

## Script disponibili

| File | Tipo | A cosa serve |
|------|------|--------------|
| `spike.js` | spike | Ondata improvvisa (inizio lezione): assorbe senza errori? recupera? |
| `soak.js` | endurance | Carico costante per N minuti: memory/connection leak, degrado lento. |
| `local-capacity.js` | capacità spinta | Spinge in alto (PEAK 800+) per trovare il limite dell'applicazione. Solo in locale. |

## Come lanciare

```bash
# backend + Postgres in locale
docker-compose up -d

k6 run -e PEAK=500 spike.js                  # spike
k6 run -e VUS=70 -e DUR=20m soak.js          # soak 20 minuti
k6 run -e PEAK=800 local-capacity.js         # capacità

# default BASE_URL = http://localhost:8080/api/v1 ; override con -e BASE_URL=...
# credenziali: utente demo staff/staff123 ; override con -e USER=... -e PASS=...
```

> Nota XFF: `local-capacity.js` assegna un `X-Forwarded-For` unico per VU per simulare utenti
> distinti rispetto al limitatore dei login (che chiavea sull'IP). Funziona solo in locale:
> dietro il reverse proxy di produzione l'XFF viene sovrascritto con l'IP reale.

## Risultati misurati (produzione, VM singola, read-only)

| Utenti concorrenti | p95 latenza | Errori | Note |
|---|---|---|---|
| 100 | ~138 ms | 0% | zona comfort |
| 500 | ~960 ms | 0% | ginocchio: la latenza sale, ma 0 errori |
| Spike 20→500 | ~1,17 s (transitorio) | 0% | assorbe e recupera |
| Soak 70 × 20 min | mediana ~83 ms, p95 ~123 ms | 0% | stabile, nessun leak |

Le leve che hanno portato a questi numeri: pool HikariCP 10→20, cache Caffeine sui lookup e sui
`UserDetails` del filtro JWT, `@EntityGraph`/JOIN FETCH contro le N+1 (vedi
`src/main/resources/application.yml` e `config/CacheConfig.java`).

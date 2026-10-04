import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// SOAK / endurance test: carico moderato COSTANTE per molti minuti. Serve a scovare
// memory leak, connection leak del pool e degrado lento della latenza nel tempo.
// Read-only, un solo login, auto-abort. Vedi README.md.
//
//   k6 run -e VUS=70 -e DUR=20m soak.js

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const USER = __ENV.USER || 'staff';
const PASS = __ENV.PASS || '1234';
const VUS = parseInt(__ENV.VUS || '70');
const DUR = __ENV.DUR || '20m';

const apiErrors = new Rate('api_errors');

export const options = {
  scenarios: {
    soak: { executor: 'constant-vus', vus: VUS, duration: DUR },
  },
  // Tolleranza ai blip, ma aborta se errori/latenza salgono in modo SOSTENUTO (segnale di leak).
  thresholds: {
    http_req_failed:   [{ threshold: 'rate<0.02',  abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '30s' }],
  },
};

export function setup() {
  const res = http.post(`${BASE}/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } });
  if (res.status !== 200) throw new Error(`Login fallito: ${res.status}`);
  return { token: res.json('token') };
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'browse' } };
  const r1 = http.get(`${BASE}/members?page=0&size=20`, params);
  apiErrors.add(!check(r1, { 'members 200': (r) => r.status === 200 }));
  sleep(0.5);
  const r2 = http.get(`${BASE}/subscriptions/expiring?days=7`, params);
  apiErrors.add(!check(r2, { 'expiring 200': (r) => r.status === 200 }));
  sleep(Math.random() * 1.0 + 0.5); // think time 0.5-1.5s
}

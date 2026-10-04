import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// SPIKE test: salto improvviso da pochi a molti utenti (scenario "tutti entrano a inizio
// lezione"). Misura se l'app assorbe l'ondata senza errori e quanto recupera.
// Read-only, un solo login, auto-abort. Vedi README.md.
//
//   k6 run -e PEAK=500 spike.js

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const USER = __ENV.USER || 'staff';
const PASS = __ENV.PASS || 'staff123';
const PEAK = parseInt(__ENV.PEAK || '500');

const apiErrors = new Rate('api_errors');

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 20,
      stages: [
        { duration: '15s', target: 20 },    // baseline tranquillo
        { duration: '5s',  target: PEAK },   // SPIKE improvviso
        { duration: '30s', target: PEAK },   // tiene il picco
        { duration: '5s',  target: 20 },     // crollo
        { duration: '25s', target: 20 },     // osserva il recupero
        { duration: '5s',  target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed:   [{ threshold: 'rate<0.05',  abortOnFail: true, delayAbortEval: '10s' }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '10s' }],
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
  sleep(0.3);
  const r2 = http.get(`${BASE}/subscriptions/expiring?days=7`, params);
  apiErrors.add(!check(r2, { 'expiring 200': (r) => r.status === 200 }));
  sleep(Math.random() * 0.5 + 0.3);
}

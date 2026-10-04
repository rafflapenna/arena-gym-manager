import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Test di capacità in LOCALE (backend dev + Postgres docker). Spinge molto in alto per
// trovare il ginocchio dell'applicazione, NON su prod.
//
// IMPORTANTE: ad ogni VU assegna un X-Forwarded-For UNICO, così il rate limiter del login
// (10/IP/5min, che chiavea sull'IP) tratta ogni VU come un utente da IP diverso — altrimenti
// da una sola macchina i login verrebbero strozzati (429) e il test sarebbe falsato.
// In LOCALE il backend si fida dell'XFF (nessun proxy davanti); in prod Caddy lo sovrascrive,
// quindi questo script va usato SOLO in locale.
//
//   k6 run -e BASE_URL=http://localhost:8080/api/v1 -e PEAK=800 local-capacity.js

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const USER = __ENV.USER || 'staff';
const PASS = __ENV.PASS || '1234';
const PEAK = parseInt(__ENV.PEAK || '800');

const apiErrors = new Rate('api_errors');

export const options = {
  scenarios: {
    capacity: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: Math.round(PEAK * 0.125) },
        { duration: '40s', target: Math.round(PEAK * 0.375) },
        { duration: '40s', target: Math.round(PEAK * 0.625) },
        { duration: '40s', target: PEAK },
        { duration: '45s', target: PEAK },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:browse}': ['p(95)<800'],
  },
};

function vuIp() {
  const n = __VU;
  return `10.${Math.floor(n / 65536) % 256}.${Math.floor(n / 256) % 256}.${n % 256}`;
}

let token = null;
const ip = vuIp();

function login() {
  const res = http.post(`${BASE}/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ip }, tags: { name: 'login' } });
  try { return res.status === 200 ? res.json('token') : null; } catch (e) { return null; }
}

export default function () {
  if (!token) token = login();
  if (!token) { sleep(1); return; }
  const params = { headers: { Authorization: `Bearer ${token}`, 'X-Forwarded-For': ip }, tags: { name: 'browse' } };
  const r1 = http.get(`${BASE}/members?page=0&size=20`, params);
  apiErrors.add(!check(r1, { 'members 200': (r) => r.status === 200 }));
  sleep(0.3);
  const r2 = http.get(`${BASE}/subscriptions/expiring?days=7`, params);
  apiErrors.add(!check(r2, { 'expiring 200': (r) => r.status === 200 }));
  sleep(Math.random() * 0.7 + 0.3);
}

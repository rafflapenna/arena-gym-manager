/**
 * Responsive mobile guardrail — run with: npm run test:responsive
 *
 * Prerequisites:
 *   - Backend running on http://localhost:8080 (Spring Boot, any profile)
 *   - Frontend dev server on http://localhost:5173 (npm run dev)
 *   - Playwright Chromium available — set PLAYWRIGHT_BROWSERS_PATH=E:\playwright-browsers
 *     (or run `npx playwright install chromium` once to install it there)
 *
 * The script logs in as the INSTRUCTOR role (env FE_USER / FE_PASS), visits every route at
 * 375px viewport, and exits with code 1 if any page regresses on one of four checks (see
 * AUDIT-mobile.md for the original findings these guard against):
 *   1. Horizontal overflow (scrollWidth > clientWidth) — the original check.
 *   2. Touch targets: any visible <button>/<select>/<a href>/checkbox-or-radio-<input> smaller
 *      than 44x44px. Exception (documented, not guessed): an <a> whose parent element's own
 *      text is longer than the link's own text is an inline link inside running prose (e.g.
 *      "contatta l'assistenza" inside a sentence) — those are exempt, same as everywhere else
 *      on the web; a link that IS its container's entire content (a nav item, a button-shaped
 *      link) is not exempt.
 *   3. Input font size: any visible input/select/textarea with computed font-size < 16px —
 *      triggers iOS Safari's auto-zoom on focus.
 *   4. Bottom-nav coverage: after scrolling the page's main content to its end, the last
 *      element's bottom edge must clear the fixed bottom-nav's top edge — otherwise the nav
 *      permanently hides the tail of the page.
 * Screenshots land in responsive-report/ (gitignored).
 *
 * Optional second pass (MEMBER area): set FE_MEMBER_USER / FE_MEMBER_PASS to also log in as
 * a MEMBER and sweep the /me/** routes (incl. /me/friends, "porta un amico"). Skipped — with
 * a log line — when those env vars aren't set, so the staff-only guardrail keeps working as
 * before without requiring a member fixture account.
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:5173';
const USER = process.env.FE_USER || 'instructor';
const PASS = process.env.FE_PASS || 'instructor';
const VIEWPORT = { width: 375, height: 812 }; // iPhone-ish portrait
const OUT = 'responsive-report';
mkdirSync(OUT, { recursive: true });

const routes = [
  { path: '/login', auth: false },
  { path: '/dashboard' },
  { path: '/members' },
  { path: '/enroll' },
  { path: '/leads' },
  { path: '/fees' },
  { path: '/subscription-types' },
  { path: '/subscriptions' },
  { path: '/courses' },
  { path: '/courses/new' },
  { path: '/packages' },
  { path: '/packages/new' },
  { path: '/check-ins' },
  { path: '/my-gym' },
  { path: '/users' },
];

const memberRoutes = [
  { path: '/me' },
  { path: '/me/check-ins' },
  { path: '/me/qr' },
  { path: '/me/subscriptions' },
  { path: '/me/courses' },
  { path: '/me/payments' },
  { path: '/me/documents' },
  { path: '/me/friends' },
];

const MIN_TOUCH = 44;
const MIN_FONT = 16;

/** Visits one route at 375px, screenshots it, and reports regressions on all four checks. */
async function checkRoute(page, path) {
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' }).catch(() => {});
  await page.waitForTimeout(700);
  const info = await page.evaluate(({ MIN_TOUCH, MIN_FONT }) => {
    const el = document.scrollingElement || document.documentElement;
    const vw = window.innerWidth;

    // 1. elementi che sforano oltre il bordo destro del viewport
    const all = [...document.body.querySelectorAll('*')];
    const exceeds = (n) => n.getBoundingClientRect().right > vw + 1;
    const overflowOffenders = [];
    for (const node of all) {
      const rect = node.getBoundingClientRect();
      if (rect.width > 0 && rect.right > vw + 1) {
        // solo elementi-foglia: nessun figlio sfora (così salto i container ereditati)
        const childExceeds = [...node.children].some(exceeds);
        if (childExceeds) continue;
        const cls = (node.className && node.className.toString) ? node.className.toString().slice(0, 60) : '';
        overflowOffenders.push(`<${node.tagName.toLowerCase()} class="${cls}"> right=${Math.round(rect.right)} w=${Math.round(rect.width)} "${(node.textContent || '').trim().slice(0, 25)}"`);
      }
    }

    // 2. target tattili < 44px (bottoni, link, select, checkbox/radio)
    const isInteractive = (node) => {
      const tag = node.tagName.toLowerCase();
      if (tag === 'button' || tag === 'select') return true;
      if (tag === 'a' && node.hasAttribute('href')) return true;
      if (tag === 'input' && ['checkbox', 'radio', 'submit', 'button'].includes(node.type)) return true;
      return false;
    };
    const touchOffenders = [];
    for (const node of all) {
      if (!isInteractive(node)) continue;
      const rect = node.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue; // nascosto
      if (getComputedStyle(node).visibility === 'hidden') continue;
      if (node.tagName.toLowerCase() === 'a') {
        // Eccezione documentata: link inline dentro una frase di testo normale — il genitore
        // contiene altro testo oltre al link stesso. Un link che è l'intero contenuto del suo
        // contenitore (nav item, bottone mascherato da link) non è esente.
        const parentText = (node.parentElement?.textContent || '').trim();
        const linkText = (node.textContent || '').trim();
        if (parentText.length > linkText.length) continue;
      }
      if (node.tagName.toLowerCase() === 'input' && ['checkbox', 'radio'].includes(node.type)) {
        // Eccezione documentata: un checkbox/radio nativo è piccolo per design — quello che
        // conta è l'area cliccabile reale, cioè la riga <label> che lo contiene (click sulla
        // label = click sul controllo). Esente solo se quella riga è già >=44px.
        const label = node.closest('label');
        if (label) {
          const lrect = label.getBoundingClientRect();
          if (Math.min(lrect.width, lrect.height) >= MIN_TOUCH) continue;
        }
      }
      const minSide = Math.min(rect.width, rect.height);
      if (minSide < MIN_TOUCH) {
        const cls = (node.className && node.className.toString) ? node.className.toString().slice(0, 50) : '';
        touchOffenders.push(`<${node.tagName.toLowerCase()} class="${cls}"> ${Math.round(rect.width)}x${Math.round(rect.height)} "${(node.textContent || '').trim().slice(0, 25)}"`);
      }
    }

    // 3. input/select/textarea con font-size < 16px (zoom automatico iOS)
    const fontOffenders = [];
    for (const node of document.querySelectorAll('input, select, textarea')) {
      const rect = node.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;
      const fs = parseFloat(getComputedStyle(node).fontSize);
      if (fs < MIN_FONT) {
        fontOffenders.push(`<${node.tagName.toLowerCase()}> font-size=${fs}px id="${node.id || ''}" name="${node.name || ''}"`);
      }
    }

    return {
      scrollW: el.scrollWidth, clientW: el.clientWidth,
      overflow: el.scrollWidth - el.clientWidth,
      vw,
      overflowOffenders: [...new Set(overflowOffenders)].slice(0, 6),
      touchOffenders: [...new Set(touchOffenders)].slice(0, 8),
      fontOffenders: [...new Set(fontOffenders)].slice(0, 8),
    };
  }, { MIN_TOUCH, MIN_FONT });

  // 4. bottom-nav coverage: scroll al fondo e verifica che l'ultimo elemento non finisca sotto
  // la bottom-nav fissa (padding-bottom insufficiente del contenuto scrollabile).
  const navCoverage = await page.evaluate(() => {
    const navEl = [...document.querySelectorAll('nav')].find((n) => getComputedStyle(n).position === 'fixed');
    if (!navEl) return null;
    // AppLayout/MemberLayout's <main> has `overflow-y-auto` but its wrapper is only
    // `min-h-screen` (not height-clamped), so `main` itself never actually grows a scrollbar —
    // the real scroll happens on the document/window. Scrolling `main` (as an earlier version
    // of this check did) silently no-ops and always reports the page's *unscrolled* layout.
    const scroller = document.scrollingElement || document.documentElement;
    scroller.scrollTop = scroller.scrollHeight;
    const navTop = navEl.getBoundingClientRect().top;
    const main = document.querySelector('main');
    if (!main) return null;
    const candidates = [...main.querySelectorAll('*')].filter((n) => n.getBoundingClientRect().height > 0);
    if (candidates.length === 0) return null;
    const lowest = candidates.reduce((a, b) => (b.getBoundingClientRect().bottom > a.getBoundingClientRect().bottom ? b : a));
    const bottom = lowest.getBoundingClientRect().bottom;
    if (bottom > navTop + 2) {
      return `ultimo elemento arriva a ${Math.round(bottom)}px, bottom-nav inizia a ${Math.round(navTop)}px (coperto di ${Math.round(bottom - navTop)}px)`;
    }
    return null;
  });

  const file = `${OUT}${path.replace(/\//g, '_') || '_root'}.png`;
  // `fullPage: true` stitches the screenshot by scrolling, which duplicates/misplaces any
  // `position: fixed` element (e.g. the mobile bottom nav) at every scroll step — on a page
  // just taller than the viewport this can paint the nav bar over real content below it,
  // making a correctly-rendered page look empty in the screenshot. Growing the viewport to the
  // page's actual height and taking a plain (non-stitched) screenshot avoids that artifact; the
  // checks above already ran at the real VIEWPORT, so this only affects the screenshot.
  const contentHeight = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: VIEWPORT.width, height: Math.max(contentHeight, VIEWPORT.height) });
  await page.screenshot({ path: file }).catch(() => {});
  await page.setViewportSize(VIEWPORT);

  const problems = [];
  if (info.overflow > 2) problems.push(`OVERFLOW +${info.overflow}px`);
  if (info.touchOffenders.length) problems.push(`${info.touchOffenders.length} target < ${MIN_TOUCH}px`);
  if (info.fontOffenders.length) problems.push(`${info.fontOffenders.length} campo < ${MIN_FONT}px`);
  if (navCoverage) problems.push('coperto da bottom-nav');
  const verdict = problems.length ? problems.join(', ') : 'ok';

  const result = { path, finalUrl: page.url().replace(BASE, ''), verdict, info, navCoverage };
  console.log(`\n${path}  ->  ${verdict}  (scrollW=${info.scrollW}, clientW=${info.clientW})`);
  // Un elemento può sforare il bordo destro del viewport restando comunque contenuto (es. un
  // box con overflow-y-auto che clippa anche in orizzontale) senza causare scroll di pagina:
  // si stampano solo quando c'è overflow di pagina reale, altrimenti è rumore.
  if (info.overflow > 2) for (const o of info.overflowOffenders) console.log(`    ⤷ overflow: ${o}`);
  for (const o of info.touchOffenders) console.log(`    ⤷ target piccolo: ${o}`);
  for (const o of info.fontOffenders) console.log(`    ⤷ font piccolo: ${o}`);
  if (navCoverage) console.log(`    ⤷ ${navCoverage}`);
  return result;
}

function printSummary(label, results) {
  console.log(`\n===== RIEPILOGO ${label} =====`);
  for (const r of results) {
    console.log(`${r.verdict === 'ok' ? '✓' : '✗'}  ${r.path.padEnd(20)} ${r.verdict}${r.finalUrl !== r.path ? '  (redirect -> ' + r.finalUrl + ')' : ''}`);
  }
  const broken = results.filter((r) => r.verdict !== 'ok');
  console.log(`\n${broken.length} pagine con overflow orizzontale su ${results.length}.`);
  return broken;
}

const browser = await chromium.launch();

// --- staff pass (unchanged) ---
const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 2 });
const page = await ctx.newPage();

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await page.locator('input[type=text]').first().fill(USER);
await page.locator('input[type=password]').first().fill(PASS);
await page.locator('button[type=submit]').click();
await page.waitForURL('**/dashboard', { timeout: 15000 }).catch(() => {});
const loggedIn = page.url().includes('/dashboard');
console.log(`login: ${loggedIn ? 'OK' : 'FALLITO (' + page.url() + ')'}`);

const results = [];
for (const r of routes) {
  results.push(await checkRoute(page, r.path));
}
const broken = printSummary('(staff)', results);
console.log(`Screenshot in ${OUT}/`);

// --- optional member pass: /me/** incl. /me/friends ("porta un amico") ---
let memberBroken = [];
const memberUser = process.env.FE_MEMBER_USER;
const memberPass = process.env.FE_MEMBER_PASS;
if (memberUser && memberPass) {
  const memberCtx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 2 });
  const memberPage = await memberCtx.newPage();

  await memberPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await memberPage.locator('input[type=text]').first().fill(memberUser);
  await memberPage.locator('input[type=password]').first().fill(memberPass);
  await memberPage.locator('button[type=submit]').click();
  await memberPage.waitForURL('**/me', { timeout: 15000 }).catch(() => {});
  const memberLoggedIn = memberPage.url().includes('/me');
  console.log(`\nlogin (member): ${memberLoggedIn ? 'OK' : 'FALLITO (' + memberPage.url() + ')'}`);

  const memberResults = [];
  for (const r of memberRoutes) {
    memberResults.push(await checkRoute(memberPage, r.path));
  }
  memberBroken = printSummary('(member)', memberResults);
  console.log(`Screenshot in ${OUT}/`);

  await memberCtx.close();
} else {
  console.log('\nPass MEMBER saltato: imposta FE_MEMBER_USER / FE_MEMBER_PASS per includerlo.');
}

await browser.close();

if (broken.length > 0 || memberBroken.length > 0) process.exit(1);

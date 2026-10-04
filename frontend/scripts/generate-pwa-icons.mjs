/**
 * Generates the PWA icon set (manifest icons + apple-touch-icon) from the app's own mark —
 * the lime rounded-square with the lucide "Dumbbell" glyph that LoginPage/AppLayout/MemberLayout
 * already render inline as JSX (there was no standalone logo asset to start from: public/
 * favicon.svg is an unrelated leftover, a different shape and brand color). Run with:
 *   node scripts/generate-pwa-icons.mjs
 * Output lands in public/ so `vite build` copies it to dist/ verbatim, next to the manifest.
 */
import sharp from 'sharp';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(__dirname, '../public');
mkdirSync(OUT_DIR, { recursive: true });

const LIME = '#a3e635'; // Tailwind lime-400 — same token the app's own logo mark uses
const INK = '#0b0b0d'; // app's near-black, used as the icon's own stroke color on lime

// lucide-react "dumbbell" path data (v1.18.0), viewBox 0 0 24 24, stroke-based.
const DUMBBELL_PATHS = [
  'M17.596 12.768a2 2 0 1 0 2.829-2.829l-1.768-1.767a2 2 0 0 0 2.828-2.829l-2.828-2.828a2 2 0 0 0-2.829 2.828l-1.767-1.768a2 2 0 1 0-2.829 2.829z',
  'm2.5 21.5 1.4-1.4',
  'm20.1 3.9 1.4-1.4',
  'M5.343 21.485a2 2 0 1 0 2.829-2.828l1.767 1.768a2 2 0 1 0 2.829-2.829l-6.364-6.364a2 2 0 1 0-2.829 2.829l1.768 1.767a2 2 0 0 0-2.828 2.829z',
  'm9.6 14.4 4.8-4.8',
];

function dumbbellGroup(scale, translate) {
  const paths = DUMBBELL_PATHS.map((d) => `<path d="${d}"/>`).join('');
  return `<g transform="translate(${translate},${translate}) scale(${scale})" fill="none" stroke="${INK}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</g>`;
}

/** Standard icon: rounded square (matches the app's own `rounded-2xl` logo chip look). */
function standardSvg(size) {
  const rx = size * 0.22;
  const iconSize = size * 0.58; // visual icon footprint, centered
  const scale = iconSize / 24;
  const translate = (size - iconSize) / 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" rx="${rx}" fill="${LIME}"/>
    ${dumbbellGroup(scale, translate)}
  </svg>`;
}

/**
 * Maskable icon: full-bleed background, no pre-baked corner rounding (the OS applies its own
 * mask shape — circle, squircle, ...) and the glyph kept inside the ~80%-diameter "safe zone"
 * the maskable-icon spec requires, so nothing important gets clipped by that mask.
 */
function maskableSvg(size) {
  const iconSize = size * 0.42;
  const scale = iconSize / 24;
  const translate = (size - iconSize) / 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" fill="${LIME}"/>
    ${dumbbellGroup(scale, translate)}
  </svg>`;
}

const jobs = [
  { name: 'pwa-192.png', svg: standardSvg(192), size: 192 },
  { name: 'pwa-512.png', svg: standardSvg(512), size: 512 },
  { name: 'pwa-maskable-192.png', svg: maskableSvg(192), size: 192 },
  { name: 'pwa-maskable-512.png', svg: maskableSvg(512), size: 512 },
  // iOS ignores transparency and applies its own rounding — give it the full-bleed maskable
  // artwork (at its own size) rather than the pre-rounded one, so corners don't double up.
  { name: 'apple-touch-icon.png', svg: maskableSvg(180), size: 180 },
];

for (const { name, svg, size } of jobs) {
  const outPath = path.join(OUT_DIR, name);
  await sharp(Buffer.from(svg)).resize(size, size).png().toFile(outPath);
  console.log(`✓ ${name} (${size}x${size})`);
}

console.log(`\nDone — icons written to ${OUT_DIR}`);

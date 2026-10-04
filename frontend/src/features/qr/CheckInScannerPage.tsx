import { Suspense, lazy, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ChevronLeft, ScanLine, Square, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';
import type { IDetectedBarcode, IScannerError } from '@yudiel/react-qr-scanner';
import { useCheckInByQr } from './useQr';
import { getApiErrorMessage } from '../../utils/apiError';
import type { CheckInResponse } from '../../types';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';

// Lazy so the scanner engine stays out of the main bundle (only this route pays for it).
// @yudiel/react-qr-scanner replaces the unmaintained html5-qrcode, which cannot start
// the camera on iOS 17+ (mebjas/html5-qrcode#951); this one uses the native
// BarcodeDetector where available with a WASM fallback (iPhone/Android/desktop).
// scannerEngine also rebinds the WASM loader to our own bundled asset (no CDN).
const Scanner = lazy(() =>
  import('./scannerEngine').then((m) => ({ default: m.Scanner }))
);

const REPEAT_DEBOUNCE_MS = 5000;
const OVERLAY_DURATION_MS = 2000;

type Overlay =
  | { kind: 'ok'; title: string; subtitle?: string }
  | { kind: 'warning'; title: string; warnings: string[] }
  | { kind: 'error'; title: string };

function playBeep(frequency: number) {
  try {
    const AudioCtx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0.18, ctx.currentTime);
    oscillator.connect(gain);
    gain.connect(ctx.destination);
    oscillator.start();
    oscillator.stop(ctx.currentTime + 0.15);
    oscillator.onended = () => ctx.close();
  } catch {
    // Audio not available (autoplay policy, unsupported browser) — non-blocking.
  }
}

function cameraErrorMessage(error: IScannerError): string {
  switch (error.kind) {
    case 'permission-denied':
      return 'Permesso fotocamera negato. Consenti l’accesso alla fotocamera nelle impostazioni del browser e riprova.';
    case 'no-camera':
      return 'Nessuna fotocamera trovata su questo dispositivo.';
    case 'in-use':
      return 'La fotocamera è già in uso da un’altra app. Chiudila e riprova.';
    case 'insecure-context':
      return 'La fotocamera richiede una connessione HTTPS.';
    case 'unsupported':
      return 'Questo browser non supporta la scansione QR. Prova con Safari (iPhone) o Chrome.';
    default:
      return `Impossibile avviare la fotocamera: ${error.message}`;
  }
}

/** Reception scanner: continuous QR read, debounced per-token, with a large pass/warn/fail overlay. */
export function CheckInScannerPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const hasInternalHistory = location.key !== 'default';
  const [status, setStatus] = useState<'idle' | 'running' | 'error'>('idle');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [overlay, setOverlay] = useState<Overlay | null>(null);

  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const lastTokenRef = useRef<string | null>(null);
  const lastTimeRef = useRef(0);
  const pendingRef = useRef(false);
  const overlayTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const checkInByQr = useCheckInByQr();

  const showOverlay = (next: Overlay) => {
    setOverlay(next);
    if (overlayTimerRef.current) clearTimeout(overlayTimerRef.current);
    overlayTimerRef.current = setTimeout(() => setOverlay(null), OVERLAY_DURATION_MS);
  };

  const handleDecoded = (decodedText: string) => {
    const token = decodedText.trim();
    if (!token) return;
    const now = Date.now();
    if (token === lastTokenRef.current && now - lastTimeRef.current < REPEAT_DEBOUNCE_MS) return;
    if (pendingRef.current) return;

    lastTokenRef.current = token;
    lastTimeRef.current = now;
    pendingRef.current = true;

    checkInByQr.mutate(token, {
      onSuccess: (data: CheckInResponse) => {
        pendingRef.current = false;
        if (data.outcome === 'WARNING' && data.warnings.length > 0) {
          playBeep(520);
          navigator.vibrate?.([120, 60, 120]);
          showOverlay({ kind: 'warning', title: `Benvenuto ${data.memberFullName}`, warnings: data.warnings });
        } else {
          playBeep(880);
          navigator.vibrate?.(150);
          showOverlay({
            kind: 'ok',
            title: `Benvenuto ${data.memberFullName}`,
            subtitle: data.alreadyCheckedIn ? 'Già presente oggi' : undefined,
          });
        }
      },
      onError: (err) => {
        pendingRef.current = false;
        playBeep(220);
        navigator.vibrate?.([200, 80, 200]);
        const message = getApiErrorMessage(err);
        showOverlay({ kind: 'error', title: message || 'QR non valido' });
      },
    });
  };

  const handleScan = (codes: IDetectedBarcode[]) => {
    const first = codes[0];
    if (first?.rawValue) handleDecoded(first.rawValue);
  };

  const handleScannerError = (error: IScannerError) => {
    setStatus('error');
    setCameraError(cameraErrorMessage(error));
    releaseWakeLock();
  };

  const releaseWakeLock = () => {
    wakeLockRef.current?.release().catch(() => {});
    wakeLockRef.current = null;
  };

  const stopScanner = () => {
    releaseWakeLock();
    setStatus('idle');
  };

  const startScanner = async () => {
    setCameraError(null);

    // User-gesture-unlocked audio context so beeps work on first detection too.
    playBeep(0.0001);

    try {
      if ('wakeLock' in navigator) {
        wakeLockRef.current = await navigator.wakeLock.request('screen');
      }
    } catch {
      // Kiosk convenience only — never block scanning on it.
    }

    setStatus('running');
  };

  useEffect(
    () => () => {
      if (overlayTimerRef.current) clearTimeout(overlayTimerRef.current);
      releaseWakeLock();
    },
    []
  );

  const overlayClasses =
    overlay?.kind === 'ok'
      ? 'bg-lime-500/95 text-[#0b0b0d]'
      : overlay?.kind === 'warning'
        ? 'bg-amber-500/95 text-[#0b0b0d]'
        : 'bg-red-500/95 text-white';

  return (
    <div className="px-5 py-8 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <button
        type="button"
        onClick={() => (hasInternalHistory ? navigate(-1) : navigate('/check-ins'))}
        className="inline-flex items-center gap-[7px] -ml-2 px-2 min-h-11 text-zinc-400 hover:text-zinc-200 text-[14.5px] font-semibold mb-3 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" strokeWidth={2.2} />
        {hasInternalHistory ? 'Indietro' : 'Torna alle presenze'}
      </button>

      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-1.5">
        Scanner QR
      </h1>
      <p className="text-zinc-400 text-[14.5px] mb-6">
        Inquadra la tessera del socio per registrare l&apos;ingresso.
      </p>

      {status === 'idle' && (
        <button
          type="button"
          onClick={startScanner}
          className="w-full inline-flex items-center justify-center gap-2.5 py-[18px] px-8 bg-lime-400 text-[#0b0b0d] font-bold text-lg rounded-2xl hover:bg-lime-300 active:scale-[0.98] transition-all duration-150"
        >
          <ScanLine className="w-6 h-6" strokeWidth={2.2} />
          Avvia scanner
        </button>
      )}

      {cameraError && (
        <div className="mb-4 px-4 py-3 bg-red-400/10 border border-red-400/20 rounded-xl text-red-400 text-sm">
          {cameraError}
        </div>
      )}

      {status === 'error' && (
        <button
          type="button"
          onClick={startScanner}
          className="w-full inline-flex items-center justify-center gap-2.5 py-3.5 px-5 rounded-xl bg-[#1c1c1f] border border-white/9 text-zinc-100 font-semibold hover:bg-zinc-800 transition-colors"
        >
          <ScanLine className="w-[18px] h-[18px]" strokeWidth={2.2} />
          Riprova
        </button>
      )}

      {status === 'running' && (
        <>
          <div className="rounded-2xl overflow-hidden">
            <Suspense
              fallback={
                <div className="flex items-center justify-center py-12">
                  <LoadingSpinner />
                </div>
              }
            >
              <Scanner
                onScan={handleScan}
                onError={handleScannerError}
                constraints={{ facingMode: 'environment' }}
                formats={['qr_code']}
                sound={false}
                components={{ finder: true, torch: true }}
              />
            </Suspense>
          </div>
          <button
            type="button"
            onClick={stopScanner}
            className="mt-4 w-full inline-flex items-center justify-center gap-2 py-3 px-5 rounded-xl bg-[#1c1c1f] border border-white/9 text-zinc-100 font-semibold hover:bg-zinc-800 transition-colors"
          >
            <Square className="w-4 h-4" strokeWidth={2} />
            Ferma scanner
          </button>
        </>
      )}

      {overlay && (
        <div
          role="alert"
          aria-live="assertive"
          className={`fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 px-8 text-center ${overlayClasses}`}
        >
          {overlay.kind === 'ok' && <CheckCircle2 className="w-20 h-20" strokeWidth={2} />}
          {overlay.kind === 'warning' && <AlertTriangle className="w-20 h-20" strokeWidth={2} />}
          {overlay.kind === 'error' && <XCircle className="w-20 h-20" strokeWidth={2} />}
          <p className="font-display font-bold text-3xl uppercase">{overlay.title}</p>
          {overlay.kind === 'ok' && overlay.subtitle && <p className="text-lg font-semibold">{overlay.subtitle}</p>}
          {overlay.kind === 'warning' && (
            <ul className="text-base font-semibold list-disc list-inside">
              {overlay.warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

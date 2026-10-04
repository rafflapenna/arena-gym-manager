import { useRef } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { Download, Printer } from 'lucide-react';

type QrCodeDisplayProps = {
  token: string;
  memberFullName: string;
};

/** Slugifies a name for use as a download filename (lowercase, ASCII, dash-separated). */
const DIACRITICS_RE = new RegExp('[\\u0300-\\u036f]', 'g');

/** Escapes text for safe interpolation into HTML built via document.write. */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function slugify(name: string): string {
  return (
    name
      .normalize('NFD')
      .replace(DIACRITICS_RE, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'socio'
  );
}

/**
 * Reusable QR card: renders the identity token as a scannable QR code with the
 * member's name in clear text, plus "Download PNG" and "Print" actions.
 * Used both in the backoffice (`MemberDetailPage`) and by the member (`MyQrPage`).
 */
export function QrCodeDisplay({ token, memberFullName }: QrCodeDisplayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const handleDownload = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const url = canvas.toDataURL('image/png');
    const a = document.createElement('a');
    a.href = url;
    a.download = `tessera-qr-${slugify(memberFullName)}.png`;
    a.click();
  };

  const handlePrint = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const url = canvas.toDataURL('image/png');
    const printWindow = window.open('', '_blank', 'width=420,height=560');
    if (!printWindow) {
      alert('Impossibile aprire la finestra di stampa. Controlla il blocco popup del browser.');
      return;
    }
    const safeName = escapeHtml(memberFullName);
    const safeToken = escapeHtml(token);
    printWindow.document.write(`
      <!doctype html>
      <html lang="it">
        <head>
          <meta charset="utf-8" />
          <title>Tessera QR — ${safeName}</title>
          <style>
            body { font-family: system-ui, sans-serif; text-align: center; padding: 32px; }
            img { width: 260px; height: 260px; }
            h1 { font-size: 18px; margin: 20px 0 4px; }
            p { font-size: 12px; color: #555; word-break: break-all; }
          </style>
        </head>
        <body>
          <img src="${url}" alt="QR Code" />
          <h1>${safeName}</h1>
          <p>${safeToken}</p>
        </body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.onload = () => printWindow.print();
    // Fallback in case onload doesn't fire (some browsers with document.write).
    setTimeout(() => printWindow.print(), 300);
  };

  return (
    <div className="flex flex-col items-center gap-4 bg-[#161618] border border-white/6 rounded-2xl p-6">
      <div className="bg-white p-3 rounded-xl">
        <QRCodeCanvas ref={canvasRef} value={token} size={200} level="M" marginSize={0} />
      </div>
      <div className="text-center">
        <p className="text-zinc-100 text-[16px] font-bold">{memberFullName}</p>
        <p className="text-zinc-500 text-[12px] mt-1 break-all" style={{ overflowWrap: 'anywhere' }}>
          {token}
        </p>
      </div>
      <div className="flex gap-2.5 w-full">
        <button
          type="button"
          onClick={handleDownload}
          className="flex-1 inline-flex items-center justify-center gap-2 text-[14px] font-semibold py-2.5 px-4 rounded-xl bg-[#1c1c1f] border border-white/9 text-zinc-100 hover:bg-zinc-800 transition-colors"
        >
          <Download className="w-4 h-4" strokeWidth={1.9} />
          Scarica PNG
        </button>
        <button
          type="button"
          onClick={handlePrint}
          className="flex-1 inline-flex items-center justify-center gap-2 text-[14px] font-semibold py-2.5 px-4 rounded-xl bg-[#1c1c1f] border border-white/9 text-zinc-100 hover:bg-zinc-800 transition-colors"
        >
          <Printer className="w-4 h-4" strokeWidth={1.9} />
          Stampa
        </button>
      </div>
    </div>
  );
}

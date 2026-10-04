import { Link } from 'react-router-dom';
import { ChevronLeft, Sun } from 'lucide-react';
import { useMyQr } from './useQr';
import { useMyProfile } from '../member/useMe';
import { useWakeLock } from '../../hooks/useWakeLock';
import { QrCodeDisplay } from '../../components/qr/QrCodeDisplay';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';

/** Member's own QR card (`/me/qr`), scannable at reception for a fast check-in. */
export function MyQrPage() {
  const { data: qr, isLoading, isError } = useMyQr();
  const { data: profile } = useMyProfile();

  const fullName = profile
    ? `${profile.personalInfo.firstName} ${profile.personalInfo.lastName}`
    : 'Tessera QR';

  // Schermo acceso finché la tessera è a video — la reception deve poter scansionare senza
  // che il telefono si blocchi a metà. Attivo solo quando il QR è davvero mostrato.
  useWakeLock(!isLoading && !isError && !!qr);

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <Link
        to="/me/check-ins"
        className="inline-flex items-center gap-[7px] -ml-2 px-2 min-h-11 text-zinc-400 hover:text-zinc-200 text-[14.5px] font-semibold mb-3 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" strokeWidth={2.2} />
        Torna alle presenze
      </Link>

      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-1.5">
        La mia tessera QR
      </h1>
      <p className="text-zinc-400 text-[14.5px] mb-6">
        Mostra questo codice alla reception per registrare l&apos;ingresso senza digitare nulla.
      </p>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError || !qr ? (
        <ErrorMessage message="Errore nel caricamento della tessera QR." />
      ) : (
        <>
          <QrCodeDisplay token={qr.token} memberFullName={fullName} />
          <p className="flex items-center justify-center gap-1.5 mt-4 text-zinc-500 text-[13px]">
            <Sun className="w-[14px] h-[14px] shrink-0" strokeWidth={1.9} />
            Alza la luminosità dello schermo per una lettura più rapida.
          </p>
        </>
      )}
    </div>
  );
}

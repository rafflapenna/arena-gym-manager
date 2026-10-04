import { Link } from 'react-router-dom';
import { QrCode, UserPlus, ChevronRight } from 'lucide-react';
import { useMyFederationCards, useMyProfile } from './useMe';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';

export function MyProfilePage() {
  const { data, isLoading, isError } = useMyProfile();
  const { data: cards } = useMyFederationCards();

  const personal = data?.personalInfo;

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-[18px]">
        Il mio profilo
      </h1>

      <Link
        to="/me/qr"
        className="flex items-center gap-3 bg-[#1c1c1f] border border-white/6 rounded-[16px] px-4 py-[14px] mb-3.5 hover:bg-white/[.04] transition-colors"
      >
        <span className="w-10 h-10 rounded-xl bg-lime-400/12 flex items-center justify-center shrink-0">
          <QrCode className="w-5 h-5 text-lime-400" strokeWidth={1.9} />
        </span>
        <span className="flex-1 min-w-0">
          <p className="text-zinc-100 text-[15px] font-semibold">La mia tessera QR</p>
          <p className="text-zinc-500 text-[13px]">Mostrala alla reception per un ingresso rapido</p>
        </span>
        <ChevronRight className="w-[18px] h-[18px] text-zinc-600 shrink-0" strokeWidth={2} />
      </Link>

      <Link
        to="/me/friends"
        className="flex items-center gap-3 bg-[#1c1c1f] border border-white/6 rounded-[16px] px-4 py-[14px] mb-3.5 hover:bg-white/[.04] transition-colors"
      >
        <span className="w-10 h-10 rounded-xl bg-lime-400/12 flex items-center justify-center shrink-0">
          <UserPlus className="w-5 h-5 text-lime-400" strokeWidth={1.9} />
        </span>
        <span className="flex-1 min-w-0">
          <p className="text-zinc-100 text-[15px] font-semibold">Porta un amico</p>
          <p className="text-zinc-500 text-[13px]">Segnala una prova gratuita e matura uno sconto</p>
        </span>
        <ChevronRight className="w-[18px] h-[18px] text-zinc-600 shrink-0" strokeWidth={2} />
      </Link>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <ErrorMessage message="Errore nel caricamento del profilo." />
      ) : (
        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-[18px]">
            <ProfileField label="Nome" value={personal?.firstName} />
            <ProfileField label="Cognome" value={personal?.lastName} />
            <ProfileField label="Email" value={personal?.email} />
            {personal?.beltGrade && <ProfileField label="Cintura" value={personal.beltGrade.label} />}
            {cards?.map((c) => (
              <ProfileField key={c.federation.id} label={`N. tessera ${c.federation.name}`} value={c.cardNumber} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function ProfileField({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="min-w-0">
      <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">{label}</p>
      <p className="text-zinc-100 text-[15.5px]" style={{ overflowWrap: 'anywhere' }}>{value ?? '—'}</p>
    </div>
  );
}

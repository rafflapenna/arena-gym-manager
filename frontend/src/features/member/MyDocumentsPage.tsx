import { useState } from 'react';
import { Download, FileText, ShieldCheck } from 'lucide-react';
import { useMyDocuments, useDownloadMyDocument } from './useMe';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Badge } from '../../components/ui/Badge';
import { getApiErrorMessage } from '../../utils/apiError';
import { medicalCertStatus, medicalCertBadgeVariant, medicalCertStatusLabel } from '../../utils/documentStatus';
import { formatDayMonthYear } from '../../utils/dateTime';
import type { MemberDocumentResponse } from '../../types';

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Read-only "my documents" view for the MEMBER area: list + download. */
export function MyDocumentsPage() {
  const { data: documents = [], isLoading, isError } = useMyDocuments();
  const downloadDocument = useDownloadMyDocument();
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);

  const handleDownload = (doc: MemberDocumentResponse) => {
    setDownloadError(null);
    setPendingId(doc.id);
    downloadDocument.mutate(doc, {
      onError: (err) => setDownloadError(getApiErrorMessage(err)),
      onSettled: () => setPendingId(null),
    });
  };

  const medicalCert = documents.find((d) => d.type === 'MEDICAL_CERT');
  const certStatus = medicalCertStatus(medicalCert?.expiryDate);

  return (
    <div className="px-5 py-8 md:px-8 max-w-3xl mx-auto font-body">
      <div className="flex items-center justify-between gap-3 mb-6 flex-wrap">
        <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none">Documenti</h1>
        <Badge variant={medicalCertBadgeVariant[certStatus]}>
          Certificato medico: {medicalCertStatusLabel[certStatus]}
        </Badge>
      </div>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei documenti." />}
      {downloadError && (
        <div className="mb-4">
          <ErrorMessage message={downloadError} />
        </div>
      )}

      {!isLoading && !isError && (
        documents.length === 0 ? (
          <div className="bg-zinc-900 border border-white/5 rounded-2xl p-6 text-center text-zinc-500 text-sm">
            Nessun documento disponibile.
          </div>
        ) : (
          <div className="bg-zinc-900 border border-white/5 rounded-2xl px-5">
            {documents.map((doc) => (
              <div key={doc.id} className="flex items-center gap-3 py-4 border-b border-white/5 last:border-b-0">
                <div className="w-9 h-9 rounded-lg bg-white/6 flex items-center justify-center shrink-0">
                  {doc.type === 'MEDICAL_CERT' ? (
                    <ShieldCheck className="w-[16px] h-[16px] text-lime-400" strokeWidth={1.8} />
                  ) : (
                    <FileText className="w-[16px] h-[16px] text-zinc-400" strokeWidth={1.8} />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-zinc-100 text-[14.5px] font-semibold truncate">
                    {doc.title || doc.originalFilename}
                  </p>
                  <p className="text-zinc-500 text-[13px] truncate">
                    {doc.originalFilename} · {formatFileSize(doc.sizeBytes)} · {formatDayMonthYear(doc.uploadedAt)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleDownload(doc)}
                  disabled={pendingId === doc.id && downloadDocument.isPending}
                  title="Scarica"
                  aria-label={`Scarica ${doc.title || doc.originalFilename}`}
                  className="p-2 rounded-lg text-zinc-400 hover:text-lime-400 hover:bg-white/5 transition-colors disabled:opacity-50 shrink-0"
                >
                  <Download className="w-[16px] h-[16px]" strokeWidth={1.9} />
                </button>
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}

import { useState } from 'react';
import { Download, Trash2, FileText, ShieldCheck } from 'lucide-react';
import { useMemberDocuments, useDeleteDocument, useDownloadDocument } from './useDocuments';
import { DocumentUploadControl } from './DocumentUploadControl';
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

/** The medical cert with the latest expiryDate stands for the member's current cert status. */
function pickCurrentMedicalCert(docs: MemberDocumentResponse[]): MemberDocumentResponse | null {
  const certs = docs.filter((d) => d.type === 'MEDICAL_CERT');
  if (certs.length === 0) return null;
  return certs.reduce((latest, d) => {
    if (!latest.expiryDate) return d;
    if (!d.expiryDate) return latest;
    return d.expiryDate > latest.expiryDate ? d : latest;
  });
}

type RowProps = {
  doc: MemberDocumentResponse;
  onDownload: (doc: MemberDocumentResponse) => void;
  onRequestDelete: (doc: MemberDocumentResponse) => void;
  onConfirmDelete: (doc: MemberDocumentResponse) => void;
  onCancelDelete: () => void;
  downloading: boolean;
  deleting: boolean;
  confirming: boolean;
};

function DocumentRow({
  doc, onDownload, onRequestDelete, onConfirmDelete, onCancelDelete, downloading, deleting, confirming,
}: RowProps) {
  if (confirming) {
    return (
      <div className="flex items-center justify-between gap-3 py-3 border-b border-white/5 last:border-b-0 flex-wrap">
        <span className="text-amber-400 text-[13.5px]">
          Eliminare &quot;{doc.title || doc.originalFilename}&quot;? Non reversibile.
        </span>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => onConfirmDelete(doc)}
            disabled={deleting}
            className="bg-red-500 hover:bg-red-400 text-white font-bold px-3 py-1.5 rounded-lg text-xs transition-colors disabled:opacity-50"
          >
            {deleting ? 'Eliminazione…' : 'Elimina'}
          </button>
          <button
            type="button"
            onClick={onCancelDelete}
            className="text-zinc-500 hover:text-zinc-300 text-xs px-3 py-1.5 rounded-lg border border-zinc-700 transition-colors"
          >
            Annulla
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3 py-3 border-b border-white/5 last:border-b-0">
      <div className="w-9 h-9 rounded-lg bg-white/6 flex items-center justify-center shrink-0">
        <FileText className="w-[16px] h-[16px] text-zinc-400" strokeWidth={1.8} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-zinc-100 text-[14.5px] font-semibold truncate">{doc.title || doc.originalFilename}</p>
        <p className="text-zinc-500 text-[13px] truncate">
          {doc.originalFilename} · {formatFileSize(doc.sizeBytes)} · {formatDayMonthYear(doc.uploadedAt)}
        </p>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        <button
          type="button"
          onClick={() => onDownload(doc)}
          disabled={downloading}
          title="Scarica"
          aria-label={`Scarica ${doc.title || doc.originalFilename}`}
          className="p-2 rounded-lg text-zinc-400 hover:text-lime-400 hover:bg-white/5 transition-colors disabled:opacity-50"
        >
          <Download className="w-[16px] h-[16px]" strokeWidth={1.9} />
        </button>
        <button
          type="button"
          onClick={() => onRequestDelete(doc)}
          disabled={deleting}
          title="Elimina"
          aria-label={`Elimina ${doc.title || doc.originalFilename}`}
          className="p-2 rounded-lg text-zinc-400 hover:text-red-400 hover:bg-white/5 transition-colors disabled:opacity-50"
        >
          <Trash2 className="w-[16px] h-[16px]" strokeWidth={1.9} />
        </button>
      </div>
    </div>
  );
}

export function MemberDocumentsSection({ memberId }: { memberId: number }) {
  const { data: documents = [], isLoading, isError } = useMemberDocuments(memberId);
  const deleteDocument = useDeleteDocument(memberId);
  const downloadDocument = useDownloadDocument(memberId);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  const handleDownload = (doc: MemberDocumentResponse) => {
    setActionError(null);
    setPendingId(doc.id);
    downloadDocument.mutate(doc, {
      onError: (err) => setActionError(getApiErrorMessage(err)),
      onSettled: () => setPendingId(null),
    });
  };

  const handleRequestDelete = (doc: MemberDocumentResponse) => {
    setActionError(null);
    setConfirmDeleteId(doc.id);
  };

  const handleConfirmDelete = (doc: MemberDocumentResponse) => {
    setActionError(null);
    setPendingId(doc.id);
    deleteDocument.mutate(doc.id, {
      onSuccess: () => setConfirmDeleteId(null),
      onError: (err) => setActionError(getApiErrorMessage(err)),
      onSettled: () => setPendingId(null),
    });
  };

  const currentCert = pickCurrentMedicalCert(documents);
  const certStatus = medicalCertStatus(currentCert?.expiryDate);
  const otherDocs = documents.filter((d) => d.id !== currentCert?.id);

  return (
    <div className="flex flex-col gap-4 mt-4">
      <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
        <div className="flex items-center justify-between gap-3 mb-3 flex-wrap">
          <p className="text-zinc-100 text-[15.5px] font-bold flex items-center gap-2">
            <ShieldCheck className="w-[18px] h-[18px] text-lime-400" strokeWidth={1.9} />
            Documenti
          </p>
          <Badge variant={medicalCertBadgeVariant[certStatus]}>
            Certificato medico: {medicalCertStatusLabel[certStatus]}
          </Badge>
        </div>

        {actionError && (
          <div className="mb-3">
            <ErrorMessage message={actionError} />
          </div>
        )}

        {isLoading ? (
          <LoadingSpinner />
        ) : isError ? (
          <ErrorMessage message="Errore nel caricamento dei documenti." />
        ) : (
          <>
            {currentCert ? (
              <DocumentRow
                doc={currentCert}
                onDownload={handleDownload}
                onRequestDelete={handleRequestDelete}
                onConfirmDelete={handleConfirmDelete}
                onCancelDelete={() => setConfirmDeleteId(null)}
                downloading={pendingId === currentCert.id && downloadDocument.isPending}
                deleting={pendingId === currentCert.id && deleteDocument.isPending}
                confirming={confirmDeleteId === currentCert.id}
              />
            ) : (
              <p className="text-zinc-500 text-[14.5px] py-1">Nessun certificato medico caricato.</p>
            )}

            {otherDocs.length > 0 && (
              <div className="mt-2 pt-1 border-t border-white/6">
                {otherDocs.map((doc) => (
                  <DocumentRow
                    key={doc.id}
                    doc={doc}
                    onDownload={handleDownload}
                    onRequestDelete={handleRequestDelete}
                    onConfirmDelete={handleConfirmDelete}
                    onCancelDelete={() => setConfirmDeleteId(null)}
                    downloading={pendingId === doc.id && downloadDocument.isPending}
                    deleting={pendingId === doc.id && deleteDocument.isPending}
                    confirming={confirmDeleteId === doc.id}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </div>

      <DocumentUploadControl memberId={memberId} />
    </div>
  );
}

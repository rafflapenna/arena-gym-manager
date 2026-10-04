import { useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { Upload, FileText } from 'lucide-react';
import { useUploadDocument } from './useDocuments';
import { getApiErrorMessage } from '../../utils/apiError';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import type { DocumentType } from '../../types';

const ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

const inputClass =
  'w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors';
const labelClass = 'block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5';

type Props = { memberId: number };

/** Upload form for a member's medical certificate or a free-title generic document. */
export function DocumentUploadControl({ memberId }: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const uploadDocument = useUploadDocument(memberId);
  const [type, setType] = useState<DocumentType>('MEDICAL_CERT');
  const [title, setTitle] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const resetForm = () => {
    setFile(null);
    setTitle('');
    setExpiryDate('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0] ?? null;
    setClientError(null);
    setSuccess(false);
    if (!selected) {
      setFile(null);
      return;
    }
    if (!ALLOWED_MIME_TYPES.includes(selected.type)) {
      setFile(null);
      setClientError('Formato non ammesso. Sono accettati PDF, JPEG o PNG.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    if (selected.size > MAX_SIZE_BYTES) {
      setFile(null);
      setClientError('Il file supera la dimensione massima di 10 MB.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    setFile(selected);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);
    setSuccess(false);

    if (!file) {
      setClientError('Seleziona un file da caricare.');
      return;
    }
    if (type === 'OTHER' && !title.trim()) {
      setClientError('Inserisci un titolo per il documento.');
      return;
    }
    setClientError(null);

    try {
      await uploadDocument.mutateAsync({
        file,
        type,
        title: title.trim() || null,
        expiryDate: type === 'MEDICAL_CERT' && expiryDate ? expiryDate : null,
      });
      setSuccess(true);
      resetForm();
    } catch (err) {
      const status = err instanceof AxiosError ? err.response?.status : undefined;
      if (status === 413) {
        setApiError('Il file è troppo grande. La dimensione massima è 10 MB.');
      } else {
        setApiError(getApiErrorMessage(err));
      }
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-[#161618] border border-white/6 rounded-2xl p-[18px] flex flex-col gap-4"
    >
      <div className="flex items-center gap-2">
        <FileText className="w-[18px] h-[18px] text-lime-400" strokeWidth={1.9} />
        <p className="text-zinc-100 text-[15.5px] font-bold">Carica documento</p>
      </div>

      <div>
        <label className={labelClass}>Tipo di documento</label>
        <div className="inline-flex rounded-xl bg-zinc-900 border border-white/5 p-1 gap-1">
          <button
            type="button"
            onClick={() => setType('MEDICAL_CERT')}
            className={`px-4 py-2 rounded-lg text-[13.5px] font-semibold transition-colors ${
              type === 'MEDICAL_CERT' ? 'bg-lime-400 text-[#0b0b0d]' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Certificato medico
          </button>
          <button
            type="button"
            onClick={() => setType('OTHER')}
            className={`px-4 py-2 rounded-lg text-[13.5px] font-semibold transition-colors ${
              type === 'OTHER' ? 'bg-lime-400 text-[#0b0b0d]' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Altro documento
          </button>
        </div>
      </div>

      <div>
        <label className={labelClass} htmlFor="doc-title">
          Titolo {type === 'OTHER' ? '*' : '(opzionale)'}
        </label>
        <input
          id="doc-title"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={type === 'OTHER' ? 'Es. Contratto, Liberatoria…' : 'Es. Certificato agonistico'}
          className={inputClass}
        />
      </div>

      {type === 'MEDICAL_CERT' && (
        <div>
          <label className={labelClass} htmlFor="doc-expiry">
            Data di scadenza
          </label>
          <input
            id="doc-expiry"
            type="date"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
            className={inputClass}
          />
        </div>
      )}

      <div>
        <label className={labelClass} htmlFor="doc-file">
          File (PDF, JPEG o PNG, max 10 MB)
        </label>
        <input
          id="doc-file"
          ref={fileInputRef}
          type="file"
          accept="application/pdf,image/jpeg,image/png"
          onChange={handleFileChange}
          className="w-full text-zinc-300 text-sm file:mr-4 file:py-2.5 file:px-4 file:rounded-lg file:border-0 file:bg-zinc-800 file:text-zinc-100 file:text-sm file:font-semibold file:cursor-pointer hover:file:bg-zinc-700 cursor-pointer"
        />
      </div>

      {clientError && <ErrorMessage message={clientError} />}
      {apiError && <ErrorMessage message={apiError} />}
      {success && (
        <div className="px-4 py-3 bg-lime-400/10 border border-lime-400/20 rounded-xl text-lime-400 text-sm">
          Documento caricato con successo.
        </div>
      )}

      <button
        type="submit"
        disabled={uploadDocument.isPending}
        className="inline-flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3 rounded-xl hover:bg-lime-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        <Upload className="w-[16px] h-[16px]" strokeWidth={2.2} />
        {uploadDocument.isPending ? 'Caricamento…' : 'Carica'}
      </button>
    </form>
  );
}

import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft, Save, UserPlus } from 'lucide-react';
import { useMember, useCreateMember, useUpdateMember } from './useMembers';
import { getApiErrorMessage } from '../../utils/apiError';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Button } from '../../components/ui/Button';
import { StickyActions } from '../../components/ui/StickyActions';
import { MemberPersonalFields } from './MemberPersonalFields';
import type { MemberPersonalFieldsValue } from './MemberPersonalFields';
import { loadDraft, saveDraft, clearDraft } from '../../utils/formDraft';
import { todayIsoLocal } from '../../utils/dateTime';
import type { MemberRequest, MemberResponse } from '../../types';

type FormData = MemberPersonalFieldsValue;

const DRAFT_KEY = 'member-form-draft';

function emptyForm(): FormData {
  return {
    firstName: '', lastName: '', email: '', phone: '',
    birthDate: '', birthPlace: '', fiscalCode: '', gender: '',
    address: '', city: '', postalCode: '', province: '', region: '',
    country: 'Italia', beltGradeId: '', registrationDate: todayIsoLocal(), notes: '',
    referredBy: null,
  };
}

function memberToForm(m: MemberResponse): FormData {
  const pi = m.personalInfo;
  return {
    firstName: pi.firstName,
    lastName: pi.lastName,
    email: pi.email,
    phone: pi.phone ?? '',
    birthDate: pi.birthDate ?? '',
    birthPlace: pi.birthPlace ?? '',
    fiscalCode: pi.fiscalCode ?? '',
    gender: pi.gender ?? '',
    address: pi.address ?? '',
    city: pi.city ?? '',
    postalCode: pi.postalCode ?? '',
    province: pi.province ?? '',
    region: pi.region ?? '',
    country: pi.country ?? 'Italia',
    beltGradeId: pi.beltGrade?.id.toString() ?? '',
    registrationDate: m.registrationDate ?? '',
    notes: m.notes ?? '',
    referredBy: m.referredBy,
  };
}

type SubmitAction = 'save' | 'saveAndNew';

function FormInner({
  initialForm,
  isEdit,
  memberId,
}: {
  initialForm: FormData;
  isEdit: boolean;
  memberId: number;
}) {
  const navigate = useNavigate();
  const createMember = useCreateMember();
  const updateMember = useUpdateMember(memberId);
  const [form, setForm] = useState<FormData>(() => {
    if (isEdit) return initialForm;
    const draft = loadDraft<FormData>(DRAFT_KEY);
    return draft ? { ...initialForm, ...draft } : initialForm;
  });
  const [apiError, setApiError] = useState<string | null>(null);
  const [savedAndNewMessage, setSavedAndNewMessage] = useState<string | null>(null);
  // Remounts MemberPersonalFields after "Salva e nuovo" so its internal state (which
  // fields were CF-derived, the Altro open/closed toggle…) resets along with the form.
  const [formInstance, setFormInstance] = useState(0);

  // Draft recovery is create-only: an edit always starts from the saved member, and
  // persisting every keystroke of an edit would risk clobbering a draft from a later,
  // unrelated "Nuovo socio" attempt that reuses the same key.
  useEffect(() => {
    if (!isEdit) saveDraft(DRAFT_KEY, form);
  }, [form, isEdit]);

  const handleSubmit = (action: SubmitAction) => async (e: React.SyntheticEvent) => {
    e.preventDefault();
    setApiError(null);
    setSavedAndNewMessage(null);

    const data: MemberRequest = {
      personalInfo: {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        phone: form.phone || null,
        birthDate: form.birthDate || null,
        birthPlace: form.birthPlace || null,
        fiscalCode: form.fiscalCode || null,
        gender: form.gender || null,
        address: form.address || null,
        city: form.city || null,
        postalCode: form.postalCode || null,
        province: form.province || null,
        region: form.region || null,
        country: form.country || null,
        beltGradeId: form.beltGradeId ? Number(form.beltGradeId) : null,
      },
      registrationDate: form.registrationDate || null,
      notes: form.notes || null,
      referredByMemberId: form.referredBy?.id ?? null,
    };

    try {
      const saved = isEdit
        ? await updateMember.mutateAsync(data)
        : await createMember.mutateAsync(data);

      clearDraft(DRAFT_KEY);

      if (!isEdit && action === 'saveAndNew') {
        setForm(emptyForm());
        setFormInstance((n) => n + 1);
        setSavedAndNewMessage(
          `${saved.personalInfo.firstName} ${saved.personalInfo.lastName} salvato. Pronto per il prossimo.`,
        );
        return;
      }

      navigate(`/members/${saved.id}`);
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  const isPending = createMember.isPending || updateMember.isPending;

  return (
    <>
      {apiError && <div className="mb-4"><ErrorMessage message={apiError} /></div>}
      {savedAndNewMessage && (
        <div className="mb-4 bg-lime-400/10 border border-lime-400/20 rounded-xl p-4 text-lime-300 text-sm">
          {savedAndNewMessage}
        </div>
      )}

      <form onSubmit={handleSubmit('save')} className="flex flex-col gap-5">
        <MemberPersonalFields
          key={formInstance}
          value={form}
          onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          mode={isEdit ? 'edit' : 'create'}
          memberId={memberId || undefined}
        />

        <StickyActions className="flex flex-col sm:flex-row gap-3">
          <Button type="submit" loading={isPending} className="flex-1 py-4">
            <Save className="w-5 h-5" />
            {isPending ? 'Salvataggio…' : 'Salva'}
          </Button>
          {!isEdit && (
            <Button type="button" variant="secondary" onClick={handleSubmit('saveAndNew')} disabled={isPending} className="flex-1 py-4">
              <UserPlus className="w-5 h-5" />
              Salva e nuovo
            </Button>
          )}
        </StickyActions>
      </form>
    </>
  );
}

export function MemberFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const memberId = isEdit ? Number(id) : 0;

  const { data: member, isLoading } = useMember(memberId);

  if (isEdit && isLoading) return <LoadingSpinner />;
  if (isEdit && !member) return <div className="p-8"><ErrorMessage message="Socio non trovato." /></div>;

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <Link
        to={isEdit ? `/members/${memberId}` : '/members'}
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        {isEdit ? 'Dettaglio' : 'Soci'}
      </Link>

      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        {isEdit ? 'Modifica Socio' : 'Nuovo Socio'}
      </h1>

      <FormInner
        initialForm={member ? memberToForm(member) : emptyForm()}
        isEdit={isEdit}
        memberId={memberId}
      />
    </div>
  );
}

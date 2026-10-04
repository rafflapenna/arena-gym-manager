import { useEffect, useState } from 'react';
import { Save } from 'lucide-react';
import { useCreateMember } from '../../members/useMembers';
import { getApiErrorMessage } from '../../../utils/apiError';
import { ErrorMessage } from '../../../components/ui/ErrorMessage';
import { Button } from '../../../components/ui/Button';
import { StickyActions } from '../../../components/ui/StickyActions';
import { MemberPersonalFields } from '../../members/MemberPersonalFields';
import type { MemberPersonalFieldsValue } from '../../members/MemberPersonalFields';
import { loadDraft, saveDraft, clearDraft } from '../../../utils/formDraft';
import { todayIsoLocal } from '../../../utils/dateTime';
import type { MemberRequest, MemberResponse } from '../../../types';

type FormData = MemberPersonalFieldsValue;

const DRAFT_KEY = 'enrollment-member-draft';

function emptyForm(): FormData {
  return {
    firstName: '', lastName: '', email: '', phone: '',
    birthDate: '', birthPlace: '', fiscalCode: '', gender: '',
    address: '', city: '', postalCode: '', province: '', region: '',
    country: 'Italia', beltGradeId: '', registrationDate: todayIsoLocal(), notes: '',
    referredBy: null,
  };
}

type MemberStepProps = {
  onCommit: (member: MemberResponse) => void;
};

/** Step 1 — same fields as the standalone member form (shared via `MemberPersonalFields`),
 *  wired to the wizard's commit-per-step flow. */
export function MemberStep({ onCommit }: MemberStepProps) {
  const createMember = useCreateMember();
  const [form, setForm] = useState<FormData>(() => {
    const draft = loadDraft<FormData>(DRAFT_KEY);
    return draft ? { ...emptyForm(), ...draft } : emptyForm();
  });
  const [apiError, setApiError] = useState<string | null>(null);

  useEffect(() => {
    saveDraft(DRAFT_KEY, form);
  }, [form]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

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
      const member = await createMember.mutateAsync(data);
      clearDraft(DRAFT_KEY);
      onCommit(member);
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      {apiError && <ErrorMessage message={apiError} />}

      <MemberPersonalFields
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        mode="create"
      />

      <StickyActions>
        <Button type="submit" loading={createMember.isPending} className="w-full py-4">
          <Save className="w-5 h-5" />
          {createMember.isPending ? 'Salvataggio…' : 'Continua'}
        </Button>
      </StickyActions>
    </form>
  );
}

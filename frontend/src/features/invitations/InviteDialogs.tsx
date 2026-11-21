import {useState, type FormEvent} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Copy} from 'lucide-react';
import type {components} from '../../api/generated/auth-service';
import {authApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import type {OrgRole} from '../../auth/roles';
import {Button} from '../../ui/Button';
import {Dialog} from '../../ui/Dialog';
import {InlineAlert} from '../../ui/InlineAlert';
import {Select} from '../../ui/Select';
import {TextField} from '../../ui/TextField';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {inviteStrings as t} from './strings';

type Created = components['schemas']['CreatedInvitation'];

export interface InviteDialogProps {
    orgId: string;
    isOpen: boolean;
    onOpenChange: (open: boolean) => void;
    roles: readonly OrgRole[];
    seats: {used: number; limit: number | null};
    onInvited: (created: Created) => void;
}

/** AC-3.3: invite by email with a role within the caller's ceiling. */
export function InviteDialog({orgId, isOpen, onOpenChange, roles, seats, onInvited}: InviteDialogProps) {
    const queryClient = useQueryClient();
    const [email, setEmail] = useState('');
    const [role, setRole] = useState<OrgRole>('MEMBER');
    const [errors, setErrors] = useState<{email?: string[]; role?: string[]}>({});
    const [formError, setFormError] = useState<string | null>(null);

    const invite = useMutation({
        // The created invitation carries the token: keep it out of the mutation cache once used.
        gcTime: 0,
        mutationFn: () => unwrap(authApi.POST('/api/orgs/{orgId}/invitations', {params: {path: {orgId}}, body: {email, role}})),
        onSuccess: (created) => {
            void queryClient.invalidateQueries({queryKey: ['orgs', orgId, 'invitations']});
            setEmail('');
            onInvited(created);
            invite.reset();
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'SEAT_LIMIT_REACHED') setFormError(t.seatFull(seats.used, seats.limit ?? seats.used));
            else if (ui.code === 'ALREADY_MEMBER') setErrors({email: [t.alreadyMember]});
            else if (ui.code === 'INVITATION_PENDING') setErrors({email: [t.alreadyInvited]});
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['email', 'role']);
                setErrors(split.fields);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else setFormError(errorText(ui));
        },
    });

    function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        if (email.trim() === '') {
            setErrors({email: [t.emailRequired]});
            return;
        }
        setErrors({});
        invite.mutate();
    }

    return (
        <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={t.inviteTitle} size="md">
            <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
                {formError ? (
                    <InlineAlert tone="danger" role="alert">
                        {formError}
                    </InlineAlert>
                ) : null}
                <TextField id="invite-email" label={t.email} type="email" value={email} onChange={setEmail} errors={errors.email} autoComplete="off" required/>
                <Select
                    label={t.role}
                    options={roles.map((r) => ({id: r, label: r}))}
                    value={role}
                    onChange={(value) => {
                        const next = roles.find((r) => r === value);
                        if (next) setRole(next);
                    }}
                    errors={errors.role}
                />
                <div className="flex justify-end gap-2 pt-2">
                    <Button variant="secondary" onPress={() => onOpenChange(false)}>
                        {t.cancel}
                    </Button>
                    <Button type="submit" variant="primary" loading={invite.isPending}>
                        {t.send}
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}

/** AC-3.3: the token is shown once, as a link; closing the dialog drops it. */
export function TokenDialog({token, onClose}: { token: string | null; onClose: () => void }) {
    const link = token ? `${window.location.origin}/invite#${token}` : '';
    return (
        <Dialog
            isOpen={token !== null}
            onOpenChange={(open) => {
                if (!open) onClose();
            }}
            title={t.tokenTitle}
            size="md"
            footer={
                <>
                    <Button
                        variant="secondary"
                        onPress={() => {
                            void navigator.clipboard?.writeText(link).then(() => showToast('success', t.copied));
                        }}
                    >
                        <Copy aria-hidden="true" size={16}/>
                        {t.copy}
                    </Button>
                    <Button variant="primary" onPress={onClose}>
                        {t.done}
                    </Button>
                </>
            }
        >
            <div className="flex flex-col gap-3">
                <p>{t.tokenBody}</p>
                <TextField id="invite-link" label={t.linkLabel} value={link} onChange={() => undefined} readOnly/>
            </div>
        </Dialog>
    );
}

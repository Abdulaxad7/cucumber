import {useState, type FormEvent} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import {authApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {TextField} from '../../ui/TextField';
import {errorText} from '../../ui/errorText';
import {orgStrings as t} from './strings';

/** AC-3.1: any signed-in user creates an organization and becomes its OWNER. */
export function CreateOrgPage() {
    const router = useRouter();
    const queryClient = useQueryClient();
    const [name, setName] = useState('');
    const [nameErrors, setNameErrors] = useState<string[] | undefined>();
    const [formError, setFormError] = useState<string | null>(null);

    const create = useMutation({
        mutationFn: () => unwrap(authApi.POST('/api/orgs', {body: {name}})),
        onSuccess: (org) => {
            void queryClient.invalidateQueries({queryKey: ['orgs']});
            router.history.push(`/orgs/${org.id}/projects`);
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['name']);
                setNameErrors(split.fields.name);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else setFormError(errorText(ui));
        },
    });

    function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        if (name.trim() === '') {
            setNameErrors([t.nameRequired]);
            return;
        }
        if ([...name].length > 100) {
            setNameErrors([t.nameTooLong]);
            return;
        }
        setNameErrors(undefined);
        create.mutate();
    }

    return (
        <>
            <PageHeader title={t.createTitle} breadcrumbs={[{label: t.orgsCrumb, href: '/orgs'}, {label: t.createTitle}]}/>
            <div className="max-w-xl">
                <Card padding="lg">
                    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
                        {formError ? (
                            <InlineAlert tone="danger" role="alert">
                                {formError}
                            </InlineAlert>
                        ) : null}
                        <TextField id="org-name" label={t.nameLabel} value={name} onChange={setName} errors={nameErrors} required/>
                        <div className="flex justify-end">
                            <Button type="submit" variant="primary" loading={create.isPending}>
                                {t.create}
                            </Button>
                        </div>
                    </form>
                </Card>
            </div>
        </>
    );
}

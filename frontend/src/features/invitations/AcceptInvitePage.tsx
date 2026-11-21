import {useEffect, useState, type FormEvent} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useRouter, useRouterState} from '@tanstack/react-router';
import {CircleCheck} from 'lucide-react';
import type {components} from '../../api/generated/auth-service';
import {authApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import {startSession} from '../../auth/session';
import {useSession} from '../../auth/useSession';
import {AppLink} from '../../ui/AppLink';
import {AuthLayout} from '../../ui/AuthLayout';
import {Button} from '../../ui/Button';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeading} from '../../ui/PageHeading';
import {TextField} from '../../ui/TextField';
import {errorText} from '../../ui/errorText';
import {forgetInvite, pendingInvite, rememberInvite} from './pendingInvite';
import {inviteStrings as t} from './strings';

type Accepted = components['schemas']['AcceptInvitationResponse'];

/** US-2 / D14: accept an invitation from "/invite#<token>"; the token never stays in the URL. */
export function AcceptInvitePage() {
    const router = useRouter();
    const queryClient = useQueryClient();
    const session = useSession();
    const hash = useRouterState({select: (s) => s.location.hash});
    const [token, setToken] = useState<string | null>(() => pendingInvite());
    const [fullName, setFullName] = useState('');
    const [password, setPassword] = useState('');
    const [fieldErrors, setFieldErrors] = useState<{fullName?: string[]; password?: string[]}>({});
    const [failure, setFailure] = useState<string | null>(null);
    const [unusable, setUnusable] = useState<string | null>(null);
    const [joined, setJoined] = useState<Accepted | null>(null);
    const [loginPassword, setLoginPassword] = useState('');
    const [loginError, setLoginError] = useState<string | null>(null);

    // Read the fragment once, keep it in memory, and remove it from the address bar.
    useEffect(() => {
        const fromHash = hash.replace(/^#/, '');
        if (!fromHash) return;
        let decoded: string | null = null;
        try {
            decoded = decodeURIComponent(fromHash);
        } catch {
            // FE-QA-2: a malformed escape (for example "%ZZ") is just an invalid link.
            decoded = null;
        }
        if (decoded) {
            rememberInvite(decoded);
            setToken(decoded);
        } else {
            forgetInvite();
            setToken(null);
            setUnusable(t.invalid);
        }
        router.history.replace('/invite');
    }, [hash, router]);

    const accept = useMutation({
        mutationFn: (value: string) =>
            unwrap(
                authApi.POST('/api/auth/invitations/{token}/accept', {
                    params: {path: {token: value}},
                    ...(session ? {} : {body: {fullName, password}}),
                }),
            ),
        onSuccess: (result) => {
            forgetInvite();
            setToken(null);
            void queryClient.invalidateQueries({queryKey: ['orgs']});
            if (session) router.history.push(`/orgs/${result.organization.id}/projects`);
            else setJoined(result);
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            const message = ui.code ? t.unusable[ui.code] : undefined;
            if (message) {
                forgetInvite();
                setUnusable(message);
            } else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['fullName', 'password']);
                setFieldErrors(split.fields);
                if (split.form.length > 0) setFailure(split.form.join(' '));
            } else {
                setFailure(errorText(ui));
            }
        },
    });

    const login = useMutation({
        mutationFn: (body: {email: string; password: string}) => unwrap(authApi.POST('/api/auth/login', {body})),
        onSuccess: ({accessToken}) => {
            if (startSession(accessToken) && joined) router.history.push(`/orgs/${joined.organization.id}/projects`);
        },
        onError: (error) => setLoginError(errorText(uiErrorOf(error))),
    });

    function onAccept(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!token || accept.isPending) return;
        setFailure(null);
        if (!session) {
            const missing: {fullName?: string[]; password?: string[]} = {};
            if (fullName.trim() === '') missing.fullName = [t.required];
            if (password === '') missing.password = [t.required];
            setFieldErrors(missing);
            if (missing.fullName || missing.password) return;
        }
        accept.mutate(token);
    }

    if (joined) {
        return (
            <AuthLayout>
                <div className="mb-4">
                    <PageHeading>{t.acceptTitle}</PageHeading>
                </div>
                <div className="mb-5">
                    <InlineAlert tone="info" role="status">
                        <span className="inline-flex items-center gap-2">
                            <CircleCheck aria-hidden="true" size={16} className="text-success"/>
                            {t.joined(joined.organization.name, joined.organization.myRole)}
                        </span>
                        <p className="text-text-muted">{t.loginNow}</p>
                    </InlineAlert>
                </div>
                {loginError ? (
                    <div className="mb-4">
                        <InlineAlert tone="danger" role="alert">
                            {loginError}
                        </InlineAlert>
                    </div>
                ) : null}
                <form
                    noValidate
                    className="flex flex-col gap-4"
                    onSubmit={(event) => {
                        event.preventDefault();
                        setLoginError(null);
                        login.mutate({email: joined.user.email, password: loginPassword});
                    }}
                >
                    <TextField id="invite-login-email" label={t.email} type="email" value={joined.user.email} onChange={() => undefined} readOnly autoComplete="username"/>
                    <TextField id="invite-login-password" label={t.password} type="password" value={loginPassword} onChange={setLoginPassword} autoComplete="current-password"/>
                    <Button type="submit" variant="primary" size="lg" fullWidth loading={login.isPending}>
                        {t.logIn}
                    </Button>
                </form>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout>
            <div className="mb-6 flex flex-col gap-1">
                <PageHeading>{t.acceptTitle}</PageHeading>
                {!unusable && token ? (
                    <p className="text-base text-text-muted">{session ? t.acceptSignedIn(session.email ?? '') : t.acceptIntro}</p>
                ) : null}
            </div>
            {unusable || !token ? (
                <div role="alert">
                    <EmptyState title={unusable ?? t.invalid} action={<AppLink href="/">{t.backHome}</AppLink>}/>
                </div>
            ) : (
                <form noValidate onSubmit={onAccept} className="flex flex-col gap-4">
                    {failure ? (
                        <InlineAlert tone="danger" role="alert">
                            {failure}
                        </InlineAlert>
                    ) : null}
                    {session ? null : (
                        <>
                            <TextField id="invite-full-name" label={t.fullName} value={fullName} onChange={setFullName} errors={fieldErrors.fullName} autoComplete="name" required/>
                            <TextField
                                id="invite-password"
                                label={t.password}
                                type="password"
                                hint={t.passwordHint}
                                value={password}
                                onChange={setPassword}
                                errors={fieldErrors.password}
                                autoComplete="new-password"
                                required
                            />
                        </>
                    )}
                    <div className="flex flex-col gap-3 pt-2">
                        <Button type="submit" variant="primary" size="lg" fullWidth loading={accept.isPending}>
                            {t.accept}
                        </Button>
                        {session ? null : (
                            <div className="text-center">
                                <AppLink href="/login?next=%2Finvite">{t.haveAccount}</AppLink>
                            </div>
                        )}
                    </div>
                </form>
            )}
        </AuthLayout>
    );
}

// D14: the invitation token is read from the URL fragment, removed from the address bar, and kept in
// sessionStorage (cucumber.pendingInvite) by src/auth/session.ts, the only owner of sessionStorage.
import {forgetInvite as forget, rememberInvite as remember, takeInvite} from '../../auth/session';

export function rememberInvite(value: string): void {
    remember(value);
}

export function pendingInvite(): string | null {
    return takeInvite();
}

export function forgetInvite(): void {
    forget();
}

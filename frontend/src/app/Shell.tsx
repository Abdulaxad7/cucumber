import { useEffect, type MouseEvent } from 'react';
import { Outlet, useRouter } from '@tanstack/react-router';
import { shouldFocusHeading } from './focus';
import { appStrings } from './strings';

const HEADING_WAIT_MS = 1500;
const HEADING_POLL_MS = 50;
let pendingFocus: ReturnType<typeof setInterval> | null = null;
/** The element that had focus when the current navigation started. */
let focusAtNavigation: Element | null = null;
/** True once an in-app navigation has happened (a full page load alone leaves it false). */
let navigatedInApp = false;

/**
 * FE-QA-5: for pages that replace the focused page (the error page). After an in-app navigation, if focus was lost
 * (it fell back to <body> because its element was removed), returns true so the page can focus its own h1.
 * Never true when the user has moved focus, or on a full page load.
 */
export function shouldRecoverFocus(): boolean {
    return navigatedInApp && (document.activeElement === null || document.activeElement === document.body);
}

/**
 * D17: focus the page h1 after navigation. A lazy route renders its h1 after the route resolves, so wait for it (at
 * most HEADING_WAIT_MS), then fall back to <main>. A newer navigation, or the user moving focus anywhere after the
 * navigation started, cancels the wait.
 */
function focusHeadingWhenReady() {
    if (pendingFocus !== null) clearInterval(pendingFocus);
    const started = Date.now();
    const startFocus = focusAtNavigation;
    const tick = () => {
        const main = document.getElementById('main');
        const active = document.activeElement;
        // The user moved focus after navigating (for example, started tabbing): never take it away.
        const moved = active !== startFocus && active !== null && active !== document.body && active !== main;
        if (moved && active.tagName !== 'H1') return stop();
        const heading = document.querySelector<HTMLElement>('main h1');
        if (heading) {
            heading.focus();
            return stop();
        }
        if (Date.now() - started >= HEADING_WAIT_MS) {
            main?.focus();
            stop();
        }
    };
    const stop = () => {
        if (pendingFocus !== null) clearInterval(pendingFocus);
        pendingFocus = null;
    };
    pendingFocus = setInterval(tick, HEADING_POLL_MS);
    setTimeout(tick, 0);
}

function focusMain(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    document.getElementById('main')?.focus();
}

/** Root layout: the skip link first; each layout below renders one main landmark. Focus moves to the h1 after navigation (D17). */
export function Shell() {
    const router = useRouter();

    useEffect(() => {
        // A new app (a full page load) starts with no in-app navigation.
        navigatedInApp = false;
        const offBefore = router.subscribe('onBeforeNavigate', (event) => {
            focusAtNavigation = document.activeElement;
            if (event.fromLocation) navigatedInApp = true;
        });
        const offResolved = router.subscribe('onResolved', (event) => {
            if (!event.fromLocation || !event.pathChanged) return;
            if (!shouldFocusHeading()) return;
            focusHeadingWhenReady();
        });
        return () => {
            offBefore();
            offResolved();
        };
    }, [router],
    );

    return (
        <>
            <a
                href="#main"
                onClick={focusMain}
                className="fixed left-2 top-2 z-50 -translate-y-16 rounded-md border border-border bg-surface-raised px-3 py-2 text-base font-medium text-primary shadow-md focus:translate-y-0"
            >
                {appStrings.skipLink}
            </a>
            <Outlet />
        </>
    );
}

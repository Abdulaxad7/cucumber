import {useEffect, useRef} from 'react';
import {RotateCw, ServerCrash} from 'lucide-react';
import {AppLink} from '../ui/AppLink';
import {Button} from '../ui/Button';
import {PageHeading} from '../ui/PageHeading';
import {shouldRecoverFocus} from './Shell';
import {appStrings} from './strings';

/**
 * FE-QA-2 / F13: the router's default error component. It shows a safe generic message, Retry and Home, and never
 * the error's text or stack (they may hold internal details).
 */
export function ErrorPage({reset}: { reset?: () => void }) {
    const ref = useRef<HTMLDivElement>(null);
    // FE-QA-5: the error replaced the page (and its focused h1) after an in-app navigation; move focus to this h1.
    useEffect(() => {
        const timer = setTimeout(() => {
            if (shouldRecoverFocus()) ref.current?.querySelector<HTMLElement>('h1')?.focus();
        }, 0);
        return () => clearTimeout(timer);
    }, []);
    return (
        // No landmark of its own: it renders inside the shell's <main>, or alone when the shell itself failed.
        <div className="flex justify-center px-4 py-16">
            <div ref={ref} role="alert" className="flex w-full max-w-md flex-col items-start gap-3 rounded-xl border border-border bg-surface-raised p-8 shadow-lg">
                <span className="inline-flex size-10 items-center justify-center rounded-full bg-surface-hover text-text-muted">
                    <ServerCrash aria-hidden="true" size={24}/>
                </span>
                <PageHeading>{appStrings.errorTitle}</PageHeading>
                <p className="text-base text-text-muted">{appStrings.errorText}</p>
                <div className="flex items-center gap-3 pt-2">
                    <Button
                        variant="primary"
                        onPress={() => {
                            if (reset) reset();
                            else window.location.reload();
                        }}
                    >
                        <RotateCw aria-hidden="true" size={16}/>
                        {appStrings.retry}
                    </Button>
                    <AppLink href="/">{appStrings.goHome}</AppLink>
                </div>
            </div>
        </div>
    );
}

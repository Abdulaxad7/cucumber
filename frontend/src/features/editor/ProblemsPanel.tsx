import {CircleX} from 'lucide-react';
import type {ParseError} from '../../api/problem';
import {editorStrings as t} from './strings';

/** The parser's message without its "(line:column): " prefix; the location is shown separately. */
export function readableMessage(message: string): string {
    return message.replace(/^\(\d+:\d+\):\s*/, '');
}

function lineText(source: string, line: number): string {
    return (source.split('\n')[line - 1] ?? '').trim();
}

/**
 * AC-6.2: the Gherkin problems under the editor. Each row moves the cursor to its error (click or Enter); F8 in the
 * editor moves between them too. The count is announced by the editor's live region, not here.
 */
export function ProblemsPanel({errors, source, onGoTo}: { errors: readonly ParseError[]; source: string; onGoTo: (line: number, column: number) => void }) {
    if (errors.length === 0) return null;
    return (
        <section aria-label={t.problemsLabel} className="flex max-h-56 shrink-0 flex-col border-t border-border bg-surface-raised">
            <header className="flex items-center gap-2 border-b border-border px-4 py-2">
                <CircleX aria-hidden="true" size={16} className="text-danger"/>
                <h3 className="text-sm font-semibold text-text">{t.problems(errors.length)}</h3>
                <p className="ml-auto hidden text-xs text-text-muted sm:block">{t.problemsHint}</p>
            </header>
            <ul className="min-h-0 overflow-y-auto py-1">
                {errors.map((error, index) => {
                    const snippet = lineText(source, error.line);
                    return (
                        <li key={`${error.line}:${error.column}:${index}`}>
                            <button
                                type="button"
                                onClick={() => onGoTo(error.line, error.column)}
                                className="flex w-full items-start gap-3 px-4 py-2 text-left outline-none transition duration-120 ease-standard hover:bg-surface-hover focus-visible:bg-surface-hover"
                            >
                                <span className="mt-0.5 shrink-0 rounded-sm bg-status-failed-bg px-1.5 py-0.5 text-xs font-medium tabular-nums text-status-failed-fg">
                                    {t.location(error.line, error.column)}
                                </span>
                                <span className="flex min-w-0 flex-1 flex-col gap-1">
                                    <span className="text-sm text-text">{readableMessage(error.message)}</span>
                                    {snippet ? (
                                        <code className="truncate rounded-sm bg-surface-hover px-1.5 py-0.5 font-mono text-xs text-text-muted">{snippet}</code>
                                    ) : null}
                                </span>
                            </button>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

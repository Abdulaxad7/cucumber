import {ChevronLeft, ChevronRight} from 'lucide-react';
import {Button} from './Button';
import {uiStrings} from './strings';

/** DS-9 Pagination: "1–20 of 57", previous and next. `page` is zero-based, like the API. */
export function Pagination({page, size, total, onPage}: { page: number; size: number; total: number; onPage: (page: number) => void }) {
    if (total === 0) return null;
    const first = page * size + 1;
    const last = Math.min(total, (page + 1) * size);
    return (
        <nav aria-label={uiStrings.pagination} className="flex items-center justify-between gap-3 border-t border-border px-4 py-2">
            <p className="text-sm tabular-nums text-text-muted" aria-live="polite">
                {uiStrings.range(first, last, total)}
            </p>
            <div className="flex items-center gap-1">
                <Button variant="ghost" size="sm" isDisabled={page === 0} onPress={() => onPage(page - 1)}>
                    <ChevronLeft aria-hidden="true" size={16}/>
                    {uiStrings.previousPage}
                </Button>
                <Button variant="ghost" size="sm" isDisabled={last >= total} onPress={() => onPage(page + 1)}>
                    {uiStrings.nextPage}
                    <ChevronRight aria-hidden="true" size={16}/>
                </Button>
            </div>
        </nav>
    );
}

import type {ReactNode} from 'react';
import {Check} from 'lucide-react';
import {Checkbox as AriaCheckbox} from 'react-aria-components';
import {cx} from './cx';

/** DS-9 Checkbox: a labelled box with a check mark (never colour alone); invalid shows a danger border. */
export function Checkbox({isSelected, onChange, isInvalid = false, children}: { isSelected: boolean; onChange: (selected: boolean) => void; isInvalid?: boolean; children: ReactNode }) {
    return (
        <AriaCheckbox isSelected={isSelected} onChange={onChange} isInvalid={isInvalid} className="group flex cursor-default items-center gap-2 text-base text-text outline-none">
            {({isSelected: selected}) => (
                <>
                    <span
                        aria-hidden="true"
                        className={cx(
                            'inline-flex size-4 shrink-0 items-center justify-center rounded-sm border transition duration-120 ease-standard group-data-focus-visible:outline-2 group-data-focus-visible:outline-offset-2 group-data-focus-visible:outline-focus-ring',
                            selected ? 'border-primary bg-primary text-on-primary' : isInvalid ? 'border-danger bg-surface-raised' : 'border-border-control bg-surface-raised',
                        )}
                    >
                        {selected ? <Check size={12} strokeWidth={3}/> : null}
                    </span>
                    <span className="min-w-0 flex-1">{children}</span>
                </>
            )}
        </AriaCheckbox>
    );
}

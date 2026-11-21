import type {ReactNode} from 'react';
import {ToggleButton, ToggleButtonGroup, type Key} from 'react-aria-components';

export interface Segment {
    id: string;
    label: string;
    icon?: ReactNode;
}

/** Segmented single choice (radio semantics): each segment has a label, so the choice is never colour alone. */
export function SegmentedControl({label, segments, value, onChange}: { label: string; segments: readonly Segment[]; value: string | null; onChange: (value: string) => void }) {
    return (
        <ToggleButtonGroup
            aria-label={label}
            selectionMode="single"
            disallowEmptySelection
            selectedKeys={value ? [value] : []}
            onSelectionChange={(keys: Set<Key>) => {
                const [first] = [...keys];
                if (first !== undefined) onChange(String(first));
            }}
            className="inline-flex flex-wrap rounded-md border border-border-control bg-surface-raised p-0.5 shadow-xs"
        >
            {segments.map((segment) => (
                <ToggleButton
                    key={segment.id}
                    id={segment.id}
                    className="inline-flex h-8 cursor-default items-center gap-1.5 rounded-sm px-3 text-sm font-medium text-text-muted outline-none transition duration-120 ease-standard data-hovered:text-text data-selected:bg-primary data-selected:text-on-primary data-focus-visible:outline-2 data-focus-visible:outline-offset-1 data-focus-visible:outline-focus-ring"
                >
                    {segment.icon}
                    {segment.label}
                </ToggleButton>
            ))}
        </ToggleButtonGroup>
    );
}

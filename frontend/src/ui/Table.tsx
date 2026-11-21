import type {ReactNode} from 'react';
import {Cell as AriaCell, Column as AriaColumn, Row as AriaRow, Table as AriaTable, TableBody, TableHeader} from 'react-aria-components';
import {cx} from './cx';

export interface TableColumn {
    id: string;
    label: string;
    /** Right-aligned numerics (DS-9). */
    numeric?: boolean;
    isRowHeader?: boolean;
    /** Columns with only actions get a visually hidden header. */
    hideLabel?: boolean;
}

export interface TableRowData {
    id: string;
    cells: Record<string, ReactNode>;
}

/** DS-9 Table on react-aria-components: sticky header, row hover, right-aligned numerics. */
export function Table({label, columns, rows, empty}: { label: string; columns: readonly TableColumn[]; rows: readonly TableRowData[]; empty?: ReactNode }) {
    return (
        <div className="overflow-x-auto">
            <AriaTable aria-label={label} className="w-full border-separate border-spacing-0 text-sm">
                <TableHeader className="sticky top-0 z-10 bg-surface-raised">
                    {columns.map((column) => (
                        <AriaColumn
                            key={column.id}
                            id={column.id}
                            isRowHeader={column.isRowHeader}
                            className={cx(
                                'border-b border-border px-4 py-2 text-left text-xs font-medium whitespace-nowrap text-text-muted',
                                column.numeric && 'text-right',
                            )}
                        >
                            {column.hideLabel ? <span className="sr-only">{column.label}</span> : column.label}
                        </AriaColumn>
                    ))}
                </TableHeader>
                <TableBody renderEmptyState={empty ? () => empty : undefined}>
                    {rows.map((row) => (
                        <AriaRow key={row.id} id={row.id} className="group outline-none data-focus-visible:outline-2 data-focus-visible:-outline-offset-2 data-focus-visible:outline-focus-ring">
                            {columns.map((column) => (
                                <AriaCell
                                    key={column.id}
                                    className={cx(
                                        'border-b border-border px-4 py-2.5 align-middle text-text group-last:border-b-0 group-data-hovered:bg-surface-hover',
                                        column.numeric && 'text-right tabular-nums',
                                    )}
                                >
                                    {row.cells[column.id]}
                                </AriaCell>
                            ))}
                        </AriaRow>
                    ))}
                </TableBody>
            </AriaTable>
        </div>
    );
}

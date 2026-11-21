import {Check, ChevronDown} from 'lucide-react';
import {
    Button as AriaButton,
    FieldError,
    Label,
    ListBox,
    ListBoxItem,
    Popover,
    Select as AriaSelect,
    SelectValue,
    Text,
    type Key,
} from 'react-aria-components';
import {cx} from './cx';
import {popoverClasses} from './Menu';

export interface SelectOption {
    id: string;
    label: string;
    description?: string;
}

export interface SelectProps {
    label: string;
    /** Hide the label visually (in table rows); it still names the control. */
    hideLabel?: boolean;
    options: readonly SelectOption[];
    value: string | null;
    onChange: (value: string) => void;
    placeholder?: string;
    errors?: readonly string[];
    description?: string;
    isDisabled?: boolean;
    size?: 'sm' | 'md';
}

/** DS-9 Select: a labelled button with a listbox popover; the selected item is checked. */
export function Select({label, hideLabel = false, options, value, onChange, placeholder, errors, description, isDisabled = false, size = 'md'}: SelectProps) {
    const invalid = errors !== undefined && errors.length > 0;
    return (
        <AriaSelect
            selectedKey={value}
            onSelectionChange={(key: Key | null) => {
                if (key !== null) onChange(String(key));
            }}
            isDisabled={isDisabled}
            isInvalid={invalid}
            validationBehavior="aria"
            placeholder={placeholder}
            className="flex flex-col gap-1.5"
        >
            <Label className={hideLabel ? 'sr-only' : 'text-base font-medium text-text'}>{label}</Label>
            {description ? (
                <Text slot="description" className="text-sm text-text-muted">
                    {description}
                </Text>
            ) : null}
            <AriaButton
                className={cx(
                    'inline-flex items-center justify-between gap-2 rounded-md border bg-surface-raised px-3 text-left text-text shadow-xs outline-none transition duration-120 ease-standard data-disabled:cursor-not-allowed data-disabled:opacity-50 data-hovered:bg-surface-hover',
                    size === 'sm' ? 'h-7 text-sm' : 'h-10 text-base',
                    invalid ? 'border-danger' : 'border-border-control',
                )}
            >
                <SelectValue className="min-w-0 truncate data-placeholder:text-text-muted">
                    {({selectedText, isPlaceholder}) => (isPlaceholder ? placeholder : selectedText)}
                </SelectValue>
                <ChevronDown aria-hidden="true" size={16} className="shrink-0 text-text-muted"/>
            </AriaButton>
            <FieldError className="text-sm font-medium text-danger">{errors?.join(' ')}</FieldError>
            <Popover placement="bottom start" offset={4} className={`${popoverClasses} w-(--trigger-width)`}>
                <ListBox className="outline-none">
                    {options.map((option) => (
                        <ListBoxItem
                            key={option.id}
                            id={option.id}
                            textValue={option.label}
                            className="flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-base text-text outline-none data-focused:bg-surface-hover"
                        >
                            {({isSelected}) => (
                                <>
                                    <span className="inline-flex size-4 shrink-0 items-center justify-center text-primary">
                                        {isSelected ? <Check aria-hidden="true" size={16}/> : null}
                                    </span>
                                    <span className="flex min-w-0 flex-col">
                                        <span className="truncate">{option.label}</span>
                                        {option.description ? <span className="truncate text-xs text-text-muted">{option.description}</span> : null}
                                    </span>
                                </>
                            )}
                        </ListBoxItem>
                    ))}
                </ListBox>
            </Popover>
        </AriaSelect>
    );
}

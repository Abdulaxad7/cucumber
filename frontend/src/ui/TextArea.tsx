import {Label, Text, TextArea as AriaTextArea, TextField as AriaTextField, FieldError} from 'react-aria-components';
import {cx} from './cx';

/** DS-9 TextArea: label, description and errors linked; grows up to about 8 lines. */
export function TextArea({id, label, value, onChange, hint, errors}: { id: string; label: string; value: string; onChange: (value: string) => void; hint?: string; errors?: readonly string[] }) {
    const invalid = errors !== undefined && errors.length > 0;
    return (
        <AriaTextField id={id} value={value} onChange={onChange} isInvalid={invalid} validationBehavior="aria" className="flex flex-col gap-1.5">
            <Label className="text-base font-medium text-text">{label}</Label>
            {hint ? (
                <Text slot="description" className="text-sm text-text-muted">
                    {hint}
                </Text>
            ) : null}
            <AriaTextArea
                rows={3}
                className={cx(
                    'field-sizing-content max-h-48 min-h-20 resize-none rounded-md border bg-surface-raised px-3 py-2 text-base text-text shadow-xs outline-none transition duration-120 ease-standard placeholder:text-text-muted data-focused:border-primary',
                    invalid ? 'border-danger' : 'border-border-control',
                )}
            />
            <FieldError className="text-sm font-medium text-danger">{errors?.join(' ')}</FieldError>
        </AriaTextField>
    );
}

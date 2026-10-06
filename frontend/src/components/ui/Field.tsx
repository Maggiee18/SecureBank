import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
  leading?: ReactNode;
  trailing?: ReactNode;
}

export const Field = forwardRef<HTMLInputElement, Props>(function Field(
  { label, error, hint, leading, trailing, className = '', id, ...rest }, ref,
) {
  const generated = useId();
  const inputId = id ?? generated;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;
  return (
    <div className={className}>
      <label htmlFor={inputId} className="label">{label}</label>
      <div className="relative">
        {leading && <span className="pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-muted">{leading}</span>}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
          className={`field ${leading ? 'pl-9' : ''} ${trailing ? 'pr-11' : ''} ${error ? 'field-error' : ''}`}
          {...rest}
        />
        {trailing && <span className="absolute inset-y-0 right-2 flex items-center">{trailing}</span>}
      </div>
      {error
        ? <p id={`${inputId}-error`} className="mt-1.5 text-xs font-medium text-danger">{error}</p>
        : hint && <p id={`${inputId}-hint`} className="mt-1.5 text-xs text-muted">{hint}</p>}
    </div>
  );
});

'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Form } from '@bpmn-io/form-js';
import '@bpmn-io/form-js/dist/assets/form-js.css';
// Werkflow theme — must stay after the library CSS import above.
import './formjs-theme.css';

interface FormJsViewerProps {
  schema: any;
  data?: Record<string, any>;
  onSubmit?: (data: Record<string, any>) => void;
  onChange?: (data: Record<string, any>) => void;
  onError?: (err: unknown) => void;
  readonly?: boolean;
  className?: string;
}

export default function FormJsViewer({
  schema,
  data = {},
  onSubmit,
  onChange,
  onError,
  readonly = false,
  className = ''
}: FormJsViewerProps) {
  const t = useTranslations('formBuilder')
  const containerRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<Form | null>(null);
  const onSubmitRef = useRef(onSubmit);
  const onChangeRef = useRef(onChange);
  const onErrorRef = useRef(onError);
  const isSettingDataRef = useRef(false);
  const [importError, setImportError] = useState<string | null>(null);

  // Keep refs in sync without triggering re-renders
  onSubmitRef.current = onSubmit;
  onChangeRef.current = onChange;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!containerRef.current) return;

    setImportError(null);

    const form = new Form({
      container: containerRef.current
    });

    formRef.current = form;

    form.importSchema(schema, data).catch((err) => {
      const message = err instanceof Error ? err.message : String(err)
      console.error('FormJsViewer: schema import failed', err)
      if (onErrorRef.current) {
        onErrorRef.current(err)
      } else {
        setImportError(message)
      }
    });

    if (readonly && containerRef.current) {
      containerRef.current.classList.add('form-js-readonly');
    }

    form.on('changed', (event: any) => {
      if (isSettingDataRef.current) return  // suppress programmatic _setState events
      if (onChangeRef.current) {
        onChangeRef.current(event.data)
      }
    });

    form.on('submit', (event: any) => {
      if (onSubmitRef.current) {
        onSubmitRef.current(event.data);
        // form-js calls _reset() synchronously after this handler returns, which fires
        // an async changed event.  Suppress it so the cascade in the parent does not
        // clear the options arrays and erase the user's selections.
        isSettingDataRef.current = true;
        setTimeout(() => { isSettingDataRef.current = false; }, 0);
      }
    });

    return () => {
      formRef.current = null;
      form.destroy();
    };
  // Only re-create the form when schema identity changes
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [schema, readonly]);

  // Merge data prop into the live form instance without re-importing schema.
  // form-js _setState is @internal — TypeScript cast required.
  useEffect(() => {
    if (!formRef.current || data == null) return
    const form = formRef.current as any
    const currentData = form._getState().data
    // form-js useOptionsAsync reads options (valuesKey) from initialData, not from data.
    // We must update both so dynamically loaded options (cascade selects) are visible.
    const currentInitialData = form._getState().initialData
    isSettingDataRef.current = true
    form._setState({
      data: { ...currentData, ...data },
      initialData: { ...currentInitialData, ...data },
    })
    isSettingDataRef.current = false
  }, [data])

  const handleSubmit = useCallback(() => {
    if (!formRef.current) return;

    formRef.current.submit();
  }, []);

  return (
    <div className={`form-js-viewer-wrapper ${className}`}>
      {importError && (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4 mb-4">
          <p className="text-sm font-medium text-destructive">This form could not be loaded.</p>
          <p className="text-xs text-muted-foreground mt-1">Please contact your administrator.</p>
        </div>
      )}
      <div
        ref={containerRef}
        className="form-js-container"
        style={{ minHeight: '200px' }}
      />

      {onSubmit && !readonly && !importError && (
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            onClick={handleSubmit}
            className="inline-flex items-center justify-center rounded-md bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground shadow hover:bg-primary/90 transition-colors"
          >
            {t('submit')}
          </button>
        </div>
      )}
    </div>
  );
}

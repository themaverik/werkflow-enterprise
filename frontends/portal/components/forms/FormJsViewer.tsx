'use client';

import { useEffect, useRef, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { Form } from '@bpmn-io/form-js';
import '@bpmn-io/form-js/dist/assets/form-js.css';
import '@bpmn-io/form-js/dist/assets/form-js-editor.css';

interface FormJsViewerProps {
  schema: any;
  data?: Record<string, any>;
  onSubmit?: (data: Record<string, any>) => void;
  onChange?: (data: Record<string, any>) => void;
  readonly?: boolean;
  className?: string;
}

export default function FormJsViewer({
  schema,
  data = {},
  onSubmit,
  onChange,
  readonly = false,
  className = ''
}: FormJsViewerProps) {
  const t = useTranslations('formBuilder')
  const containerRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<Form | null>(null);
  const onSubmitRef = useRef(onSubmit);
  const onChangeRef = useRef(onChange);
  const isSettingDataRef = useRef(false);

  // Keep refs in sync without triggering re-renders
  onSubmitRef.current = onSubmit;
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!containerRef.current) return;

    const form = new Form({
      container: containerRef.current
    });

    formRef.current = form;

    form.importSchema(schema, data).catch((err) => {
      console.error('Failed to import form schema:', err);
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
      <div
        ref={containerRef}
        className="form-js-container"
        style={{ minHeight: '200px' }}
      />

      {onSubmit && !readonly && (
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

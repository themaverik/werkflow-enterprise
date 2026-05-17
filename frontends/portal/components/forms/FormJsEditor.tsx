'use client';

import { useEffect, useRef, useState } from 'react';
import { FormEditor } from '@bpmn-io/form-js-editor';
import { Loader2 } from 'lucide-react';
import '@bpmn-io/form-js/dist/assets/form-js.css';
import '@bpmn-io/form-js/dist/assets/form-js-editor.css';
// Werkflow theme — MUST stay after the library CSS imports above so our
// --panel-* mappings beat the library's local CSS variable declarations
// on .fjs-palette-container / .fjs-properties-container in source order.
import './formjs-theme.css';
import { serializeSchemaProperties, deserializeSchemaProperties } from '@/lib/forms/propertyValueSerializer';
import { injectPaletteFilter } from '@/lib/forms/createPaletteFilterModule';

interface FormJsEditorProps {
  schema?: any;
  onSchemaChange?: (schema: any) => void;
  onSave?: (schema: any) => void;
  onError?: (err: unknown) => void;
  className?: string;
}

/**
 * FormJsEditor Component
 *
 * Wrapper component for bpmn-io/form-js-editor library.
 * Provides a visual editor for creating and modifying form schemas.
 *
 * Usage:
 * ```tsx
 * <FormJsEditor
 *   schema={initialSchema}
 *   onSchemaChange={handleSchemaChange}
 *   onSave={handleSave}
 * />
 * ```
 */
export default function FormJsEditor({
  schema,
  onSchemaChange,
  onSave,
  onError,
  className = ''
}: FormJsEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<FormEditor | null>(null);
  const isInternalChangeRef = useRef(false);
  // Keep onError in a ref so closures inside the mount effect always see the
  // latest callback identity without re-running the effect (which would
  // remount the editor).
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;
  // Keep latest schema in a ref so the async mount effect imports the most
  // recent prop value even if it arrived AFTER mount started — fixes the
  // race where the parent's API fetch resolves before editor creation.
  const schemaRef = useRef(schema);
  schemaRef.current = schema;
  // Snapshot the schemaJson last imported by the mount effect so the
  // schemaJson-on-isReady useEffect can skip a redundant cold-mount
  // re-import (the schema imported by mount IS the one in the prop).
  const lastImportedSchemaJsonRef = useRef<string | null>(null);
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    if (!containerRef.current) return;

    // StrictMode double-invoke guard — mirrors the pattern in BpmnDesigner.tsx.
    // Without this, Effect 1's async tail overwrites editorRef.current back to a
    // destroyed editor after Effect 2 has already mounted a fresh one, leaving the
    // canvas in a corrupted state that crashes drag/drop.
    let cancelled = false;

    const init = async () => {
      if (!containerRef.current) return;

      // Clear any leftover DOM from a previous (StrictMode) run before creating
      // a new editor instance in the same container element.
      containerRef.current.innerHTML = '';

      // Static allowlist — synced with `FormSchemaValidator.VALID_FIELD_TYPES`
      // on the engine (VARIABLE_TYPES + DISPLAY_TYPES + SERVICE_TYPES). Adding
      // anything here that the backend doesn't accept results in a 400
      // FormValidationException on save. Tenant-specific allowlists will be
      // wired to /api/v1/config/vars?type=FORM_COMPONENT_ALLOWLIST later.
      //
      // Notably excluded (would require backend `VALID_FIELD_TYPES` extension):
      //   filepicker, iframe, documentPreview, separator
      const allowedTypes: string[] = [
        // VARIABLE_TYPES — produce process variables on submit
        'textfield', 'textarea', 'number',
        'checkbox', 'radio', 'select', 'checklist', 'taglist',
        'date', 'time', 'datetime', 'email',
        'group', 'columns',
        // DISPLAY_TYPES — presentational, no key required
        'html', 'text', 'button', 'image', 'spacer',
        // SERVICE_TYPES
        'dynamiclist',
      ];

      // Fetch tenant CSS theme vars — no-op if empty. Auth comes from the
      // NextAuth session cookie which the proxy reads server-side.
      const cssRes = await fetch('/api/proxy/admin/config/vars?type=CSS_THEME').catch(() => null)
      if (cancelled) return;

      const cssVars: Array<{ varKey: string; varValue: string }> = cssRes?.ok
        ? (await cssRes.json().catch(() => null) as Array<{ varKey: string; varValue: string }> | null) ?? []
        : [];
      if (cancelled) return;

      // Initialize form-js editor — no additionalModules.
      // The palette filter is injected via CSS after importSchema resolves
      // (see injectPaletteFilter call below).  Using additionalModules with
      // a broken DI token silently corrupts editor bootstrapping and leaves
      // _formFieldRegistry empty, which crashes drag/drop.
      const editor = new FormEditor({
        container: containerRef.current,
      });

      editorRef.current = editor;

      // Import initial schema — serialize object/array property values to strings
      // so the form-js-editor properties panel can display them in text inputs.
      // Read schemaRef.current (not the closure-captured `schema`) so a prop
      // update that landed between mount start and now is honoured.
      const initialSchemaInput = schemaRef.current || {
        type: 'default',
        components: [],
        schemaVersion: 9
      };
      const initialSchema = serializeSchemaProperties(initialSchemaInput);
      lastImportedSchemaJsonRef.current = JSON.stringify(initialSchemaInput);

      const importResult = await editor.importSchema(initialSchema).catch((err: unknown) => ({ __error: err }));
      if (cancelled) return;
      if (importResult && '__error' in importResult) {
        onErrorRef.current?.(importResult.__error);
        setIsReady(true);
        return;
      }
      // Surface warnings on successful import — silent component drops are
      // a common cause of "empty canvas" reports.
      const warnings = (importResult as { warnings?: unknown[] })?.warnings ?? [];
      if (warnings.length > 0) {
        onErrorRef.current?.(new Error(`Form imported with ${warnings.length} warning(s) — see browser console`));
        console.warn('[FormJsEditor] importSchema warnings:', warnings);
      }

      // Inject palette CSS filter now that the editor DOM is fully rendered.
      // Done here (not via additionalModules) to avoid any DI token issues
      // that would silently break editor bootstrapping.
      if (containerRef.current) {
        injectPaletteFilter(containerRef.current, allowedTypes);

        // bpmn.io attribution — LGPL license requirement. The editor
        // canvas does not include a default PoweredBy (only the viewer
        // does); inject a clickable link into the library-rendered
        // palette footer. Uses the library's own class convention
        // (`fjs-powered-by fjs-form-field`) so it inherits any
        // library theming applied to the viewer's PoweredBy element.
        const footer = containerRef.current.querySelector('.fjs-palette-footer');
        if (footer && !footer.querySelector('.fjs-powered-by')) {
          const wrap = document.createElement('div');
          wrap.className = 'fjs-powered-by fjs-form-field';
          const link = document.createElement('a');
          link.className = 'fjs-powered-by-link werkflow-powered-by';
          link.href = 'https://bpmn.io';
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          link.title = 'Powered by bpmn.io';
          link.textContent = 'Powered by bpmn.io';
          wrap.appendChild(link);
          footer.appendChild(wrap);
        }
      }

      setIsReady(true);

      // Apply tenant CSS theme vars to the editor container
      const container = containerRef.current
      if (container && cssVars.length) {
        cssVars.forEach(({ varKey, varValue }) => {
          if (varKey.startsWith('--')) container.style.setProperty(varKey, varValue)
        })
      }

      // Listen to schema changes — deserialize string property values back to
      // objects/arrays before propagating the schema to the parent.
      editor.on('changed', () => {
        try {
          const updatedSchema = editor.saveSchema();
          const deserializedSchema = deserializeSchemaProperties(updatedSchema);

          if (onSchemaChange) {
            isInternalChangeRef.current = true;
            onSchemaChange(deserializedSchema);
          }
        } catch (err) {
          onErrorRef.current?.(err);
        }
      });
    };

    init().catch((err) => {
      onErrorRef.current?.(err);
    });

    // Cleanup — set cancelled BEFORE destroy so the async tail of init()
    // sees the flag immediately and does not overwrite editorRef.current.
    return () => {
      cancelled = true;
      if (editorRef.current) {
        editorRef.current.destroy();
        editorRef.current = null;
      }
      // Drop any DOM the editor (or our attribution injection) left behind
      // so detached nodes don't linger on the page after final unmount.
      if (containerRef.current) {
        containerRef.current.innerHTML = '';
      }
    };
  }, []);

  // Update editor schema when props change — serialize before importing.
  // Stringify the schema to avoid re-running on every render due to referential inequality.
  // Depends on `isReady` so the effect re-fires once the editor finishes
  // mounting — covers the race where a new schema prop arrives BEFORE the
  // async mount effect has set editorRef.current.
  const schemaJson = JSON.stringify(schema);
  useEffect(() => {
    if (!isReady) return;
    // Skip the redundant cold-mount re-import: the schema imported by
    // the mount effect is recorded in lastImportedSchemaJsonRef. If the
    // current schemaJson matches, the editor already has this schema.
    if (lastImportedSchemaJsonRef.current === schemaJson) return;
    if (isInternalChangeRef.current) {
      isInternalChangeRef.current = false;
      return;
    }
    if (editorRef.current && schema) {
      const serialized = serializeSchemaProperties(schema);
      lastImportedSchemaJsonRef.current = schemaJson;
      editorRef.current.importSchema(serialized).catch((err) => {
        onErrorRef.current?.(err);
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [schemaJson, isReady]);

  return (
    <div className={`form-js-editor-wrapper relative ${className}`} style={{ height: '100%' }}>
      {/* Loading overlay — prevents interaction before importSchema resolves */}
      {!isReady && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-background/80">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">Loading editor…</p>
        </div>
      )}

      {/* Editor container — pointer-events blocked until importSchema resolves.
          NO minHeight: when the parent (FormJsBuilder under
          flex-1 overflow-hidden) allocates less than the hardcoded
          minimum, the container would overflow and the bottom of the
          palette (including the bpmn.io footer) would render below the
          parent's clipped region. Letting height:100% govern alone
          keeps the footer pinned to the visible bottom. */}
      <div
        ref={containerRef}
        className="form-js-editor-container"
        style={{ height: '100%', pointerEvents: isReady ? 'auto' : 'none' }}
      />
    </div>
  );
}

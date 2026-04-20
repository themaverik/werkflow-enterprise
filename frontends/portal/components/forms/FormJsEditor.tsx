'use client';

import { useEffect, useRef, useState } from 'react';
import { FormEditor } from '@bpmn-io/form-js-editor';
import '@bpmn-io/form-js/dist/assets/form-js.css';
import '@bpmn-io/form-js/dist/assets/form-js-editor.css';
import '@bpmn-io/form-js-editor/dist/assets/form-js-editor.css';
import { serializeSchemaProperties, deserializeSchemaProperties } from '@/lib/forms/propertyValueSerializer';

interface FormJsEditorProps {
  schema?: any;
  onSchemaChange?: (schema: any) => void;
  onSave?: (schema: any) => void;
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
  className = ''
}: FormJsEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<FormEditor | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string>('');

  useEffect(() => {
    if (!containerRef.current) return;

    // Initialize form-js editor
    const editor = new FormEditor({
      container: containerRef.current
    });

    editorRef.current = editor;

    // Import initial schema — serialize object/array property values to strings
    // so the form-js-editor properties panel can display them in text inputs.
    const initialSchema = serializeSchemaProperties(schema || {
      type: 'default',
      components: [],
      schemaVersion: 9
    });

    editor.importSchema(initialSchema).catch((err) => {
      console.error('Failed to import form schema:', err);
    });

    // Listen to schema changes — deserialize string property values back to
    // objects/arrays before propagating the schema to the parent.
    editor.on('changed', () => {
      editor.saveSchema().then((updatedSchema: any) => {
        const deserializedSchema = deserializeSchemaProperties(updatedSchema);

        if (onSchemaChange) {
          onSchemaChange(deserializedSchema);
        }
      }).catch((err: any) => {
        console.error('Failed to save schema:', err);
      });
    });

    // Cleanup
    return () => {
      if (editorRef.current) {
        editorRef.current.destroy();
      }
    };
  }, []);

  // Update editor schema when props change — serialize before importing.
  // Stringify the schema to avoid re-running on every render due to referential inequality.
  const schemaJson = JSON.stringify(schema);
  useEffect(() => {
    if (editorRef.current && schema) {
      editorRef.current.importSchema(serializeSchemaProperties(schema)).catch((err) => {
        console.error('Failed to update editor schema:', err);
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [schemaJson]);

  const handleSave = async () => {
    if (!editorRef.current || !onSave) return;

    setIsSaving(true);
    setSaveMessage('');

    try {
      const schemaToSave = deserializeSchemaProperties(await editorRef.current.saveSchema());
      await onSave(schemaToSave);
      setSaveMessage('Form saved successfully!');

      setTimeout(() => {
        setSaveMessage('');
      }, 3000);
    } catch (error) {
      console.error('Failed to save form:', error);
      setSaveMessage('Failed to save form. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };


  return (
    <div className={`form-js-editor-wrapper ${className}`}>
      {/* Toolbar */}
      <div className="form-editor-toolbar bg-gray-100 border-b border-gray-300 p-3 flex items-center justify-between">
        <div className="flex items-center space-x-2">
          <h2 className="text-lg font-semibold text-gray-800">Form Editor</h2>
        </div>

        <div className="flex items-center space-x-2">
          {onSave && (
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSaving ? 'Saving...' : 'Save Form'}
            </button>
          )}
        </div>
      </div>

      {/* Save message */}
      {saveMessage && (
        <div
          className={`save-message p-3 text-center font-medium ${
            saveMessage.includes('success')
              ? 'bg-green-100 text-green-800'
              : 'bg-red-100 text-red-800'
          }`}
        >
          {saveMessage}
        </div>
      )}

      {/* Editor container */}
      <div
        ref={containerRef}
        className="form-js-editor-container"
        style={{ height: 'calc(100vh - 200px)', minHeight: '600px' }}
      />
    </div>
  );
}

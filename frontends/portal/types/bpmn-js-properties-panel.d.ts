declare module 'bpmn-js-properties-panel' {
  export const BpmnPropertiesPanelModule: any;
  export const BpmnPropertiesProviderModule: any;
  export const CamundaPlatformPropertiesProviderModule: any;
}

declare module '@bpmn-io/properties-panel' {
  export const SelectEntry: any;
  export const TextFieldEntry: any;
  export const CheckboxEntry: any;
  export const TextAreaEntry: any;
  export const NumberFieldEntry: any;
  export function isSelectEntryEdited(entry: any): boolean;
  export function isTextFieldEntryEdited(entry: any): boolean;
  export function isTextAreaEntryEdited(entry: any): boolean;
  export function isNumberFieldEntryEdited(entry: any): boolean;
}

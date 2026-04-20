/**
 * Type declarations for dmn-js.
 * dmn-js does not ship @types/dmn-js; this minimal declaration satisfies the TypeScript compiler.
 */
declare module 'dmn-js/lib/Modeler' {
  interface DmnModelerOptions {
    container: HTMLElement | null
    [key: string]: unknown
  }

  interface SaveXmlOptions {
    format?: boolean
  }

  interface SaveXmlResult {
    xml: string
  }

  export default class DmnModeler {
    constructor(options: DmnModelerOptions)
    importXML(xml: string): Promise<void>
    saveXML(options?: SaveXmlOptions): Promise<SaveXmlResult>
    on(event: string, handler: (...args: unknown[]) => void): void
    destroy(): void
  }
}

declare module 'dmn-js/dist/assets/dmn-js-shared.css' { const _: string; export default _ }
declare module 'dmn-js/dist/assets/dmn-js-drd.css' { const _: string; export default _ }
declare module 'dmn-js/dist/assets/dmn-js-decision-table.css' { const _: string; export default _ }
declare module 'dmn-js/dist/assets/dmn-font/css/dmn-embedded.css' { const _: string; export default _ }

declare module 'bpmn-js/lib/Modeler' {
  export default class BpmnModeler {
    constructor(options?: any);
    importXML(xml: string): Promise<{ warnings: any[] }>;
    saveXML(options?: any): Promise<{ xml: string }>;
    saveSVG(options?: any): Promise<{ svg: string }>;
    get(serviceName: string): any;
    destroy(): void;
  }
}

declare module 'bpmn-js/lib/Viewer' {
  export default class BpmnViewer {
    constructor(options?: any);
    importXML(xml: string): Promise<{ warnings: any[] }>;
    get(serviceName: string): any;
    destroy(): void;
  }
}

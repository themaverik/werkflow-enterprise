package com.werkflow.engine.config.flowable;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.converter.SendTaskXMLConverter;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.SendTask;

import javax.xml.stream.XMLStreamReader;

/**
 * Custom BPMN XML converter for {@code <sendTask>} that preserves
 * {@code flowable:delegateExpression} (and {@code flowable:class} /
 * {@code flowable:expression}) in {@link SendTask#getAttributes()} so that
 * {@link WerkflowSendTaskValidator} and {@link WerkflowSendTaskParseHandler}
 * can read them from the model after XML parsing.
 *
 * <p>Flowable's stock {@link SendTaskXMLConverter} only reads {@code flowable:type}
 * and {@code implementation}. Unlike {@code UserTaskXMLConverter} (which calls
 * {@link org.flowable.bpmn.converter.util.BpmnXMLUtil#addCustomAttributes}),
 * {@code SendTaskXMLConverter} does not call {@code addCustomAttributes}, so
 * {@code flowable:delegateExpression} is silently dropped at parse time.
 *
 * <p>This converter reads the three custom implementation attributes
 * <em>before</em> delegating to {@code super.convertXMLToElement()} (which
 * advances the reader past the element's child nodes), then injects them as
 * {@link ExtensionAttribute} objects on the returned {@link SendTask} model.
 *
 * <p>Registration: call
 * {@link org.flowable.bpmn.converter.BpmnXMLConverter#addConverter(org.flowable.bpmn.converter.BaseBpmnXMLConverter)}
 * with an instance of this class at engine-configuration time to replace the
 * default {@code SendTaskXMLConverter}.
 *
 * <p><strong>Round-trip caveat:</strong> this converter only fixes the <em>read</em>
 * (XML→model) direction. The write (model→XML) direction still uses the stock serializer,
 * so {@code flowable:delegateExpression/class/expression} are NOT written back on
 * re-serialization. This is acceptable: there is no round-trip path today — deploys are
 * read-only from the classpath.
 *
 * @see WerkflowSendTaskParseHandler
 * @see WerkflowSendTaskValidator
 */
public class WerkflowSendTaskXMLConverter extends SendTaskXMLConverter {

    private static final String ATTR_DELEGATE_EXPRESSION = "delegateExpression";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_EXPRESSION = "expression";

    @Override
    protected BaseElement convertXMLToElement(XMLStreamReader xtr, org.flowable.bpmn.model.BpmnModel model)
            throws Exception {

        // Read custom implementation attributes while the reader is positioned at <sendTask>
        // (before super.convertXMLToElement advances the reader through child elements).
        String ns = BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE;
        String delegateExpr = xtr.getAttributeValue(ns, ATTR_DELEGATE_EXPRESSION);
        String classValue   = xtr.getAttributeValue(ns, ATTR_CLASS);
        String expression   = xtr.getAttributeValue(ns, ATTR_EXPRESSION);

        SendTask sendTask = (SendTask) super.convertXMLToElement(xtr, model);

        injectAttribute(sendTask, ns, ATTR_DELEGATE_EXPRESSION, delegateExpr);
        injectAttribute(sendTask, ns, ATTR_CLASS, classValue);
        injectAttribute(sendTask, ns, ATTR_EXPRESSION, expression);

        return sendTask;
    }

    private void injectAttribute(SendTask sendTask, String ns, String name, String value) {
        if (StringUtils.isNotEmpty(value)) {
            ExtensionAttribute attr = new ExtensionAttribute(name);
            attr.setNamespace(ns);
            attr.setNamespacePrefix(BpmnXMLConstants.FLOWABLE_EXTENSIONS_PREFIX);
            attr.setValue(value);
            sendTask.addAttribute(attr);
        }
    }
}

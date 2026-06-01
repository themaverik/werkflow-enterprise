package com.werkflow.engine.workflow;

import com.werkflow.engine.action.ConnectorDelegateBase;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scans a BPMN 2.0 XML string and returns three boolean indicator flags:
 * <ul>
 *   <li>{@code hasDmn} — at least one {@code <serviceTask flowable:type="dmn">} is present
 *       (ADR-026 DMN binding pattern; {@code <businessRuleTask>} is intentionally excluded —
 *       it routes to Drools in Flowable 7.2 and never reaches the DMN engine)</li>
 *   <li>{@code hasConnector} — at least one {@code <serviceTask>} whose
 *       {@code flowable:delegateExpression} resolves to a known connector bean.
 *       The set of known connector bean names is derived at construction time from the
 *       Spring context via {@code Map<String, ConnectorDelegateBase>} injection — all
 *       subclasses of {@link ConnectorDelegateBase} (including any registered aliases such
 *       as {@code restConnectorDelegate}) are captured automatically.</li>
 *   <li>{@code hasNotification} — at least one {@code <serviceTask>} or {@code <sendTask>}
 *       whose {@code flowable:delegateExpression} is {@code ${notificationDelegate}}.
 *       The notification bean name is hardcoded (single source) rather than Spring-derived,
 *       because {@code NotificationDelegate} is not a {@link ConnectorDelegateBase} subclass —
 *       it is a separate infrastructure concern (SendTask).</li>
 * </ul>
 *
 * <p>Reads directly from the raw XML (DOM) rather than via Flowable's {@code BpmnModel},
 * for the same reason as {@link BpmnBundleRefExtractor}: the model does not reliably
 * round-trip custom {@code flowable:} extension attributes.
 *
 * <p>XXE hardening is applied identically to {@link BpmnBundleRefExtractor#parse}.
 */
@Component
public class BpmnIndicatorScanner {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    /**
     * Single hardcoded name for the notification delegate.
     * Not Spring-derived because NotificationDelegate is not a ConnectorDelegateBase subclass.
     */
    private static final String NOTIFICATION_DELEGATE_BEAN = "notificationDelegate";

    /**
     * Exact match pattern for the notification delegate.
     * {@code Pattern.matches()} semantics: the entire attribute value must match.
     */
    private static final Pattern NOTIFICATION_DELEGATE_PATTERN = Pattern.compile(
        "\\$\\{\\s*" + Pattern.quote(NOTIFICATION_DELEGATE_BEAN) + "\\s*}");

    /**
     * Connector delegate pattern built at construction time from the Spring-injected bean map.
     * Joining all bean names with {@code |} and wrapping in {@code \$\{\s*(...)\s*\}} ensures:
     * <ul>
     *   <li>Only exact bean names match — {@code myExternalApiCallDelegateWrapper} does NOT match.</li>
     *   <li>All registered aliases (e.g. {@code restConnectorDelegate} from
     *       {@code RestConnectorDelegateAlias}) are included if Spring's autowired
     *       {@code Map<String,T>} includes them (it does for {@code @Bean} alias registrations).</li>
     * </ul>
     */
    private final Pattern connectorDelegatePattern;

    /** Indicator flags computed from a single BPMN definition. */
    public record Indicators(boolean hasDmn, boolean hasConnector, boolean hasNotification) {}

    /**
     * Spring constructor — the container injects all {@link ConnectorDelegateBase} beans keyed by
     * their bean name. This includes {@code @Bean} method aliases registered in
     * {@code @Configuration} classes (e.g. {@code restConnectorDelegate} from
     * {@code RestConnectorDelegateAlias}).
     *
     * <p>Note on alias inclusion: Spring's {@code Map<String, T>} autowiring populates the map
     * with both primary bean names AND aliases registered via {@code @Bean} in a
     * {@code @Configuration} class. It does NOT include aliases added via
     * {@code BeanDefinitionRegistry.registerAlias()} directly (those are purely name redirects
     * with no own entry). Our alias is a {@code @Bean("restConnectorDelegate")} method returning
     * the same instance — this registers an independent bean entry, so it IS included.
     *
     * @param connectorBeans all {@link ConnectorDelegateBase} beans in the context, keyed by name
     */
    public BpmnIndicatorScanner(Map<String, ConnectorDelegateBase> connectorBeans) {
        Set<String> beanNames = Set.copyOf(connectorBeans.keySet());
        String alternation = beanNames.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
        this.connectorDelegatePattern = Pattern.compile(
            "\\$\\{\\s*(" + alternation + ")\\s*}");
    }

    /**
     * Scans {@code bpmnXml} and returns the three indicator flags.
     *
     * @param bpmnXml raw BPMN 2.0 XML
     * @return computed indicators; never {@code null}
     * @throws IllegalArgumentException if the XML is malformed or empty
     */
    public Indicators scan(String bpmnXml) {
        Document doc = parse(bpmnXml);

        boolean hasDmn = false;
        boolean hasConnector = false;
        boolean hasNotification = false;

        NodeList tasks = doc.getElementsByTagNameNS("*", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);

            if (!hasDmn && "dmn".equalsIgnoreCase(task.getAttributeNS(FLOWABLE_NS, "type"))) {
                hasDmn = true;
            }

            String delegateExpr = task.getAttributeNS(FLOWABLE_NS, "delegateExpression");
            if (delegateExpr != null && !delegateExpr.isBlank()) {
                if (!hasConnector && connectorDelegatePattern.matcher(delegateExpr).matches()) {
                    hasConnector = true;
                }
                if (!hasNotification && NOTIFICATION_DELEGATE_PATTERN.matcher(delegateExpr).matches()) {
                    hasNotification = true;
                }
            }

            if (hasDmn && hasConnector && hasNotification) {
                break; // all found; no need to scan further
            }
        }

        // Also check sendTask elements for notification delegate (SendTask is the canonical element)
        if (!hasNotification) {
            NodeList sendTasks = doc.getElementsByTagNameNS("*", "sendTask");
            for (int i = 0; i < sendTasks.getLength(); i++) {
                Element task = (Element) sendTasks.item(i);
                String delegateExpr = task.getAttributeNS(FLOWABLE_NS, "delegateExpression");
                if (delegateExpr != null && NOTIFICATION_DELEGATE_PATTERN.matcher(delegateExpr).matches()) {
                    hasNotification = true;
                    break;
                }
            }
        }

        return new Indicators(hasDmn, hasConnector, hasNotification);
    }

    private Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML is empty");
        }
        try {
            // Factory built per-call: DocumentBuilderFactory/DocumentBuilder are not thread-safe.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE hardening — no DOCTYPE, no external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse BPMN XML: " + e.getMessage(), e);
        }
    }
}

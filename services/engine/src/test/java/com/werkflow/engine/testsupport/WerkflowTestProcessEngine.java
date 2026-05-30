package com.werkflow.engine.testsupport;

import com.werkflow.engine.config.flowable.WerkflowProcessEngineCustomizer;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;

import java.util.Map;

/**
 * Builds a standalone in-memory Flowable process engine that mirrors the production engine's
 * deploy-time behaviour, for use in BPMN unit tests that need to actually deploy and execute
 * processes without a Spring context.
 *
 * <p>Specifically, this engine applies the SAME parse handlers and validator set as production
 * via {@link WerkflowProcessEngineCustomizer#applyValidatorsAndParseHandlers}, which means:
 * <ul>
 *   <li>The Werkflow SendTask parse handler is registered (ADR-015).</li>
 *   <li>The Werkflow validator family (SendTask / ScriptTask quarantine / BusinessRuleTask /
 *       ManualTask) is registered as the pre-deployment gate.</li>
 *   <li>The DMN engine is wired via {@link DmnEngineConfigurator}, exactly as the Spring Boot
 *       starter configures it in production (same shared datasource, separate DMN tables).</li>
 * </ul>
 *
 * <p>Spring-only concerns are intentionally <em>not</em> applied here — the caller is responsible
 * for them if needed:
 * <ul>
 *   <li>{@code RestrictedExpressionManager} — requires the Spring beans map; tests that need EL
 *       sandbox enforcement should boot Spring or install the manager explicitly.</li>
 *   <li>{@code GlobalTaskNotificationListener} — an email side-effect bean; unit tests must not
 *       trigger real email dispatch.</li>
 * </ul>
 *
 * <p>Each call to {@link #build(String)} creates a new, isolated engine backed by its own H2
 * in-memory database. Use a distinct {@code dbName} per test class (or per test when isolation
 * matters) to avoid schema collisions across parallel or sequential test runs.
 *
 * <p>Caller is responsible for closing the engine (typically in {@code @AfterAll}) to release
 * the H2 connection and Flowable thread pool.
 */
public final class WerkflowTestProcessEngine {

    private final ProcessEngine processEngine;
    private final DmnEngineConfiguration dmnEngineConfiguration;

    private WerkflowTestProcessEngine(ProcessEngine processEngine, DmnEngineConfiguration dmnEngineConfiguration) {
        this.processEngine = processEngine;
        this.dmnEngineConfiguration = dmnEngineConfiguration;
    }

    /**
     * Builds and returns a {@link WerkflowTestProcessEngine} backed by an isolated H2 in-memory
     * database named {@code dbName}.
     *
     * <p>The engine has:
     * <ul>
     *   <li>H2 in-memory JDBC URL: {@code jdbc:h2:mem:<dbName>;DB_CLOSE_DELAY=1000}</li>
     *   <li>{@code DB_SCHEMA_UPDATE_TRUE} — schema is created on first boot</li>
     *   <li>{@code createDiagramOnDeploy=false} — prevents NPE on BPMN files without graphic info</li>
     *   <li>{@code enableSafeBpmnXml=true} — mirrors production's detailed XML validation</li>
     *   <li>DMN engine wired via {@link DmnEngineConfigurator}</li>
     *   <li>Werkflow parse handlers + validators via {@link WerkflowProcessEngineCustomizer}</li>
     * </ul>
     *
     * @param dbName a short, URL-safe name for the H2 in-memory database (e.g. {@code bundleRollback})
     * @return a fully configured and started {@link WerkflowTestProcessEngine}
     */
    public static WerkflowTestProcessEngine build(String dbName) {
        return build(dbName, Map.of());
    }

    /**
     * Builds and returns a {@link WerkflowTestProcessEngine} with stub beans injected into the
     * engine's expression context. Use this overload when the BPMN under test contains
     * {@code flowable:delegateExpression="${beanName}"} service tasks that need stub
     * {@code JavaDelegate} implementations to execute in the bare in-memory engine.
     *
     * <p>The {@code beans} map is registered via
     * {@link org.flowable.common.engine.impl.AbstractEngineConfiguration#setBeans}, which is how
     * {@code ${beanName}} EL expressions are resolved when no Spring context is present.
     *
     * <p>All other settings are identical to {@link #build(String)}.
     *
     * @param dbName a short, URL-safe name for the H2 in-memory database
     * @param beans  a map of bean name to bean instance (e.g. {@code Map.of("myDelegate", new MyStub())});
     *               use {@code Map.of()} to register no additional beans (same as {@link #build(String)})
     * @return a fully configured and started {@link WerkflowTestProcessEngine}
     */
    public static WerkflowTestProcessEngine build(String dbName, Map<String, Object> beans) {
        DmnEngineConfigurator dmnConfigurator = new DmnEngineConfigurator();

        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        cfg.setCreateDiagramOnDeploy(false);
        cfg.setEnableSafeBpmnXml(true);

        // Wire the DMN engine exactly as the Spring Boot starter does in production:
        // a shared datasource, separate ACT_DMN_* tables, same H2 instance.
        cfg.addConfigurator(dmnConfigurator);

        // Apply the same parse handlers + validator family as FlowableConfig does in production.
        // See WerkflowProcessEngineCustomizer for what is intentionally excluded (Spring-only).
        WerkflowProcessEngineCustomizer.applyValidatorsAndParseHandlers(cfg);

        // Register stub beans so ${beanName} delegate expressions resolve without Spring.
        // This is the same mechanism FlowableConfig uses via SpringBeanFactoryProxyMap;
        // here we supply a plain map, sufficient for test stubs.
        if (!beans.isEmpty()) {
            // Flowable's setBeans expects Map<Object,Object>; copy to widen key type.
            cfg.setBeans(new java.util.HashMap<>(beans));
        }

        ProcessEngine processEngine = cfg.buildProcessEngine();

        // The configurator holds a reference to the fully-initialised DmnEngineConfiguration
        // (including all three DMN services) after the process engine has been built.
        return new WerkflowTestProcessEngine(processEngine, dmnConfigurator.getDmnEngineConfiguration());
    }

    /** The Flowable process engine (BPMN repository, runtime, task, history services). */
    public ProcessEngine getProcessEngine() {
        return processEngine;
    }

    /** DMN repository — deploy and query DMN decisions. */
    public DmnRepositoryService getDmnRepositoryService() {
        return dmnEngineConfiguration.getDmnRepositoryService();
    }

    /** Flowable's DMN decision execution service. */
    public org.flowable.dmn.api.DmnDecisionService getFlowableDmnDecisionService() {
        return dmnEngineConfiguration.getDmnDecisionService();
    }

    /** DMN history service — query past decision executions. */
    public DmnHistoryService getDmnHistoryService() {
        return dmnEngineConfiguration.getDmnHistoryService();
    }

    /** Closes the process engine and its embedded DMN engine. */
    public void close() {
        processEngine.close();
    }
}

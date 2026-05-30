package com.werkflow.engine.testsupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

/**
 * Abstract base class for Werkflow Scope-2 process tests (ADR-028).
 *
 * <p>Manages the {@link WerkflowTestProcessEngine} lifecycle and exposes a {@link ProcessTestDsl}
 * for deploying and driving BPMN processes in an isolated H2 in-memory engine.
 *
 * <p>Subclass usage:
 * <pre>{@code
 * class MyProcessTest extends WerkflowProcessTest {
 *     @BeforeAll void setup() {
 *         startEngine("myDb", Map.of("myDelegate", mock(JavaDelegate.class)));
 *         dsl.deploy("fragments/my-process.bpmn20.xml");
 *     }
 *
 *     @Test void flowCompletes() {
 *         dsl.start("my-process", Map.of("amount", 1000))
 *            .assertCompleted()
 *            .assertHistoricVariable("result", "approved");
 *     }
 * }
 * }</pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class WerkflowProcessTest {

    private WerkflowTestProcessEngine engine;
    protected ProcessTestDsl dsl;
    protected DmnTestRunner dmnRunner;

    protected void startEngine(String dbName) {
        startEngine(dbName, Map.of());
    }

    protected void startEngine(String dbName, Map<Object, Object> beans) {
        engine = WerkflowTestProcessEngine.build(dbName, beans);
        dsl = new ProcessTestDsl(engine.getProcessEngine());
        dmnRunner = new DmnTestRunner(engine.getProcessEngine());
    }

    @AfterAll
    void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }
}

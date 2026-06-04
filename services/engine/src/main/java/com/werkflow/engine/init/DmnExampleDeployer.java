package com.werkflow.engine.init;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnRepositoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Deploys example DMN decision tables from classpath:dmn/ on startup under the
 * {@code "default"} tenant so the BPMN designer's Decision Key dropdown can
 * resolve them.
 *
 * <p>Controlled by the same flags as {@link ProcessExampleDeployer}:
 * <ul>
 *   <li>{@code werkflow.examples.deploy-on-startup} (default: false) — gates all
 *       deployment; set to {@code true} to seed DMN examples.</li>
 *   <li>{@code werkflow.examples.reset-on-startup} (default: false) — wipes
 *       ALL tenantless DMN deployments (legacy dev cruft) and all prior
 *       default-tenant deployments for each example file before redeploying.
 *       Use once to clean an accumulated dev DB, then revert.</li>
 * </ul>
 *
 * <p>{@code enableDuplicateFiltering()} is set on every deploy so that restarts
 * with {@code reset-on-startup=false} do not bump DMN versions when file content
 * is unchanged.
 *
 * <p>This bean is ordered before {@link ProcessExampleDeployer} via
 * {@code @DependsOn("dmnExampleDeployer")} on that bean so DMN tables exist
 * before any process instance can evaluate a {@code decisionTableReferenceKey}.
 *
 * <p><strong>Cascade note:</strong> {@code DmnRepositoryService.deleteDeployment(id)}
 * takes a single argument (no cascade flag unlike BPMN's {@code RepositoryService}).
 * Flowable 7.2 cascades through DMN decision definitions and byte-arrays. Decision
 * execution history rows ({@code act_dmn_hi_decision_execution}) referencing deleted
 * decisions are NOT cascaded and will be left as orphans — acceptable for dev-only
 * cleanup where no production history exists.
 */
@Slf4j
@Component("dmnExampleDeployer")
public class DmnExampleDeployer {

    /**
     * Examples are kept under {@code dmn-examples/} (not the conventional {@code dmn/})
     * so Flowable's spring-boot {@code SpringBootAutoDeployment} cannot find them and
     * re-deploy as tenantless duplicates. This deployer is the sole owner of example
     * DMN seeding.
     */
    private static final String DMN_RESOURCE_PATTERN = "classpath:dmn-examples/*.dmn";
    private static final String DMN_RESOURCE_PREFIX = "dmn-examples/";

    private final DmnRepositoryService dmnRepositoryService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final boolean deployOnStartup;
    private final boolean resetOnStartup;
    private final String exampleTenantId;

    public DmnExampleDeployer(DmnRepositoryService dmnRepositoryService,
                               ResourcePatternResolver resourcePatternResolver,
                               @Value("${werkflow.examples.deploy-on-startup:false}") boolean deployOnStartup,
                               @Value("${werkflow.examples.reset-on-startup:false}") boolean resetOnStartup,
                               @Value("${werkflow.examples.tenant-id:default}") String exampleTenantId) {
        this.dmnRepositoryService = dmnRepositoryService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.deployOnStartup = deployOnStartup;
        this.resetOnStartup = resetOnStartup;
        this.exampleTenantId = exampleTenantId;
    }

    @PostConstruct
    public void deploy() throws IOException {
        if (!deployOnStartup) {
            log.info("Example DMN deployment skipped (werkflow.examples.deploy-on-startup=false)");
            return;
        }

        Resource[] resources = resourcePatternResolver.getResources(DMN_RESOURCE_PATTERN);
        if (resources.length == 0) {
            log.info("No example DMN files found in classpath:dmn/");
            return;
        }

        if (resetOnStartup) {
            cleanupLegacyTenantlessDeployments();
            resetDefaultTenantDeployments(resources);
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                dmnRepositoryService.createDeployment()
                        .name(filename)
                        .tenantId(exampleTenantId)
                        .enableDuplicateFiltering()
                        .addClasspathResource(DMN_RESOURCE_PREFIX + filename)
                        .deploy();
                log.info("Deployed example DMN: {}", filename);
            } catch (Exception e) {
                log.error("Failed to deploy example DMN '{}': {}", filename, e.getMessage());
            }
        }
    }

    /**
     * Deletes every DMN deployment that has no tenant (null or empty tenantId).
     * These are legacy dev deployments with no BPMN references and no source files
     * in the current repo (e.g. asset_transfer_routing, doa_routing,
     * general_approval_routing, and over-deployed tenantless copies of the example
     * DMNs). Runs only when {@code reset-on-startup=true}.
     *
     * <p>{@code act_dmn_hi_decision_execution} rows referencing these deployments
     * are left as orphans (Flowable 7.2 single-arg deleteDeployment does not
     * cascade to history). Acceptable for dev databases.
     */
    private void cleanupLegacyTenantlessDeployments() {
        List<DmnDeployment> tenantless = dmnRepositoryService.createDeploymentQuery()
                .deploymentWithoutTenantId()
                .list();
        if (tenantless.isEmpty()) {
            return;
        }
        log.warn("Legacy cleanup: deleting {} tenantless DMN deployment(s)", tenantless.size());
        for (DmnDeployment d : tenantless) {
            dmnRepositoryService.deleteDeployment(d.getId());
        }
    }

    /**
     * For each example DMN filename, deletes all prior default-tenant deployments
     * registered under that name. This resets accumulated version history so the
     * subsequent deploy starts fresh at version 1. Duplicate filtering then keeps
     * it stable on subsequent restarts without the reset flag.
     */
    private void resetDefaultTenantDeployments(Resource[] resources) {
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            List<DmnDeployment> existing = dmnRepositoryService.createDeploymentQuery()
                    .deploymentName(filename)
                    .deploymentTenantId(exampleTenantId)
                    .list();
            if (existing.isEmpty()) {
                continue;
            }
            log.warn("Reset mode: deleting {} prior default-tenant deployment(s) for '{}'",
                    existing.size(), filename);
            for (DmnDeployment d : existing) {
                dmnRepositoryService.deleteDeployment(d.getId());
            }
        }
    }
}

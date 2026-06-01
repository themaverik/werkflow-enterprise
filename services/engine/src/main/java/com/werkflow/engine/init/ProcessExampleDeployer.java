package com.werkflow.engine.init;

import com.werkflow.engine.service.ProcessDefinitionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deploys example BPMN processes from classpath:processes/examples/ on startup.
 * Controlled by the werkflow.examples.deploy-on-startup flag (default: false).
 *
 * <p>Optional reset mode (werkflow.examples.reset-on-startup, default false): before
 * deploying each example, removes every prior Flowable deployment registered under the
 * same name via RepositoryService.deleteDeployment(id, true). Use to clear accumulated
 * version history (e.g. Leave v119, Event Ticket v104) from dev databases predating
 * enableDuplicateFiltering(). After one boot with the flag set, examples redeploy fresh
 * at version 1; subsequent restarts without the flag stay at version 1 thanks to
 * duplicate filtering.
 */
@Slf4j
@Component
@DependsOn("dmnExampleDeployer")
public class ProcessExampleDeployer {

    private static final String BPMN_SUFFIX = ".bpmn20.xml";

    private final ProcessDefinitionService processDefinitionService;
    private final RepositoryService repositoryService;
    private final JdbcTemplate jdbcTemplate;
    private final ResourcePatternResolver resourcePatternResolver;
    private final boolean deployOnStartup;
    private final boolean resetOnStartup;

    public ProcessExampleDeployer(ProcessDefinitionService processDefinitionService,
                                   RepositoryService repositoryService,
                                   JdbcTemplate jdbcTemplate,
                                   ResourcePatternResolver resourcePatternResolver,
                                   @Value("${werkflow.examples.deploy-on-startup:false}") boolean deployOnStartup,
                                   @Value("${werkflow.examples.reset-on-startup:false}") boolean resetOnStartup) {
        this.processDefinitionService = processDefinitionService;
        this.repositoryService = repositoryService;
        this.jdbcTemplate = jdbcTemplate;
        this.resourcePatternResolver = resourcePatternResolver;
        this.deployOnStartup = deployOnStartup;
        this.resetOnStartup = resetOnStartup;
    }

    @PostConstruct
    public void deploy() throws IOException {
        if (!deployOnStartup) {
            log.info("Example process deployment skipped (werkflow.examples.deploy-on-startup=false)");
            return;
        }

        Resource[] resources = resourcePatternResolver.getResources("classpath:processes/examples/*.bpmn20.xml");
        if (resources.length == 0) {
            log.info("No example BPMN files found in classpath:processes/examples/");
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                if (resetOnStartup) {
                    String processKey = stripBpmnSuffix(filename);
                    resetExampleDraftAndBundle(processKey);
                    resetExampleDeployments(filename);
                }
                String bpmnXml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                processDefinitionService.deployExampleProcessDefinition(bpmnXml, filename);
                log.info("Deployed example process: {}", filename);
            } catch (Exception e) {
                log.error("Failed to deploy example process '{}': {}", filename, e.getMessage());
            }
        }
    }

    /**
     * Removes orphan example drafts + bundle index for a process key via JdbcTemplate
     * (implicit per-statement transaction; sidesteps the AOP self-call limitation of
     * a @PostConstruct calling a @Transactional method on the same bean). Either table
     * may be absent on fresh DBs that have not run Flyway — caught and logged at debug.
     *
     * <p><strong>Non-atomic with subsequent deploy:</strong> these JdbcTemplate deletes
     * auto-commit per statement and are not enrolled in the Flowable deployment
     * transaction. If the deploy fails after this method succeeds, the orphan rows are
     * gone but Flowable is unchanged — re-running with the flag set converges the state.
     *
     * <p>Assumes the BPMN filename stripped of {@value #BPMN_SUFFIX} equals the
     * {@code process_key} stored in those tables (true for shipped examples).
     */
    private void resetExampleDraftAndBundle(String processKey) {
        try {
            int n = jdbcTemplate.update("DELETE FROM process_draft WHERE process_key = ?", processKey);
            if (n > 0) {
                log.info("Reset mode: removed {} orphan draft(s) for '{}'", n, processKey);
            }
        } catch (DataAccessException e) {
            log.debug("Reset mode: process_draft cleanup skipped for '{}' (table likely absent)", processKey);
        }
        try {
            jdbcTemplate.update("DELETE FROM process_bundle WHERE process_key = ?", processKey);
        } catch (DataAccessException e) {
            log.debug("Reset mode: process_bundle cleanup skipped for '{}' (table likely absent)", processKey);
        }
    }

    private static String stripBpmnSuffix(String filename) {
        if (filename != null && filename.endsWith(BPMN_SUFFIX)) {
            return filename.substring(0, filename.length() - BPMN_SUFFIX.length());
        }
        return filename;
    }

    /**
     * Deletes every Flowable deployment registered under {@code deploymentName} (the
     * example's BPMN filename — the same string used by deployExampleProcessDefinition
     * as the deployment name + duplicate-filter key), cascading through process
     * definitions, bytearrays, and synchronously-written history rows. Skips if no
     * deployments exist. Cascade=true is essential — direct SQL DELETE on
     * ACT_RE_DEPLOYMENT fails on Flowable's FK constraints.
     *
     * <p><strong>Filename stability:</strong> the deployment name equals the resource
     * filename. Renaming a BPMN file breaks both this reset query and the duplicate
     * filter at deploy time.
     *
     * <p><strong>Async history caveat:</strong> with async-history enabled, history
     * events that were enqueued but not yet flushed at delete time are not removed by
     * this cascade — they will fail and land in the dead-letter table on next flush.
     * Acceptable for dev-only one-shot reset where no live instances exist.
     */
    private void resetExampleDeployments(String deploymentName) {
        List<Deployment> existing = repositoryService.createDeploymentQuery()
            .deploymentName(deploymentName)
            .list();
        if (existing.isEmpty()) {
            return;
        }
        log.warn("Reset mode: deleting {} prior deployment(s) for '{}'", existing.size(), deploymentName);
        for (Deployment d : existing) {
            repositoryService.deleteDeployment(d.getId(), true);
        }
    }
}

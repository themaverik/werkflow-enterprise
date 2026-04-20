package com.werkflow.engine.service;

import com.werkflow.engine.dto.dmn.DmnDecisionDto;
import com.werkflow.engine.dto.dmn.DmnExecutionDto;
import com.werkflow.engine.dto.dmn.DmnTestResultDto;
import com.werkflow.engine.exception.ProcessNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnHistoricDecisionExecution;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing DMN decision tables.
 * Wraps Flowable DmnRepositoryService, DmnDecisionService, and DmnHistoryService.
 *
 * Note: Flowable also provides an interface named DmnDecisionService — this class is injected
 * as {@code flowableDmnDecisionService} to avoid a name clash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DmnDecisionService {

    private final DmnRepositoryService dmnRepositoryService;
    /** Flowable's DmnDecisionService — injected via @RequiredArgsConstructor by Spring type match */
    private final org.flowable.dmn.api.DmnDecisionService flowableDmnDecisionService;
    private final DmnHistoryService dmnHistoryService;

    /**
     * Returns the latest deployed version of every decision key visible to the given tenant.
     */
    public List<DmnDecisionDto> listDecisions(String tenantId) {
        List<DmnDecision> decisions = dmnRepositoryService.createDecisionQuery()
                .decisionTenantId(tenantId)
                .latestVersion()
                .orderByDecisionName().asc()
                .list();
        return decisions.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Returns metadata for the latest version of a specific decision by key.
     *
     * @throws ProcessNotFoundException if no decision with the given key exists for the tenant
     */
    public DmnDecisionDto getDecision(String key, String tenantId) {
        DmnDecision decision = resolveDecision(key, tenantId);
        return toDto(decision);
    }

    /**
     * Returns the raw DMN XML for the latest version of a decision.
     */
    public String getDecisionXml(String key, String tenantId) {
        DmnDecision decision = resolveDecision(key, tenantId);
        try (InputStream xmlStream = dmnRepositoryService.getDmnResource(decision.getDeploymentId())) {
            return new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read DMN XML for key: " + key, e);
        }
    }

    /**
     * Deploys a new DMN decision from raw XML.
     * Validates XML before persisting; Flowable auto-increments the version if the key already exists.
     */
    @Transactional
    public DmnDecisionDto deployDecision(String dmnXml, String resourceName, String tenantId) {
        validateDmnXml(dmnXml);
        log.info("Deploying DMN decision: {} for tenant: {}", resourceName, tenantId);

        String fileName = resourceName.endsWith(".dmn") ? resourceName : resourceName + ".dmn";
        try (InputStream inputStream = new ByteArrayInputStream(
                dmnXml.getBytes(StandardCharsets.UTF_8))) {

            DmnDeployment deployment = dmnRepositoryService.createDeployment()
                    .name(resourceName)
                    .tenantId(tenantId)
                    .addInputStream(fileName, inputStream)
                    .deploy();

            DmnDecision deployed = dmnRepositoryService.createDecisionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();

            if (deployed == null) {
                throw new IllegalStateException(
                        "DMN deployed but no decision found in deployment: " + deployment.getId());
            }
            return toDto(deployed);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deploy DMN XML: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a deployment by Flowable deployment ID.
     */
    @Transactional
    public void deleteDeployment(String deploymentId) {
        log.info("Deleting DMN deployment: {}", deploymentId);
        dmnRepositoryService.deleteDeployment(deploymentId);
    }

    /**
     * Evaluates a decision using the supplied variable map.
     * Returns a test result — this does NOT persist to Flowable history (safe for UI test panel).
     */
    public DmnTestResultDto testDecision(String key, String tenantId, Map<String, Object> inputs) {
        List<Map<String, Object>> resultList = flowableDmnDecisionService
                .createExecuteDecisionBuilder()
                .decisionKey(key)
                .tenantId(tenantId)
                .variables(inputs)
                .execute();

        if (resultList == null) {
            resultList = Collections.emptyList();
        }

        return DmnTestResultDto.builder()
                .inputs(inputs)
                .resultList(resultList)
                .matchedRuleCount(resultList.size())
                .build();
    }

    /**
     * Returns paginated execution history for a decision key.
     * Reads from Flowable's ACT_HI_DEC_INST table via DmnHistoryService.
     */
    public Page<DmnExecutionDto> getExecutionHistory(String key, String tenantId, Pageable pageable) {
        long total = dmnHistoryService.createHistoricDecisionExecutionQuery()
                .decisionKey(key)
                .count();

        List<DmnHistoricDecisionExecution> executions = dmnHistoryService
                .createHistoricDecisionExecutionQuery()
                .decisionKey(key)
                .orderByEndTime().desc()
                .listPage((int) pageable.getOffset(), pageable.getPageSize());

        List<DmnExecutionDto> dtos = executions.stream()
                .map(this::toExecutionDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, total);
    }

    // --- private helpers ---

    private DmnDecision resolveDecision(String key, String tenantId) {
        DmnDecision decision = dmnRepositoryService.createDecisionQuery()
                .decisionKey(key)
                .decisionTenantId(tenantId)
                .latestVersion()
                .singleResult();
        if (decision == null) {
            throw new ProcessNotFoundException(
                    "DMN decision not found: " + key + " (tenant: " + tenantId + ")");
        }
        return decision;
    }

    private DmnDecisionDto toDto(DmnDecision decision) {
        return DmnDecisionDto.builder()
                .id(decision.getId())
                .key(decision.getKey())
                .name(decision.getName())
                .version(decision.getVersion())
                .deploymentId(decision.getDeploymentId())
                .tenantId(decision.getTenantId())
                .deployedAt(null) // DmnDecision does not expose createTime; use deployment query if needed
                .build();
    }

    private DmnExecutionDto toExecutionDto(DmnHistoricDecisionExecution execution) {
        // executionJson contains the serialised input/output — parse if non-null
        Map<String, Object> inputs = Collections.emptyMap();
        Map<String, Object> outputs = Collections.emptyMap();

        return DmnExecutionDto.builder()
                .id(execution.getId())
                .decisionKey(execution.getDecisionKey())
                .decisionName(execution.getDecisionName())
                .inputs(inputs)
                .outputs(outputs)
                .matchedRuleCount(0)
                .processInstanceId(execution.getInstanceId())
                .executedAt(toOffsetDateTime(execution.getEndTime()))
                .build();
    }

    private OffsetDateTime toOffsetDateTime(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * Validates that the supplied string is well-formed XML.
     * Uses a hardened DocumentBuilderFactory to prevent XXE injection.
     */
    private void validateDmnXml(String dmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new ByteArrayInputStream(dmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Invalid DMN XML: " + e.getMessage(), e);
        }
    }
}

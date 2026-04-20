package com.werkflow.business.finance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles CapEx workflow operations called by the Engine service via RestServiceDelegate.
 *
 * MVP: Uses stub responses. Post-MVP: Will be backed by CapEx entity/repository
 * once the Finance domain model includes CapEx request tracking.
 */
@Slf4j
@Service
public class CapExWorkflowService {

    public Map<String, Object> createRequest(Map<String, Object> request) {
        String title = (String) request.get("title");
        String departmentName = (String) request.get("departmentName");
        String requestedBy = (String) request.get("requestedBy");

        log.info("Creating CapEx request: title={}, department={}, requestedBy={}",
            title, departmentName, requestedBy);

        // MVP stub: generate an ID and request number
        long capexId = System.currentTimeMillis();
        String requestNumber = "CAPEX-" + capexId;

        Map<String, Object> response = new HashMap<>();
        response.put("capexId", capexId);
        response.put("requestNumber", requestNumber);
        response.put("status", "SUBMITTED");
        response.put("success", true);
        response.put("message", "CapEx request created successfully");

        log.info("CapEx request created: id={}, number={}", capexId, requestNumber);
        return response;
    }

    public Map<String, Object> checkBudget(Map<String, Object> request) {
        Object capexId = request.get("capexId");
        Object requestAmount = request.get("requestAmount");
        String departmentName = (String) request.get("departmentName");

        log.info("Checking budget: capexId={}, amount={}, department={}",
            capexId, requestAmount, departmentName);

        // MVP stub: always return budget available
        Map<String, Object> response = new HashMap<>();
        response.put("budgetAvailable", true);
        response.put("availableBudget", 1000000.0);
        response.put("requestAmount", requestAmount);
        response.put("success", true);
        response.put("message", "Budget available for request");

        return response;
    }

    public Map<String, Object> updateStatus(Map<String, Object> request) {
        Object capexId = request.get("capexId");
        String status = (String) request.get("status");
        String comments = (String) request.getOrDefault("comments", "");

        log.info("Updating CapEx status: capexId={}, status={}", capexId, status);

        // MVP stub: acknowledge status update
        Map<String, Object> response = new HashMap<>();
        response.put("capexId", capexId);
        response.put("status", status);
        response.put("success", true);
        response.put("message", "Status updated to " + status);

        return response;
    }

    public Map<String, Object> allocateBudget(Map<String, Object> request) {
        Object capexId = request.get("capexId");
        Object requestAmount = request.get("requestAmount");
        String departmentName = (String) request.get("departmentName");

        log.info("Allocating budget: capexId={}, amount={}, department={}",
            capexId, requestAmount, departmentName);

        // MVP stub: always succeed
        String allocationId = "ALLOC-" + capexId + "-" + System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("allocationSuccessful", true);
        response.put("allocationId", allocationId);
        response.put("allocatedAmount", requestAmount);
        response.put("success", true);
        response.put("message", "Budget allocated successfully");

        log.info("Budget allocated: allocationId={}", allocationId);
        return response;
    }
}

package com.werkflow.business.finance.service;

import com.werkflow.business.finance.dto.BudgetPlanRequest;
import com.werkflow.business.finance.dto.BudgetPlanResponse;
import com.werkflow.business.finance.entity.BudgetPlan;
import com.werkflow.business.finance.repository.BudgetPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetPlanService {
    private final BudgetPlanRepository budgetPlanRepository;

    @Transactional(readOnly = true)
    public List<BudgetPlanResponse> getAllBudgetPlans() {
        return budgetPlanRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BudgetPlanResponse getBudgetPlanById(Long id) {
        return budgetPlanRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Budget plan not found: " + id));
    }

    @Transactional
    public BudgetPlanResponse createBudgetPlan(BudgetPlanRequest request) {
        BudgetPlan plan = BudgetPlan.builder()
            .departmentId(request.getDepartmentId())
            .fiscalYear(request.getFiscalYear())
            .periodStart(request.getPeriodStart())
            .periodEnd(request.getPeriodEnd())
            .totalAmount(request.getTotalAmount())
            .allocatedAmount(BigDecimal.ZERO)
            .spentAmount(BigDecimal.ZERO)
            .status(BudgetPlan.BudgetStatus.DRAFT)
            .createdByUserId(request.getCreatedByUserId())
            .notes(request.getNotes())
            .build();
        return toResponse(budgetPlanRepository.save(plan));
    }

    private BudgetPlanResponse toResponse(BudgetPlan plan) {
        return BudgetPlanResponse.builder()
            .id(plan.getId())
            .departmentId(plan.getDepartmentId())
            .fiscalYear(plan.getFiscalYear())
            .periodStart(plan.getPeriodStart())
            .periodEnd(plan.getPeriodEnd())
            .totalAmount(plan.getTotalAmount())
            .allocatedAmount(plan.getAllocatedAmount())
            .spentAmount(plan.getSpentAmount())
            .status(plan.getStatus())
            .createdByUserId(plan.getCreatedByUserId())
            .approvedByUserId(plan.getApprovedByUserId())
            .approvedDate(plan.getApprovedDate())
            .notes(plan.getNotes())
            .createdAt(plan.getCreatedAt())
            .updatedAt(plan.getUpdatedAt())
            .build();
    }
}

package com.werkflow.business.finance.service;

import com.werkflow.business.finance.dto.BudgetCheckRequest;
import com.werkflow.business.finance.dto.BudgetCheckResponse;
import com.werkflow.business.finance.entity.BudgetPlan;
import com.werkflow.business.finance.repository.BudgetPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service for checking budget availability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetCheckService {

    private final BudgetPlanRepository budgetPlanRepository;

    @Transactional(readOnly = true)
    public BudgetCheckResponse checkBudgetAvailability(BudgetCheckRequest request) {
        log.debug("Checking budget availability for department: {}, amount: {}",
                request.getDepartmentId(), request.getAmount());

        Integer fiscalYear = request.getFiscalYear();
        if (fiscalYear == null) {
            fiscalYear = LocalDate.now().getYear();
        }

        final Integer finalFiscalYear = fiscalYear;
        BudgetPlan budgetPlan = budgetPlanRepository
                .findByDepartmentIdAndFiscalYear(request.getDepartmentId(), fiscalYear)
                .orElse(null);

        if (budgetPlan == null) {
            return BudgetCheckResponse.builder()
                    .available(false)
                    .reason("No budget plan found for department ID " + request.getDepartmentId() +
                           " in fiscal year " + finalFiscalYear)
                    .requestedAmount(request.getAmount())
                    .availableAmount(BigDecimal.ZERO)
                    .allocatedAmount(BigDecimal.ZERO)
                    .utilizedAmount(BigDecimal.ZERO)
                    .costCenter(request.getCostCenter())
                    .fiscalYear(finalFiscalYear)
                    .build();
        }

        return checkOverallBudget(request, budgetPlan, finalFiscalYear);
    }

    private BudgetCheckResponse checkOverallBudget(BudgetCheckRequest request,
                                                   BudgetPlan budgetPlan,
                                                   Integer fiscalYear) {
        BigDecimal allocatedAmount = budgetPlan.getAllocatedAmount() != null ?
                budgetPlan.getAllocatedAmount() : BigDecimal.ZERO;
        BigDecimal spentAmount = budgetPlan.getSpentAmount() != null ?
                budgetPlan.getSpentAmount() : BigDecimal.ZERO;

        BigDecimal availableAmount = allocatedAmount.subtract(spentAmount);

        boolean isBudgetAvailable = availableAmount.compareTo(request.getAmount()) >= 0;

        String reason = isBudgetAvailable ?
                "Sufficient budget available for department" :
                "Insufficient budget for department. Available: " + availableAmount +
                ", Requested: " + request.getAmount();

        return BudgetCheckResponse.builder()
                .available(isBudgetAvailable)
                .reason(reason)
                .requestedAmount(request.getAmount())
                .availableAmount(availableAmount)
                .allocatedAmount(allocatedAmount)
                .utilizedAmount(spentAmount)
                .costCenter(request.getCostCenter())
                .fiscalYear(fiscalYear)
                .build();
    }
}

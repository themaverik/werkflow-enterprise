package com.werkflow.business.finance.service;

import com.werkflow.business.finance.dto.ExpenseRequest;
import com.werkflow.business.finance.dto.ExpenseResponse;
import com.werkflow.business.finance.entity.Expense;
import com.werkflow.business.finance.repository.ExpenseRepository;
import com.werkflow.business.finance.repository.BudgetCategoryRepository;
import com.werkflow.business.finance.repository.BudgetLineItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final BudgetCategoryRepository categoryRepository;
    private final BudgetLineItemRepository lineItemRepository;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getAllExpenses() {
        return expenseRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(Long id) {
        return expenseRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Expense not found: " + id));
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request) {
        var category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new RuntimeException("Category not found"));

        Expense expense = Expense.builder()
            .departmentId(request.getDepartmentId())
            .expenseDate(request.getExpenseDate())
            .amount(request.getAmount())
            .category(category)
            .vendorName(request.getVendorName())
            .description(request.getDescription())
            .receiptUrl(request.getReceiptUrl())
            .status(Expense.ExpenseStatus.SUBMITTED)
            .submittedByUserId(request.getSubmittedByUserId())
            .metadata(request.getMetadata())
            .build();

        if (request.getBudgetLineItemId() != null) {
            var lineItem = lineItemRepository.findById(request.getBudgetLineItemId())
                .orElseThrow(() -> new RuntimeException("Budget line item not found"));
            expense.setBudgetLineItem(lineItem);
        }

        return toResponse(expenseRepository.save(expense));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
            .id(expense.getId())
            .budgetLineItemId(expense.getBudgetLineItem() != null ? expense.getBudgetLineItem().getId() : null)
            .departmentId(expense.getDepartmentId())
            .expenseDate(expense.getExpenseDate())
            .amount(expense.getAmount())
            .categoryId(expense.getCategory().getId())
            .categoryName(expense.getCategory().getName())
            .vendorName(expense.getVendorName())
            .description(expense.getDescription())
            .receiptUrl(expense.getReceiptUrl())
            .status(expense.getStatus())
            .submittedByUserId(expense.getSubmittedByUserId())
            .approvedByUserId(expense.getApprovedByUserId())
            .approvedDate(expense.getApprovedDate())
            .processInstanceId(expense.getProcessInstanceId())
            .rejectionReason(expense.getRejectionReason())
            .metadata(expense.getMetadata())
            .createdAt(expense.getCreatedAt())
            .updatedAt(expense.getUpdatedAt())
            .build();
    }
}

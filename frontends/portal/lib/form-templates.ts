// Predefined form templates for common HR workflows

export const formTemplates = {
  'leave-request': {
    title: 'Leave Request Form',
    display: 'form',
    components: [
      {
        type: 'select',
        key: 'leaveType',
        label: 'Leave Type',
        placeholder: 'Select leave type',
        data: {
          values: [
            { label: 'Annual Leave', value: 'annual' },
            { label: 'Sick Leave', value: 'sick' },
            { label: 'Personal Leave', value: 'personal' },
            { label: 'Maternity/Paternity Leave', value: 'maternity' },
            { label: 'Unpaid Leave', value: 'unpaid' },
          ],
        },
        validate: {
          required: true,
        },
      },
      {
        type: 'datetime',
        key: 'startDate',
        label: 'Start Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: {
          required: true,
        },
      },
      {
        type: 'datetime',
        key: 'endDate',
        label: 'End Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: {
          required: true,
        },
      },
      {
        type: 'number',
        key: 'totalDays',
        label: 'Total Days',
        validate: {
          required: true,
          min: 0.5,
        },
      },
      {
        type: 'textarea',
        key: 'reason',
        label: 'Reason',
        placeholder: 'Please provide reason for leave',
        validate: {
          required: true,
          minLength: 10,
        },
      },
      {
        type: 'file',
        key: 'attachments',
        label: 'Supporting Documents (if any)',
        storage: 'base64',
        multiple: true,
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Leave Request',
        theme: 'primary',
      },
    ],
  },

  'employee-onboarding': {
    title: 'Employee Onboarding Form',
    display: 'wizard',
    components: [
      {
        title: 'Personal Information',
        key: 'personalInfo',
        type: 'panel',
        components: [
          {
            type: 'textfield',
            key: 'firstName',
            label: 'First Name',
            validate: { required: true },
          },
          {
            type: 'textfield',
            key: 'lastName',
            label: 'Last Name',
            validate: { required: true },
          },
          {
            type: 'email',
            key: 'email',
            label: 'Email Address',
            validate: { required: true },
          },
          {
            type: 'phoneNumber',
            key: 'phone',
            label: 'Phone Number',
            validate: { required: true },
          },
          {
            type: 'datetime',
            key: 'dateOfBirth',
            label: 'Date of Birth',
            format: 'yyyy-MM-dd',
            enableTime: false,
            validate: { required: true },
          },
        ],
      },
      {
        title: 'Employment Details',
        key: 'employmentDetails',
        type: 'panel',
        components: [
          {
            type: 'textfield',
            key: 'jobTitle',
            label: 'Job Title',
            validate: { required: true },
          },
          {
            type: 'select',
            key: 'department',
            label: 'Department',
            data: {
              values: [
                { label: 'Engineering', value: 'engineering' },
                { label: 'Human Resources', value: 'hr' },
                { label: 'Finance', value: 'finance' },
                { label: 'Sales', value: 'sales' },
                { label: 'Marketing', value: 'marketing' },
              ],
            },
            validate: { required: true },
          },
          {
            type: 'datetime',
            key: 'startDate',
            label: 'Start Date',
            format: 'yyyy-MM-dd',
            enableTime: false,
            validate: { required: true },
          },
          {
            type: 'select',
            key: 'employmentType',
            label: 'Employment Type',
            data: {
              values: [
                { label: 'Full-time', value: 'fulltime' },
                { label: 'Part-time', value: 'parttime' },
                { label: 'Contract', value: 'contract' },
                { label: 'Intern', value: 'intern' },
              ],
            },
            validate: { required: true },
          },
        ],
      },
      {
        title: 'Documents',
        key: 'documents',
        type: 'panel',
        components: [
          {
            type: 'file',
            key: 'resume',
            label: 'Resume/CV',
            storage: 'base64',
            validate: { required: true },
          },
          {
            type: 'file',
            key: 'idProof',
            label: 'ID Proof',
            storage: 'base64',
            validate: { required: true },
          },
          {
            type: 'file',
            key: 'educationCertificates',
            label: 'Education Certificates',
            storage: 'base64',
            multiple: true,
          },
        ],
      },
      {
        title: 'Emergency Contact',
        key: 'emergencyContact',
        type: 'panel',
        components: [
          {
            type: 'textfield',
            key: 'emergencyContactName',
            label: 'Contact Name',
            validate: { required: true },
          },
          {
            type: 'textfield',
            key: 'emergencyContactRelationship',
            label: 'Relationship',
            validate: { required: true },
          },
          {
            type: 'phoneNumber',
            key: 'emergencyContactPhone',
            label: 'Phone Number',
            validate: { required: true },
          },
        ],
      },
    ],
  },

  'performance-review': {
    title: 'Performance Review Form',
    display: 'form',
    components: [
      {
        type: 'textfield',
        key: 'reviewPeriod',
        label: 'Review Period',
        placeholder: 'e.g., Q4 2024',
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'overallRating',
        label: 'Overall Performance Rating',
        data: {
          values: [
            { label: '5 - Exceptional', value: '5' },
            { label: '4 - Exceeds Expectations', value: '4' },
            { label: '3 - Meets Expectations', value: '3' },
            { label: '2 - Needs Improvement', value: '2' },
            { label: '1 - Unsatisfactory', value: '1' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'achievements',
        label: 'Key Achievements',
        placeholder: 'List major accomplishments during this period',
        validate: { required: true, minLength: 50 },
      },
      {
        type: 'textarea',
        key: 'areasOfImprovement',
        label: 'Areas for Improvement',
        placeholder: 'Identify areas for development',
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'goalsForNextPeriod',
        label: 'Goals for Next Period',
        placeholder: 'Set goals for the upcoming period',
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'managerComments',
        label: 'Manager Comments',
        placeholder: 'Additional feedback',
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Review',
        theme: 'primary',
      },
    ],
  },

  'expense-claim': {
    title: 'Expense Claim Form',
    display: 'form',
    components: [
      {
        type: 'select',
        key: 'expenseType',
        label: 'Expense Type',
        data: {
          values: [
            { label: 'Travel', value: 'travel' },
            { label: 'Meals', value: 'meals' },
            { label: 'Accommodation', value: 'accommodation' },
            { label: 'Office Supplies', value: 'supplies' },
            { label: 'Training', value: 'training' },
            { label: 'Other', value: 'other' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'number',
        key: 'amount',
        label: 'Amount',
        prefix: '$',
        validate: {
          required: true,
          min: 0,
        },
      },
      {
        type: 'datetime',
        key: 'expenseDate',
        label: 'Expense Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'merchant',
        label: 'Merchant/Vendor',
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'description',
        label: 'Description',
        placeholder: 'Provide details about the expense',
        validate: { required: true },
      },
      {
        type: 'file',
        key: 'receipt',
        label: 'Receipt',
        storage: 'base64',
        validate: { required: true },
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Claim',
        theme: 'primary',
      },
    ],
  },

  'asset-request': {
    title: 'IT Asset Request Form',
    display: 'form',
    components: [
      {
        type: 'select',
        key: 'assetType',
        label: 'Asset Type',
        data: {
          values: [
            { label: 'Laptop', value: 'laptop' },
            { label: 'Desktop', value: 'desktop' },
            { label: 'Monitor', value: 'monitor' },
            { label: 'Mobile Phone', value: 'mobile' },
            { label: 'Software License', value: 'software' },
            { label: 'Other Hardware', value: 'other' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'specifications',
        label: 'Specifications',
        placeholder: 'e.g., MacBook Pro 16", 32GB RAM',
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'businessJustification',
        label: 'Business Justification',
        placeholder: 'Explain why this asset is needed',
        validate: { required: true, minLength: 20 },
      },
      {
        type: 'select',
        key: 'priority',
        label: 'Priority',
        data: {
          values: [
            { label: 'Urgent', value: 'urgent' },
            { label: 'High', value: 'high' },
            { label: 'Medium', value: 'medium' },
            { label: 'Low', value: 'low' },
          ],
        },
        defaultValue: 'medium',
        validate: { required: true },
      },
      {
        type: 'datetime',
        key: 'requiredBy',
        label: 'Required By Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Request',
        theme: 'primary',
      },
    ],
  },

  'capex-request': {
    title: 'Capital Expenditure Request Form',
    display: 'form',
    components: [
      {
        type: 'textfield',
        key: 'projectName',
        label: 'Project Name',
        placeholder: 'Enter project or asset name',
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'departmentId',
        label: 'Department',
        data: {
          values: [
            { label: 'Engineering', value: 'engineering' },
            { label: 'Finance', value: 'finance' },
            { label: 'Operations', value: 'operations' },
            { label: 'Sales', value: 'sales' },
            { label: 'Marketing', value: 'marketing' },
            { label: 'IT', value: 'it' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'capexCategory',
        label: 'CapEx Category',
        data: {
          values: [
            { label: 'Equipment', value: 'EQUIPMENT' },
            { label: 'Infrastructure', value: 'INFRASTRUCTURE' },
            { label: 'Technology', value: 'TECHNOLOGY' },
            { label: 'Facility Improvement', value: 'FACILITY' },
            { label: 'Vehicles', value: 'VEHICLES' },
            { label: 'Other', value: 'OTHER' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'number',
        key: 'requestAmount',
        label: 'Requested Amount',
        prefix: '$',
        placeholder: '0.00',
        validate: {
          required: true,
          min: 0.01,
        },
      },
      {
        type: 'textarea',
        key: 'description',
        label: 'Project Description',
        placeholder: 'Detailed description of the capital expenditure',
        validate: { required: true, minLength: 50 },
      },
      {
        type: 'textarea',
        key: 'businessJustification',
        label: 'Business Justification',
        placeholder: 'Explain the business case and strategic alignment',
        validate: { required: true, minLength: 50 },
      },
      {
        type: 'number',
        key: 'expectedRoi',
        label: 'Expected ROI (%)',
        suffix: '%',
        placeholder: 'Expected return on investment',
        validate: {
          required: true,
          min: 0,
        },
      },
      {
        type: 'number',
        key: 'paybackPeriod',
        label: 'Payback Period (months)',
        suffix: 'months',
        placeholder: 'Time to recover investment',
        validate: {
          required: true,
          min: 1,
        },
      },
      {
        type: 'datetime',
        key: 'proposedStartDate',
        label: 'Proposed Start Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: { required: true },
      },
      {
        type: 'datetime',
        key: 'expectedCompletionDate',
        label: 'Expected Completion Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: { required: true },
      },
      {
        type: 'file',
        key: 'supportingDocuments',
        label: 'Supporting Documents',
        storage: 'base64',
        multiple: true,
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit CapEx Request',
        theme: 'primary',
      },
    ],
  },

  'procurement-request': {
    title: 'Purchase Request Form',
    display: 'form',
    components: [
      {
        type: 'textfield',
        key: 'requestTitle',
        label: 'Request Title',
        placeholder: 'Brief description of purchase',
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'itemCategory',
        label: 'Item Category',
        data: {
          values: [
            { label: 'Office Supplies', value: 'OFFICE_SUPPLIES' },
            { label: 'IT Equipment', value: 'IT_EQUIPMENT' },
            { label: 'Furniture', value: 'FURNITURE' },
            { label: 'Software', value: 'SOFTWARE' },
            { label: 'Services', value: 'SERVICES' },
            { label: 'Raw Materials', value: 'RAW_MATERIALS' },
            { label: 'Other', value: 'OTHER' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'itemDescription',
        label: 'Item Description',
        placeholder: 'Detailed description of items to be purchased',
        validate: { required: true, minLength: 20 },
      },
      {
        type: 'number',
        key: 'quantity',
        label: 'Quantity',
        placeholder: 'Number of items',
        validate: {
          required: true,
          min: 1,
        },
      },
      {
        type: 'textfield',
        key: 'unitOfMeasure',
        label: 'Unit of Measure',
        placeholder: 'e.g., pieces, boxes, licenses',
        validate: { required: true },
      },
      {
        type: 'number',
        key: 'estimatedUnitPrice',
        label: 'Estimated Unit Price',
        prefix: '$',
        placeholder: '0.00',
        validate: {
          required: true,
          min: 0.01,
        },
      },
      {
        type: 'number',
        key: 'estimatedTotalCost',
        label: 'Estimated Total Cost',
        prefix: '$',
        placeholder: '0.00',
        disabled: true,
        calculateValue: 'value = data.quantity * data.estimatedUnitPrice',
      },
      {
        type: 'textarea',
        key: 'businessJustification',
        label: 'Business Justification',
        placeholder: 'Explain why this purchase is necessary',
        validate: { required: true, minLength: 30 },
      },
      {
        type: 'textfield',
        key: 'preferredVendor',
        label: 'Preferred Vendor (if any)',
        placeholder: 'Leave blank to select from approved vendors',
      },
      {
        type: 'datetime',
        key: 'requiredByDate',
        label: 'Required By Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'urgency',
        label: 'Urgency',
        data: {
          values: [
            { label: 'Critical', value: 'CRITICAL' },
            { label: 'High', value: 'HIGH' },
            { label: 'Normal', value: 'NORMAL' },
            { label: 'Low', value: 'LOW' },
          ],
        },
        defaultValue: 'NORMAL',
        validate: { required: true },
      },
      {
        type: 'file',
        key: 'attachments',
        label: 'Attachments',
        storage: 'base64',
        multiple: true,
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Purchase Request',
        theme: 'primary',
      },
    ],
  },

  'asset-transfer-request': {
    title: 'Asset Transfer Request Form',
    display: 'form',
    components: [
      {
        type: 'textfield',
        key: 'assetId',
        label: 'Asset ID / Tag Number',
        placeholder: 'Enter asset tag or ID',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'assetName',
        label: 'Asset Name',
        placeholder: 'Asset description',
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'transferType',
        label: 'Transfer Type',
        data: {
          values: [
            { label: 'Department Transfer', value: 'DEPARTMENT' },
            { label: 'Employee Transfer', value: 'EMPLOYEE' },
            { label: 'Location Transfer', value: 'LOCATION' },
            { label: 'Temporary Assignment', value: 'TEMPORARY' },
            { label: 'Return to Pool', value: 'RETURN' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'currentCustodian',
        label: 'Current Custodian',
        placeholder: 'Current person responsible',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'currentLocation',
        label: 'Current Location',
        placeholder: 'Current physical location',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'currentDepartment',
        label: 'Current Department',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'newCustodian',
        label: 'New Custodian',
        placeholder: 'Name or email of new custodian',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'newLocation',
        label: 'New Location',
        placeholder: 'Destination location',
        validate: { required: true },
      },
      {
        type: 'textfield',
        key: 'newDepartment',
        label: 'New Department',
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'transferReason',
        label: 'Reason for Transfer',
        placeholder: 'Explain why this transfer is needed',
        validate: { required: true, minLength: 20 },
      },
      {
        type: 'datetime',
        key: 'requestedTransferDate',
        label: 'Requested Transfer Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        validate: { required: true },
      },
      {
        type: 'select',
        key: 'assetCondition',
        label: 'Current Asset Condition',
        data: {
          values: [
            { label: 'Excellent', value: 'EXCELLENT' },
            { label: 'Good', value: 'GOOD' },
            { label: 'Fair', value: 'FAIR' },
            { label: 'Poor', value: 'POOR' },
            { label: 'Needs Maintenance', value: 'NEEDS_MAINTENANCE' },
          ],
        },
        validate: { required: true },
      },
      {
        type: 'textarea',
        key: 'conditionNotes',
        label: 'Condition Notes',
        placeholder: 'Any specific notes about asset condition',
      },
      {
        type: 'checkbox',
        key: 'returnExpected',
        label: 'Return Expected?',
        description: 'Check if this is a temporary transfer',
      },
      {
        type: 'datetime',
        key: 'expectedReturnDate',
        label: 'Expected Return Date',
        format: 'yyyy-MM-dd',
        enableTime: false,
        conditional: {
          show: true,
          when: 'returnExpected',
          eq: true,
        },
      },
      {
        type: 'file',
        key: 'transferDocuments',
        label: 'Supporting Documents',
        storage: 'base64',
        multiple: true,
      },
      {
        type: 'button',
        action: 'submit',
        label: 'Submit Transfer Request',
        theme: 'primary',
      },
    ],
  },
};

export type FormTemplateKey = keyof typeof formTemplates;

export function getFormTemplate(key: FormTemplateKey) {
  return formTemplates[key];
}

export function getAvailableTemplates() {
  return Object.keys(formTemplates).map((key) => ({
    key,
    title: formTemplates[key as FormTemplateKey].title,
  }));
}

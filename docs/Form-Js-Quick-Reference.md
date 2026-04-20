# Form.js Quick Reference

## Component Types Cheat Sheet

### Text Input Components

```json
// Single-line text
{
  "type": "textfield",
  "key": "username",
  "label": "Username",
  "placeholder": "Enter username",
  "validate": {
    "required": true,
    "minLength": 3,
    "maxLength": 50,
    "pattern": "^[A-Za-z0-9_]+$"
  }
}

// Multi-line text
{
  "type": "textarea",
  "key": "description",
  "label": "Description",
  "rows": 5,
  "validate": {
    "required": true,
    "maxLength": 1000
  }
}

// Email
{
  "type": "textfield",
  "key": "email",
  "label": "Email",
  "validate": {
    "required": true,
    "validationType": "email"
  }
}
```

### Number Input

```json
{
  "type": "number",
  "key": "amount",
  "label": "Amount",
  "decimalDigits": 2,
  "suffix": "USD",
  "validate": {
    "required": true,
    "min": 0.01,
    "max": 1000000
  },
  "serializeToString": false
}
```

### Date/Time Input

```json
{
  "type": "datetime",
  "key": "startDate",
  "label": "Start Date",
  "dateLabel": "Date",
  "timeSerializingFormat": "utc_offset",
  "validate": {
    "required": true
  }
}
```

### Selection Components

```json
// Dropdown
{
  "type": "select",
  "key": "department",
  "label": "Department",
  "validate": {
    "required": true
  },
  "values": [
    { "label": "Finance", "value": "finance" },
    { "label": "IT", "value": "it" },
    { "label": "HR", "value": "hr" }
  ]
}

// Radio buttons
{
  "type": "radio",
  "key": "approved",
  "label": "Decision",
  "validate": {
    "required": true
  },
  "values": [
    { "label": "Approve", "value": true },
    { "label": "Reject", "value": false }
  ]
}

// Checkbox
{
  "type": "checkbox",
  "key": "agreed",
  "label": "I agree to terms and conditions",
  "validate": {
    "required": true
  }
}

// Multi-select checklist
{
  "type": "checklist",
  "key": "skills",
  "label": "Skills",
  "values": [
    { "label": "JavaScript", "value": "js" },
    { "label": "Python", "value": "py" },
    { "label": "Java", "value": "java" }
  ]
}
```

### Display Components

```json
// Static text
{
  "type": "text",
  "text": "## Section Header\n\nProvide detailed information below.",
  "id": "Field_header"
}

// Image
{
  "type": "image",
  "alt": "Company Logo",
  "source": "https://example.com/logo.png",
  "id": "Field_logo"
}
```

### Button

```json
{
  "type": "button",
  "label": "Submit Request",
  "action": "submit",
  "id": "Field_submitBtn"
}
```

## Validation Rules

### Common Validations

```json
{
  "validate": {
    // Required field
    "required": true,

    // String length
    "minLength": 10,
    "maxLength": 500,

    // Number range
    "min": 0,
    "max": 1000,

    // Pattern (regex)
    "pattern": "^[A-Z]{2}-[0-9]{4}$",

    // Email validation
    "validationType": "email"
  }
}
```

### Custom Error Messages

Use `description` field to provide guidance:

```json
{
  "key": "costCenter",
  "label": "Cost Center",
  "description": "Format: CC-XXXX (e.g., CC-1234)",
  "validate": {
    "required": true,
    "pattern": "^CC-[0-9]{4}$"
  }
}
```

## Conditional Logic

### Hide/Show Fields

```json
// Hide field when condition is true
{
  "key": "rejectionReason",
  "type": "textarea",
  "conditional": {
    "hide": "=approved = true"
  }
}

// Show field only when condition is met
{
  "key": "approvedAmount",
  "type": "number",
  "conditional": {
    "hide": "=approved = false"
  }
}
```

### Expression Syntax

```javascript
// Equality
"=fieldName = 'value'"

// Inequality
"=fieldName != 'value'"

// Greater than
"=amount > 1000"

// Less than
"=amount < 100"

// And condition
"=approved = true and amount > 1000"

// Or condition
"=type = 'A' or type = 'B'"

// Not condition
"=not(approved = true)"
```

## Default Values

### Static Defaults

```json
{
  "key": "priority",
  "type": "select",
  "defaultValue": "MEDIUM"
}
```

### Dynamic Defaults

```json
// Current user ID
{
  "key": "userId",
  "defaultValue": "${currentUser.id}",
  "readonly": true
}

// Current user email
{
  "key": "email",
  "defaultValue": "${currentUser.email}",
  "readonly": true
}

// Calculated value
{
  "key": "totalAmount",
  "type": "number",
  "defaultValue": "${quantity * unitPrice}",
  "readonly": true
}
```

## Layout

### Grid Layout

```json
{
  "key": "firstName",
  "type": "textfield",
  "layout": {
    "row": "Row_1",
    "columns": 8  // 8 out of 16 columns
  }
}
```

### Sections

Group related fields using text headers:

```json
{
  "type": "text",
  "text": "### Personal Information",
  "id": "Field_personalInfoHeader"
}
```

## Read-only Fields

```json
{
  "key": "requestNumber",
  "type": "textfield",
  "label": "Request Number",
  "readonly": true,
  "defaultValue": "${requestId}"
}
```

## Number Formatting

```json
// Currency
{
  "type": "number",
  "key": "price",
  "decimalDigits": 2,
  "serializeToString": false
}

// Percentage
{
  "type": "number",
  "key": "discount",
  "suffix": "%",
  "decimalDigits": 2
}

// Units
{
  "type": "number",
  "key": "weight",
  "suffix": "kg",
  "decimalDigits": 3
}
```

## Common Patterns

### Employee Selector

```json
{
  "key": "employeeId",
  "type": "textfield",
  "label": "Employee",
  "readonly": true,
  "defaultValue": "${currentUser.id}",
  "validate": {
    "required": true
  }
}
```

### Date Range

```json
// Start date
{
  "key": "startDate",
  "type": "datetime",
  "label": "Start Date",
  "validate": {
    "required": true
  }
}

// End date
{
  "key": "endDate",
  "type": "datetime",
  "label": "End Date",
  "validate": {
    "required": true
  }
}
```

### Approval Decision

```json
// Decision
{
  "key": "approved",
  "type": "radio",
  "label": "Decision",
  "validate": {
    "required": true
  },
  "values": [
    { "label": "Approve", "value": true },
    { "label": "Reject", "value": false }
  ]
}

// Comments (always required)
{
  "key": "comments",
  "type": "textarea",
  "label": "Comments",
  "validate": {
    "required": true,
    "minLength": 10
  }
}

// Rejection reason (conditional)
{
  "key": "rejectionReason",
  "type": "textarea",
  "label": "Rejection Reason",
  "conditional": {
    "hide": "=approved = true"
  },
  "validate": {
    "required": true,
    "minLength": 20
  }
}
```

### Budget Check

```json
// Budget confirmation
{
  "key": "budgetConfirmed",
  "type": "checkbox",
  "label": "I confirm that budget is available",
  "validate": {
    "required": true
  }
}

// Cost center
{
  "key": "costCenter",
  "type": "textfield",
  "label": "Cost Center",
  "description": "Format: CC-XXXX",
  "validate": {
    "required": true,
    "pattern": "^CC-[0-9]{4}$"
  }
}

// GL account
{
  "key": "glAccount",
  "type": "textfield",
  "label": "GL Account",
  "description": "Format: GL-XXXX-XXXX",
  "validate": {
    "pattern": "^GL-[0-9]{4}-[0-9]{4}$"
  }
}
```

## Usage in React

### Basic Form

```typescript
import FormJsViewer from '@/components/forms/FormJsViewer';

function MyForm() {
  return (
    <FormJsViewer
      schema={formSchema}
      data={{ userId: 'john' }}
      onSubmit={(data) => console.log(data)}
    />
  );
}
```

### Form with Validation

```typescript
function ValidatedForm() {
  const [errors, setErrors] = useState([]);

  const handleSubmit = async (data) => {
    try {
      await submitToBackend(data);
      alert('Success!');
    } catch (error) {
      setErrors([error.message]);
    }
  };

  return (
    <>
      {errors.length > 0 && (
        <div className="errors">
          {errors.map(err => <p key={err}>{err}</p>)}
        </div>
      )}

      <FormJsViewer
        schema={schema}
        onSubmit={handleSubmit}
      />
    </>
  );
}
```

### Read-only Form

```typescript
<FormJsViewer
  schema={schema}
  data={existingData}
  readonly={true}
/>
```

## Tips and Tricks

### 1. Use Descriptive Keys

```json
// Good
{ "key": "requesterEmail" }

// Bad
{ "key": "email" }
```

### 2. Provide Clear Labels and Descriptions

```json
{
  "key": "expectedROI",
  "label": "Expected ROI (%)",
  "description": "Expected return on investment (if applicable)"
}
```

### 3. Use Placeholders

```json
{
  "key": "description",
  "label": "Description",
  "placeholder": "Provide a detailed description of the request"
}
```

### 4. Group Related Fields

```json
// Section header
{
  "type": "text",
  "text": "### Contact Information"
}

// Related fields
{ "key": "name" }
{ "key": "email" }
{ "key": "phone" }
```

### 5. Use Conditional Logic Wisely

Don't overuse conditionals - keep forms simple when possible.

### 6. Test Thoroughly

Always test forms with various inputs to ensure validation works correctly.

## Debugging

### Enable Console Logging

```typescript
form.on('changed', (event) => {
  console.log('Form data:', event.data);
});

form.on('submit', (event) => {
  console.log('Submitting:', event.data);
});
```

### Validate Schema

```typescript
const result = form.validate();
console.log('Validation result:', result);
```

### Check Form State

```typescript
const data = form.getData();
console.log('Current form data:', data);
```

## Resources

- [Form Schema Docs](https://github.com/bpmn-io/form-js/blob/develop/docs/FORM_SCHEMA.md)
- [Component Reference](https://github.com/bpmn-io/form-js)
- [Examples](https://github.com/bpmn-io/form-js-examples)

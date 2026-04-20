# Generic Flowable Delegates Library

Reusable Flowable BPMN service task delegates for the werkflow enterprise platform.

## Overview

This library provides generic, configurable delegates that can be used across all departments without code changes. These delegates enable the 90%+ no-code workflow approach.

## Generic Delegates (Planned - Phase 1, Week 5-6)

### RestServiceDelegate
Invoke HTTP REST APIs from BPMN processes.

**Configuration:**
- `url` - Target endpoint
- `method` - HTTP method (GET, POST, PUT, DELETE)
- `headers` - Request headers
- `body` - Request body (supports process variables)
- `responseVariable` - Variable name to store response

### EmailDelegate
Send emails from workflow processes.

**Configuration:**
- `to` - Recipient email(s)
- `cc` - CC recipients
- `subject` - Email subject
- `template` - Email template name
- `variables` - Template variables

### NotificationDelegate
Multi-channel notifications (email, SMS, push).

**Configuration:**
- `channels` - Notification channels
- `recipients` - Recipient IDs or roles
- `message` - Notification message
- `priority` - Notification priority

### ValidationDelegate
Validate form data and process variables.

**Configuration:**
- `schema` - JSON schema for validation
- `variables` - Variables to validate
- `failOnError` - Whether to throw error on validation failure

### ApprovalDelegate
Standard approval logic with escalation.

**Configuration:**
- `approverRole` - Role of approver
- `escalationTime` - Time before escalation
- `escalationRole` - Escalation target role

### FormRequestDelegate
Generic cross-department form-based request handler.

**Configuration:**
- `targetDepartment` - Department to handle request
- `formType` - Type of request form
- `targetServiceUrl` - Department service endpoint
- `autoAssignToRole` - Auto-assign to role

## Technology Stack

- Java 17
- Flowable 7.0.x
- Spring Boot 3.3.x
- RestTemplate/WebClient for HTTP calls
- Spring Mail for emails

## Usage

Delegates are referenced in BPMN XML using Spring bean names:

```xml
<serviceTask id="callExternalAPI"
             flowable:delegateExpression="${restServiceDelegate}">
  <extensionElements>
    <flowable:field name="url">
      <flowable:string>http://api.example.com/endpoint</flowable:string>
    </flowable:field>
    <flowable:field name="method">
      <flowable:string>POST</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

## Status

**TODO**: To be implemented in Phase 1, Week 5-6

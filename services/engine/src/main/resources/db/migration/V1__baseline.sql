-- ============================================================
-- WERKFLOW ENGINE — OSS BASELINE SCHEMA
-- ============================================================
-- Single consolidated migration replacing all prior incremental
-- migrations. Covers: RBAC, service registry, form schemas,
-- process drafts, action blocks, and OSS sample form data.
-- Enterprise features (DOA thresholds, custody, asset-request
-- forms) are handled in werkflow-enterprise migrations.
-- ============================================================

-- ============================================================
-- SHARED UTILITY FUNCTION
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- RBAC & AUTHORIZATION TABLES
-- ============================================================

CREATE TABLE workflow_role_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_key VARCHAR(255) NOT NULL,
    task_key VARCHAR(255) NOT NULL,
    task_name VARCHAR(255),
    required_roles TEXT[] NOT NULL,
    required_groups TEXT[],
    custom_logic VARCHAR(255),
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT unique_workflow_task UNIQUE (workflow_key, task_key)
);

CREATE INDEX idx_workflow_role_mappings_workflow ON workflow_role_mappings(workflow_key);
CREATE INDEX idx_workflow_role_mappings_task ON workflow_role_mappings(task_key);

COMMENT ON TABLE workflow_role_mappings IS 'Maps workflow tasks to required Keycloak roles and groups';

CREATE TABLE doa_overrides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255),
    override_doa_level INT NOT NULL CHECK (override_doa_level BETWEEN 1 AND 4),
    original_doa_level INT,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    reason TEXT NOT NULL,
    approved_by VARCHAR(255),
    approved_by_email VARCHAR(255),
    approved_at TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_by VARCHAR(255),
    revoked_at TIMESTAMP,
    revoke_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT valid_date_range CHECK (valid_until > valid_from),
    CONSTRAINT valid_doa_levels CHECK (override_doa_level > COALESCE(original_doa_level, 0))
);

CREATE INDEX idx_doa_overrides_user_id ON doa_overrides(user_id);
CREATE INDEX idx_doa_overrides_valid_period ON doa_overrides(valid_from, valid_until);
CREATE INDEX idx_doa_overrides_active ON doa_overrides(user_id, revoked) WHERE NOT revoked;

COMMENT ON TABLE doa_overrides IS 'Temporary delegation of authority overrides';

CREATE TABLE authorization_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255),
    username VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    workflow_key VARCHAR(255),
    task_key VARCHAR(255),
    roles TEXT[],
    groups TEXT[],
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('ALLOWED', 'DENIED')),
    reason TEXT,
    doa_level INT,
    required_doa_level INT,
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id ON authorization_audit_log(user_id);
CREATE INDEX idx_audit_created_at ON authorization_audit_log(created_at DESC);
CREATE INDEX idx_audit_decision ON authorization_audit_log(decision);
CREATE INDEX idx_audit_workflow ON authorization_audit_log(workflow_key, task_key);
CREATE INDEX idx_audit_resource ON authorization_audit_log(resource_type, resource_id);

COMMENT ON TABLE authorization_audit_log IS 'Audit log for all authorization decisions';

CREATE TABLE task_assignment_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(255) NOT NULL UNIQUE,
    workflow_key VARCHAR(255) NOT NULL,
    task_key VARCHAR(255) NOT NULL,
    candidate_users TEXT[],
    candidate_groups TEXT[],
    assignee VARCHAR(255),
    assignment_reason TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_task_assignment_task_id ON task_assignment_cache(task_id);
CREATE INDEX idx_task_assignment_expires ON task_assignment_cache(expires_at);

COMMENT ON TABLE task_assignment_cache IS 'Cache for workflow task assignments to reduce Keycloak API calls';

CREATE TABLE role_hierarchy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_role VARCHAR(255) NOT NULL,
    child_role VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT unique_role_hierarchy UNIQUE (parent_role, child_role)
);

CREATE INDEX idx_role_hierarchy_parent ON role_hierarchy(parent_role);
CREATE INDEX idx_role_hierarchy_child ON role_hierarchy(child_role);

COMMENT ON TABLE role_hierarchy IS 'Role hierarchy for permission inheritance';

-- Triggers
CREATE TRIGGER update_workflow_role_mappings_updated_at
    BEFORE UPDATE ON workflow_role_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_doa_overrides_updated_at
    BEFORE UPDATE ON doa_overrides
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_task_assignment_cache_updated_at
    BEFORE UPDATE ON task_assignment_cache
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Utility functions
CREATE OR REPLACE FUNCTION audit_authorization(
    p_user_id VARCHAR(255),
    p_action VARCHAR(100),
    p_decision VARCHAR(20),
    p_reason TEXT DEFAULT NULL,
    p_resource_type VARCHAR(100) DEFAULT NULL,
    p_resource_id VARCHAR(255) DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    INSERT INTO authorization_audit_log (
        user_id, action, decision, reason, resource_type, resource_id
    ) VALUES (
        p_user_id, p_action, p_decision, p_reason, p_resource_type, p_resource_id
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_expired_task_cache()
RETURNS VOID AS $$
BEGIN
    DELETE FROM task_assignment_cache WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- SERVICE REGISTRY
-- ============================================================

CREATE TABLE service_registry (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    service_type VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    is_active BOOLEAN NOT NULL DEFAULT true,
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE service_endpoints (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    description TEXT,
    requires_auth BOOLEAN NOT NULL DEFAULT true,
    is_public BOOLEAN NOT NULL DEFAULT false,
    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    retry_count INTEGER NOT NULL DEFAULT 3,
    circuit_breaker_enabled BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_endpoints_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

CREATE TABLE service_environment_urls (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT true,
    health_check_url VARCHAR(500),
    health_check_interval_seconds INTEGER NOT NULL DEFAULT 60,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_env_urls_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT uq_service_environment UNIQUE (service_id, environment)
);

CREATE TABLE service_health_checks (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    status_code INTEGER,
    error_message TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    CONSTRAINT fk_service_health_checks_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

CREATE TABLE service_tags (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    tag_value VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_tags_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

CREATE INDEX idx_service_registry_name ON service_registry(service_name);
CREATE INDEX idx_service_registry_type ON service_registry(service_type);
CREATE INDEX idx_service_registry_active ON service_registry(is_active);
CREATE INDEX idx_service_registry_health ON service_registry(health_status);
CREATE INDEX idx_service_endpoints_service_id ON service_endpoints(service_id);
CREATE INDEX idx_service_env_urls_service_id ON service_environment_urls(service_id);
CREATE INDEX idx_service_env_urls_environment ON service_environment_urls(environment);
CREATE INDEX idx_service_health_checks_service_id ON service_health_checks(service_id);
CREATE INDEX idx_service_health_checks_checked_at ON service_health_checks(checked_at);
CREATE INDEX idx_service_tags_service_id ON service_tags(service_id);

CREATE TRIGGER tr_service_registry_updated_at
    BEFORE UPDATE ON service_registry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER tr_service_endpoints_updated_at
    BEFORE UPDATE ON service_endpoints
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER tr_service_env_urls_updated_at
    BEFORE UPDATE ON service_environment_urls
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- OSS service registry seed (admin-service)
INSERT INTO service_registry (service_name, display_name, description, service_type, version, is_active, health_status, metadata, created_by)
VALUES ('admin-service', 'Administration Service', 'Provides system configuration, user management, and administrative functions',
        'TECHNICAL', '1.0.0', true, 'UNKNOWN',
        '{"department": "IT", "owner": "platform-team@werkflow.com", "cost_center": "CC-4001"}'::jsonb, 'system')
ON CONFLICT (service_name) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV',     'http://localhost:8083',           true, 100, true, 'http://localhost:8083/actuator/health',           60 FROM service_registry WHERE service_name = 'admin-service' ON CONFLICT (service_id, environment) DO NOTHING;
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://admin-service:8083',       true, 100, true, 'http://admin-service:8083/actuator/health',       60 FROM service_registry WHERE service_name = 'admin-service' ON CONFLICT (service_id, environment) DO NOTHING;
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD',    'https://admin-api.werkflow.com',  true, 100, true, 'https://admin-api.werkflow.com/actuator/health',  30 FROM service_registry WHERE service_name = 'admin-service' ON CONFLICT (service_id, environment) DO NOTHING;

INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'department', 'IT'           FROM service_registry WHERE service_name = 'admin-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'team',       'platform-team' FROM service_registry WHERE service_name = 'admin-service';
INSERT INTO service_tags (service_id, tag_name, tag_value) SELECT id, 'priority',   'high'          FROM service_registry WHERE service_name = 'admin-service';

-- ============================================================
-- FORM SCHEMAS
-- ============================================================

CREATE TABLE IF NOT EXISTS form_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_key VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    version INT NOT NULL DEFAULT 1,
    schema_json JSONB NOT NULL,
    description TEXT,
    form_type VARCHAR(50) DEFAULT 'TASK_FORM',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uk_form_key_version UNIQUE(form_key, version)
);

CREATE INDEX IF NOT EXISTS idx_form_schemas_form_key ON form_schemas(form_key);
CREATE INDEX IF NOT EXISTS idx_form_schemas_form_type ON form_schemas(form_type);
CREATE INDEX IF NOT EXISTS idx_form_schemas_is_active ON form_schemas(is_active);
CREATE INDEX IF NOT EXISTS idx_form_schemas_latest_active ON form_schemas(form_key, version DESC) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_form_schemas_schema_json ON form_schemas USING GIN (schema_json);

CREATE TABLE IF NOT EXISTS form_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_schema_id UUID NOT NULL REFERENCES form_schemas(id),
    task_id VARCHAR(255),
    process_instance_id VARCHAR(255),
    form_data JSONB NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    submitted_by VARCHAR(255) NOT NULL,
    validation_errors JSONB,
    submission_status VARCHAR(50) DEFAULT 'SUBMITTED',
    CONSTRAINT fk_form_schema FOREIGN KEY (form_schema_id) REFERENCES form_schemas(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_form_submissions_task_id ON form_submissions(task_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_process_instance_id ON form_submissions(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_submitted_at ON form_submissions(submitted_at DESC);

-- ============================================================
-- PROCESS DRAFTS
-- ============================================================

CREATE TABLE process_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_key VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    bpmn_xml TEXT NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- ============================================================
-- ACTION BLOCKS (audit log + notification templates)
-- ============================================================

CREATE TABLE process_audit_log (
    id                     BIGSERIAL PRIMARY KEY,
    process_instance_id    VARCHAR(255) NOT NULL,
    execution_id           VARCHAR(255) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    action_type            VARCHAR(50)  NOT NULL,
    task_id                VARCHAR(255),
    task_name              VARCHAR(255),
    initiated_by           VARCHAR(255),
    timestamp              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    request_url            TEXT,
    request_method         VARCHAR(10),
    request_hash           VARCHAR(64),
    response_status        INTEGER,
    response_body          JSONB,
    response_truncated     BOOLEAN      NOT NULL DEFAULT FALSE,
    masked_fields          TEXT[],
    duration_ms            BIGINT,
    error_message          TEXT
);

CREATE INDEX idx_audit_process_instance ON process_audit_log(process_instance_id);
CREATE INDEX idx_audit_timestamp        ON process_audit_log(timestamp);

CREATE TABLE notification_templates (
    id           BIGSERIAL    PRIMARY KEY,
    template_key VARCHAR(100) NOT NULL UNIQUE,
    channel      VARCHAR(50)  NOT NULL,
    subject      VARCHAR(500),
    body         TEXT         NOT NULL,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- OSS notification templates (generic, not tied to enterprise workflows)
INSERT INTO notification_templates (template_key, channel, subject, body) VALUES
('task-assigned', 'email', 'A New Task Requires Your Attention',
 'Dear ${assigneeName},

A new task has been assigned to you.

Task: ${taskName}
Process: ${processName}

Please log in to the system to review and act on this task.

Best regards,
Workflow System'),

('leave-approved', 'email', 'Your Leave Request Has Been Approved',
 'Dear ${requesterName},

Your leave request from ${startDate} to ${endDate} has been approved.

Enjoy your time off.

Best regards,
HR Team'),

('leave-rejected', 'email', 'Your Leave Request Has Been Declined',
 'Dear ${requesterName},

Your leave request from ${startDate} to ${endDate} has been declined.

Reason: ${reason}

Please contact HR if you need further clarification.

Best regards,
HR Team');

-- ============================================================
-- SAMPLE FORM DATA (OSS example workflows)
-- ============================================================

-- General Approval workflow forms
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-form', 1, 'General Approval Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "title", "key": "title", "label": "Request Title", "validate": {"required": true, "maxLength": 100}},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "validate": {"required": true, "minLength": 10}},
            {"type": "number", "id": "amount", "key": "amount", "label": "Amount ($)", "validate": {"required": true, "min": 0}},
            {"type": "select", "id": "category", "key": "category", "label": "Category", "validate": {"required": true},
             "values": [{"label": "Budget", "value": "BUDGET"}, {"label": "Operational", "value": "OPERATIONAL"}, {"label": "Capital", "value": "CAPITAL"}, {"label": "Other", "value": "OTHER"}]},
            {"type": "textfield", "id": "attachments", "key": "attachments", "label": "Attachments (optional)", "validate": {"required": false}, "description": "Comma-separated file references or URLs"}
        ]
    }'::jsonb,
    'General Approval Form — process start form for the general approval sample workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'general-approval-decision', 1, 'General Approval Decision',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "reviewHeader", "text": "### Review Request"},
            {"type": "textfield", "id": "title", "key": "title", "label": "Request Title", "disabled": true},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "disabled": true},
            {"type": "number", "id": "amount", "key": "amount", "label": "Amount ($)", "disabled": true},
            {"type": "textfield", "id": "category", "key": "category", "label": "Category", "disabled": true},
            {"type": "select", "id": "decisionField", "key": "decision", "label": "Decision", "validate": {"required": true},
             "values": [{"label": "Approve", "value": "approve"}, {"label": "Reject", "value": "reject"}]},
            {"type": "textarea", "id": "reviewComments", "key": "reviewComments", "label": "Comments", "validate": {"required": false}}
        ]
    }'::jsonb,
    'General Approval Decision Form — used by Manager and Director approval tasks',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- Document Review workflow forms
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'document-review-form', 1, 'Document Review Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "title", "key": "title", "label": "Document Title", "validate": {"required": true, "maxLength": 200}},
            {"type": "select", "id": "documentType", "key": "documentType", "label": "Document Type", "validate": {"required": true},
             "values": [{"label": "Policy", "value": "POLICY"}, {"label": "Contract", "value": "CONTRACT"}, {"label": "Report", "value": "REPORT"}, {"label": "Specification", "value": "SPECIFICATION"}, {"label": "Other", "value": "OTHER"}]},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "validate": {"required": true, "minLength": 10}},
            {"type": "textfield", "id": "attachment", "key": "attachment", "label": "Attachment", "validate": {"required": false}},
            {"type": "select", "id": "reviewerGroup", "key": "reviewerGroup", "label": "Reviewer Group", "validate": {"required": true},
             "values": [{"label": "Legal", "value": "LEGAL"}, {"label": "Finance", "value": "FINANCE"}, {"label": "Engineering", "value": "ENGINEERING"}, {"label": "HR", "value": "HR"}, {"label": "Operations", "value": "OPERATIONS"}]}
        ]
    }'::jsonb,
    'Document Review Form — process start form for the document review sample workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'document-review-decision', 1, 'Document Review Decision',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "text", "id": "reviewHeader", "text": "### Review Document"},
            {"type": "textfield", "id": "title", "key": "title", "label": "Document Title", "disabled": true},
            {"type": "textfield", "id": "documentType", "key": "documentType", "label": "Document Type", "disabled": true},
            {"type": "textarea", "id": "description", "key": "description", "label": "Description", "disabled": true},
            {"type": "select", "id": "decisionField", "key": "decision", "label": "Review Decision", "validate": {"required": true},
             "values": [{"label": "Approve", "value": "approve"}, {"label": "Reject", "value": "reject"}, {"label": "Request Revision", "value": "revise"}]},
            {"type": "textarea", "id": "reviewFeedback", "key": "reviewFeedback", "label": "Feedback", "validate": {"required": false}}
        ]
    }'::jsonb,
    'Document Review Decision Form — used by reviewer to approve, reject, or request revision',
    'APPROVAL', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

-- Onboarding Checklist workflow form
INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'onboarding-checklist-form', 1, 'Onboarding Checklist Form',
    '{
        "type": "default",
        "schemaVersion": 9,
        "components": [
            {"type": "textfield", "id": "employeeName", "key": "employeeName", "label": "Employee Full Name", "validate": {"required": true}},
            {"type": "textfield", "id": "employeeEmail", "key": "employeeEmail", "label": "Employee Email", "validate": {"required": true, "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"}},
            {"type": "select", "id": "department", "key": "department", "label": "Department", "validate": {"required": true},
             "values": [{"label": "Engineering", "value": "ENGINEERING"}, {"label": "Finance", "value": "FINANCE"}, {"label": "HR", "value": "HR"}, {"label": "Operations", "value": "OPERATIONS"}, {"label": "IT", "value": "IT"}]},
            {"type": "datetime", "id": "startDate", "key": "startDate", "label": "Start Date", "subtype": "date", "validate": {"required": true}},
            {"type": "textarea", "id": "equipmentNeeded", "key": "equipmentNeeded", "label": "Equipment Needed", "validate": {"required": false}},
            {"type": "checkbox", "id": "buddyRequired", "key": "buddyRequired", "label": "Assign Manager Buddy", "defaultValue": true}
        ]
    }'::jsonb,
    'Onboarding Checklist Form — process start form for the employee onboarding sample workflow',
    'PROCESS_START', true, 'system', 'system'
) ON CONFLICT (form_key, version) DO NOTHING;

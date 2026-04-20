-- ================================================================
-- RBAC-Related Tables for Werkflow Engine
-- ================================================================
-- These tables complement Keycloak's user/role/group management
-- with workflow-specific authorization data
-- ================================================================

-- Workflow Role Mappings
-- Maps workflow tasks to required Keycloak roles/groups
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
COMMENT ON COLUMN workflow_role_mappings.custom_logic IS 'Custom authorization logic: manager_id_match, doa_level_check, hub_match, etc.';

-- DOA Override Table
-- Temporary delegation of authority for users
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
COMMENT ON COLUMN doa_overrides.user_id IS 'Keycloak user ID';

-- Authorization Audit Log
-- Track all authorization decisions for compliance and debugging
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

-- Task Assignment Cache
-- Cache task assignments to avoid repeated Keycloak API calls
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

-- Role Hierarchy
-- Define role hierarchy for inheritance (optional - can use Keycloak composite roles instead)
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

COMMENT ON TABLE role_hierarchy IS 'Role hierarchy for permission inheritance (alternative to Keycloak composite roles)';

-- ================================================================
-- Seed Data
-- ================================================================

-- Insert workflow role mappings for Asset Request workflow
INSERT INTO workflow_role_mappings (workflow_key, task_key, task_name, required_roles, required_groups, custom_logic, description) VALUES
('asset_request', 'submit_request', 'Submit Asset Request',
    ARRAY['employee'], NULL, NULL,
    'Any employee can submit asset requests'),

('asset_request', 'line_manager_approval', 'Line Manager Approval',
    ARRAY['asset_request_approver'], NULL, 'manager_id_match',
    'Requires approval from submitter''s direct manager'),

('asset_request', 'it_approval', 'IT Department Approval',
    ARRAY['asset_request_approver'], ARRAY['/IT Department/Managers', '/IT Department/POC'], NULL,
    'Requires approval from IT department manager or POC'),

('asset_request', 'procurement_approval', 'Procurement Approval',
    ARRAY['procurement_approver'], ARRAY['/Procurement Department/Managers', '/Procurement Department/POC'], NULL,
    'Requires approval from Procurement department'),

('asset_request', 'finance_doa_approval', 'Finance DOA Approval',
    ARRAY['doa_approver_level1', 'doa_approver_level2', 'doa_approver_level3', 'doa_approver_level4'],
    ARRAY['/Finance Department/Approvers'], 'doa_level_check',
    'Requires approval from Finance with sufficient DOA level based on amount'),

('asset_request', 'hub_assignment', 'Warehouse Hub Assignment',
    ARRAY['hub_manager', 'central_hub_manager'], NULL, 'hub_match',
    'Assign to appropriate warehouse hub manager'),

('asset_request', 'asset_delivery', 'Asset Delivery',
    ARRAY['hub_manager', 'central_hub_manager'], NULL, NULL,
    'Mark asset as delivered to employee');

-- Insert role hierarchy examples (if using database for hierarchy instead of Keycloak composites)
INSERT INTO role_hierarchy (parent_role, child_role, description) VALUES
('super_admin', 'admin', 'Super admin inherits all admin permissions'),
('admin', 'workflow_designer', 'Admin can design workflows'),
('admin', 'workflow_admin', 'Admin can administer workflows'),
('hr_head', 'asset_request_approver', 'HR head can approve asset requests'),
('hr_head', 'doa_approver_level1', 'HR head has level 1 DOA'),
('it_head', 'asset_request_approver', 'IT head can approve asset requests'),
('finance_head', 'doa_approver_level4', 'Finance head has level 4 DOA'),
('finance_head', 'doa_approver_level3', 'Finance head has level 3 DOA'),
('central_hub_manager', 'hub_manager', 'Central hub manager has hub manager permissions');

-- ================================================================
-- Functions
-- ================================================================

-- Function to check if DOA override is active for user
CREATE OR REPLACE FUNCTION get_effective_doa_level(p_user_id VARCHAR(255), p_check_time TIMESTAMP DEFAULT NOW())
RETURNS INT AS $$
DECLARE
    v_override_level INT;
BEGIN
    -- Check for active DOA override
    SELECT override_doa_level INTO v_override_level
    FROM doa_overrides
    WHERE user_id = p_user_id
      AND NOT revoked
      AND p_check_time BETWEEN valid_from AND valid_until
    ORDER BY override_doa_level DESC
    LIMIT 1;

    -- Return override level if found, otherwise NULL (use Keycloak attribute)
    RETURN v_override_level;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_effective_doa_level IS 'Get effective DOA level for user, considering active overrides';

-- Function to audit authorization decision
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

COMMENT ON FUNCTION audit_authorization IS 'Insert authorization audit log entry';

-- Function to clean up expired task assignment cache
CREATE OR REPLACE FUNCTION cleanup_expired_task_cache()
RETURNS VOID AS $$
BEGIN
    DELETE FROM task_assignment_cache
    WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_task_cache IS 'Remove expired task assignment cache entries';

-- ================================================================
-- Triggers
-- ================================================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_workflow_role_mappings_updated_at
    BEFORE UPDATE ON workflow_role_mappings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_doa_overrides_updated_at
    BEFORE UPDATE ON doa_overrides
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_task_assignment_cache_updated_at
    BEFORE UPDATE ON task_assignment_cache
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

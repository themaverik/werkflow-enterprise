-- ================================================================
-- WERKFLOW DATABASE INITIALIZATION SCRIPT
-- ================================================================
-- Creates schemas for all microservices in the werkflow platform
-- Single PostgreSQL instance with schema separation
-- ================================================================

-- Create schemas for each service
CREATE SCHEMA IF NOT EXISTS flowable;
CREATE SCHEMA IF NOT EXISTS hr_service;
CREATE SCHEMA IF NOT EXISTS admin_service;
CREATE SCHEMA IF NOT EXISTS finance_service;
CREATE SCHEMA IF NOT EXISTS procurement_service;
CREATE SCHEMA IF NOT EXISTS inventory_service;
CREATE SCHEMA IF NOT EXISTS legal_service;

-- Grant permissions to werkflow_admin user
GRANT ALL PRIVILEGES ON SCHEMA flowable TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA hr_service TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA admin_service TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA finance_service TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA procurement_service TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA inventory_service TO werkflow_admin;
GRANT ALL PRIVILEGES ON SCHEMA legal_service TO werkflow_admin;

-- Grant usage and create privileges
GRANT USAGE, CREATE ON SCHEMA flowable TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA hr_service TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA admin_service TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA finance_service TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA procurement_service TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA inventory_service TO werkflow_admin;
GRANT USAGE, CREATE ON SCHEMA legal_service TO werkflow_admin;

-- Set default search path for services
ALTER DATABASE werkflow SET search_path TO public, flowable, hr_service, admin_service, finance_service, procurement_service, inventory_service;

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For text search

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Werkflow Database Initialized';
    RAISE NOTICE 'Database: werkflow';
    RAISE NOTICE 'User: werkflow_admin';
    RAISE NOTICE 'Schemas created:';
    RAISE NOTICE '  - flowable (Flowable BPM Engine)';
    RAISE NOTICE '  - hr_service (HR Domain)';
    RAISE NOTICE '  - admin_service (User/Org/Dept Management)';
    RAISE NOTICE '  - finance_service (Finance Domain - Phase 3)';
    RAISE NOTICE '  - procurement_service (Procurement Domain - Phase 3)';
    RAISE NOTICE '  - inventory_service (Inventory Domain - Phase 3)';
    RAISE NOTICE '  - legal_service (Legal Domain - Future)';
    RAISE NOTICE '========================================';
END $$;

ALTER TABLE doa_threshold ADD COLUMN label       VARCHAR(100);
ALTER TABLE doa_threshold ADD COLUMN description VARCHAR(500);

-- Seed human-readable labels for default tenant
UPDATE doa_threshold SET label = 'No Approval Authority' WHERE doa_level = 'DOA_L0';
UPDATE doa_threshold SET label = 'Junior Approver'       WHERE doa_level = 'DOA_L1';
UPDATE doa_threshold SET label = 'Manager'               WHERE doa_level = 'DOA_L2';
UPDATE doa_threshold SET label = 'Senior Manager'        WHERE doa_level = 'DOA_L3';
UPDATE doa_threshold SET label = 'Executive'             WHERE doa_level = 'DOA_L4';

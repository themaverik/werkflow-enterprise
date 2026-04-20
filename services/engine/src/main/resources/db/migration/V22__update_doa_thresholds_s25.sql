-- Updates DOA approval thresholds for the default tenant to match S25 sample workflow ranges.
-- L1: up to $10K (managers), L2: up to $50K (senior managers), L3: up to $200K (directors).
-- L4 (unlimited/executive) is unchanged.

UPDATE doa_threshold SET max_amount = 10000.00  WHERE tenant_id = 'default' AND doa_level = 'DOA_L1';
UPDATE doa_threshold SET max_amount = 50000.00  WHERE tenant_id = 'default' AND doa_level = 'DOA_L2';
UPDATE doa_threshold SET max_amount = 200000.00 WHERE tenant_id = 'default' AND doa_level = 'DOA_L3';

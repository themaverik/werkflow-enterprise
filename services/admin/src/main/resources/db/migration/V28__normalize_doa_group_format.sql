-- Migrate DOA:L* → DOA_L* in role_group_mappings (ADR-029: underscore is canonical).
-- Idempotent: WHERE clause limits to rows still using the colon format.
UPDATE role_group_mappings
SET group_name = REPLACE(group_name, 'DOA:L', 'DOA_L')
WHERE group_name LIKE 'DOA:L%';

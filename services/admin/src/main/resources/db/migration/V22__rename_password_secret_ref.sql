-- V22: rename password_secret_ref to encrypted_password on tenant_datasource
-- Column now stores AES-256-GCM ciphertext; SecretsResolver dependency removed.
ALTER TABLE tenant_datasource RENAME COLUMN password_secret_ref TO encrypted_password;

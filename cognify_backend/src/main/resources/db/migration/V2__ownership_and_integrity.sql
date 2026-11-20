ALTER TABLE wallet ADD CONSTRAINT fk_wallet_owner FOREIGN KEY (owner_id) REFERENCES accounts(id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES accounts(id);
ALTER TABLE refreshsession ADD CONSTRAINT fk_refresh_owner FOREIGN KEY (owner_id) REFERENCES accounts(id);
ALTER TABLE wallet ADD CONSTRAINT ck_wallet_nonnegative CHECK (balance >= 0);
ALTER TABLE accounts ADD CONSTRAINT ck_account_role CHECK (role IN ('USER', 'ADMIN'));
CREATE INDEX ix_documents_owner_updated ON documents(owner_id, updated_at);
CREATE INDEX ix_refresh_owner ON refreshsession(owner_id);
CREATE INDEX ix_refresh_expiry ON refreshsession(expires_at);

CREATE TABLE password_reset (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 token_hash VARCHAR(255) NOT NULL UNIQUE, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used BOOLEAN NOT NULL
);
CREATE INDEX ix_password_reset_owner ON password_reset(owner_id);
CREATE INDEX ix_password_reset_expiry ON password_reset(expires_at);

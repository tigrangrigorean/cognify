CREATE TABLE usersession (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked BOOLEAN NOT NULL
);
CREATE INDEX ix_session_owner ON usersession(owner_id);
ALTER TABLE refreshsession ADD COLUMN session_id UUID REFERENCES usersession(id);
CREATE INDEX ix_refresh_session ON refreshsession(session_id);
-- Old tokens have no session binding. Require a new login after upgrading.
UPDATE refreshsession SET revoked = TRUE;

CREATE TABLE cms_block (
 id UUID PRIMARY KEY, block_key VARCHAR(255) NOT NULL UNIQUE,
 title VARCHAR(255) NOT NULL, body TEXT NOT NULL, items_json TEXT NOT NULL,
 position INTEGER NOT NULL, visible BOOLEAN NOT NULL, version BIGINT NOT NULL
);
CREATE INDEX ix_cms_block_position ON cms_block(position, block_key);
CREATE TABLE audit_log (
 id UUID PRIMARY KEY, actor_id UUID NOT NULL REFERENCES accounts(id),
 action VARCHAR(255) NOT NULL, target VARCHAR(255) NOT NULL,
 before_value TEXT, after_value TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_created ON audit_log(created_at);
CREATE INDEX ix_audit_actor ON audit_log(actor_id);

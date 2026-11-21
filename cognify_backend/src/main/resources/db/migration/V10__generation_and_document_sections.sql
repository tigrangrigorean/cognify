ALTER TABLE documents ADD COLUMN generation_mode VARCHAR(255) NOT NULL DEFAULT 'MANUAL';
CREATE TABLE generation_job (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id), request_key VARCHAR(255) NOT NULL,
 fingerprint VARCHAR(255) NOT NULL, topic VARCHAR(255) NOT NULL, kind VARCHAR(255) NOT NULL,
 language VARCHAR(255) NOT NULL, subject VARCHAR(255) NOT NULL, university VARCHAR(255) NOT NULL,
 requirements TEXT NOT NULL, pages INTEGER NOT NULL CHECK(pages BETWEEN 1 AND 40),
 status VARCHAR(255) NOT NULL, phase VARCHAR(255) NOT NULL, plan_json TEXT NOT NULL,
 provider VARCHAR(255) NOT NULL, model VARCHAR(255) NOT NULL,
 quoted_coins BIGINT NOT NULL, unit_cost BIGINT NOT NULL, charged_coins BIGINT NOT NULL DEFAULT 0,
 reservation_id UUID REFERENCES coin_reservation(id), billing_attempt INTEGER NOT NULL DEFAULT 0,
 plan_attempts INTEGER NOT NULL DEFAULT 0, lease_token UUID, lease_until TIMESTAMP WITH TIME ZONE,
 error_code VARCHAR(255), document_id UUID UNIQUE REFERENCES documents(id) ON DELETE SET NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL,
 CONSTRAINT uq_generation_request UNIQUE(owner_id,request_key)
);
CREATE INDEX ix_job_queue ON generation_job(status,lease_until,created_at);
CREATE INDEX ix_job_owner ON generation_job(owner_id,created_at);
CREATE TABLE generation_step (
 id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES generation_job(id) ON DELETE CASCADE,
 position INTEGER NOT NULL, title VARCHAR(255) NOT NULL, content TEXT NOT NULL,
 completed BOOLEAN NOT NULL, attempts INTEGER NOT NULL,
 CONSTRAINT uq_job_step UNIQUE(job_id,position)
);
CREATE TABLE ai_usage (
 id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES generation_job(id), step_index INTEGER NOT NULL,
 provider VARCHAR(255) NOT NULL, model VARCHAR(255) NOT NULL, input_tokens BIGINT NOT NULL,
 output_tokens BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_ai_usage_job ON ai_usage(job_id);
CREATE TABLE document_section (
 id UUID PRIMARY KEY, document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 position INTEGER NOT NULL, title VARCHAR(255) NOT NULL, content TEXT NOT NULL
);
CREATE INDEX ix_section_document ON document_section(document_id,position);
CREATE TABLE document_version (
 id UUID PRIMARY KEY, document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 revision BIGINT NOT NULL, title VARCHAR(255) NOT NULL, content TEXT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_document_revision UNIQUE(document_id,revision)
);

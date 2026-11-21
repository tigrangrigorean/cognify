ALTER TABLE wallet ADD COLUMN reserved BIGINT NOT NULL DEFAULT 0;
ALTER TABLE wallet ADD CONSTRAINT ck_wallet_reserved CHECK (reserved >= 0 AND reserved <= balance);
ALTER TABLE wallet ADD CONSTRAINT ck_wallet_safe_integer CHECK (balance <= 9007199254740991);
CREATE TABLE coin_reservation (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 operation VARCHAR(255) NOT NULL, request_key VARCHAR(255) NOT NULL,
 amount BIGINT NOT NULL CHECK (amount > 0), settled_amount BIGINT NOT NULL DEFAULT 0,
 refunded_amount BIGINT NOT NULL DEFAULT 0, status VARCHAR(255) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_reservation_key UNIQUE(owner_id,request_key),
 CONSTRAINT ck_reservation_amounts CHECK(settled_amount>=0 AND settled_amount<=amount AND refunded_amount>=0 AND refunded_amount<=settled_amount),
 CONSTRAINT ck_reservation_status CHECK(status IN ('HELD','SETTLED','RELEASED'))
);
CREATE INDEX ix_reservation_owner ON coin_reservation(owner_id,created_at);
CREATE TABLE coin_transaction (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 request_key VARCHAR(255) NOT NULL, fingerprint VARCHAR(255) NOT NULL,
 kind VARCHAR(255) NOT NULL, amount BIGINT NOT NULL, reserved_delta BIGINT NOT NULL,
 balance_after BIGINT NOT NULL CHECK(balance_after>=0), reserved_after BIGINT NOT NULL,
 reference_id UUID, reason VARCHAR(1000) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_coin_transaction_key UNIQUE(owner_id,request_key),
 CONSTRAINT ck_transaction_reserved CHECK(reserved_after>=0 AND reserved_after<=balance_after)
);
CREATE INDEX ix_coin_transaction_owner ON coin_transaction(owner_id,created_at,id);
CREATE TABLE coin_package (
 id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, coins BIGINT NOT NULL CHECK(coins>0),
 amount_minor BIGINT NOT NULL CHECK(amount_minor>0), currency VARCHAR(255) NOT NULL,
 active BOOLEAN NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE pricing_rule (
 id UUID PRIMARY KEY, operation VARCHAR(255) NOT NULL UNIQUE, unit VARCHAR(255) NOT NULL,
 unit_cost BIGINT NOT NULL CHECK(unit_cost>0), active BOOLEAN NOT NULL, version BIGINT NOT NULL,
 CONSTRAINT ck_pricing_unit CHECK(unit IN ('OPERATION','WORD','PAGE','CHAPTER','ANALYSIS','EXPORT'))
);
CREATE TABLE payment_record (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 package_id UUID NOT NULL REFERENCES coin_package(id), request_key VARCHAR(255) NOT NULL,
 coins BIGINT NOT NULL CHECK(coins>0), amount_minor BIGINT NOT NULL CHECK(amount_minor>0),
 currency VARCHAR(255) NOT NULL, provider VARCHAR(255) NOT NULL, provider_reference VARCHAR(255),
 status VARCHAR(255) NOT NULL, test BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT uq_payment_key UNIQUE(owner_id,request_key),
 CONSTRAINT uq_payment_provider_reference UNIQUE(provider,provider_reference)
);
CREATE INDEX ix_payment_owner ON payment_record(owner_id,created_at);
CREATE TABLE usage_record (
 id UUID PRIMARY KEY, reservation_id UUID NOT NULL UNIQUE REFERENCES coin_reservation(id),
 owner_id UUID NOT NULL REFERENCES accounts(id), operation VARCHAR(255) NOT NULL,
 coins BIGINT NOT NULL CHECK(coins>=0), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_usage_owner ON usage_record(owner_id,created_at);

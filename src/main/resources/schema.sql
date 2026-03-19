CREATE TABLE IF NOT EXISTS currency_rates (
    code VARCHAR(3) NOT NULL,
    rate NUMERIC(19, 8) NOT NULL,
    source_id VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_currency_rates PRIMARY KEY (code, source_id),
    CONSTRAINT chk_currency_rates_rate_non_negative CHECK (rate >= 0)
);

CREATE INDEX IF NOT EXISTS idx_currency_rates_code_updated_at
    ON currency_rates (code, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_currency_rates_source_updated_at
    ON currency_rates (source_id, updated_at DESC);

CREATE DATABASE nomad_rates;
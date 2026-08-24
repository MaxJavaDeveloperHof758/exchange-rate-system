-- Schema for the CurrencyUsage entity
-- (com.exchange.exchangeratesystem.usage.CurrencyUsage). currency_code is
-- the natural-key PK (no surrogate id); query_count defaults to 0 at the
-- DB level (matches the entity's columnDefinition) with a non-negative
-- check (@Check); last_queried_date NOT NULL. query_count is only ever
-- mutated via CurrencyUsageRepository's atomic increment/decrement
-- queries, never read-modify-write from Java.
CREATE TABLE currency_usage (
    currency_code VARCHAR(3) NOT NULL PRIMARY KEY,
    query_count BIGINT NOT NULL DEFAULT 0,
    last_queried_date DATE NOT NULL,
    CONSTRAINT ck_currency_usage_query_count_non_negative CHECK (query_count >= 0)
);

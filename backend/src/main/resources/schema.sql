-- ShedLock's own lock table (net.javacrumbs.shedlock, SchedulingConfig's
-- JdbcTemplateLockProvider bean) - not a JPA entity, so Hibernate's
-- ddl-auto never creates it. Standard ANSI SQL (matches ShedLock's own
-- documented H2/PostgreSQL schema), portable as-is if this project later
-- moves off H2. IF NOT EXISTS keeps this idempotent across restarts against
-- the persistent dev database file.
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ShedLock's own lock table (net.javacrumbs.shedlock, SchedulingConfig's
-- JdbcTemplateLockProvider bean) - not a JPA entity, so Hibernate's
-- ddl-auto never creates it. Ported from the former schema.sql, which this
-- migration replaces. ANSI SQL, matching ShedLock's own documented
-- H2/PostgreSQL schema.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

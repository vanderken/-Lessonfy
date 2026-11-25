-- =====================================================
-- 01-create-lessonfy-app.sql
-- Give Spring Boot its own small connection allowance so pgAdmin tabs
-- cannot exhaust every PostgreSQL slot. Safe to run more than once.
-- =====================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lessonfy_app') THEN
        CREATE ROLE lessonfy_app LOGIN PASSWORD 'postgres' CONNECTION LIMIT 10;
    ELSE
        ALTER ROLE lessonfy_app LOGIN PASSWORD 'postgres' CONNECTION LIMIT 10;
    END IF;
END
$$;

-- pgAdmin normally connects as postgres. Cap it well below max_connections=200.
ALTER ROLE postgres CONNECTION LIMIT 80;

GRANT CONNECT, TEMPORARY ON DATABASE lessonfy TO lessonfy_app;
GRANT USAGE, CREATE ON SCHEMA public TO lessonfy_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO lessonfy_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO lessonfy_app;

-- Objects created by later migrations are automatically usable by the app role.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT ALL PRIVILEGES ON TABLES TO lessonfy_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT ALL PRIVILEGES ON SEQUENCES TO lessonfy_app;

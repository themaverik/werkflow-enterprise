-- V31: Drop dead Service Registry tables.
-- The Service Registry feature has been removed (no runtime consumer since RestServiceDelegate
-- was deleted). All five FK-linked tables are dropped children-first to satisfy constraints.
-- IF EXISTS guards make this safe on fresh deployments where the tables may not exist.

DROP TABLE IF EXISTS service_tags;
DROP TABLE IF EXISTS service_health_checks;
DROP TABLE IF EXISTS service_environment_urls;
DROP TABLE IF EXISTS service_endpoints;
DROP TABLE IF EXISTS service_registry;

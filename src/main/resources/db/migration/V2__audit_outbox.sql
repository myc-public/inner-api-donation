SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ==========================================================
-- audit_outbox table (K4d, DA2 / DA6)
-- Ecrite dans la meme transaction que la modification / suppression d'une donation.
-- L'API ne fait qu'inserer : le CDC (autre composant) publie TOUTE la table vers le topic audit.events,
-- consomme par Elasticsearch (event_id = _id du document, idempotence en cas de rejeu).
-- ==========================================================

CREATE TABLE IF NOT EXISTS audit_outbox (
    event_id BINARY(16) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    event_version VARCHAR(20) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,

    INDEX idx_audit_outbox_occurred_at (occurred_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

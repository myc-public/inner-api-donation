SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ==========================================================
-- outbox_event : l'API ne connait pas Kafka (ADR 05/10)
-- Le routage (topic, cle de message) est decide par le CDC a partir des faits metier deja presents :
-- aggregate_type -> topic, aggregate_id -> cle. Les colonnes topic (valeur fixe) et message_key
-- (copie de aggregate_id) sont supprimees.
-- ==========================================================

ALTER TABLE outbox_event
    DROP COLUMN topic,
    DROP COLUMN message_key;

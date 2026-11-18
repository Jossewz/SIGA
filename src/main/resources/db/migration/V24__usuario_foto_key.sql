-- =========================================================================
-- FLYWAY MIGRATION V24: Foto de perfil en cuentas de usuario
-- =========================================================================

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS foto_key VARCHAR(255);


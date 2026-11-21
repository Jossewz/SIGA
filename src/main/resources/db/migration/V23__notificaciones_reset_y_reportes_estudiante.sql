-- =========================================================================
-- FLYWAY MIGRATION V23: Notificaciones, Password Reset Tokens y Reportes Estudiantiles
-- =========================================================================

-- 1. Ajustar permisos base granulares para ESTUDIANTE y DOCENTE (reportes y certificados)
UPDATE rol_permisos
SET puede_acceder = TRUE,
    puede_editar  = TRUE,
    puede_eliminar = FALSE
WHERE rol = 'ESTUDIANTE' AND modulo = 'REPORTES';

UPDATE rol_permisos
SET puede_editar  = TRUE
WHERE rol = 'DOCENTE' AND modulo = 'REPORTES';

UPDATE rol_permisos
SET puede_editar  = TRUE
WHERE rol = 'ESTUDIANTE' AND modulo = 'CERTIFICADOS';

-- 2. Tabla persistente de notificaciones in-app (campana estilo SEGI)
CREATE TABLE IF NOT EXISTS notificaciones (
    id            UUID PRIMARY KEY,
    usuario_id    UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo          VARCHAR(80) NOT NULL,
    titulo        VARCHAR(200) NOT NULL,
    mensaje       TEXT NOT NULL,
    referencia_id VARCHAR(100),
    icono         VARCHAR(50) DEFAULT 'bell',
    color         VARCHAR(30) DEFAULT 'emerald',
    leida_en      TIMESTAMP WITHOUT TIME ZONE,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario_estado
    ON notificaciones (usuario_id, leida_en, created_at DESC);

-- 3. Tokens de un solo uso para recuperación de contraseña por correo
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expira_en   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    usado_en    TIMESTAMP WITHOUT TIME ZONE,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_usuario
    ON password_reset_tokens (usuario_id, expira_en);

-- 4. Extender reportes para soportar emisor ESTUDIANTE y reportes hacia ESTUDIANTE o DOCENTE
ALTER TABLE reportes ALTER COLUMN estudiante_id DROP NOT NULL;
ALTER TABLE reportes ALTER COLUMN docente_id DROP NOT NULL;

ALTER TABLE reportes
    ADD COLUMN IF NOT EXISTS reportante_tipo VARCHAR(30) NOT NULL DEFAULT 'DOCENTE';

ALTER TABLE reportes
    ADD COLUMN IF NOT EXISTS objetivo_tipo VARCHAR(30) NOT NULL DEFAULT 'ESTUDIANTE';

ALTER TABLE reportes
    ADD COLUMN IF NOT EXISTS reportante_usuario_id UUID REFERENCES usuarios(id);

ALTER TABLE reportes
    ADD COLUMN IF NOT EXISTS reportante_estudiante_id UUID REFERENCES estudiantes(id);

CREATE INDEX IF NOT EXISTS idx_reportes_reportante_est
    ON reportes (reportante_estudiante_id, created_at DESC);

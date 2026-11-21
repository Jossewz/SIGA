-- =======================================================
-- FLYWAY MIGRATION V22: Asignaturas múltiples para Docentes
-- =======================================================

ALTER TABLE docentes
    ALTER COLUMN especialidad TYPE TEXT;

CREATE TABLE IF NOT EXISTS docente_materias (
    docente_id UUID NOT NULL REFERENCES docentes(id) ON DELETE CASCADE,
    materia_id UUID NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    PRIMARY KEY (docente_id, materia_id)
);

CREATE INDEX IF NOT EXISTS idx_docente_materias_materia
    ON docente_materias (materia_id);


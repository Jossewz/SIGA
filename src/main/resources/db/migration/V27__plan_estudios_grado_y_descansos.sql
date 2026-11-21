-- =======================================================
-- FLYWAY MIGRATION V27: Plan de Estudios por Grado e
-- Horarios Oficiales con Descansos Reales (Mañana/Tarde)
-- =======================================================

-- 1. Tabla: plan_estudios_grado (intensidad horaria dinámica por grado y asignatura)
CREATE TABLE IF NOT EXISTS plan_estudios_grado (
    id UUID PRIMARY KEY,
    grado VARCHAR(20) NOT NULL,
    materia_id UUID NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    intensidad_horaria INT NOT NULL CHECK (intensidad_horaria >= 0 AND intensidad_horaria <= 20),
    CONSTRAINT uk_plan_grado_materia UNIQUE (grado, materia_id)
);

-- 2. Actualizar bloques de Jornada Mañana (07:00 - 12:00, descanso 09:30 - 10:00)
UPDATE bloques SET numero = 1, nombre = 'Bloque 1 Mañana', hora_inicio = '07:00:00', hora_fin = '08:15:00', jornada = 'Mañana', tipo = 'CLASE', estado = 'Activo'
WHERE id = 'c0000000-0000-0000-0000-000000000001';

UPDATE bloques SET numero = 2, nombre = 'Bloque 2 Mañana', hora_inicio = '08:15:00', hora_fin = '09:30:00', jornada = 'Mañana', tipo = 'CLASE', estado = 'Activo'
WHERE id = 'c0000000-0000-0000-0000-000000000002';

UPDATE bloques SET numero = 3, nombre = 'Descanso Mañana', hora_inicio = '09:30:00', hora_fin = '10:00:00', jornada = 'Mañana', tipo = 'DESCANSO', estado = 'Activo'
WHERE id = 'c0000000-0000-0000-0000-000000000003';

UPDATE bloques SET numero = 4, nombre = 'Bloque 3 Mañana', hora_inicio = '10:00:00', hora_fin = '11:00:00', jornada = 'Mañana', tipo = 'CLASE', estado = 'Activo'
WHERE id = 'c0000000-0000-0000-0000-000000000004';

UPDATE bloques SET numero = 5, nombre = 'Bloque 4 Mañana', hora_inicio = '11:00:00', hora_fin = '12:00:00', jornada = 'Mañana', tipo = 'CLASE', estado = 'Activo'
WHERE id = 'c0000000-0000-0000-0000-000000000005';

-- 3. Registrar bloques oficiales para Jornada Tarde (13:00 - 18:00, descanso 15:00 - 15:30)
INSERT INTO bloques (id, numero, nombre, hora_inicio, hora_fin, jornada, tipo, estado) VALUES
    ('c0000000-0000-0000-0000-000000000101', 1, 'Bloque 1 Tarde', '13:00:00', '14:00:00', 'Tarde', 'CLASE', 'Activo'),
    ('c0000000-0000-0000-0000-000000000102', 2, 'Bloque 2 Tarde', '14:00:00', '15:00:00', 'Tarde', 'CLASE', 'Activo'),
    ('c0000000-0000-0000-0000-000000000103', 3, 'Descanso Tarde', '15:00:00', '15:30:00', 'Tarde', 'DESCANSO', 'Activo'),
    ('c0000000-0000-0000-0000-000000000104', 4, 'Bloque 3 Tarde', '15:30:00', '16:45:00', 'Tarde', 'CLASE', 'Activo'),
    ('c0000000-0000-0000-0000-000000000105', 5, 'Bloque 4 Tarde', '16:45:00', '18:00:00', 'Tarde', 'CLASE', 'Activo')
ON CONFLICT (numero, jornada) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    hora_inicio = EXCLUDED.hora_inicio,
    hora_fin = EXCLUDED.hora_fin,
    tipo = EXCLUDED.tipo,
    estado = EXCLUDED.estado;

-- 4. Poblar plan de estudios dinámico por grado (1° a 11°) según nivel educativo
INSERT INTO plan_estudios_grado (id, grado, materia_id, intensidad_horaria)
SELECT
    gen_random_uuid(),
    g.grado,
    m.id,
    CASE
        -- Primaria (1° a 5°): énfasis en Matemáticas (5h) y Español (5h), sin Filosofía (0h) -> ~22h
        WHEN g.num <= 5 THEN
            CASE
                WHEN LOWER(m.nombre) LIKE '%matem%' THEN 5
                WHEN LOWER(m.nombre) LIKE '%espa%' OR LOWER(m.nombre) LIKE '%castell%' THEN 5
                WHEN LOWER(m.nombre) LIKE '%natural%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%social%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%ingl%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%tecnolog%' OR LOWER(m.nombre) LIKE '%inform%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%físic%' OR LOWER(m.nombre) LIKE '%fisic%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%art%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%ética%' OR LOWER(m.nombre) LIKE '%etica%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%filosof%' THEN 0
                ELSE COALESCE(m.intensidad_horaria, 2)
            END
        -- Básica Secundaria (6° a 9°): equilibrio entre exactas, sociales, lenguaje y práctica/PRAE -> ~22-23h
        WHEN g.num <= 9 THEN
            CASE
                WHEN LOWER(m.nombre) LIKE '%matem%' THEN 4
                WHEN LOWER(m.nombre) LIKE '%espa%' OR LOWER(m.nombre) LIKE '%castell%' THEN 4
                WHEN LOWER(m.nombre) LIKE '%natural%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%social%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%ingl%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%tecnolog%' OR LOWER(m.nombre) LIKE '%inform%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%físic%' OR LOWER(m.nombre) LIKE '%fisic%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%art%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%ética%' OR LOWER(m.nombre) LIKE '%etica%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%filosof%' THEN CASE WHEN g.num = 9 THEN 1 ELSE 0 END
                ELSE COALESCE(m.intensidad_horaria, 2)
            END
        -- Media Vocacional (10° y 11°): preparación Saber 11 + Ciencias/PRAE + Filosofía + Inglés -> 23h
        ELSE
            CASE
                WHEN LOWER(m.nombre) LIKE '%matem%' THEN 4
                WHEN LOWER(m.nombre) LIKE '%espa%' OR LOWER(m.nombre) LIKE '%castell%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%natural%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%ingl%' THEN 3
                WHEN LOWER(m.nombre) LIKE '%social%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%tecnolog%' OR LOWER(m.nombre) LIKE '%inform%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%físic%' OR LOWER(m.nombre) LIKE '%fisic%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%filosof%' THEN 2
                WHEN LOWER(m.nombre) LIKE '%art%' THEN 1
                WHEN LOWER(m.nombre) LIKE '%ética%' OR LOWER(m.nombre) LIKE '%etica%' THEN 1
                ELSE COALESCE(m.intensidad_horaria, 2)
            END
    END
FROM (VALUES
    (1, '1°'), (2, '2°'), (3, '3°'), (4, '4°'), (5, '5°'),
    (6, '6°'), (7, '7°'), (8, '8°'), (9, '9°'), (10, '10°'), (11, '11°')
) AS g(num, grado)
CROSS JOIN materias m
ON CONFLICT (grado, materia_id) DO NOTHING;

-- =======================================================
-- FLYWAY MIGRATION V28: Extensión de jornada Mañana (12:30)
-- y Tarde (18:30) con bloques exactos de 1h, 1h30 y 2h
-- =======================================================

UPDATE bloques SET nombre = 'Bloque 1 Mañana', hora_inicio = '07:00:00', hora_fin = '08:00:00'
WHERE id = 'c0000000-0000-0000-0000-000000000001';

UPDATE bloques SET nombre = 'Bloque 2 Mañana', hora_inicio = '08:00:00', hora_fin = '09:30:00'
WHERE id = 'c0000000-0000-0000-0000-000000000002';

UPDATE bloques SET nombre = 'Descanso Mañana', hora_inicio = '09:30:00', hora_fin = '10:00:00', tipo = 'DESCANSO'
WHERE id = 'c0000000-0000-0000-0000-000000000003';

UPDATE bloques SET nombre = 'Bloque 3 Mañana', hora_inicio = '10:00:00', hora_fin = '11:00:00'
WHERE id = 'c0000000-0000-0000-0000-000000000004';

UPDATE bloques SET nombre = 'Bloque 4 Mañana', hora_inicio = '11:00:00', hora_fin = '12:30:00'
WHERE id = 'c0000000-0000-0000-0000-000000000005';

UPDATE bloques SET nombre = 'Bloque 1 Tarde', hora_inicio = '13:00:00', hora_fin = '14:00:00'
WHERE id = 'c0000000-0000-0000-0000-000000000101';

UPDATE bloques SET nombre = 'Bloque 2 Tarde', hora_inicio = '14:00:00', hora_fin = '15:00:00'
WHERE id = 'c0000000-0000-0000-0000-000000000102';

UPDATE bloques SET nombre = 'Descanso Tarde', hora_inicio = '15:00:00', hora_fin = '15:30:00', tipo = 'DESCANSO'
WHERE id = 'c0000000-0000-0000-0000-000000000103';

UPDATE bloques SET nombre = 'Bloque 3 Tarde', hora_inicio = '15:30:00', hora_fin = '16:30:00'
WHERE id = 'c0000000-0000-0000-0000-000000000104';

UPDATE bloques SET nombre = 'Bloque 4 Tarde', hora_inicio = '16:30:00', hora_fin = '17:30:00'
WHERE id = 'c0000000-0000-0000-0000-000000000105';

INSERT INTO bloques (id, numero, nombre, hora_inicio, hora_fin, jornada, tipo, estado) VALUES
    ('c0000000-0000-0000-0000-000000000106', 6, 'Bloque 5 Tarde', '17:30:00', '18:30:00', 'Tarde', 'CLASE', 'Activo')
ON CONFLICT (numero, jornada) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    hora_inicio = EXCLUDED.hora_inicio,
    hora_fin = EXCLUDED.hora_fin,
    tipo = EXCLUDED.tipo,
    estado = EXCLUDED.estado;

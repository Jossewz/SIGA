-- ==============================================================================
-- V26: Capa Analítica Power BI — 5 Vistas Modelo Estrella (SIGA - IEACI)
-- Compatible con SIGA - DASHBOARD ACADEMICO.pbip (conexión directa PostgreSQL)
-- ==============================================================================

-- 1. Dim_Nivel_Desempeno
CREATE OR REPLACE VIEW vw_bi_dim_nivel_desempeno AS
SELECT
    CASE UPPER(codigo)
        WHEN 'BAJO'     THEN 1
        WHEN 'BASICO'   THEN 2
        WHEN 'ALTO'     THEN 3
        WHEN 'SUPERIOR' THEN 4
        ELSE orden
    END::bigint AS "NivelKey",
    CASE UPPER(codigo)
        WHEN 'BAJO'     THEN 'Bajo'
        WHEN 'BASICO'   THEN 'Básico'
        WHEN 'ALTO'     THEN 'Alto'
        WHEN 'SUPERIOR' THEN 'Superior'
        ELSE nombre
    END::text AS "Nombre",
    nota_minima::double precision AS "Rango_Min",
    nota_maxima::double precision AS "Rango_Max",
    CASE UPPER(codigo)
        WHEN 'BAJO'     THEN 1
        WHEN 'BASICO'   THEN 2
        WHEN 'ALTO'     THEN 3
        WHEN 'SUPERIOR' THEN 4
        ELSE orden
    END::bigint AS "Orden",
    CASE WHEN UPPER(codigo) = 'BAJO' THEN 0 ELSE 1 END::bigint AS "Aprueba"
FROM escala_desempeno
ORDER BY "Orden";

-- 2. Dim_Periodo
CREATE OR REPLACE VIEW vw_bi_dim_periodo AS
SELECT
    p.numero_periodo::bigint AS "PeriodoKey",
    ('Periodo ' || p.numero_periodo)::text AS "Nombre_Periodo",
    al.anio::bigint AS "Anio_Lectivo",
    ROUND((p.peso_porcentaje / 100.0)::numeric, 2)::double precision AS "Peso",
    GREATEST(1, ROUND((p.fecha_fin - p.fecha_inicio) / 7.0))::bigint AS "Semanas",
    p.fecha_inicio::date AS "Fecha_Inicio",
    p.fecha_fin::date AS "Fecha_Fin"
FROM periodos_academicos p
INNER JOIN anios_lectivos al ON al.id = p.anio_lectivo_id
WHERE al.es_actual = true
ORDER BY p.numero_periodo;

-- 3. Dim_Curso
CREATE OR REPLACE VIEW vw_bi_dim_curso AS
WITH base AS (
    SELECT
        c.id::text AS curso_key,
        (c.grado || '-' || c.grupo)::text AS nombre_curso,
        CASE
            WHEN c.grado ILIKE '%transici%' THEN 0
            WHEN c.grado ~ '^[0-9]+' THEN CAST(SUBSTRING(c.grado FROM '^[0-9]+') AS INTEGER)
            ELSE 99
        END AS grado_num,
        c.grupo::text AS grupo,
        COALESCE(c.jornada, 'Mañana')::text AS jornada
    FROM cursos c
    INNER JOIN anios_lectivos al ON al.id = c.anio_lectivo_id
    WHERE al.es_actual = true
)
SELECT
    curso_key AS "CursoKey",
    nombre_curso AS "Nombre_Curso",
    grado_num::bigint AS "Grado",
    grupo AS "Grupo",
    CASE
        WHEN grado_num = 0 THEN 'Preescolar'
        WHEN grado_num BETWEEN 1 AND 5 THEN 'Primaria'
        WHEN grado_num BETWEEN 6 AND 9 THEN 'Básica secundaria'
        ELSE 'Media'
    END::text AS "Nivel_Educativo",
    jornada AS "Jornada",
    ROW_NUMBER() OVER (ORDER BY grado_num, grupo)::bigint AS "Orden_Curso"
FROM base;

-- 4. Dim_Estudiante (Anonimizada conforme a Ley 1581 de 2012)
CREATE OR REPLACE VIEW vw_bi_dim_estudiante AS
SELECT
    COALESCE(e.codigo, e.id::text)::text AS "EstudianteKey",
    COALESCE(e.genero, 'No especificado')::text AS "Genero",
    COALESCE(e.estado, 'Activo')::text AS "Estado"
FROM estudiantes e
WHERE EXISTS (
    SELECT 1
    FROM curso_estudiante ce
    INNER JOIN anios_lectivos al ON al.id = ce.anio_lectivo_id
    WHERE ce.estudiante_id = e.id AND al.es_actual = true
);

-- 5. Fact_Seguimiento_Periodo (Grano: estudiante x curso x periodo; sin columna redundante Es_Aprobado)
CREATE OR REPLACE VIEW vw_bi_fact_seguimiento_periodo AS
WITH notas_por_materia AS (
    SELECT
        ce.estudiante_id,
        ce.curso_id,
        ev.periodo AS numero_periodo,
        cm.materia_id,
        ROUND(
            (SUM(cal.nota * ev.peso) / NULLIF(SUM(ev.peso), 0))::numeric,
            2
        ) AS nota_materia
    FROM curso_estudiante ce
    INNER JOIN anios_lectivos al ON al.id = ce.anio_lectivo_id AND al.es_actual = true
    INNER JOIN curso_materia cm ON cm.curso_id = ce.curso_id AND cm.anio_lectivo_id = ce.anio_lectivo_id
    INNER JOIN evaluaciones ev ON ev.curso_materia_id = cm.id
    INNER JOIN calificaciones cal ON cal.evaluacion_id = ev.id AND cal.estudiante_id = ce.estudiante_id
    GROUP BY ce.estudiante_id, ce.curso_id, ev.periodo, cm.materia_id
),
agg_academico AS (
    SELECT
        estudiante_id,
        curso_id,
        numero_periodo,
        ROUND(AVG(nota_materia)::numeric, 2)::double precision AS promedio_periodo,
        COUNT(*)::bigint AS areas_evaluadas,
        COUNT(*) FILTER (WHERE nota_materia < 3.00)::bigint AS areas_en_bajo
    FROM notas_por_materia
    GROUP BY estudiante_id, curso_id, numero_periodo
),
agg_asistencia AS (
    SELECT
        a.estudiante_id,
        sc.curso_id,
        p.numero_periodo,
        ROUND(
            (100.0 * COUNT(*) FILTER (WHERE UPPER(a.estado) = 'INASISTENCIA') / NULLIF(COUNT(*), 0))::numeric,
            1
        )::double precision AS pct_inasistencia
    FROM asistencias a
    INNER JOIN sesiones_clase sc ON sc.id = a.sesion_id
    INNER JOIN periodos_academicos p ON sc.fecha BETWEEN p.fecha_inicio AND p.fecha_fin
    INNER JOIN anios_lectivos al ON al.id = p.anio_lectivo_id AND al.es_actual = true
    GROUP BY a.estudiante_id, sc.curso_id, p.numero_periodo
),
agg_reportes AS (
    SELECT
        r.estudiante_id,
        p.numero_periodo,
        COUNT(*) FILTER (WHERE r.categoria ILIKE '%conviv%' OR r.categoria ILIKE '%otra%')::bigint AS reportes_convivencia,
        COUNT(*) FILTER (WHERE r.categoria ILIKE '%rendim%' OR r.categoria ILIKE '%acad%')::bigint AS reportes_rendimiento,
        COUNT(*) FILTER (WHERE r.categoria ILIKE '%asist%')::bigint AS reportes_asistencia,
        COUNT(*) FILTER (WHERE r.estado ILIKE '%pend%' OR r.estado ILIKE '%radic%' OR r.estado ILIKE '%revis%')::bigint AS reportes_pendientes,
        COUNT(*) FILTER (WHERE r.estado ILIKE '%acept%' OR r.estado ILIKE '%atend%' OR r.estado ILIKE '%contest%' OR r.estado ILIKE '%cerr%')::bigint AS reportes_atendidos
    FROM reportes r
    INNER JOIN periodos_academicos p ON r.created_at::date BETWEEN p.fecha_inicio AND p.fecha_fin
    INNER JOIN anios_lectivos al ON al.id = p.anio_lectivo_id AND al.es_actual = true
    WHERE r.estudiante_id IS NOT NULL
    GROUP BY r.estudiante_id, p.numero_periodo
)
SELECT
    ('SEG-' || COALESCE(e.codigo, e.id::text) || '-P' || p.numero_periodo)::text AS "SeguimientoKey",
    COALESCE(e.codigo, e.id::text)::text AS "EstudianteKey",
    ce.curso_id::text AS "CursoKey",
    p.numero_periodo::bigint AS "PeriodoKey",
    CASE
        WHEN COALESCE(ac.promedio_periodo, 0) < 3.0 THEN 1
        WHEN COALESCE(ac.promedio_periodo, 0) < 4.0 THEN 2
        WHEN COALESCE(ac.promedio_periodo, 0) < 4.6 THEN 3
        ELSE 4
    END::bigint AS "NivelKey",
    COALESCE(ac.promedio_periodo, 0.0)::double precision AS "Promedio_Periodo",
    COALESCE(ac.areas_evaluadas, 0)::bigint AS "Areas_Evaluadas",
    COALESCE(ac.areas_en_bajo, 0)::bigint AS "Areas_En_Bajo",
    COALESCE(asi.pct_inasistencia, 0.0)::double precision AS "Pct_Inasistencia",
    COALESCE(rep.reportes_convivencia, 0)::bigint AS "Reportes_Convivencia",
    COALESCE(rep.reportes_rendimiento, 0)::bigint AS "Reportes_Rendimiento",
    COALESCE(rep.reportes_asistencia, 0)::bigint AS "Reportes_Asistencia",
    COALESCE(rep.reportes_pendientes, 0)::bigint AS "Reportes_Pendientes",
    COALESCE(rep.reportes_atendidos, 0)::bigint AS "Reportes_Atendidos"
FROM curso_estudiante ce
INNER JOIN anios_lectivos al ON al.id = ce.anio_lectivo_id AND al.es_actual = true
INNER JOIN estudiantes e ON e.id = ce.estudiante_id
INNER JOIN periodos_academicos p ON p.anio_lectivo_id = ce.anio_lectivo_id
LEFT JOIN agg_academico ac
    ON ac.estudiante_id = ce.estudiante_id
   AND ac.curso_id = ce.curso_id
   AND ac.numero_periodo = p.numero_periodo
LEFT JOIN agg_asistencia asi
    ON asi.estudiante_id = ce.estudiante_id
   AND asi.curso_id = ce.curso_id
   AND asi.numero_periodo = p.numero_periodo
LEFT JOIN agg_reportes rep
    ON rep.estudiante_id = ce.estudiante_id
   AND rep.numero_periodo = p.numero_periodo;

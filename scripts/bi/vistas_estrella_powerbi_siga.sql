-- ==============================================================================
-- SIGA - IEACI | Seed realista + 5 Vistas Modelo Estrella para Power BI (Live)
-- Idempotente: preserva los registros existentes sin borrar datos previos.
-- ==============================================================================

BEGIN;

-- 1. SEED REALISTA (idempotente)
DO $$
DECLARE
    v_anio_id       uuid := 'e0000000-0000-0000-0000-000000002026';
    v_docente_id    uuid;
    v_admin_id      uuid;
    v_curso_rec     RECORD;
    v_mat_rec       RECORD;
    v_est_rec       RECORD;
    v_cm_rec        RECORD;
    v_per_rec       RECORD;
    v_eval_id_1     uuid;
    v_eval_id_2     uuid;
    v_ses_id        uuid;
    v_new_est_id    uuid;
    v_curso_id      uuid;
    v_hash          int;
    v_base_nota     numeric(4,2);
    v_nota1         numeric(3,2);
    v_nota2         numeric(3,2);
    v_estado_asis   text;
    v_fecha_ses     date;
    v_rep_cat       text;
    v_rep_razon     text;
    v_rep_estado    text;
    v_rep_fecha     timestamp;
    v_idx           int := 0;
    v_nombres_m     text[] := ARRAY['Mateo','Santiago','Sebastián','Samuel','Nicolás','Alejandro','Daniel','Lucas','Martín','Tomás','Gabriel','Andrés','Julián','Felipe','David','Emiliano','Jerónimo','Miguel','Juan','Carlos'];
    v_nombres_f     text[] := ARRAY['Sofía','Valentina','Isabella','Camila','Mariana','Luciana','Salomé','Gabriela','Antonella','Valeria','Sara','María','Paula','Laura','Juliana','Natalia','Manuela','Andrea','Daniela','Elena'];
    v_apellidos     text[] := ARRAY['García','Rodríguez','Martínez','Hernández','López','González','Pérez','Sánchez','Ramírez','Torres','Flores','Rivera','Gómez','Díaz','Reyes','Morales','Ortiz','Gutiérrez','Chávez','Ramos','Vargas','Castillo','Jiménez','Moreno','Romero','Herrera','Medina','Aguilar','Castro','Mendoza'];
    v_grados        text[] := ARRAY['Transición','1°','3°','5°','6°','7°','8°','9°','10°','11°'];
    v_g             text;
    v_genero        text;
    v_nom           text;
    v_ape           text;
    v_codigo        text;
    v_doc           text;
    v_cursos_arr    uuid[];
    v_target_curso  uuid;
    v_i             int;
    v_s             int;
BEGIN
    SELECT id INTO v_docente_id FROM docentes LIMIT 1;
    SELECT id INTO v_admin_id FROM personal_administrativo LIMIT 1;

    -- 1.1 Asegurar 10 cursos (Transición a 11°, preservando 9°-01 y 11°-01 existentes)
    FOREACH v_g IN ARRAY v_grados LOOP
        INSERT INTO cursos (id, grado, grupo, jornada, cupos_maximos, director_id, ano_lectivo, estado, created_at, anio_lectivo_id)
        VALUES (
            md5('curso-' || v_g || '-01-2026')::uuid,
            v_g, '01', 'Mañana', 35, v_docente_id, '2026', 'Activo', now(), v_anio_id
        )
        ON CONFLICT (grado, grupo, anio_lectivo_id) DO NOTHING;
    END LOOP;

    SELECT array_agg(id ORDER BY grado, grupo) INTO v_cursos_arr
    FROM cursos WHERE anio_lectivo_id = v_anio_id;

    -- 1.2 Asignar al 4to estudiante existente (si aún no tiene curso en 2026)
    SELECT id INTO v_target_curso FROM cursos WHERE grado = '11°' AND grupo = '01' AND anio_lectivo_id = v_anio_id LIMIT 1;
    FOR v_est_rec IN
        SELECT e.id FROM estudiantes e
        WHERE NOT EXISTS (
            SELECT 1 FROM curso_estudiante ce WHERE ce.estudiante_id = e.id AND ce.anio_lectivo_id = v_anio_id
        )
    LOOP
        INSERT INTO curso_estudiante (id, curso_id, estudiante_id, ano_lectivo, anio_lectivo_id)
        VALUES (md5('ce-' || v_est_rec.id::text || '-2026')::uuid, v_target_curso, v_est_rec.id, '2026', v_anio_id)
        ON CONFLICT (estudiante_id, anio_lectivo_id) DO NOTHING;
    END LOOP;

    -- 1.3 Sembrar 116 estudiantes adicionales (total 120 estudiantes distribuidos en los 10 cursos)
    FOR v_i IN 5..120 LOOP
        v_codigo := '2026' || lpad(v_i::text, 3, '0');
        v_doc    := '102030' || lpad(v_i::text, 4, '0');
        v_new_est_id := md5('est-siga-' || v_codigo)::uuid;
        IF (v_i % 2 = 0) THEN
            v_genero := 'F';
            v_nom := v_nombres_f[1 + (v_i % array_length(v_nombres_f, 1))];
        ELSE
            v_genero := 'M';
            v_nom := v_nombres_m[1 + (v_i % array_length(v_nombres_m, 1))];
        END IF;
        v_ape := v_apellidos[1 + ((v_i * 3) % array_length(v_apellidos, 1))] || ' ' ||
                 v_apellidos[1 + ((v_i * 7 + 5) % array_length(v_apellidos, 1))];

        INSERT INTO estudiantes (id, codigo, nombres, apellidos, tipo_documento, numero_documento, genero, fecha_nacimiento, estado, created_at, updated_at)
        VALUES (
            v_new_est_id, v_codigo, v_nom, v_ape, 'TI', v_doc, v_genero,
            DATE '2010-01-01' + ((v_i * 37) % 3650),
            'Activo', now(), now()
        )
        ON CONFLICT (codigo) DO NOTHING;

        SELECT id INTO v_new_est_id FROM estudiantes WHERE codigo = v_codigo;
        v_target_curso := v_cursos_arr[1 + ((v_i - 1) % array_length(v_cursos_arr, 1))];

        INSERT INTO curso_estudiante (id, curso_id, estudiante_id, ano_lectivo, anio_lectivo_id)
        VALUES (md5('ce-' || v_new_est_id::text || '-2026')::uuid, v_target_curso, v_new_est_id, '2026', v_anio_id)
        ON CONFLICT (estudiante_id, anio_lectivo_id) DO NOTHING;

        INSERT INTO matriculas (id, estudiante_id, grado, salon, ano_lectivo, estado, fecha_matricula, curso_id, autoriza_tratamiento_datos, anio_lectivo_id)
        SELECT md5('mat-' || v_new_est_id::text || '-2026')::uuid,
               v_new_est_id, c.grado, c.grupo, '2026', 'APROBADA', DATE '2026-01-20', c.id, true, v_anio_id
        FROM cursos c WHERE c.id = v_target_curso
        ON CONFLICT (estudiante_id, anio_lectivo_id) DO NOTHING;
    END LOOP;

    -- 1.4 Asignar 6 materias principales a cada curso (60 curso_materia)
    FOR v_curso_rec IN SELECT id FROM cursos WHERE anio_lectivo_id = v_anio_id LOOP
        FOR v_mat_rec IN SELECT id FROM materias ORDER BY nombre LIMIT 6 LOOP
            INSERT INTO curso_materia (id, curso_id, materia_id, docente_id, ano_lectivo, anio_lectivo_id)
            VALUES (
                md5('cm-' || v_curso_rec.id::text || '-' || v_mat_rec.id::text || '-2026')::uuid,
                v_curso_rec.id, v_mat_rec.id, v_docente_id, '2026', v_anio_id
            )
            ON CONFLICT (curso_id, materia_id, anio_lectivo_id) DO NOTHING;
        END LOOP;
    END LOOP;

    -- 1.5 Crear 2 evaluaciones por curso_materia x periodo (360 evaluaciones) y calificaciones realistas
    FOR v_cm_rec IN SELECT id, curso_id, materia_id FROM curso_materia WHERE anio_lectivo_id = v_anio_id LOOP
        FOR v_per_rec IN SELECT numero_periodo, fecha_inicio, fecha_fin FROM periodos_academicos WHERE anio_lectivo_id = v_anio_id ORDER BY numero_periodo LOOP
            v_eval_id_1 := md5('eval1-' || v_cm_rec.id::text || '-p' || v_per_rec.numero_periodo)::uuid;
            v_eval_id_2 := md5('eval2-' || v_cm_rec.id::text || '-p' || v_per_rec.numero_periodo)::uuid;

            INSERT INTO evaluaciones (id, curso_materia_id, nombre, periodo, peso, fecha, created_at)
            VALUES
                (v_eval_id_1, v_cm_rec.id, 'Taller y Seguimiento P' || v_per_rec.numero_periodo, v_per_rec.numero_periodo, 40.00, v_per_rec.fecha_inicio + 20, now()),
                (v_eval_id_2, v_cm_rec.id, 'Evaluación Acumulativa P' || v_per_rec.numero_periodo, v_per_rec.numero_periodo, 60.00, v_per_rec.fecha_fin - 7, now())
            ON CONFLICT (id) DO NOTHING;

            FOR v_est_rec IN
                SELECT ce.estudiante_id FROM curso_estudiante ce
                WHERE ce.curso_id = v_cm_rec.curso_id AND ce.anio_lectivo_id = v_anio_id
            LOOP
                v_hash := abs(hashtext(v_est_rec.estudiante_id::text || v_cm_rec.materia_id::text || v_per_rec.numero_periodo::text));
                -- Distribución realista SIEACI: ~13% Bajo (1.8-2.8), ~35% Básico (3.0-3.9), ~34% Alto (4.0-4.5), ~18% Superior (4.6-5.0)
                IF (abs(hashtext(v_est_rec.estudiante_id::text || 'p' || v_per_rec.numero_periodo::text)) % 100) < 13 THEN
                    v_base_nota := 1.90 + ((v_hash % 95) / 100.0);
                ELSIF (abs(hashtext(v_est_rec.estudiante_id::text || 'p' || v_per_rec.numero_periodo::text)) % 100) < 48 THEN
                    v_base_nota := 3.10 + ((v_hash % 85) / 100.0);
                ELSIF (abs(hashtext(v_est_rec.estudiante_id::text || 'p' || v_per_rec.numero_periodo::text)) % 100) < 82 THEN
                    v_base_nota := 4.00 + ((v_hash % 55) / 100.0);
                ELSE
                    v_base_nota := 4.60 + ((v_hash % 40) / 100.0);
                END IF;

                v_nota1 := LEAST(5.00, GREATEST(1.00, round(v_base_nota + (((v_hash % 7) - 3) * 0.08), 2)));
                v_nota2 := LEAST(5.00, GREATEST(1.00, round(v_base_nota + ((((v_hash / 7) % 7) - 3) * 0.08), 2)));

                INSERT INTO calificaciones (id, evaluacion_id, estudiante_id, nota, created_at, updated_at)
                VALUES
                    (md5('cal-' || v_eval_id_1::text || '-' || v_est_rec.estudiante_id::text)::uuid, v_eval_id_1, v_est_rec.estudiante_id, v_nota1, now(), now()),
                    (md5('cal-' || v_eval_id_2::text || '-' || v_est_rec.estudiante_id::text)::uuid, v_eval_id_2, v_est_rec.estudiante_id, v_nota2, now(), now())
                ON CONFLICT (evaluacion_id, estudiante_id) DO NOTHING;
            END LOOP;
        END LOOP;
    END LOOP;

    -- 1.6 Crear 6 sesiones de clase por curso x periodo (180 sesiones) y asistencias realistas
    FOR v_curso_rec IN SELECT id FROM cursos WHERE anio_lectivo_id = v_anio_id LOOP
        SELECT id INTO v_cm_rec FROM curso_materia WHERE curso_id = v_curso_rec.id AND anio_lectivo_id = v_anio_id LIMIT 1;
        FOR v_per_rec IN SELECT numero_periodo, fecha_inicio, fecha_fin FROM periodos_academicos WHERE anio_lectivo_id = v_anio_id ORDER BY numero_periodo LOOP
            FOR v_s IN 1..6 LOOP
                v_fecha_ses := v_per_rec.fecha_inicio + (v_s * 7);
                IF v_fecha_ses > v_per_rec.fecha_fin THEN
                    v_fecha_ses := v_per_rec.fecha_fin - v_s;
                END IF;
                v_ses_id := md5('ses-' || v_curso_rec.id::text || '-p' || v_per_rec.numero_periodo || '-s' || v_s)::uuid;

                INSERT INTO sesiones_clase (id, curso_id, curso_materia_id, docente_id, fecha, hora_inicio, hora_fin, estado, tipo, tema)
                VALUES (
                    v_ses_id, v_curso_rec.id, v_cm_rec.id, v_docente_id,
                    v_fecha_ses, TIME '08:00:00', TIME '09:30:00', 'DICTADA', 'ASIGNATURA',
                    'Sesión ' || v_s || ' - Periodo ' || v_per_rec.numero_periodo
                )
                ON CONFLICT (curso_id, fecha, hora_inicio) DO NOTHING;

                SELECT id INTO v_ses_id FROM sesiones_clase
                WHERE curso_id = v_curso_rec.id AND fecha = v_fecha_ses AND hora_inicio = TIME '08:00:00';

                FOR v_est_rec IN
                    SELECT ce.estudiante_id FROM curso_estudiante ce
                    WHERE ce.curso_id = v_curso_rec.id AND ce.anio_lectivo_id = v_anio_id
                LOOP
                    v_hash := abs(hashtext(v_ses_id::text || v_est_rec.estudiante_id::text)) % 100;
                    IF v_hash < 5 THEN
                        v_estado_asis := 'INASISTENCIA';
                    ELSIF v_hash < 8 THEN
                        v_estado_asis := 'TARDANZA';
                    ELSIF v_hash < 10 THEN
                        v_estado_asis := 'EXCUSADO';
                    ELSE
                        v_estado_asis := 'PRESENTE';
                    END IF;

                    INSERT INTO asistencias (id, sesion_id, estudiante_id, estado)
                    VALUES (md5('asi-' || v_ses_id::text || '-' || v_est_rec.estudiante_id::text)::uuid, v_ses_id, v_est_rec.estudiante_id, v_estado_asis)
                    ON CONFLICT (sesion_id, estudiante_id) DO NOTHING;
                END LOOP;
            END LOOP;
        END LOOP;
    END LOOP;

    -- 1.7 Sembrar 59 reportes institucionales realistas distribuidos en los 3 periodos
    v_idx := 0;
    FOR v_est_rec IN
        SELECT ce.estudiante_id FROM curso_estudiante ce
        WHERE ce.anio_lectivo_id = v_anio_id
        ORDER BY ce.estudiante_id
        LIMIT 59
    LOOP
        v_idx := v_idx + 1;
        IF (v_idx % 3 = 1) THEN
            v_rep_cat := 'Convivencia';
            v_rep_razon := 'Incumplimiento de normas de convivencia en aula';
        ELSIF (v_idx % 3 = 2) THEN
            v_rep_cat := 'Rendimiento Académico';
            v_rep_razon := 'Seguimiento preventivo por bajo desempeño en área';
        ELSE
            v_rep_cat := 'Asistencia';
            v_rep_razon := 'Inasistencias reiteradas sin justificación';
        END IF;

        IF (v_idx % 4 = 0) THEN
            v_rep_estado := 'Pendiente';
        ELSE
            v_rep_estado := 'Aceptado';
        END IF;

        IF (v_idx % 3 = 1) THEN
            v_rep_fecha := TIMESTAMP '2026-03-15 09:00:00' + ((v_idx % 20) || ' days')::interval;
        ELSIF (v_idx % 3 = 2) THEN
            v_rep_fecha := TIMESTAMP '2026-08-01 09:00:00' + ((v_idx % 20) || ' days')::interval;
        ELSE
            v_rep_fecha := TIMESTAMP '2026-10-05 09:00:00' + ((v_idx % 20) || ' days')::interval;
        END IF;

        INSERT INTO reportes (
            id, codigo, estudiante_id, docente_id, categoria, razon, detalles,
            estado, requiere_acudiente, atendido_por, created_at, updated_at,
            reportante_tipo, objetivo_tipo
        )
        VALUES (
            md5('rep-siga-2026-' || v_idx)::uuid,
            'REP-2026-BI-' || lpad(v_idx::text, 3, '0'),
            v_est_rec.estudiante_id,
            v_docente_id,
            v_rep_cat,
            v_rep_razon,
            'Registro institucional de seguimiento generado en SIGA.',
            v_rep_estado,
            (v_idx % 2 = 0),
            CASE WHEN v_rep_estado = 'Aceptado' THEN v_admin_id ELSE NULL END,
            v_rep_fecha,
            v_rep_fecha + INTERVAL '2 days',
            'DOCENTE',
            'ESTUDIANTE'
        )
        ON CONFLICT (codigo) DO NOTHING;
    END LOOP;
END $$;

-- ==============================================================================
-- 2. VISTAS DEL MODELO ESTRELLA PARA POWER BI (Esquema 100% compatible con SIGA)
-- ==============================================================================

-- 2.1 Dim_Nivel_Desempeno
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

-- 2.2 Dim_Periodo
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

-- 2.3 Dim_Curso
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

-- 2.4 Dim_Estudiante (Anonimizada conforme a Ley 1581 de 2012)
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

-- 2.5 Fact_Seguimiento_Periodo (Grano: estudiante x curso x periodo; sin columna redundante Es_Aprobado)
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

COMMIT;

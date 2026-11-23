package com.siga.siga_iea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Seeder masivo e idempotente para poblar SIGA-IEACI con el volumen realista
 * de una institución educativa grande (~800+ personas, 24 cursos en 2 jornadas,
 * directivos, docentes especialistas, acudientes, matrículas Ley 1581, carga
 * académica, horarios, evaluaciones, calificaciones, sesiones, asistencias,
 * reportes de convivencia, certificados y notificaciones).
 *
 * Activación bajo demanda (no afecta pruebas ni el arranque normal):
 *   --siga.seed.enabled=true
 * Opcional para ejecutar como tarea única y salir sin conflicto de puerto:
 *   --server.port=0 --siga.seed.enabled=true --siga.seed.exit-after=true
 *
 * Contraseña estándar de las cuentas generadas: Siga2026*
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "siga.seed.enabled", havingValue = "true")
public class InstitucionGrandeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InstitucionGrandeSeeder.class);

    private static final int BATCH_SIZE = 500;
    private static final String DEFAULT_PASSWORD_RAW = "Siga2026*";

    private static final String[] NOMBRES_M = {
            "Andrés", "Carlos", "David", "Diego", "Esteban", "Felipe", "Gabriel", "Héctor",
            "Iván", "Javier", "Jorge", "José", "Juan", "Julián", "Luis", "Manuel",
            "Mateo", "Miguel", "Nicolás", "Óscar", "Pablo", "Rafael", "Ricardo", "Samuel",
            "Santiago", "Sebastián", "Sergio", "Tomás", "Alejandro", "Brayan", "Camilo", "Cristian",
            "Daniel", "Eduardo", "Emiliano", "Fernando", "Gustavo", "Hernán", "Kevin", "Leonardo"
    };

    private static final String[] NOMBRES_F = {
            "Adriana", "Alejandra", "Ana", "Andrea", "Ángela", "Camila", "Carolina", "Catalina",
            "Claudia", "Daniela", "Diana", "Elena", "Estefanía", "Gabriela", "Isabella", "Juliana",
            "Karen", "Laura", "Liliana", "Lucía", "Luisa", "Manuela", "Marcela", "María",
            "Mariana", "Marta", "Melissa", "Natalia", "Paola", "Patricia", "Paula", "Salomé",
            "Sandra", "Sara", "Sofía", "Tatiana", "Valentina", "Valeria", "Verónica", "Yuliana"
    };

    private static final String[] APELLIDOS = {
            "Acosta", "Aguilar", "Alvarado", "Álvarez", "Arango", "Arias", "Avila", "Ayola",
            "Barrera", "Bautista", "Becerra", "Benítez", "Bermúdez", "Blanco", "Bolaños", "Cabrera",
            "Caldera", "Camacho", "Cárdenas", "Carrillo", "Castañeda", "Castillo", "Castro", "Céspedes",
            "Chávez", "Contreras", "Córdoba", "Cortés", "Cruz", "De la Rosa", "Delgado", "Díaz",
            "Domínguez", "Duarte", "Escobar", "Espinosa", "Fernández", "Figueroa", "Flores", "Fuentes",
            "Galeano", "García", "Giraldo", "Gómez", "González", "Guerrero", "Gutiérrez", "Hernández",
            "Herrera", "Ibarra", "Jiménez", "León", "López", "Lozano", "Machado", "Maldonado",
            "Marín", "Márquez", "Martínez", "Medina", "Mejía", "Méndez", "Mendoza", "Mercado",
            "Molina", "Montes", "Montoya", "Morales", "Moreno", "Muñoz", "Narváez", "Navarro",
            "Nieto", "Núñez", "Ochoa", "Ortega", "Ortiz", "Osorio", "Ospina", "Padilla",
            "Palacios", "Pardo", "Paredes", "Paternina", "Peña", "Pérez", "Pineda", "Pinto",
            "Polo", "Quintero", "Quiroz", "Ramírez", "Ramos", "Reyes", "Rincón", "Ríos",
            "Rivera", "Roa", "Rodríguez", "Rojas", "Romero", "Ruiz", "Salazar", "Sánchez",
            "Sandoval", "Sarmiento", "Serrano", "Sierra", "Silva", "Solano", "Suárez", "Torres",
            "Trujillo", "Uribe", "Valencia", "Vargas", "Vásquez", "Vega", "Velásquez", "Vergara",
            "Villadiego", "Villalobos", "Zambrano", "Zapata"
    };

    private static final String[] BARRIOS = {
            "Manga", "Bocagrande", "Pie de la Popa", "Crespo", "La Castellana", "Los Alpes",
            "El Recreo", "San Fernando", "Blas de Lezo", "El Socorro", "Ternera", "Las Gaviotas",
            "Torices", "Amberes", "Bruselas", "España", "Chiquinquirá", "Olaya Herrera",
            "Los Caracoles", "Nuevo Bosque", "Alto Bosque", "Ceballos", "La Campiña", "Santa Lucía"
    };

    private static final String[] GRADOS = {
            "1°", "2°", "3°", "4°", "5°", "6°", "7°", "8°", "9°", "10°", "11°"
    };

    private static final int[] EDAD_POR_GRADO = {
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
    };

    private static final String[] DIAS_SEMANA = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ConfigurableApplicationContext appContext;
    private final boolean exitAfter;

    public InstitucionGrandeSeeder(JdbcTemplate jdbc,
                                   PasswordEncoder passwordEncoder,
                                   ConfigurableApplicationContext appContext,
                                   @Value("${siga.seed.exit-after:false}") boolean exitAfter) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.appContext = appContext;
        this.exitAfter = exitAfter;
    }

    @Override
    public void run(ApplicationArguments args) {
        long startMs = System.currentTimeMillis();
        log.info("======================================================================");
        log.info("  SIGA-IEACI :: Iniciando Seeder de Institución Grande (Idempotente)  ");
        log.info("======================================================================");

        Random rng = new Random(20261122L);
        String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD_RAW);

        UUID anioLectivoId = obtenerOcrearAnioLectivoActual();
        limpiarGradosNoSoportados();
        Map<String, UUID> materiasPorNombre = cargarMaterias();
        List<UUID> listaMaterias = new ArrayList<>(materiasPorNombre.values());

        // 1. Directivos y personal administrativo (20 personas)
        List<UUID> personalIds = seedDirectivosYAdministrativos(passwordHash, rng);

        // 2. Docentes (55 profesores) + especialidades + docente_materias
        List<DocenteSeed> docentes = seedDocentes(passwordHash, materiasPorNombre, rng);

        // 3. Salones (ampliar infraestructura a 34 espacios)
        List<SalonSeed> salones = seedSalones();

        // 4. Cursos (22 cursos: 1° a 11°, grupos 01 Mañana y 02 Tarde)
        List<CursoSeed> cursos = seedCursos(anioLectivoId, docentes);

        // 5. Acudientes (600) + Estudiantes activos + 15 matrículas en trámite
        List<EstudianteSeed> estudiantesActivos = seedAcudientesEstudiantesYMatriculas(
                anioLectivoId, cursos, passwordHash, rng);
        seedEstudiantesPendientes(anioLectivoId, passwordHash, rng);
        sincronizarCorreosInstitucionalesReales();

        // 6. Carga académica (curso_materia) y Horarios
        List<CursoMateriaSeed> cursosMaterias = seedCursosMaterias(anioLectivoId, cursos, materiasPorNombre, docentes);
        seedHorarios(cursos, cursosMaterias, salones);

        // 7. Evaluaciones y Calificaciones (3 periodos x 3 evaluaciones)
        seedEvaluacionesYCalificaciones(cursosMaterias, cursos, rng);

        // 8. Sesiones de clase y Asistencias (3 periodos, días hábiles)
        seedSesionesYAsistencias(cursos, cursosMaterias, rng);

        // 9. Reportes de convivencia / rendimiento / asistencia (180)
        seedReportes(estudiantesActivos, docentes, personalIds, rng);

        // 10. Solicitudes de certificado (60)
        seedSolicitudesCertificado(estudiantesActivos, personalIds, rng);

        // 11. Notificaciones iniciales para equipo directivo y Super Admin
        seedNotificaciones();

        long elapsedSec = (System.currentTimeMillis() - startMs) / 1000;
        logResumenConteos(elapsedSec);

        if (exitAfter) {
            log.info("siga.seed.exit-after=true -> Finalizando ejecución del seeder limpiamente.");
            int code = SpringApplication.exit(appContext, () -> 0);
            System.exit(code);
        }
    }

    private UUID obtenerOcrearAnioLectivoActual() {
        List<UUID> existentes = jdbc.queryForList(
                "SELECT id FROM anios_lectivos WHERE es_actual = true LIMIT 1", UUID.class);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO anios_lectivos (id, anio, estado, fecha_inicio, fecha_fin, es_actual, created_at, updated_at) " +
                "VALUES (?, 2026, 'ACTIVO', '2026-01-20', '2026-12-05', true, NOW(), NOW()) ON CONFLICT (anio) DO NOTHING",
                id);
        return jdbc.queryForObject("SELECT id FROM anios_lectivos WHERE anio = 2026 LIMIT 1", UUID.class);
    }

    private void limpiarGradosNoSoportados() {
        jdbc.update("DELETE FROM plan_estudios_grado WHERE grado = 'Transición'");
        jdbc.update("UPDATE matriculas SET grado = '1°', curso_id = NULL WHERE grado = 'Transición'");
        jdbc.update("UPDATE solicitudes_certificado SET grado_referencia = '1°' WHERE grado_referencia = 'Transición'");
        jdbc.update("DELETE FROM horarios WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')");
        jdbc.update("DELETE FROM asistencias WHERE sesion_id IN (SELECT id FROM sesiones_clase WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición'))");
        jdbc.update("DELETE FROM sesiones_clase WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')");
        jdbc.update("DELETE FROM calificaciones WHERE evaluacion_id IN (SELECT id FROM evaluaciones WHERE curso_materia_id IN (SELECT id FROM curso_materia WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')))");
        jdbc.update("DELETE FROM evaluaciones WHERE curso_materia_id IN (SELECT id FROM curso_materia WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición'))");
        jdbc.update("DELETE FROM curso_materia WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')");
        jdbc.update("DELETE FROM curso_estudiante WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')");
        jdbc.update("UPDATE matriculas SET curso_id = NULL WHERE curso_id IN (SELECT id FROM cursos WHERE grado = 'Transición')");
        jdbc.update("DELETE FROM cursos WHERE grado = 'Transición'");
    }

    private Map<String, UUID> cargarMaterias() {
        record MateriaDef(String nombre, String area, int ih) {}
        List<MateriaDef> catalogo = List.of(
                new MateriaDef("Matemáticas", "Ciencias Exactas", 4),
                new MateriaDef("Español y Literatura", "Humanidades y Lengua Castellana", 4),
                new MateriaDef("Ciencias Naturales", "Ciencias Naturales y Educación Ambiental", 3),
                new MateriaDef("Ciencias Sociales", "Ciencias Sociales, Historia y Geografía", 3),
                new MateriaDef("Inglés Técnico", "Idiomas Extranjeros", 2),
                new MateriaDef("Tecnología e Informática", "Tecnología e Innovación", 2),
                new MateriaDef("Educación Física", "Educación Física, Recreación y Deportes", 2),
                new MateriaDef("Educación Artística", "Educación Artística y Cultural", 1),
                new MateriaDef("Ética y Valores", "Formación Humana y Ciudadana", 1),
                new MateriaDef("Filosofía", "Filosofía y Pensamiento Crítico", 2)
        );

        Map<String, UUID> map = new LinkedHashMap<>();
        jdbc.query("SELECT id, nombre FROM materias ORDER BY nombre", rs -> {
            map.put(rs.getString("nombre"), rs.getObject("id", UUID.class));
        });

        for (MateriaDef def : catalogo) {
            if (!map.containsKey(def.nombre())) {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO materias (id, nombre, area, intensidad_horaria, estado) VALUES (?, ?, ?, ?, 'Activo')",
                        id, def.nombre(), def.area(), def.ih());
                map.put(def.nombre(), id);
            }
        }

        // Asegurar plan_estudios_grado para los grados 1° a 11°
        for (int idx = 0; idx < GRADOS.length; idx++) {
            String grado = GRADOS[idx];
            int num = idx + 1;
            for (MateriaDef def : catalogo) {
                UUID matId = map.get(def.nombre());
                if (matId == null) continue;
                int ih = def.ih();
                if ("Filosofía".equals(def.nombre())) {
                    ih = (num >= 10) ? 2 : (num == 9 ? 1 : 0);
                } else if ("Inglés Técnico".equals(def.nombre()) && num >= 10) {
                    ih = 3;
                } else if ("Español y Literatura".equals(def.nombre()) && num >= 10) {
                    ih = 3;
                } else if ("Ciencias Sociales".equals(def.nombre()) && num >= 10) {
                    ih = 2;
                }
                jdbc.update(
                        "INSERT INTO plan_estudios_grado (id, grado, materia_id, intensidad_horaria) " +
                        "VALUES (?, ?, ?, ?) ON CONFLICT (grado, materia_id) DO NOTHING",
                        UUID.randomUUID(), grado, matId, ih);
            }
        }

        return map;
    }

    private List<UUID> seedDirectivosYAdministrativos(String passwordHash, Random rng) {
        record CargoDef(String cargo, String area, String rol, String emailAlias, String genero) {}
        List<CargoDef> definiciones = List.of(
                new CargoDef("Rector", "Rectoría", "ADMIN", "rectoria", "M"),
                new CargoDef("Vicerrectora Académica", "Vicerrectoría", "PERSONAL_ADMINISTRATIVO", "vicerrectoria", "F"),
                new CargoDef("Coordinador Académico (Mañana)", "Coordinación Académica", "PERSONAL_ADMINISTRATIVO", "coord.academica.manana", "M"),
                new CargoDef("Coordinadora Académica (Tarde)", "Coordinación Académica", "PERSONAL_ADMINISTRATIVO", "coord.academica.tarde", "F"),
                new CargoDef("Coordinador de Convivencia (Mañana)", "Coordinación de Convivencia", "PERSONAL_ADMINISTRATIVO", "coord.convivencia.manana", "M"),
                new CargoDef("Coordinadora de Convivencia (Tarde)", "Coordinación de Convivencia", "PERSONAL_ADMINISTRATIVO", "coord.convivencia.tarde", "F"),
                new CargoDef("Coordinadora de Preescolar y Primaria", "Coordinación Primaria", "PERSONAL_ADMINISTRATIVO", "coord.primaria", "F"),
                new CargoDef("Coordinador de Bachillerato y Media", "Coordinación Media", "PERSONAL_ADMINISTRATIVO", "coord.media", "M"),
                new CargoDef("Secretaria General", "Secretaría Académica", "PERSONAL_ADMINISTRATIVO", "secretaria.general", "F"),
                new CargoDef("Auxiliar de Admisiones y Matrículas", "Secretaría Académica", "PERSONAL_ADMINISTRATIVO", "admisiones.1", "F"),
                new CargoDef("Auxiliar de Registro y Certificados", "Secretaría Académica", "PERSONAL_ADMINISTRATIVO", "certificados.1", "M"),
                new CargoDef("Psicoorientadora Escolar (Mañana)", "Bienestar Estudiantil", "PERSONAL_ADMINISTRATIVO", "psicoorientacion.manana", "F"),
                new CargoDef("Psicoorientador Escolar (Tarde)", "Bienestar Estudiantil", "PERSONAL_ADMINISTRATIVO", "psicoorientacion.tarde", "M"),
                new CargoDef("Coordinador de Sistemas y BI", "Tecnología e Información", "ADMIN", "sistemas.bi", "M"),
                new CargoDef("Auxiliar de Soporte Técnico", "Tecnología e Información", "PERSONAL_ADMINISTRATIVO", "soporte.ti", "M"),
                new CargoDef("Tesorera Institucional", "Gestión Financiera", "PERSONAL_ADMINISTRATIVO", "tesoreria", "F"),
                new CargoDef("Contador Institucional", "Gestión Financiera", "PERSONAL_ADMINISTRATIVO", "contabilidad", "M"),
                new CargoDef("Bibliotecóloga Escolar", "Recursos Educativos", "PERSONAL_ADMINISTRATIVO", "biblioteca", "F"),
                new CargoDef("Enfermera Escolar", "Bienestar Estudiantil", "PERSONAL_ADMINISTRATIVO", "enfermeria", "F"),
                new CargoDef("Coordinador Ambiental PRAE", "Gestión Ambiental", "PERSONAL_ADMINISTRATIVO", "coordinacion.prae", "M")
        );

        List<Object[]> batchPersonal = new ArrayList<>();
        List<Object[]> batchUsuarios = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        for (int i = 0; i < definiciones.size(); i++) {
            CargoDef def = definiciones.get(i);
            String doc = String.valueOf(70001001 + i);
            boolean esMujer = "F".equals(def.genero());
            String nombres = generarNombres(rng, esMujer);
            String apellidos = generarApellidos(rng);
            String generoTexto = esMujer ? "Femenino" : "Masculino";
            String telefono = generarTelefono(rng);
            LocalDate nacimiento = LocalDate.of(1972 + rng.nextInt(22), 1 + rng.nextInt(12), 1 + rng.nextInt(28));
            String direccion = generarDireccion(rng);
            String email = def.emailAlias() + "@ieaci.edu.co";

            batchPersonal.add(new Object[]{
                    UUID.randomUUID(), nombres, apellidos, "CC", doc, def.cargo(), def.area(),
                    generoTexto, telefono, Date.valueOf(nacimiento), direccion, "Activo", now, now
            });
            batchUsuarios.add(new Object[]{
                    UUID.randomUUID(), email, passwordHash, def.rol(), doc, "Activo", now
            });
        }

        jdbc.batchUpdate(
                "INSERT INTO personal_administrativo (id, nombres, apellidos, tipo_documento, numero_documento, " +
                "cargo, area, genero, telefono, fecha_nacimiento, direccion, estado, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (numero_documento) DO NOTHING",
                batchPersonal);

        jdbc.batchUpdate(
                "INSERT INTO usuarios (id, email, password, rol, numero_documento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchUsuarios);

        List<UUID> ids = jdbc.queryForList("SELECT id FROM personal_administrativo ORDER BY numero_documento", UUID.class);
        log.info("  [+] Personal directivo y administrativo total en BD: {}", ids.size());
        return ids;
    }

    private List<DocenteSeed> seedDocentes(String passwordHash, Map<String, UUID> materiasPorNombre, Random rng) {
        record PerfilDocente(String materiaPrincipal, String materiaSecundaria, String especialidad, String titulo) {}
        List<PerfilDocente> perfiles = List.of(
                new PerfilDocente("Matemáticas", "Tecnología e Informática", "Matemáticas Aplicadas y Estadística", "Licenciado en Matemáticas"),
                new PerfilDocente("Matemáticas", "Ciencias Naturales", "Didáctica de las Matemáticas", "Magíster en Educación Matemática"),
                new PerfilDocente("Ciencias Naturales", "Matemáticas", "Biología y Química Escolar", "Licenciado en Biología y Química"),
                new PerfilDocente("Ciencias Naturales", "Ética y Valores", "Ciencias Ambientales y PRAE", "Magíster en Ciencias Ambientales"),
                new PerfilDocente("Español y Literatura", "Filosofía", "Lengua Castellana y Comprensión Lectora", "Licenciado en Lengua Castellana"),
                new PerfilDocente("Español y Literatura", "Educación Artística", "Literatura Hispanoamericana", "Especialista en Pedagogía de la Lengua"),
                new PerfilDocente("Ciencias Sociales", "Ética y Valores", "Historia, Geografía y Constitución", "Licenciado en Ciencias Sociales"),
                new PerfilDocente("Ciencias Sociales", "Filosofía", "Democracia y Ciudadanía", "Magíster en Historia y Educación"),
                new PerfilDocente("Inglés Técnico", "Español y Literatura", "Bilingüismo y Lenguas Extranjeras", "Licenciado en Lenguas Extranjeras (Inglés)"),
                new PerfilDocente("Tecnología e Informática", "Matemáticas", "Sistemas, Programación y Robótica", "Ingeniero de Sistemas / Especialista en TIC"),
                new PerfilDocente("Educación Física", "Ética y Valores", "Educación Física, Recreación y Deportes", "Licenciado en Educación Física"),
                new PerfilDocente("Educación Artística", "Español y Literatura", "Artes Plásticas, Música y Teatro", "Licenciado en Educación Artística"),
                new PerfilDocente("Ética y Valores", "Ciencias Sociales", "Ética, Valores y Convivencia Escolar", "Especialista en Orientación Educativa"),
                new PerfilDocente("Filosofía", "Ciencias Sociales", "Filosofía y Pensamiento Crítico", "Licenciado en Filosofía y Humanidades")
        );

        int totalDocentes = 55;
        List<Object[]> batchDocentes = new ArrayList<>();
        List<Object[]> batchUsuarios = new ArrayList<>();
        Map<String, PerfilDocente> perfilPorDoc = new LinkedHashMap<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        for (int i = 0; i < totalDocentes; i++) {
            PerfilDocente perfil = perfiles.get(i % perfiles.size());
            String doc = String.valueOf(71001001 + i);
            boolean esMujer = (i % 2 == 1);
            String nombres = generarNombres(rng, esMujer);
            String apellidos = generarApellidos(rng);
            String generoTexto = esMujer ? "Femenino" : "Masculino";
            String telefono = generarTelefono(rng);
            LocalDate nacimiento = LocalDate.of(1970 + rng.nextInt(28), 1 + rng.nextInt(12), 1 + rng.nextInt(28));
            String direccion = generarDireccion(rng);
            String email = String.format("docente.%02d.%s@ieaci.edu.co",
                    i + 1, normalizarSlug(apellidos.split(" ")[0]));

            batchDocentes.add(new Object[]{
                    UUID.randomUUID(), nombres, apellidos, "CC", doc, generoTexto, telefono,
                    Date.valueOf(nacimiento), direccion, perfil.especialidad(), perfil.titulo(), "Activo", now, now
            });
            batchUsuarios.add(new Object[]{
                    UUID.randomUUID(), email, passwordHash, "DOCENTE", doc, "Activo", now
            });
            perfilPorDoc.put(doc, perfil);
        }

        jdbc.batchUpdate(
                "INSERT INTO docentes (id, nombres, apellidos, tipo_documento, numero_documento, genero, telefono, " +
                "fecha_nacimiento, direccion, especialidad, titulo, estado, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (numero_documento) DO NOTHING",
                batchDocentes);

        jdbc.batchUpdate(
                "INSERT INTO usuarios (id, email, password, rol, numero_documento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchUsuarios);

        // Cargar todos los docentes con sus IDs reales y asociar docente_materias
        List<DocenteSeed> docentes = new ArrayList<>();
        List<Object[]> batchDocenteMaterias = new ArrayList<>();

        jdbc.query("SELECT id, numero_documento, nombres, apellidos FROM docentes ORDER BY numero_documento", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String doc = rs.getString("numero_documento");
            PerfilDocente p = perfilPorDoc.get(doc);
            UUID matPrincipal = null;
            if (p != null) {
                matPrincipal = materiasPorNombre.get(p.materiaPrincipal());
                UUID matSec = materiasPorNombre.get(p.materiaSecundaria());
                if (matPrincipal != null) {
                    batchDocenteMaterias.add(new Object[]{id, matPrincipal});
                }
                if (matSec != null && !matSec.equals(matPrincipal)) {
                    batchDocenteMaterias.add(new Object[]{id, matSec});
                }
            } else if (!materiasPorNombre.isEmpty()) {
                matPrincipal = materiasPorNombre.values().iterator().next();
                batchDocenteMaterias.add(new Object[]{id, matPrincipal});
            }
            docentes.add(new DocenteSeed(id, doc, matPrincipal));
        });

        jdbc.batchUpdate(
                "INSERT INTO docente_materias (docente_id, materia_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                batchDocenteMaterias);

        log.info("  [+] Docentes totales en BD: {} (con {} asignaciones docente_materias)", docentes.size(), batchDocenteMaterias.size());
        return docentes;
    }

    private List<SalonSeed> seedSalones() {
        List<Object[]> salonesNuevos = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        // Bloque A (Primaria y Bachillerato): AULA-101..110, AULA-201..210, AULA-301..308
        for (int i = 101; i <= 110; i++) {
            salonesNuevos.add(new Object[]{UUID.randomUUID(), "AULA-" + i, "Aula " + i + " - Bloque A", 38, "Bloque A - Piso 1", "AULA", "Activo", now, now});
        }
        for (int i = 201; i <= 210; i++) {
            salonesNuevos.add(new Object[]{UUID.randomUUID(), "AULA-" + i, "Aula " + i + " - Bloque A", 38, "Bloque A - Piso 2", "AULA", "Activo", now, now});
        }
        for (int i = 301; i <= 308; i++) {
            salonesNuevos.add(new Object[]{UUID.randomUUID(), "AULA-" + i, "Aula " + i + " - Bloque C", 38, "Bloque C - Piso 3", "AULA", "Activo", now, now});
        }
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "LAB-FISICA", "Laboratorio de Física y Robótica", 35, "Bloque B - Laboratorios", "LABORATORIO", "Activo", now, now});
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "LAB-QUIMICA", "Laboratorio de Química y Biología", 35, "Bloque B - Laboratorios", "LABORATORIO", "Activo", now, now});
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "SALA-SISTEMAS-2", "Sala de Informática 2", 38, "Bloque B - Tecnología", "SALA_SISTEMAS", "Activo", now, now});
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "SALON-ARTES", "Taller de Artes y Música", 38, "Bloque D - Cultural", "AULA", "Activo", now, now});
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "CANCHA-MULTIPLE", "Escenario Deportivo Principal", 80, "Zona Deportiva", "CANCHA", "Activo", now, now});
        salonesNuevos.add(new Object[]{UUID.randomUUID(), "AUDITORIO-IEACI", "Auditorio Institucional", 180, "Bloque D - Cultural", "AUDITORIO", "Activo", now, now});

        jdbc.batchUpdate(
                "INSERT INTO salones (id, codigo, nombre, capacidad, edificio, tipo, estado, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (codigo) DO NOTHING",
                salonesNuevos);

        List<SalonSeed> salones = jdbc.query(
                "SELECT id, codigo, tipo FROM salones WHERE estado = 'Activo' ORDER BY codigo",
                (rs, rowNum) -> new SalonSeed(rs.getObject("id", UUID.class), rs.getString("codigo"), rs.getString("tipo")));
        log.info("  [+] Salones y espacios totales en BD: {}", salones.size());
        return salones;
    }

    private List<CursoSeed> seedCursos(UUID anioLectivoId, List<DocenteSeed> docentes) {
        List<Object[]> batchCursos = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int idxDoc = 0;

        for (String grado : GRADOS) {
            for (String grupo : List.of("01", "02")) {
                String jornada = "01".equals(grupo) ? "Mañana" : "Tarde";
                UUID directorId = docentes.get(idxDoc % docentes.size()).id();
                idxDoc++;
                batchCursos.add(new Object[]{
                        UUID.randomUUID(), grado, grupo, jornada, 38, directorId, "2026", "Activo", now, anioLectivoId
                });
            }
        }

        jdbc.batchUpdate(
                "INSERT INTO cursos (id, grado, grupo, jornada, cupos_maximos, director_id, ano_lectivo, estado, created_at, anio_lectivo_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (grado, grupo, anio_lectivo_id) DO NOTHING",
                batchCursos);

        // Asegurar director_id y cupo >= 38 en cursos preexistentes que no tenían director
        List<CursoSeed> cursos = jdbc.query(
                "SELECT id, grado, grupo, jornada, director_id FROM cursos WHERE anio_lectivo_id = ? ORDER BY grado, grupo",
                (rs, rowNum) -> new CursoSeed(
                        rs.getObject("id", UUID.class),
                        rs.getString("grado"),
                        rs.getString("grupo"),
                        rs.getString("jornada"),
                        rs.getObject("director_id", UUID.class)),
                anioLectivoId);

        for (int i = 0; i < cursos.size(); i++) {
            CursoSeed c = cursos.get(i);
            if (c.directorId() == null && !docentes.isEmpty()) {
                UUID dir = docentes.get(i % docentes.size()).id();
                jdbc.update("UPDATE cursos SET director_id = ?, cupos_maximos = GREATEST(COALESCE(cupos_maximos, 35), 38) WHERE id = ?", dir, c.id());
                cursos.set(i, new CursoSeed(c.id(), c.grado(), c.grupo(), c.jornada(), dir));
            }
        }

        log.info("  [+] Cursos totales en año lectivo 2026: {}", cursos.size());
        return cursos;
    }

    private List<EstudianteSeed> seedAcudientesEstudiantesYMatriculas(
            UUID anioLectivoId, List<CursoSeed> cursos, String passwordHash, Random rng) {

        int totalAcudientes = 600;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String[] parentescos = {"Madre", "Padre", "Madre", "Padre", "Abuela", "Abuelo", "Tía", "Tío"};

        // 1. Acudientes
        List<Object[]> batchAcudientes = new ArrayList<>(totalAcudientes);
        for (int i = 0; i < totalAcudientes; i++) {
            String parentesco = parentescos[i % parentescos.length];
            boolean esMujer = parentesco.equals("Madre") || parentesco.equals("Abuela") || parentesco.equals("Tía");
            String nombres = generarNombres(rng, esMujer);
            String apellidos = generarApellidos(rng);
            String doc = String.valueOf(72001001 + i);
            String tel = generarTelefono(rng);
            String email = String.format("acudiente.%03d.%s@correo.co", i + 1, normalizarSlug(apellidos.split(" ")[0]));
            batchAcudientes.add(new Object[]{
                    UUID.randomUUID(), nombres, apellidos, parentesco, "CC", doc, tel, email, now, true
            });
        }

        execBatch(
                "INSERT INTO acudientes (id, nombres, apellidos, parentesco, tipo_documento, numero_documento, " +
                "telefono, email, created_at, autoriza_notificaciones) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (numero_documento) DO NOTHING",
                batchAcudientes);

        List<AcudienteSeed> acudientes = jdbc.query(
                "SELECT id, nombres, apellidos, parentesco, numero_documento FROM acudientes ORDER BY numero_documento",
                (rs, rowNum) -> new AcudienteSeed(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombres") + " " + rs.getString("apellidos"),
                        rs.getString("parentesco"),
                        rs.getString("numero_documento")));

        // 2. Estudiantes activos: 30 por cada uno de los 24 cursos = 720 estudiantes nuevos
        int estudiantesPorCurso = 30;
        int totalEstudiantes = cursos.size() * estudiantesPorCurso;

        List<Object[]> batchEstudiantes = new ArrayList<>(totalEstudiantes);
        List<Object[]> batchUsuarios = new ArrayList<>(totalEstudiantes);
        Map<String, CursoSeed> cursoPorDocumento = new LinkedHashMap<>();
        Map<String, AcudienteSeed> acudientePorDocumento = new LinkedHashMap<>();

        int seq = 0;
        for (CursoSeed curso : cursos) {
            int gradoIdx = indiceGrado(curso.grado());
            int edadBase = EDAD_POR_GRADO[Math.max(0, Math.min(gradoIdx, EDAD_POR_GRADO.length - 1))];

            for (int k = 0; k < estudiantesPorCurso; k++) {
                seq++;
                String doc = String.valueOf(1048000000L + seq);
                String codigo = String.format("EST-2026-%04d", seq);
                boolean esMujer = rng.nextBoolean();
                String nombres = generarNombres(rng, esMujer);
                String apellidos = generarApellidos(rng);
                String genero = esMujer ? "Femenino" : "Masculino";
                int edad = edadBase + (rng.nextInt(10) < 2 ? 1 : 0);
                String tipoDoc = edad < 7 ? "RC" : (edad < 18 ? "TI" : "CC");
                LocalDate fechaNac = LocalDate.of(2026 - edad, 1 + rng.nextInt(12), 1 + rng.nextInt(28));
                String tel = generarTelefono(rng);
                String dir = generarDireccion(rng);
                AcudienteSeed acud = acudientes.get((seq - 1) % acudientes.size());
                String email = String.format("est.2026.%04d@ieaci.edu.co", seq);

                batchEstudiantes.add(new Object[]{
                        UUID.randomUUID(), codigo, nombres, apellidos, tipoDoc, doc, genero, tel,
                        Date.valueOf(fechaNac), dir, "Activo", acud.id(), now, now
                });
                batchUsuarios.add(new Object[]{
                        UUID.randomUUID(), email, passwordHash, "ESTUDIANTE", doc, "Activo", now
                });
                cursoPorDocumento.put(doc, curso);
                acudientePorDocumento.put(doc, acud);
            }
        }

        execBatch(
                "INSERT INTO estudiantes (id, codigo, nombres, apellidos, tipo_documento, numero_documento, genero, " +
                "telefono, fecha_nacimiento, direccion, estado, acudiente_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchEstudiantes);

        execBatch(
                "INSERT INTO usuarios (id, email, password, rol, numero_documento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchUsuarios);

        // Cargar todos los estudiantes seeded con su ID real para crear relaciones puente, matrículas y curso_estudiante
        List<EstudianteSeed> seeded = jdbc.query(
                "SELECT id, numero_documento, codigo, nombres, apellidos FROM estudiantes " +
                "WHERE numero_documento LIKE '1048000%' AND estado = 'Activo' ORDER BY numero_documento",
                (rs, rowNum) -> {
                    String doc = rs.getString("numero_documento");
                    CursoSeed c = cursoPorDocumento.get(doc);
                    if (c == null && !cursos.isEmpty()) {
                        c = cursos.get(rowNum % cursos.size());
                    }
                    return new EstudianteSeed(
                            rs.getObject("id", UUID.class),
                            doc,
                            rs.getString("codigo"),
                            c);
                });

        List<Object[]> batchEstAcud = new ArrayList<>(seeded.size());
        List<Object[]> batchMatriculas = new ArrayList<>(seeded.size());
        List<Object[]> batchCursoEst = new ArrayList<>(seeded.size());

        LocalDate fechaMatBase = LocalDate.of(2026, 1, 15);
        for (int i = 0; i < seeded.size(); i++) {
            EstudianteSeed est = seeded.get(i);
            CursoSeed curso = est.curso();
            AcudienteSeed acud = acudientePorDocumento.get(est.numeroDocumento());
            if (acud == null && !acudientes.isEmpty()) {
                acud = acudientes.get(i % acudientes.size());
            }
            if (curso == null || acud == null) continue;

            LocalDate fechaMat = fechaMatBase.plusDays(i % 20);
            batchEstAcud.add(new Object[]{
                    UUID.randomUUID(), est.id(), acud.id(), true, acud.parentesco(), now
            });
            batchMatriculas.add(new Object[]{
                    UUID.randomUUID(), est.id(), curso.grado(), curso.grupo(), "2026", "APROBADA",
                    Date.valueOf(fechaMat), curso.id(), true, Timestamp.valueOf(fechaMat.atTime(9, 30)),
                    acud.nombreCompleto(), acud.numeroDocumento(), anioLectivoId
            });
            batchCursoEst.add(new Object[]{
                    UUID.randomUUID(), curso.id(), est.id(), "2026", anioLectivoId
            });
        }

        execBatch(
                "INSERT INTO estudiante_acudiente (id, estudiante_id, acudiente_id, es_principal, parentesco, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (estudiante_id, acudiente_id) DO NOTHING",
                batchEstAcud);

        execBatch(
                "INSERT INTO matriculas (id, estudiante_id, grado, salon, ano_lectivo, estado, fecha_matricula, " +
                "curso_id, autoriza_tratamiento_datos, fecha_autorizacion_datos, autorizado_por_nombre, " +
                "autorizado_por_documento, anio_lectivo_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (estudiante_id, anio_lectivo_id) DO UPDATE SET grado = EXCLUDED.grado, salon = EXCLUDED.salon, curso_id = EXCLUDED.curso_id",
                batchMatriculas);

        execBatch(
                "INSERT INTO curso_estudiante (id, curso_id, estudiante_id, ano_lectivo, anio_lectivo_id) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (estudiante_id, anio_lectivo_id) DO UPDATE SET curso_id = EXCLUDED.curso_id",
                batchCursoEst);

        Integer totalEstEnBd = jdbc.queryForObject("SELECT count(*) FROM estudiantes", Integer.class);
        log.info("  [+] Estudiantes totales en BD: {} | Acudientes: {}", totalEstEnBd, acudientes.size());
        return seeded;
    }

    private void seedEstudiantesPendientes(UUID anioLectivoId, String passwordHash, Random rng) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
        List<AcudienteSeed> acudientes = jdbc.query(
                "SELECT id, nombres, apellidos, parentesco, numero_documento FROM acudientes ORDER BY numero_documento LIMIT 20",
                (rs, rowNum) -> new AcudienteSeed(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombres") + " " + rs.getString("apellidos"),
                        rs.getString("parentesco"),
                        rs.getString("numero_documento")));
        if (acudientes.isEmpty()) return;

        List<Object[]> batchEst = new ArrayList<>();
        List<Object[]> batchUsr = new ArrayList<>();
        Map<String, String> gradoPorDoc = new LinkedHashMap<>();
        Map<String, AcudienteSeed> acudPorDoc = new LinkedHashMap<>();

        for (int i = 1; i <= 15; i++) {
            String doc = String.valueOf(1048000900L + i);
            String codigo = String.format("EST-2026-P%03d", i);
            boolean esMujer = rng.nextBoolean();
            String grado = GRADOS[i % GRADOS.length];
            int edad = EDAD_POR_GRADO[i % EDAD_POR_GRADO.length];
            AcudienteSeed acud = acudientes.get(i % acudientes.size());

            batchEst.add(new Object[]{
                    UUID.randomUUID(), codigo, generarNombres(rng, esMujer), generarApellidos(rng),
                    edad < 7 ? "RC" : "TI", doc, esMujer ? "Femenino" : "Masculino", generarTelefono(rng),
                    Date.valueOf(LocalDate.of(2026 - edad, 1 + rng.nextInt(12), 1 + rng.nextInt(28))),
                    generarDireccion(rng), "Pendiente", acud.id(), now, now
            });
            batchUsr.add(new Object[]{
                    UUID.randomUUID(), String.format("aspirante.2026.%03d@ieaci.edu.co", i),
                    passwordHash, "ESTUDIANTE", doc, "Activo", now
            });
            gradoPorDoc.put(doc, grado);
            acudPorDoc.put(doc, acud);
        }

        execBatch(
                "INSERT INTO estudiantes (id, codigo, nombres, apellidos, tipo_documento, numero_documento, genero, " +
                "telefono, fecha_nacimiento, direccion, estado, acudiente_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchEst);
        execBatch(
                "INSERT INTO usuarios (id, email, password, rol, numero_documento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                batchUsr);

        List<Object[]> batchMat = new ArrayList<>();
        jdbc.query("SELECT id, numero_documento FROM estudiantes WHERE numero_documento LIKE '10480009%'", rs -> {
            UUID estId = rs.getObject("id", UUID.class);
            String doc = rs.getString("numero_documento");
            AcudienteSeed acud = acudPorDoc.get(doc);
            String grado = gradoPorDoc.getOrDefault(doc, "6°");
            if (acud != null) {
                batchMat.add(new Object[]{
                        UUID.randomUUID(), estId, grado, "01", "2026", "PENDIENTE_DE_REVISION",
                        Date.valueOf(LocalDate.of(2026, 11, 10)), null, true, now,
                        acud.nombreCompleto(), acud.numeroDocumento(), anioLectivoId
                });
            }
        });

        execBatch(
                "INSERT INTO matriculas (id, estudiante_id, grado, salon, ano_lectivo, estado, fecha_matricula, " +
                "curso_id, autoriza_tratamiento_datos, fecha_autorizacion_datos, autorizado_por_nombre, " +
                "autorizado_por_documento, anio_lectivo_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (estudiante_id, anio_lectivo_id) DO UPDATE SET grado = EXCLUDED.grado",
                batchMat);
        jdbc.update("UPDATE estudiantes SET created_at = ? WHERE numero_documento LIKE '10480009%'", now);
        log.info("  [+] Matrículas en estado PENDIENTE_DE_REVISION aseguradas: {}", batchMat.size());
    }

    private void sincronizarCorreosInstitucionalesReales() {
        record PersonaCorreo(String doc, String nombres, String apellidos) {}
        List<PersonaCorreo> personas = new ArrayList<>();

        jdbc.query("SELECT numero_documento, nombres, apellidos FROM personal_administrativo ORDER BY numero_documento", rs -> {
            personas.add(new PersonaCorreo(rs.getString("numero_documento"), rs.getString("nombres"), rs.getString("apellidos")));
        });
        jdbc.query("SELECT numero_documento, nombres, apellidos FROM docentes ORDER BY numero_documento", rs -> {
            personas.add(new PersonaCorreo(rs.getString("numero_documento"), rs.getString("nombres"), rs.getString("apellidos")));
        });
        jdbc.query("SELECT numero_documento, nombres, apellidos FROM estudiantes ORDER BY numero_documento", rs -> {
            personas.add(new PersonaCorreo(rs.getString("numero_documento"), rs.getString("nombres"), rs.getString("apellidos")));
        });

        Set<String> usados = new HashSet<>();
        usados.add("admin@ieaci.edu.co");
        jdbc.query("SELECT email, numero_documento FROM usuarios", rs -> {
            String em = rs.getString("email");
            String doc = rs.getString("numero_documento");
            if (em != null && (doc == null || (!doc.startsWith("70001") && !doc.startsWith("71001") && !doc.startsWith("1048000")))) {
                usados.add(em.toLowerCase(Locale.ROOT));
            }
        });

        List<Object[]> updates = new ArrayList<>();
        for (PersonaCorreo p : personas) {
            String email = generarEmailRealista(p.nombres(), p.apellidos(), usados);
            updates.add(new Object[]{email, p.doc()});
        }

        execBatch("UPDATE usuarios SET email = ? WHERE numero_documento = ?", updates);
        log.info("  [+] Correos institucionales reales sincronizados para {} usuarios", updates.size());
    }

    private String generarEmailRealista(String nombres, String apellidos, Set<String> usados) {
        String[] nParts = (nombres != null && !nombres.isBlank()) ? nombres.trim().split("\\s+") : new String[]{"usuario"};
        String[] aParts = (apellidos != null && !apellidos.isBlank()) ? apellidos.trim().split("\\s+") : new String[]{"ieaci"};

        String n1 = normalizarSlug(nParts[0]);
        String n2 = nParts.length > 1 ? normalizarSlug(nParts[1]) : "";
        String a1 = normalizarSlug(aParts[0]);
        String a2 = aParts.length > 1 ? normalizarSlug(aParts[1]) : "";

        List<String> candidatos = new ArrayList<>();
        candidatos.add(n1 + "." + a1);
        if (!a2.isEmpty()) {
            candidatos.add(n1 + "." + a1 + "." + a2.charAt(0));
            candidatos.add(n1 + "." + a1 + a2);
        }
        if (!n2.isEmpty()) {
            candidatos.add(n1 + "." + n2.charAt(0) + "." + a1);
            candidatos.add(n2 + "." + a1);
        }
        if (!n2.isEmpty() && !a2.isEmpty()) {
            candidatos.add(n2 + "." + a1 + "." + a2.charAt(0));
            candidatos.add(n1.charAt(0) + "." + n2 + "." + a1);
        }

        String domain = "@ieaci.edu.co";
        for (String base : candidatos) {
            String cand = base + domain;
            if (usados.add(cand)) {
                return cand;
            }
        }

        int counter = 2;
        while (true) {
            String cand = n1 + "." + a1 + counter + domain;
            if (usados.add(cand)) {
                return cand;
            }
            counter++;
        }
    }

    private List<CursoMateriaSeed> seedCursosMaterias(
            UUID anioLectivoId, List<CursoSeed> cursos, Map<String, UUID> materiasPorNombre, List<DocenteSeed> docentes) {

        // Agrupar docentes por materia principal
        Map<UUID, List<UUID>> docentesPorMateria = new HashMap<>();
        for (DocenteSeed d : docentes) {
            if (d.materiaPrincipalId() != null) {
                docentesPorMateria.computeIfAbsent(d.materiaPrincipalId(), k -> new ArrayList<>()).add(d.id());
            }
        }

        // Cargar plan_estudios_grado (materias activas por grado)
        Map<String, List<UUID>> materiasPorGrado = new HashMap<>();
        jdbc.query("SELECT grado, materia_id FROM plan_estudios_grado WHERE intensidad_horaria > 0 ORDER BY grado", rs -> {
            materiasPorGrado.computeIfAbsent(rs.getString("grado"), k -> new ArrayList<>())
                    .add(rs.getObject("materia_id", UUID.class));
        });

        List<UUID> materiasTransicion = List.of("Matemáticas", "Español y Literatura", "Ciencias Naturales",
                        "Educación Artística", "Educación Física", "Ética y Valores")
                .stream().map(materiasPorNombre::get).filter(Objects::nonNull).toList();

        List<Object[]> batch = new ArrayList<>();
        Map<UUID, Integer> rrIndex = new HashMap<>();

        for (CursoSeed curso : cursos) {
            List<UUID> materias = materiasPorGrado.getOrDefault(curso.grado(), materiasTransicion);
            if (materias.isEmpty()) {
                materias = new ArrayList<>(materiasPorNombre.values());
            }
            for (UUID matId : materias) {
                List<UUID> especialistas = docentesPorMateria.getOrDefault(matId, List.of());
                UUID docenteId;
                if (!especialistas.isEmpty()) {
                    int idx = rrIndex.merge(matId, 1, Integer::sum);
                    docenteId = especialistas.get(idx % especialistas.size());
                } else {
                    docenteId = docentes.get(Math.abs(matId.hashCode()) % docentes.size()).id();
                }
                batch.add(new Object[]{UUID.randomUUID(), curso.id(), matId, docenteId, "2026", anioLectivoId});
            }
        }

        execBatch(
                "INSERT INTO curso_materia (id, curso_id, materia_id, docente_id, ano_lectivo, anio_lectivo_id) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (curso_id, materia_id, anio_lectivo_id) DO NOTHING",
                batch);

        List<CursoMateriaSeed> result = jdbc.query(
                "SELECT id, curso_id, materia_id, docente_id FROM curso_materia WHERE anio_lectivo_id = ?",
                (rs, rowNum) -> new CursoMateriaSeed(
                        rs.getObject("id", UUID.class),
                        rs.getObject("curso_id", UUID.class),
                        rs.getObject("materia_id", UUID.class),
                        rs.getObject("docente_id", UUID.class)),
                anioLectivoId);
        log.info("  [+] Asignaciones carga académica (curso_materia): {}", result.size());
        return result;
    }

    private void seedHorarios(List<CursoSeed> cursos, List<CursoMateriaSeed> cursosMaterias, List<SalonSeed> salones) {
        record BloqueSeed(UUID id, int numero, Time inicio, Time fin, String jornada) {}
        List<BloqueSeed> bloquesManana = jdbc.query(
                "SELECT id, numero, hora_inicio, hora_fin, jornada FROM bloques WHERE tipo = 'CLASE' AND jornada = 'Mañana' ORDER BY numero",
                (rs, r) -> new BloqueSeed(rs.getObject("id", UUID.class), rs.getInt("numero"),
                        rs.getTime("hora_inicio"), rs.getTime("hora_fin"), rs.getString("jornada")));
        List<BloqueSeed> bloquesTarde = jdbc.query(
                "SELECT id, numero, hora_inicio, hora_fin, jornada FROM bloques WHERE tipo = 'CLASE' AND jornada = 'Tarde' ORDER BY numero",
                (rs, r) -> new BloqueSeed(rs.getObject("id", UUID.class), rs.getInt("numero"),
                        rs.getTime("hora_inicio"), rs.getTime("hora_fin"), rs.getString("jornada")));

        if (bloquesManana.isEmpty() || salones.isEmpty()) return;

        // Detectar cursos que ya tienen horarios para no duplicarlos
        Set<UUID> cursosConHorario = new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT curso_id FROM horarios", UUID.class));

        Map<UUID, List<CursoMateriaSeed>> cmPorCurso = new HashMap<>();
        for (CursoMateriaSeed cm : cursosMaterias) {
            cmPorCurso.computeIfAbsent(cm.cursoId(), k -> new ArrayList<>()).add(cm);
        }

        // Control de ocupación (dia + bloqueId) para evitar choques de docente o salón
        Set<String> docenteOcupado = new HashSet<>();
        Set<String> salonOcupado = new HashSet<>();
        jdbc.query("SELECT dia_semana, bloque_id, docente_id, salon_id FROM horarios WHERE bloque_id IS NOT NULL", rs -> {
            String dia = rs.getString("dia_semana");
            UUID bId = rs.getObject("bloque_id", UUID.class);
            UUID dId = rs.getObject("docente_id", UUID.class);
            UUID sId = rs.getObject("salon_id", UUID.class);
            if (dId != null) docenteOcupado.add(dia + "|" + bId + "|" + dId);
            if (sId != null) salonOcupado.add(dia + "|" + bId + "|" + sId);
        });

        List<SalonSeed> aulas = salones.stream().filter(s -> "AULA".equals(s.tipo())).toList();
        if (aulas.isEmpty()) aulas = salones;

        List<Object[]> batchHorarios = new ArrayList<>();
        int salonBaseIdx = 0;

        for (CursoSeed curso : cursos) {
            if (cursosConHorario.contains(curso.id())) continue;
            List<CursoMateriaSeed> cms = cmPorCurso.getOrDefault(curso.id(), List.of());
            if (cms.isEmpty()) continue;

            List<BloqueSeed> bloques = "Tarde".equalsIgnoreCase(curso.jornada()) && !bloquesTarde.isEmpty()
                    ? bloquesTarde : bloquesManana;
            SalonSeed aulaPrincipal = aulas.get(salonBaseIdx % aulas.size());
            salonBaseIdx++;

            int cmIdx = 0;
            for (String dia : DIAS_SEMANA) {
                for (BloqueSeed bloque : bloques) {
                    if (cmIdx >= cms.size() * 2) break;
                    // Buscar materia cuyo docente esté libre en este bloque
                    CursoMateriaSeed elegida = null;
                    for (int intento = 0; intento < cms.size(); intento++) {
                        CursoMateriaSeed cand = cms.get((cmIdx + intento) % cms.size());
                        String keyDoc = dia + "|" + bloque.id() + "|" + cand.docenteId();
                        if (cand.docenteId() == null || !docenteOcupado.contains(keyDoc)) {
                            elegida = cand;
                            cmIdx = cmIdx + intento + 1;
                            break;
                        }
                    }
                    if (elegida == null) continue;

                    SalonSeed salonElegido = aulaPrincipal;
                    String keySal = dia + "|" + bloque.id() + "|" + salonElegido.id();
                    if (salonOcupado.contains(keySal)) {
                        salonElegido = salones.stream()
                                .filter(s -> !salonOcupado.contains(dia + "|" + bloque.id() + "|" + s.id()))
                                .findFirst().orElse(aulaPrincipal);
                    }

                    if (elegida.docenteId() != null) {
                        docenteOcupado.add(dia + "|" + bloque.id() + "|" + elegida.docenteId());
                    }
                    salonOcupado.add(dia + "|" + bloque.id() + "|" + salonElegido.id());

                    batchHorarios.add(new Object[]{
                            UUID.randomUUID(), curso.id(), elegida.materiaId(), dia,
                            bloque.inicio(), bloque.fin(), salonElegido.codigo(),
                            elegida.docenteId(), salonElegido.id(), bloque.id()
                    });
                }
            }
        }

        execBatch(
                "INSERT INTO horarios (id, curso_id, materia_id, dia_semana, hora_inicio, hora_fin, salon, docente_id, salon_id, bloque_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchHorarios);

        Integer totalHorarios = jdbc.queryForObject("SELECT count(*) FROM horarios", Integer.class);
        log.info("  [+] Bloques de horario totales en BD: {}", totalHorarios);
    }

    private void seedEvaluacionesYCalificaciones(List<CursoMateriaSeed> cursosMaterias, List<CursoSeed> cursos, Random rng) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        LocalDate[][] fechasEval = {
                {LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 24), LocalDate.of(2026, 6, 4)},
                {LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 21), LocalDate.of(2026, 9, 10)},
                {LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 18)}
        };
        String[] nombresEval = {"Taller y Seguimiento", "Quiz y Trabajo Práctico", "Evaluación de Período"};
        BigDecimal[] pesosEval = {new BigDecimal("30.00"), new BigDecimal("30.00"), new BigDecimal("40.00")};

        // Detectar (curso_materia_id, periodo) que ya tienen evaluaciones
        Set<String> yaTieneEval = new HashSet<>();
        jdbc.query("SELECT DISTINCT curso_materia_id, periodo FROM evaluaciones", rs -> {
            yaTieneEval.add(rs.getObject("curso_materia_id", UUID.class) + "|" + rs.getInt("periodo"));
        });

        List<Object[]> batchEvals = new ArrayList<>();
        for (CursoMateriaSeed cm : cursosMaterias) {
            for (int periodo = 1; periodo <= 3; periodo++) {
                if (yaTieneEval.contains(cm.id() + "|" + periodo)) continue;
                for (int e = 0; e < 3; e++) {
                    batchEvals.add(new Object[]{
                            UUID.randomUUID(), cm.id(), nombresEval[e], periodo,
                            pesosEval[e], Date.valueOf(fechasEval[periodo - 1][e]), now
                    });
                }
            }
        }

        execBatch(
                "INSERT INTO evaluaciones (id, curso_materia_id, nombre, periodo, peso, fecha, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchEvals);

        // Cargar estudiantes por curso y crear un perfil académico base determinista por estudiante
        Map<UUID, List<UUID>> estudiantesPorCurso = new HashMap<>();
        Map<UUID, Double> perfilAcademico = new HashMap<>();
        jdbc.query("SELECT curso_id, estudiante_id FROM curso_estudiante", rs -> {
            UUID cId = rs.getObject("curso_id", UUID.class);
            UUID eId = rs.getObject("estudiante_id", UUID.class);
            estudiantesPorCurso.computeIfAbsent(cId, k -> new ArrayList<>()).add(eId);
            perfilAcademico.computeIfAbsent(eId, k -> {
                double p = rng.nextDouble();
                if (p < 0.14) return 2.1 + rng.nextDouble() * 0.75;      // Bajo (1.8 - 2.9)
                if (p < 0.48) return 3.1 + rng.nextDouble() * 0.75;      // Básico (3.0 - 3.9)
                if (p < 0.84) return 4.0 + rng.nextDouble() * 0.50;      // Alto (4.0 - 4.5)
                return 4.6 + rng.nextDouble() * 0.35;                    // Superior (4.6 - 5.0)
            });
        });

        // Cargar todas las evaluaciones con su curso_id
        record EvalInfo(UUID evalId, UUID cursoId) {}
        List<EvalInfo> evaluaciones = jdbc.query(
                "SELECT e.id, cm.curso_id FROM evaluaciones e INNER JOIN curso_materia cm ON cm.id = e.curso_materia_id",
                (rs, rowNum) -> new EvalInfo(rs.getObject("id", UUID.class), rs.getObject("curso_id", UUID.class)));

        List<Object[]> batchCalif = new ArrayList<>(BATCH_SIZE);
        int totalEncoladas = 0;

        for (EvalInfo ev : evaluaciones) {
            List<UUID> ests = estudiantesPorCurso.getOrDefault(ev.cursoId(), List.of());
            for (UUID estId : ests) {
                double base = perfilAcademico.getOrDefault(estId, 3.8);
                double variacion = (rng.nextGaussian() * 0.35);
                double notaRaw = Math.max(1.2, Math.min(5.0, base + variacion));
                BigDecimal nota = BigDecimal.valueOf(notaRaw).setScale(2, RoundingMode.HALF_UP);
                batchCalif.add(new Object[]{UUID.randomUUID(), ev.evalId(), estId, nota, null, now, now});
                totalEncoladas++;

                if (batchCalif.size() >= BATCH_SIZE) {
                    jdbc.batchUpdate(
                            "INSERT INTO calificaciones (id, evaluacion_id, estudiante_id, nota, observaciones, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (evaluacion_id, estudiante_id) DO NOTHING",
                            batchCalif);
                    batchCalif.clear();
                }
            }
        }
        if (!batchCalif.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO calificaciones (id, evaluacion_id, estudiante_id, nota, observaciones, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (evaluacion_id, estudiante_id) DO NOTHING",
                    batchCalif);
        }

        Integer totalCalif = jdbc.queryForObject("SELECT count(*) FROM calificaciones", Integer.class);
        log.info("  [+] Evaluaciones: {} | Calificaciones totales en BD: {} (procesadas {})", evaluaciones.size(), totalCalif, totalEncoladas);
    }

    private void seedSesionesYAsistencias(List<CursoSeed> cursos, List<CursoMateriaSeed> cursosMaterias, Random rng) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // 9 días hábiles reales distribuidos en los 3 periodos académicos de 2026 (Lunes-Viernes)
        LocalDate[] fechasHabiles = {
                LocalDate.of(2026, 2, 18), LocalDate.of(2026, 4, 8), LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 7, 22), LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 11)
        };

        Map<UUID, List<CursoMateriaSeed>> cmPorCurso = new HashMap<>();
        for (CursoMateriaSeed cm : cursosMaterias) {
            cmPorCurso.computeIfAbsent(cm.cursoId(), k -> new ArrayList<>()).add(cm);
        }

        List<Object[]> batchSesiones = new ArrayList<>();
        for (CursoSeed curso : cursos) {
            List<CursoMateriaSeed> cms = cmPorCurso.getOrDefault(curso.id(), List.of());
            if (cms.isEmpty()) continue;
            LocalTime hIni = "Tarde".equalsIgnoreCase(curso.jornada()) ? LocalTime.of(13, 0) : LocalTime.of(7, 0);
            LocalTime hFin = "Tarde".equalsIgnoreCase(curso.jornada()) ? LocalTime.of(14, 0) : LocalTime.of(8, 0);

            for (int i = 0; i < fechasHabiles.length; i++) {
                CursoMateriaSeed cm = cms.get(i % cms.size());
                batchSesiones.add(new Object[]{
                        UUID.randomUUID(), curso.id(), cm.id(), null, cm.docenteId(),
                        Date.valueOf(fechasHabiles[i]), Time.valueOf(hIni), Time.valueOf(hFin),
                        "DICTADA", "ASIGNATURA", "Sesión académica de seguimiento " + (i + 1), now, now
                });
            }
        }

        execBatch(
                "INSERT INTO sesiones_clase (id, curso_id, curso_materia_id, horario_id, docente_id, fecha, hora_inicio, " +
                "hora_fin, estado, tipo, tema, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (curso_id, fecha, hora_inicio) DO NOTHING",
                batchSesiones);

        // Poblar asistencias para sesiones
        Map<UUID, List<UUID>> estudiantesPorCurso = new HashMap<>();
        jdbc.query("SELECT curso_id, estudiante_id FROM curso_estudiante", rs -> {
            estudiantesPorCurso.computeIfAbsent(rs.getObject("curso_id", UUID.class), k -> new ArrayList<>())
                    .add(rs.getObject("estudiante_id", UUID.class));
        });

        record SesionInfo(UUID sesionId, UUID cursoId) {}
        List<SesionInfo> sesiones = jdbc.query(
                "SELECT id, curso_id FROM sesiones_clase",
                (rs, rowNum) -> new SesionInfo(rs.getObject("id", UUID.class), rs.getObject("curso_id", UUID.class)));

        List<Object[]> batchAsist = new ArrayList<>(BATCH_SIZE);
        for (SesionInfo s : sesiones) {
            List<UUID> ests = estudiantesPorCurso.getOrDefault(s.cursoId(), List.of());
            for (UUID estId : ests) {
                double p = rng.nextDouble();
                String estado;
                String obs = null;
                if (p < 0.86) {
                    estado = "PRESENTE";
                } else if (p < 0.91) {
                    estado = "TARDE";
                    obs = "Llegada posterior al inicio del bloque";
                } else if (p < 0.95) {
                    estado = "EXCUSADO";
                    obs = "Excusa médica / calamidad registrada";
                } else {
                    estado = "AUSENTE";
                    obs = "Inasistencia sin justificación previa";
                }
                batchAsist.add(new Object[]{UUID.randomUUID(), s.sesionId(), estId, estado, obs, now, now});

                if (batchAsist.size() >= BATCH_SIZE) {
                    jdbc.batchUpdate(
                            "INSERT INTO asistencias (id, sesion_id, estudiante_id, estado, observaciones, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (sesion_id, estudiante_id) DO NOTHING",
                            batchAsist);
                    batchAsist.clear();
                }
            }
        }
        if (!batchAsist.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO asistencias (id, sesion_id, estudiante_id, estado, observaciones, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (sesion_id, estudiante_id) DO NOTHING",
                    batchAsist);
        }

        Integer totalAsist = jdbc.queryForObject("SELECT count(*) FROM asistencias", Integer.class);
        log.info("  [+] Sesiones de clase: {} | Registros de asistencia totales en BD: {}", sesiones.size(), totalAsist);
    }

    private void seedReportes(List<EstudianteSeed> estudiantes, List<DocenteSeed> docentes, List<UUID> personalIds, Random rng) {
        if (estudiantes.isEmpty() || docentes.isEmpty()) return;

        record CasoReporte(String categoria, String razon, String detalle) {}
        List<CasoReporte> casos = List.of(
                new CasoReporte("Convivencia", "Uso reiterado de celular en clase", "Se realiza llamado de atención formativo por distracción durante la explicación."),
                new CasoReporte("Convivencia", "Conflicto verbal con compañero", "Se media la situación en aula y se remite a coordinación de convivencia para seguimiento."),
                new CasoReporte("Convivencia", "Incumplimiento del manual de uniforme", "Presentación incompleta del uniforme de diario en dos jornadas consecutivas."),
                new CasoReporte("Rendimiento Académico", "Bajo desempeño en evaluaciones del período", "Se recomienda plan de refuerzo y acompañamiento con acudiente antes del cierre."),
                new CasoReporte("Rendimiento Académico", "Incumplimiento en entrega de talleres", "No presenta actividades asignadas en las últimas dos semanas del período."),
                new CasoReporte("Asistencia", "Llegadas tarde reiteradas", "Acumula tres ingresos tardíos durante el mes sin soporte de acudiente."),
                new CasoReporte("Asistencia", "Inasistencias sin justificación", "Registra ausencias consecutivas; se solicita comunicación con familia."),
                new CasoReporte("Otras Razones", "Solicitud de orientación psicosocial", "Docente sugiere valoración preventiva por parte de psicoorientación escolar.")
        );

        LocalDate[] fechasBase = {
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 22),
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 11, 5)
        };

        List<Object[]> batch = new ArrayList<>();
        for (int i = 1; i <= 180; i++) {
            String codigo = String.format("REP-SEED-2026-%04d", i);
            EstudianteSeed est = estudiantes.get(rng.nextInt(estudiantes.size()));
            DocenteSeed doc = docentes.get(rng.nextInt(docentes.size()));
            CasoReporte caso = casos.get(i % casos.size());

            double r = rng.nextDouble();
            String estado = r < 0.35 ? "Pendiente" : (r < 0.85 ? "Aceptado" : "Rechazado");
            boolean atendido = !"Pendiente".equals(estado);
            UUID atendidoPor = (atendido && !personalIds.isEmpty()) ? personalIds.get(i % personalIds.size()) : null;
            boolean requiereAcud = "Aceptado".equals(estado) && (i % 3 == 0);
            LocalDateTime createdAt = fechasBase[i % fechasBase.length].plusDays(i % 10).atTime(8 + (i % 8), 15);
            Timestamp fechaCitacion = requiereAcud ? Timestamp.valueOf(createdAt.plusDays(4)) : null;
            String obsAdmin = atendido ? "Seguimiento registrado y documentado por coordinación institucional." : null;

            batch.add(new Object[]{
                    UUID.randomUUID(), codigo, est.id(), doc.id(), caso.categoria(), caso.razon(),
                    caso.razon(), caso.detalle(), estado, fechaCitacion, requiereAcud, obsAdmin,
                    atendidoPor, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                    "DOCENTE", "ESTUDIANTE", null, null
            });
        }

        execBatch(
                "INSERT INTO reportes (id, codigo, estudiante_id, docente_id, categoria, razon, descripcion_razon, " +
                "detalles, estado, fecha_citacion, requiere_acudiente, observaciones_admin, atendido_por, " +
                "created_at, updated_at, reportante_tipo, objetivo_tipo, reportante_usuario_id, reportante_estudiante_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (codigo) DO NOTHING",
                batch);

        Integer totalRep = jdbc.queryForObject("SELECT count(*) FROM reportes", Integer.class);
        log.info("  [+] Reportes totales en BD: {}", totalRep);
    }

    private void seedSolicitudesCertificado(List<EstudianteSeed> estudiantes, List<UUID> personalIds, Random rng) {
        if (estudiantes.isEmpty()) return;

        String[][] tipos = {
                {"Certificado de estudios", "Constancias Administrativas", "Trámite de EPS / afiliación familiar"},
                {"Certificado de notas", "Certificados Académicos", "Postulación a beca o convenio externo"},
                {"Constancia de matrícula", "Constancias Administrativas", "Solicitud de caja de compensación"},
                {"Constancia de buena conducta", "Constancias Administrativas", "Trámite deportivo y cultural"},
                {"Paz y salvo institucional", "Constancias Administrativas", "Actualización de documentos familiares"}
        };

        List<Object[]> batch = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            String codigo = String.format("CERT-SEED-2026-%04d", i);
            EstudianteSeed est;
            if (i <= 10) {
                est = estudiantes.get((i - 1) % estudiantes.size());
            } else if (i <= 20) {
                est = estudiantes.get(estudiantes.size() - 1 - ((i - 11) % estudiantes.size()));
            } else {
                est = estudiantes.get(rng.nextInt(estudiantes.size()));
            }
            String[] tipo = tipos[i % tipos.length];
            String estado = (i % 3 == 0) ? "Pendiente" : ((i % 5 == 0) ? "En Proceso" : "Resuelto");
            boolean resuelto = "Resuelto".equals(estado);
            UUID respondidoPor = (resuelto && !personalIds.isEmpty()) ? personalIds.get(i % personalIds.size()) : null;
            String respuesta = resuelto ? "Documento expedido y disponible para descarga en Secretaría Académica." : null;
            LocalDateTime createdAt = LocalDateTime.of(2026, 3 + (i % 8), 1 + (i % 25), 9 + (i % 6), 0);
            String gradoRef = est.curso() != null ? est.curso().grado() : "10°";

            batch.add(new Object[]{
                    UUID.randomUUID(), codigo, est.id(), tipo[0], tipo[1], tipo[2],
                    "2026", gradoRef, estado, respuesta, null, respondidoPor,
                    Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt)
            });
        }

        execBatch(
                "INSERT INTO solicitudes_certificado (id, codigo, estudiante_id, tipo, categoria, motivo, ano_lectivo, " +
                "grado_referencia, estado, mensaje_respuesta, archivo_adjunto_key, respondido_por, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (codigo) DO UPDATE SET estudiante_id = EXCLUDED.estudiante_id, grado_referencia = EXCLUDED.grado_referencia",
                batch);

        Integer totalCert = jdbc.queryForObject("SELECT count(*) FROM solicitudes_certificado", Integer.class);
        log.info("  [+] Solicitudes de certificado totales en BD: {}", totalCert);
    }

    private void seedNotificaciones() {
        Integer yaSeeded = jdbc.queryForObject(
                "SELECT count(*) FROM notificaciones WHERE referencia_id LIKE 'SEED-%'", Integer.class);
        if (yaSeeded != null && yaSeeded > 0) return;

        List<UUID> adminUsers = jdbc.queryForList(
                "SELECT id FROM usuarios WHERE rol IN ('ADMIN', 'PERSONAL_ADMINISTRATIVO') LIMIT 10", UUID.class);
        if (adminUsers.isEmpty()) return;

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> batch = new ArrayList<>();
        int idx = 1;
        for (UUID uid : adminUsers) {
            batch.add(new Object[]{
                    UUID.randomUUID(), uid, "MATRICULA_PENDIENTE",
                    "15 matrículas pendientes de revisión",
                    "Existen solicitudes nuevas en espera de asignación de curso y validación documental.",
                    "SEED-MAT-" + idx, "user-plus", "amber", null, now
            });
            batch.add(new Object[]{
                    UUID.randomUUID(), uid, "CERTIFICADO_SOLICITUD",
                    "Nuevas solicitudes de certificados académicos",
                    "Secretaría Académica cuenta con solicitudes recientes por tramitar.",
                    "SEED-CERT-" + idx, "badge-check", "sky", null, now
            });
            batch.add(new Object[]{
                    UUID.randomUUID(), uid, "REPORTE_NUEVO",
                    "Resumen de reportes de convivencia",
                    "Se han registrado novedades de seguimiento académico y convivencial en los 3 períodos.",
                    "SEED-REP-" + idx, "alert-triangle", "rose", null, now
            });
            idx++;
        }

        execBatch(
                "INSERT INTO notificaciones (id, usuario_id, tipo, titulo, mensaje, referencia_id, icono, color, leida_en, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
        log.info("  [+] Notificaciones institucionales generadas: {}", batch.size());
    }

    private void execBatch(String sql, List<Object[]> rows) {
        if (rows.isEmpty()) return;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            jdbc.batchUpdate(sql, rows.subList(i, end));
        }
    }

    private void logResumenConteos(long elapsedSec) {
        log.info("======================================================================");
        log.info("  SIGA-IEACI :: Seeder completado en {} segundos", elapsedSec);
        log.info("  Usuarios: {} | Directivos/Admin: {} | Docentes: {} | Acudientes: {}",
                contar("usuarios"), contar("personal_administrativo"), contar("docentes"), contar("acudientes"));
        log.info("  Estudiantes: {} | Cursos: {} | Salones: {} | Horarios: {}",
                contar("estudiantes"), contar("cursos"), contar("salones"), contar("horarios"));
        log.info("  Evaluaciones: {} | Calificaciones: {} | Sesiones: {} | Asistencias: {}",
                contar("evaluaciones"), contar("calificaciones"), contar("sesiones_clase"), contar("asistencias"));
        log.info("  Reportes: {} | Certificados: {} | Notificaciones: {}",
                contar("reportes"), contar("solicitudes_certificado"), contar("notificaciones"));
        log.info("  Contraseña por defecto de cuentas generadas: {}", DEFAULT_PASSWORD_RAW);
        log.info("======================================================================");
    }

    private int contar(String tabla) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
        return c != null ? c : 0;
    }

    private String generarNombres(Random rng, boolean esMujer) {
        String[] pool = esMujer ? NOMBRES_F : NOMBRES_M;
        String n1 = pool[rng.nextInt(pool.length)];
        String n2 = pool[rng.nextInt(pool.length)];
        return n1.equals(n2) ? n1 : n1 + " " + n2;
    }

    private String generarApellidos(Random rng) {
        String a1 = APELLIDOS[rng.nextInt(APELLIDOS.length)];
        String a2 = APELLIDOS[rng.nextInt(APELLIDOS.length)];
        return a1.equals(a2) ? a1 + " " + APELLIDOS[(rng.nextInt(APELLIDOS.length - 1) + 1) % APELLIDOS.length] : a1 + " " + a2;
    }

    private String generarTelefono(Random rng) {
        int prefijo = 300 + rng.nextInt(24);
        int resto = 1000000 + rng.nextInt(8999999);
        return prefijo + String.valueOf(resto);
    }

    private String generarDireccion(Random rng) {
        String via = rng.nextBoolean() ? "Calle" : "Carrera";
        String barrio = BARRIOS[rng.nextInt(BARRIOS.length)];
        return String.format("%s %d # %d-%d, %s", via, 10 + rng.nextInt(80), 1 + rng.nextInt(60), 1 + rng.nextInt(99), barrio);
    }

    private String normalizarSlug(String texto) {
        if (texto == null) return "usuario";
        return texto.toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("[^a-z0-9]", "");
    }

    private int indiceGrado(String grado) {
        for (int i = 0; i < GRADOS.length; i++) {
            if (GRADOS[i].equalsIgnoreCase(grado)) return i;
        }
        return 6;
    }

    private record DocenteSeed(UUID id, String numeroDocumento, UUID materiaPrincipalId) {}
    private record SalonSeed(UUID id, String codigo, String tipo) {}
    private record CursoSeed(UUID id, String grado, String grupo, String jornada, UUID directorId) {}
    private record AcudienteSeed(UUID id, String nombreCompleto, String parentesco, String numeroDocumento) {}
    private record EstudianteSeed(UUID id, String numeroDocumento, String codigo, CursoSeed curso) {}
    private record CursoMateriaSeed(UUID id, UUID cursoId, UUID materiaId, UUID docenteId) {}
}

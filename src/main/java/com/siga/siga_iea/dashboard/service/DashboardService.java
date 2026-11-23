package com.siga.siga_iea.dashboard.service;

import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.auditoria.entity.AuditoriaCambio;
import com.siga.siga_iea.auditoria.repository.AuditoriaCambioRepository;
import com.siga.siga_iea.calificaciones.dto.BoletinMateriaDTO;
import com.siga.siga_iea.calificaciones.repository.EvaluacionRepository;
import com.siga.siga_iea.calificaciones.service.CalificacionesService;
import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.certificados.repository.SolicitudCertificadoRepository;
import com.siga.siga_iea.clases.application.CursoGestionAppService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.entity.Horario;
import com.siga.siga_iea.clases.enums.GradoAcademico;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.HorarioRepository;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.dashboard.dto.DashboardStatsDTO;
import com.siga.siga_iea.dashboard.dto.SemaforoItemDTO;
import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.repository.MatriculaRepository;
import com.siga.siga_iea.reportes.entity.Reporte;
import com.siga.siga_iea.reportes.repository.ReporteRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    public static final int LIMITE_TIMELINE_DASHBOARD = 5;

    private final EstudianteRepository estudianteRepository;
    private final DocenteRepository docenteRepository;
    private final ReporteRepository reporteRepository;
    private final SolicitudCertificadoRepository certificadoRepository;
    private final MatriculaRepository matriculaRepository;
    private final CursoRepository cursoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EvaluacionRepository evaluacionRepository;
    private final CursoEstudianteRepository cursoEstudianteRepository;
    private final HorarioRepository horarioRepository;
    private final CalificacionesService calificacionesService;
    private final PeriodoConfigService periodoConfigService;
    private final Clock clock;
    private final AuditoriaCambioRepository auditoriaCambioRepository;

    @Autowired
    public DashboardService(EstudianteRepository estudianteRepository,
                            DocenteRepository docenteRepository,
                            ReporteRepository reporteRepository,
                            SolicitudCertificadoRepository certificadoRepository,
                            MatriculaRepository matriculaRepository,
                            CursoRepository cursoRepository,
                            AsistenciaRepository asistenciaRepository,
                            EvaluacionRepository evaluacionRepository,
                            CursoEstudianteRepository cursoEstudianteRepository,
                            @Autowired(required = false) HorarioRepository horarioRepository,
                            @Autowired(required = false) CalificacionesService calificacionesService,
                            @Autowired(required = false) PeriodoConfigService periodoConfigService,
                            @Autowired(required = false) Clock clock,
                            @Autowired(required = false) AuditoriaCambioRepository auditoriaCambioRepository) {
        this.estudianteRepository = estudianteRepository;
        this.docenteRepository = docenteRepository;
        this.reporteRepository = reporteRepository;
        this.certificadoRepository = certificadoRepository;
        this.matriculaRepository = matriculaRepository;
        this.cursoRepository = cursoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.horarioRepository = horarioRepository;
        this.calificacionesService = calificacionesService;
        this.periodoConfigService = periodoConfigService;
        this.clock = clock != null ? clock : Clock.system(ZoneId.of("America/Bogota"));
        this.auditoriaCambioRepository = auditoriaCambioRepository;
    }

    public DashboardService(EstudianteRepository estudianteRepository,
                            DocenteRepository docenteRepository,
                            ReporteRepository reporteRepository,
                            SolicitudCertificadoRepository certificadoRepository,
                            MatriculaRepository matriculaRepository,
                            CursoRepository cursoRepository,
                            AsistenciaRepository asistenciaRepository,
                            EvaluacionRepository evaluacionRepository,
                            CursoEstudianteRepository cursoEstudianteRepository) {
        this(estudianteRepository, docenteRepository, reporteRepository, certificadoRepository,
                matriculaRepository, cursoRepository, asistenciaRepository, evaluacionRepository,
                cursoEstudianteRepository, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO obtenerEstadisticas() {
        long estudiantesCount = estudianteRepository.count();
        long docentesCount = docenteRepository.count();
        long totalReportes = reporteRepository.count();
        long totalMatriculas = matriculaRepository.count();
        long totalCursos = cursoRepository.count();

        List<Reporte> reportesPendientes = reporteRepository.findByEstado("Pendiente");
        List<SolicitudCertificado> certsPendientes = certificadoRepository.findByEstado("Pendiente");

        long alertasCount = reportesPendientes.size() + certsPendientes.size();

        // 1. Asistencia: hoy y últimos 30 días
        LocalDate hoy = LocalDate.now();
        List<Object[]> resDia = asistenciaRepository.findResumenAsistenciaPorDia(hoy);
        String asistenciaHoy = "0%";
        if (resDia != null && !resDia.isEmpty() && resDia.get(0) != null) {
            Object[] row = resDia.get(0);
            long total = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            long presentes = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            if (total > 0) {
                double pct = (double) presentes * 100.0 / total;
                asistenciaHoy = String.format(Locale.US, "%.1f%%", pct);
            }
        }

        LocalDate desde = hoy.minusDays(30);
        List<Object[]> resumenAsistencia = asistenciaRepository.findResumenAsistenciaPorFecha(desde);
        List<String> asistenciaLabels = new ArrayList<>();
        List<Double> asistenciaValores = new ArrayList<>();
        Double promedioAsistencia;

        if (resumenAsistencia != null && !resumenAsistencia.isEmpty()) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");
            double sumaPorcentajes = 0.0;
            for (Object[] row : resumenAsistencia) {
                LocalDate fecha = (LocalDate) row[0];
                long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                long presentes = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                double pct = total > 0 ? (double) presentes * 100.0 / total : 0.0;
                double rounded = Math.round(pct * 10.0) / 10.0;
                asistenciaLabels.add(fecha.format(dtf));
                asistenciaValores.add(rounded);
                sumaPorcentajes += rounded;
            }
            promedioAsistencia = Math.round((sumaPorcentajes / asistenciaValores.size()) * 10.0) / 10.0;
        } else {
            asistenciaLabels.add("Sin datos");
            asistenciaValores.add(0.0);
            promedioAsistencia = 0.0;
        }

        String asistenciaLabelsCsv = String.join(",", asistenciaLabels);
        String asistenciaValoresCsv = asistenciaValores.stream()
                .map(v -> String.format(Locale.US, "%.1f", v))
                .collect(Collectors.joining(","));

        // 2. Distribución de Alumnos (Primaria, Secundaria, Media)
        List<Matricula> matriculas = matriculaRepository.findAll();
        long totalPrimaria = 0;
        long totalSecundaria = 0;
        long totalMedia = 0;

        for (Matricula m : matriculas) {
            String gradoStr = m.getGrado();
            if ((gradoStr == null || gradoStr.isBlank()) && m.getCurso() != null) {
                gradoStr = m.getCurso().getGrado();
            }
            if (gradoStr != null && !gradoStr.isBlank()) {
                Optional<GradoAcademico> gaOpt = GradoAcademico.find(gradoStr);
                if (gaOpt.isPresent()) {
                    GradoAcademico ga = gaOpt.get();
                    if (ga.esPrimariaOPreescolar()) {
                        totalPrimaria++;
                    } else if ("SECUNDARIA".equalsIgnoreCase(ga.getNivel())) {
                        totalSecundaria++;
                    } else if ("MEDIA".equalsIgnoreCase(ga.getNivel())) {
                        totalMedia++;
                    }
                }
            }
        }

        if (totalPrimaria + totalSecundaria + totalMedia == 0) {
            List<CursoEstudiante> ces = cursoEstudianteRepository.findAll();
            for (CursoEstudiante ce : ces) {
                if (ce.getCurso() != null && ce.getCurso().getGrado() != null) {
                    Optional<GradoAcademico> gaOpt = GradoAcademico.find(ce.getCurso().getGrado());
                    if (gaOpt.isPresent()) {
                        GradoAcademico ga = gaOpt.get();
                        if (ga.esPrimariaOPreescolar()) {
                            totalPrimaria++;
                        } else if ("SECUNDARIA".equalsIgnoreCase(ga.getNivel())) {
                            totalSecundaria++;
                        } else if ("MEDIA".equalsIgnoreCase(ga.getNivel())) {
                            totalMedia++;
                        }
                    }
                }
            }
        }

        long totalEstudiantesNivel = totalPrimaria + totalSecundaria + totalMedia;
        int pctPrimaria;
        int pctSecundaria;
        int pctMedia;

        if (totalEstudiantesNivel > 0) {
            pctPrimaria = (int) Math.round((double) totalPrimaria * 100.0 / totalEstudiantesNivel);
            pctSecundaria = (int) Math.round((double) totalSecundaria * 100.0 / totalEstudiantesNivel);
            pctMedia = 100 - pctPrimaria - pctSecundaria;
            if (pctMedia < 0) pctMedia = 0;
        } else {
            pctPrimaria = 0;
            pctSecundaria = 0;
            pctMedia = 0;
        }

        // 3. Semáforos de Estado Institucional
        List<SemaforoItemDTO> semaforos = new ArrayList<>();

        // 3.1 Asistencia General
        if (resumenAsistencia == null || resumenAsistencia.isEmpty()) {
            semaforos.add(SemaforoItemDTO.advertencia("Asistencia General", "Sin datos"));
        } else if (promedioAsistencia >= 90.0) {
            semaforos.add(SemaforoItemDTO.optimo("Asistencia General", "Óptimo"));
        } else if (promedioAsistencia >= 75.0) {
            semaforos.add(SemaforoItemDTO.advertencia("Asistencia General", "Alerta"));
        } else {
            semaforos.add(SemaforoItemDTO.atencion("Asistencia General", "Atención"));
        }

        // 3.2 Proceso de Matrícula
        if (totalMatriculas >= estudiantesCount && estudiantesCount > 0) {
            semaforos.add(SemaforoItemDTO.optimo("Proceso de Matrícula", "Al día"));
        } else if (estudiantesCount == 0) {
            semaforos.add(SemaforoItemDTO.optimo("Proceso de Matrícula", "Al día"));
        } else if (totalMatriculas > 0) {
            long pendientes = estudiantesCount - totalMatriculas;
            semaforos.add(SemaforoItemDTO.advertencia("Proceso de Matrícula", pendientes > 0 ? pendientes + " pendientes" : "En proceso"));
        } else {
            semaforos.add(SemaforoItemDTO.advertencia("Proceso de Matrícula", "Pendiente"));
        }

        // 3.3 Certificados Pendientes
        if (certsPendientes.isEmpty()) {
            semaforos.add(SemaforoItemDTO.optimo("Certificados Pendientes", "Al día"));
        } else {
            semaforos.add(SemaforoItemDTO.advertencia("Certificados Pendientes", certsPendientes.size() > 1 ? certsPendientes.size() + " Pendientes" : "Pendiente"));
        }

        // 3.4 Carga de Calificaciones
        long totalEvaluaciones = evaluacionRepository.count();
        if (totalEvaluaciones > 0) {
            semaforos.add(SemaforoItemDTO.optimo("Carga de Calificaciones", "Al día"));
        } else {
            semaforos.add(SemaforoItemDTO.advertencia("Carga de Calificaciones", "Pendiente"));
        }

        // 3.5 Alertas Disciplinarias
        if (reportesPendientes.isEmpty()) {
            semaforos.add(SemaforoItemDTO.optimo("Alertas Disciplinarias", "Al día"));
        } else {
            semaforos.add(SemaforoItemDTO.atencion("Alertas Disciplinarias", reportesPendientes.size() > 1 ? "Atención (" + reportesPendientes.size() + ")" : "Atención"));
        }

        // Estudiantes que requieren atención
        List<Map<String, Object>> estudiantesAtencion = new ArrayList<>();
        for (Reporte r : reportesPendientes) {
            if (r.getEstudiante() != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("nombre", r.getEstudiante().getNombreCompleto());
                map.put("motivo", r.getRazon() != null ? r.getRazon() : "Reporte pendiente");
                map.put("accion", "Atender");
                estudiantesAtencion.add(map);
            }
        }

        // Timeline de actividad limitado para el Dashboard (combinando auditoría, matrículas, reportes y certificados)
        List<AuditoriaCambio> auditoriasDashboard = auditoriaCambioRepository != null
                ? auditoriaCambioRepository.findTop25ByOrderByFechaDesc()
                : Collections.emptyList();
        List<Map<String, Object>> todosLogsRecientes = construirLogsActividad(
                auditoriasDashboard, matriculas, reportesPendientes, certsPendientes);
        int totalActividadTimeline = todosLogsRecientes.size();
        List<Map<String, Object>> actividadTimeline = todosLogsRecientes.size() > LIMITE_TIMELINE_DASHBOARD
                ? new ArrayList<>(todosLogsRecientes.subList(0, LIMITE_TIMELINE_DASHBOARD))
                : todosLogsRecientes;

        // Alertas operativas
        List<Map<String, Object>> alertasOperativas = new ArrayList<>();
        if (!certsPendientes.isEmpty()) {
            Map<String, Object> a = new HashMap<>();
            a.put("titulo", certsPendientes.size() + " solicitudes de certificados pendientes de revisión");
            a.put("subtitulo", "Bandeja de Secretaría Académica");
            a.put("icono", "file-warning");
            a.put("tipo", "warning");
            alertasOperativas.add(a);
        }

        if (!reportesPendientes.isEmpty()) {
            Map<String, Object> a = new HashMap<>();
            a.put("titulo", reportesPendientes.size() + " reportes disciplinarios/académicos por atender");
            a.put("subtitulo", "Módulo de Coordinación / Rectoría");
            a.put("icono", "alert-triangle");
            a.put("tipo", "alert");
            alertasOperativas.add(a);
        }

        List<Curso> clasesDB = cursoRepository.findAll();
        long clasesSinDirector = clasesDB.stream().filter(c -> c.getDirector() == null).count();
        if (clasesSinDirector > 0) {
            Map<String, Object> a = new HashMap<>();
            a.put("titulo", clasesSinDirector + " cursos sin director de grupo asignado");
            a.put("subtitulo", "Revisar la gestión de clases");
            a.put("icono", "user-x");
            a.put("tipo", "warning");
            alertasOperativas.add(a);
        }

        return DashboardStatsDTO.builder()
                .estudiantesActivos(estudiantesCount)
                .docentesActivos(docentesCount)
                .asistenciaHoy(asistenciaHoy)
                .alertasSistema(alertasCount)
                .totalReportes(totalReportes)
                .totalMatriculas(totalMatriculas)
                .totalCursos(totalCursos)
                .estudiantesAtencion(estudiantesAtencion)
                .actividadTimeline(actividadTimeline)
                .totalActividadTimeline(totalActividadTimeline)
                .alertasOperativas(alertasOperativas)
                .semaforos(semaforos)
                .promedioAsistencia(promedioAsistencia)
                .asistenciaLabels(asistenciaLabels)
                .asistenciaValores(asistenciaValores)
                .asistenciaLabelsCsv(asistenciaLabelsCsv)
                .asistenciaValoresCsv(asistenciaValoresCsv)
                .porcentajePrimaria(pctPrimaria)
                .porcentajeSecundaria(pctSecundaria)
                .porcentajeMedia(pctMedia)
                .totalEstudiantesPrimaria(totalPrimaria)
                .totalEstudiantesSecundaria(totalSecundaria)
                .totalEstudiantesMedia(totalMedia)
                .build();
    }

    private static int ordenDiaSemana(String dia) {
        if (dia == null) return 99;
        String d = dia.trim().toLowerCase();
        if (d.startsWith("lun")) return 1;
        if (d.startsWith("mar")) return 2;
        if (d.startsWith("mi")) return 3;
        if (d.startsWith("jue")) return 4;
        if (d.startsWith("vie")) return 5;
        if (d.startsWith("s")) return 6;
        return 7;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumenDocente(Docente docente) {
        Map<String, Object> data = new HashMap<>();
        LocalDate hoy = LocalDate.now(clock);
        LocalTime ahora = LocalTime.now(clock);
        String diaHoy = CursoGestionAppService.obtenerNombreDiaEspanol(hoy.getDayOfWeek());
        data.put("diaHoyNombre", diaHoy);

        if (docente == null) {
            data.put("clasesHoy", Collections.emptyList());
            data.put("horarioSemanal", Collections.emptyList());
            data.put("misCursos", Collections.emptyList());
            data.put("totalEstudiantesACargo", 0L);
            data.put("totalReportesDocente", 0);
            data.put("reportesRecientes", Collections.emptyList());
            return data;
        }

        List<Horario> horariosDoc = horarioRepository != null
                ? horarioRepository.findByDocenteId(docente.getId())
                : Collections.emptyList();

        Comparator<Horario> cmpHorario = Comparator
                .comparingInt((Horario h) -> ordenDiaSemana(h.getDiaSemana()))
                .thenComparing(Horario::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder()));

        List<Horario> horariosOrdenados = horariosDoc.stream().sorted(cmpHorario).toList();
        List<Map<String, String>> clasesHoy = new ArrayList<>();
        List<Map<String, String>> horarioSemanal = new ArrayList<>();
        Map<UUID, Curso> cursosMap = new LinkedHashMap<>();

        for (Curso c : cursoRepository.findByDirectorId(docente.getId())) {
            cursosMap.put(c.getId(), c);
        }

        for (Horario h : horariosOrdenados) {
            Curso c = h.getCurso();
            if (c != null) cursosMap.putIfAbsent(c.getId(), c);

            Map<String, String> item = new HashMap<>();
            item.put("dia", h.getDiaSemana() != null ? h.getDiaSemana() : "");
            item.put("cursoCodigo", c != null ? c.getCodigoCurso() : "-");
            item.put("materia", h.getMateria() != null ? h.getMateria().getNombre() : "Asignatura");
            item.put("horaInicio", h.getHoraInicio() != null ? h.getHoraInicio().toString() : "07:00");
            item.put("horaFin", h.getHoraFin() != null ? h.getHoraFin().toString() : "08:00");
            item.put("salon", h.getSalonNombre());
            boolean enCurso = h.getDiaSemana() != null && h.getDiaSemana().equalsIgnoreCase(diaHoy)
                    && h.getHoraInicio() != null && h.getHoraFin() != null
                    && !ahora.isBefore(h.getHoraInicio()) && ahora.isBefore(h.getHoraFin());
            item.put("enCurso", String.valueOf(enCurso));
            horarioSemanal.add(item);

            if (h.getDiaSemana() != null && h.getDiaSemana().equalsIgnoreCase(diaHoy)) {
                clasesHoy.add(item);
            }
        }

        List<Map<String, Object>> misCursos = new ArrayList<>();
        long totalEstudiantesACargo = 0;
        for (Curso c : cursosMap.values()) {
            int estCount = cursoEstudianteRepository.findByCursoId(c.getId()).size();
            totalEstudiantesACargo += estCount;
            Map<String, Object> cm = new HashMap<>();
            cm.put("codigo", c.getCodigoCurso());
            cm.put("grado", c.getGrado());
            cm.put("jornada", c.getJornada() != null ? c.getJornada() : "Mañana");
            cm.put("esDirector", c.getDirector() != null && docente.getId().equals(c.getDirector().getId()));
            cm.put("totalEstudiantes", estCount);
            misCursos.add(cm);
        }

        List<Reporte> reportes = reporteRepository.findByDocenteIdOrderByCreatedAtDesc(docente.getId());
        List<Map<String, String>> reportesRecientes = new ArrayList<>();
        int count = 0;
        for (Reporte r : reportes) {
            if (count++ >= 5) break;
            Map<String, String> rm = new HashMap<>();
            rm.put("codigo", r.getCodigo());
            rm.put("razon", r.getRazon() != null ? r.getRazon() : "Reporte");
            rm.put("estado", r.getEstado() != null ? r.getEstado() : "Pendiente");
            rm.put("fecha", r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : "");
            rm.put("estudiante", r.getEstudiante() != null ? r.getEstudiante().getNombreCompleto() : "Reporte institucional");
            reportesRecientes.add(rm);
        }

        data.put("clasesHoy", clasesHoy);
        data.put("horarioSemanal", horarioSemanal);
        data.put("misCursos", misCursos);
        data.put("totalEstudiantesACargo", totalEstudiantesACargo);
        data.put("totalReportesDocente", reportes.size());
        data.put("reportesRecientes", reportesRecientes);
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumenEstudiante(Estudiante estudiante) {
        Map<String, Object> data = new HashMap<>();
        LocalDate hoy = LocalDate.now(clock);
        LocalTime ahora = LocalTime.now(clock);
        String diaHoy = CursoGestionAppService.obtenerNombreDiaEspanol(hoy.getDayOfWeek());
        data.put("diaHoyNombre", diaHoy);

        if (estudiante == null) {
            data.put("tieneCurso", false);
            data.put("codigoCurso", "Sin grupo");
            data.put("gradoCurso", "-");
            data.put("jornadaCurso", "-");
            data.put("directorCurso", "Sin asignar");
            data.put("promedioGeneral", "-");
            data.put("boletin", Collections.emptyList());
            data.put("asistenciaPct", "0%");
            data.put("asistenciaTone", "warning");
            data.put("totalAsistencias", 0L);
            data.put("asistenciasPresentes", 0L);
            data.put("asistenciasTardes", 0L);
            data.put("asistenciasExcusadas", 0L);
            data.put("asistenciasFallas", 0L);
            data.put("certificadosPendientes", 0L);
            data.put("reportesActivos", 0L);
            data.put("totalAlertasEst", 0L);
            data.put("clasesHoy", Collections.emptyList());
            data.put("horarioSemanal", Collections.emptyList());
            return data;
        }

        List<CursoEstudiante> ces = cursoEstudianteRepository.findByEstudianteId(estudiante.getId());
        Curso curso = !ces.isEmpty() ? ces.get(ces.size() - 1).getCurso() : null;
        data.put("tieneCurso", curso != null);
        data.put("codigoCurso", curso != null ? curso.getCodigoCurso() : "Sin grupo");
        data.put("gradoCurso", curso != null ? curso.getGrado() : "Por asignar");
        data.put("jornadaCurso", curso != null && curso.getJornada() != null ? curso.getJornada() : "Mañana");
        data.put("directorCurso", (curso != null && curso.getDirector() != null) ? curso.getDirector().getNombreCompleto() : "Sin asignar");

        String anioActivo = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());
        data.put("anoLectivo", anioActivo);

        List<BoletinMateriaDTO> boletin = Collections.emptyList();
        String promedioGeneral = "-";
        if (calificacionesService != null) {
            Map<Integer, BigDecimal> ponderaciones = periodoConfigService != null
                    ? periodoConfigService.getPonderaciones()
                    : Collections.emptyMap();
            boletin = calificacionesService.obtenerBoletinEstudiante(estudiante.getId(), anioActivo, ponderaciones);
            BigDecimal suma = BigDecimal.ZERO;
            int materiasConNota = 0;
            for (BoletinMateriaDTO b : boletin) {
                if (b.getNotaDefinitiva() != null && b.getNotaDefinitiva().compareTo(BigDecimal.ZERO) > 0) {
                    suma = suma.add(b.getNotaDefinitiva());
                    materiasConNota++;
                }
            }
            if (materiasConNota > 0) {
                promedioGeneral = suma.divide(BigDecimal.valueOf(materiasConNota), 2, RoundingMode.HALF_UP).toPlainString();
            }
        }
        data.put("boletin", boletin);
        data.put("promedioGeneral", promedioGeneral);

        List<Asistencia> asistencias = asistenciaRepository.findByEstudianteId(estudiante.getId());
        long totalAsist = asistencias.size();
        long presentes = asistencias.stream().filter(a -> "PRESENTE".equalsIgnoreCase(a.getEstado())).count();
        long tardes = asistencias.stream().filter(a -> "TARDE".equalsIgnoreCase(a.getEstado())).count();
        long excusadas = asistencias.stream().filter(a -> "EXCUSADO".equalsIgnoreCase(a.getEstado())).count();
        long fallas = asistencias.stream().filter(a -> "AUSENTE".equalsIgnoreCase(a.getEstado()) || "FI".equalsIgnoreCase(a.getEstado())).count();
        long cumplidas = presentes + tardes + excusadas;
        double pctAsist = totalAsist > 0 ? (cumplidas * 100.0 / totalAsist) : 100.0;

        data.put("totalAsistencias", totalAsist);
        data.put("asistenciasPresentes", presentes);
        data.put("asistenciasTardes", tardes);
        data.put("asistenciasExcusadas", excusadas);
        data.put("asistenciasFallas", fallas);
        data.put("asistenciaPct", totalAsist > 0 ? String.format(Locale.US, "%.0f%%", pctAsist) : "Sin registros");
        data.put("asistenciaTone", (totalAsist > 0 && pctAsist < 75.0) ? "warning" : "success");

        long certsPend = certificadoRepository.findByEstudianteIdOrderByCreatedAtDesc(estudiante.getId())
                .stream().filter(c -> "Pendiente".equalsIgnoreCase(c.getEstado())).count();
        long repsActivos = reporteRepository.findByEstudianteIdOrReportanteEstudianteIdOrderByCreatedAtDesc(estudiante.getId(), estudiante.getId())
                .stream().filter(r -> !"Rechazado".equalsIgnoreCase(r.getEstado())).count();

        data.put("certificadosPendientes", certsPend);
        data.put("reportesActivos", repsActivos);
        data.put("totalAlertasEst", certsPend + repsActivos);

        List<Map<String, String>> clasesHoy = new ArrayList<>();
        List<Map<String, String>> horarioSemanal = new ArrayList<>();
        if (curso != null && horarioRepository != null) {
            List<Horario> horariosCurso = horarioRepository.findByCursoId(curso.getId()).stream()
                    .sorted(Comparator
                            .comparingInt((Horario h) -> ordenDiaSemana(h.getDiaSemana()))
                            .thenComparing(Horario::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (Horario h : horariosCurso) {
                Map<String, String> item = new HashMap<>();
                item.put("dia", h.getDiaSemana() != null ? h.getDiaSemana() : "");
                item.put("materia", h.getMateria() != null ? h.getMateria().getNombre() : "Asignatura");
                item.put("docente", h.getDocente() != null ? h.getDocente().getNombreCompleto() : "Por asignar");
                item.put("horaInicio", h.getHoraInicio() != null ? h.getHoraInicio().toString() : "07:00");
                item.put("horaFin", h.getHoraFin() != null ? h.getHoraFin().toString() : "08:00");
                item.put("salon", h.getSalonNombre());
                boolean enCurso = h.getDiaSemana() != null && h.getDiaSemana().equalsIgnoreCase(diaHoy)
                        && h.getHoraInicio() != null && h.getHoraFin() != null
                        && !ahora.isBefore(h.getHoraInicio()) && ahora.isBefore(h.getHoraFin());
                item.put("enCurso", String.valueOf(enCurso));
                horarioSemanal.add(item);
                if (h.getDiaSemana() != null && h.getDiaSemana().equalsIgnoreCase(diaHoy)) {
                    clasesHoy.add(item);
                }
            }
        }
        data.put("clasesHoy", clasesHoy);
        data.put("horarioSemanal", horarioSemanal);
        return data;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerTodosLosLogsActividad(String categoriaFiltro, String busqueda) {
        List<AuditoriaCambio> auditorias = auditoriaCambioRepository != null
                ? auditoriaCambioRepository.findAllByOrderByFechaDesc()
                : Collections.emptyList();
        List<Matricula> matriculas = matriculaRepository.findAll();
        List<Reporte> reportes = reporteRepository.findAllByOrderByCreatedAtDesc();
        List<SolicitudCertificado> certificados = certificadoRepository.findAllByOrderByCreatedAtDesc();

        List<Map<String, Object>> todos = construirLogsActividad(auditorias, matriculas, reportes, certificados);
        String cat = categoriaFiltro != null ? categoriaFiltro.trim().toUpperCase(Locale.ROOT) : "TODAS";
        String q = busqueda != null ? busqueda.trim().toLowerCase(Locale.ROOT) : "";

        return todos.stream()
                .filter(item -> "TODAS".equals(cat) || cat.isEmpty() || cat.equals(item.get("categoria")))
                .filter(item -> {
                    if (q.isEmpty()) return true;
                    String t = String.valueOf(item.getOrDefault("titulo", "")).toLowerCase(Locale.ROOT);
                    String d = String.valueOf(item.getOrDefault("detalle", "")).toLowerCase(Locale.ROOT);
                    String u = String.valueOf(item.getOrDefault("usuario", "")).toLowerCase(Locale.ROOT);
                    return t.contains(q) || d.contains(q) || u.contains(q);
                })
                .toList();
    }

    private List<Map<String, Object>> construirLogsActividad(List<AuditoriaCambio> auditorias,
                                                             List<Matricula> matriculas,
                                                             List<Reporte> reportes,
                                                             List<SolicitudCertificado> certificados) {
        DateTimeFormatter fmtFechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        DateTimeFormatter fmtFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<Map<String, Object>> items = new ArrayList<>();

        if (auditorias != null) {
            for (AuditoriaCambio ac : auditorias) {
                LocalDateTime fecha = ac.getFecha();
                String prev = ac.getValorAnterior() != null ? ac.getValorAnterior() : "-";
                String next = ac.getValorNuevo() != null ? ac.getValorNuevo() : "-";
                String titulo = "Cambio en " + (ac.getEntidad() != null ? ac.getEntidad() : "Sistema")
                        + " (" + (ac.getCampo() != null ? ac.getCampo() : "campo") + ")";
                String detalle = "'" + prev + "' → '" + next + "'";
                items.add(crearLogItem(
                        titulo,
                        detalle,
                        fecha != null ? fecha.format(fmtFechaHora) : "Reciente",
                        fecha != null ? fecha : LocalDateTime.MIN,
                        "AUDITORIA",
                        "Auditoría",
                        ac.getUsuarioEmail() != null && !ac.getUsuarioEmail().isBlank() ? ac.getUsuarioEmail() : "Sistema",
                        "Auditado",
                        "bg-sky-50 text-sky-800 border-sky-200"
                ));
            }
        }

        if (matriculas != null) {
            for (Matricula m : matriculas) {
                String nombre = m.getEstudiante() != null ? m.getEstudiante().getNombreCompleto() : "Estudiante";
                String grado = m.getGrado() != null ? m.getGrado() : "-";
                String jornada = (m.getCurso() != null && m.getCurso().getJornada() != null)
                        ? m.getCurso().getJornada()
                        : "Mañana";
                LocalDateTime orden = m.getFechaAutorizacionDatos() != null
                        ? m.getFechaAutorizacionDatos()
                        : (m.getFechaMatricula() != null ? m.getFechaMatricula().atStartOfDay() : LocalDateTime.MIN);
                String tiempo = m.getFechaMatricula() != null
                        ? m.getFechaMatricula().format(fmtFecha)
                        : (m.getFechaAutorizacionDatos() != null ? m.getFechaAutorizacionDatos().format(fmtFechaHora) : "Reciente");
                items.add(crearLogItem(
                        "Matrícula registrada: " + nombre,
                        "Grado " + grado + " • Jornada " + jornada,
                        tiempo,
                        orden,
                        "MATRICULA",
                        "Matrícula",
                        "Secretaría / Matrículas",
                        "Matriculado",
                        "bg-pill-green text-sidebar border-[#c4eec0]"
                ));
            }
        }

        if (reportes != null) {
            for (Reporte r : reportes) {
                boolean objDoc = "DOCENTE".equalsIgnoreCase(r.getObjetivoTipo());
                String objetivo = objDoc
                        ? (r.getDocente() != null ? "Prof. " + r.getDocente().getNombreCompleto() : "Docente")
                        : (r.getEstudiante() != null ? r.getEstudiante().getNombreCompleto() : "Estudiante");
                String cod = r.getCodigo() != null ? r.getCodigo() : "REP";
                String razon = r.getRazon() != null ? r.getRazon() : "Reporte";
                LocalDateTime fecha = r.getCreatedAt();
                String emisor = "ESTUDIANTE".equalsIgnoreCase(r.getReportanteTipo())
                        ? (r.getReportanteEstudiante() != null ? r.getReportanteEstudiante().getNombreCompleto() : "Estudiante")
                        : (r.getDocente() != null ? r.getDocente().getNombreCompleto() : "Institucional");
                items.add(crearLogItem(
                        "Reporte [" + cod + "] para " + objetivo,
                        razon,
                        fecha != null ? fecha.format(fmtFechaHora) : "Reciente",
                        fecha != null ? fecha : LocalDateTime.MIN,
                        "REPORTE",
                        "Reporte",
                        emisor,
                        r.getEstado() != null ? r.getEstado() : "Pendiente",
                        "bg-amber-50 text-amber-800 border-amber-200"
                ));
            }
        }

        if (certificados != null) {
            for (SolicitudCertificado sc : certificados) {
                String tipo = sc.getTipo() != null ? sc.getTipo() : "Certificado";
                String cod = sc.getCodigo() != null ? sc.getCodigo() : "CERT";
                String est = sc.getEstudiante() != null ? sc.getEstudiante().getNombreCompleto() : "Estudiante";
                LocalDateTime fecha = sc.getCreatedAt();
                items.add(crearLogItem(
                        "Solicitud [" + cod + "] • " + tipo,
                        "Solicitante: " + est,
                        fecha != null ? fecha.format(fmtFechaHora) : "Reciente",
                        fecha != null ? fecha : LocalDateTime.MIN,
                        "CERTIFICADO",
                        "Certificado",
                        est,
                        sc.getEstado() != null ? sc.getEstado() : "Pendiente",
                        "bg-purple-50 text-purple-800 border-purple-200"
                ));
            }
        }

        items.sort((a, b) -> {
            LocalDateTime fa = (LocalDateTime) a.get("_ordenFecha");
            LocalDateTime fb = (LocalDateTime) b.get("_ordenFecha");
            return fb.compareTo(fa);
        });
        return items;
    }

    private static Map<String, Object> crearLogItem(String titulo,
                                                    String detalle,
                                                    String tiempo,
                                                    LocalDateTime ordenFecha,
                                                    String categoria,
                                                    String categoriaLabel,
                                                    String usuario,
                                                    String estado,
                                                    String badgeClass) {
        Map<String, Object> map = new HashMap<>();
        map.put("titulo", titulo);
        map.put("detalle", detalle);
        map.put("tiempo", tiempo);
        map.put("_ordenFecha", ordenFecha != null ? ordenFecha : LocalDateTime.MIN);
        map.put("categoria", categoria);
        map.put("categoriaLabel", categoriaLabel);
        map.put("usuario", usuario);
        map.put("estado", estado);
        map.put("badgeClass", badgeClass);
        return map;
    }
}

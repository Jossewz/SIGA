package com.siga.siga_iea.asistencias.service;

import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.asistencias.repository.SesionClaseRepository;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.HorarioRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Servicio de orquestación de la jornada diaria del docente ("Mi Jornada").
 * Deduce en backend los cursos y sesiones del docente (direcciones de grupo y horarios del día)
 * y resume la cobertura de asistencia registrada por sesión.
 */
@Service
public class MiJornadaService {

    private final Clock clock;
    private final CursoRepository cursoRepo;
    private final HorarioRepository horarioRepo;
    private final SesionClaseRepository sesionRepo;
    private final AsistenciaService asistenciaService;
    private final AsistenciaRepository asistenciaRepo;
    private final CursoEstudianteRepository cursoEstudianteRepo;

    @Autowired
    public MiJornadaService(Clock clock,
                            CursoRepository cursoRepo,
                            HorarioRepository horarioRepo,
                            SesionClaseRepository sesionRepo,
                            AsistenciaService asistenciaService,
                            AsistenciaRepository asistenciaRepo,
                            CursoEstudianteRepository cursoEstudianteRepo) {
        this.clock = clock;
        this.cursoRepo = cursoRepo;
        this.horarioRepo = horarioRepo;
        this.sesionRepo = sesionRepo;
        this.asistenciaService = asistenciaService;
        this.asistenciaRepo = asistenciaRepo;
        this.cursoEstudianteRepo = cursoEstudianteRepo;
    }

    private static final LocalTime HORA_CIERRE_INSTITUCIONAL = LocalTime.of(19, 0);

    public record SesionResumen(
            int totalEstudiantes,
            long registrados,
            long presentes,
            long tardes,
            long excusados,
            long ausentes,
            String estadoTemporal
    ) {
        public int getTotalEstudiantes() { return totalEstudiantes; }
        public long getRegistrados() { return registrados; }
        public long getPresentes() { return presentes; }
        public long getTardes() { return tardes; }
        public long getExcusados() { return excusados; }
        public long getAusentes() { return ausentes; }
        public String getEstadoTemporal() { return estadoTemporal; }
    }

    public record JornadaVista(
            LocalDate fecha,
            boolean esHoy,
            LocalDate hoy,
            boolean diaHabil,
            boolean tieneClases,
            boolean jornadaIniciada,
            boolean jornadaFinalizada,
            LocalTime horaCierreAutomatico,
            List<SesionClase> sesiones,
            Map<UUID, SesionResumen> resumenSesiones
    ) {
        public LocalDate getFecha() { return fecha; }
        public boolean isEsHoy() { return esHoy; }
        public LocalDate getHoy() { return hoy; }
        public boolean isDiaHabil() { return diaHabil; }
        public boolean isTieneClases() { return tieneClases; }
        public boolean isJornadaIniciada() { return jornadaIniciada; }
        public boolean isJornadaFinalizada() { return jornadaFinalizada; }
        public LocalTime getHoraCierreAutomatico() { return horaCierreAutomatico; }
        public List<SesionClase> getSesiones() { return sesiones; }
        public Map<UUID, SesionResumen> getResumenSesiones() { return resumenSesiones; }
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public JornadaVista vistaHoy(Docente docente) {
        return vistaPorFecha(docente, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public JornadaVista vistaPorFecha(Docente docente, LocalDate fechaSolicitada) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate target = (fechaSolicitada != null && !fechaSolicitada.isAfter(hoy)) ? fechaSolicitada : hoy;
        boolean esHoy = target.equals(hoy);

        if (esFinDeSemana(target)) {
            return new JornadaVista(target, esHoy, hoy, false, false, false, !esHoy, HORA_CIERRE_INSTITUCIONAL, List.of(), Collections.emptyMap());
        }
        boolean tieneClases = !cursosDelDia(docente, target).isEmpty();
        var sesiones = sesionRepo.findByDocenteIdAndFechaOrderByHoraInicioAsc(docente.getId(), target);
        LocalTime horaCierre = calcularHoraCierreAutomatico(sesiones);
        LocalTime ahora = LocalTime.now(clock);
        boolean jornadaIniciada = !sesiones.isEmpty();
        boolean jornadaFinalizada = !esHoy || !ahora.isBefore(horaCierre);
        Map<UUID, SesionResumen> resumen = construirResumenes(sesiones, esHoy, jornadaFinalizada, ahora);
        return new JornadaVista(target, esHoy, hoy, true, tieneClases, jornadaIniciada, jornadaFinalizada, horaCierre, sesiones, resumen);
    }

    // SIN @Transactional: abrirDia maneja su propia transacción y el reintento por colisión concurrente
    public JornadaVista iniciar(Docente docente) {
        LocalDate hoy = LocalDate.now(clock);
        if (!esFinDeSemana(hoy)) {
            cursosDelDia(docente, hoy).forEach(c -> asistenciaService.abrirDia(c.getId(), hoy));
        }
        return vistaPorFecha(docente, hoy);
    }

    public boolean puedeEditarEnJornada(SesionClase sesion,
                                        Docente docenteOperador,
                                        com.siga.siga_iea.auth.enums.RolEnum rolOperador) {
        if (!asistenciaService.puedeEditarSesion(sesion, docenteOperador, rolOperador)) {
            return false;
        }
        if (rolOperador != null && rolOperador.esAdminOAdministrativo()) {
            return true;
        }
        var sesionesDia = sesionRepo.findByDocenteIdAndFechaOrderByHoraInicioAsc(
                docenteOperador.getId(), sesion.getFecha());
        LocalTime horaCierre = calcularHoraCierreAutomatico(sesionesDia);
        return LocalTime.now(clock).isBefore(horaCierre);
    }

    private LocalTime calcularHoraCierreAutomatico(List<SesionClase> sesiones) {
        if (sesiones == null || sesiones.isEmpty()) {
            return HORA_CIERRE_INSTITUCIONAL;
        }
        LocalTime maxFin = sesiones.stream()
                .map(SesionClase::getHoraFin)
                .filter(Objects::nonNull)
                .max(LocalTime::compareTo)
                .orElse(null);
        if (maxFin == null) {
            return HORA_CIERRE_INSTITUCIONAL;
        }
        LocalTime finConMargen = maxFin.isAfter(LocalTime.of(22, 59))
                ? LocalTime.of(23, 59)
                : maxFin.plusHours(1);
        return finConMargen.isAfter(HORA_CIERRE_INSTITUCIONAL) ? finConMargen : HORA_CIERRE_INSTITUCIONAL;
    }

    private Map<UUID, SesionResumen> construirResumenes(List<SesionClase> sesiones,
                                                        boolean esHoy,
                                                        boolean jornadaFinalizada,
                                                        LocalTime ahora) {
        if (sesiones == null || sesiones.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> totalPorCurso = new HashMap<>();
        Map<UUID, SesionResumen> resumenes = new LinkedHashMap<>();

        for (SesionClase s : sesiones) {
            UUID cursoId = s.getCurso() != null ? s.getCurso().getId() : null;
            int totalEst = 0;
            if (cursoId != null) {
                totalEst = totalPorCurso.computeIfAbsent(cursoId,
                        cid -> cursoEstudianteRepo.findByCursoId(cid).size());
            }
            List<Asistencia> regs = asistenciaRepo.findBySesionId(s.getId());
            long presentes = 0, tardes = 0, excusados = 0, ausentes = 0;
            for (Asistencia a : regs) {
                String est = a.getEstado();
                if ("PRESENTE".equalsIgnoreCase(est)) presentes++;
                else if ("TARDE".equalsIgnoreCase(est)) tardes++;
                else if ("EXCUSADO".equalsIgnoreCase(est)) excusados++;
                else if ("AUSENTE".equalsIgnoreCase(est)) ausentes++;
            }
            String estadoTemporal = resolverEstadoTemporalSesion(s, esHoy, jornadaFinalizada, ahora);
            resumenes.put(s.getId(), new SesionResumen(totalEst, regs.size(), presentes, tardes, excusados, ausentes, estadoTemporal));
        }
        return resumenes;
    }

    private String resolverEstadoTemporalSesion(SesionClase s, boolean esHoy, boolean jornadaFinalizada, LocalTime ahora) {
        if ("CANCELADA".equalsIgnoreCase(s.getEstado())) {
            return "CANCELADA";
        }
        if (!esHoy || jornadaFinalizada || (s.getHoraFin() != null && ahora.isAfter(s.getHoraFin()))) {
            return "FINALIZADA";
        }
        if (s.getHoraInicio() != null && !ahora.isBefore(s.getHoraInicio())) {
            return "EN_CURSO";
        }
        return "PROGRAMADA";
    }

    private Collection<Curso> cursosDelDia(Docente d, LocalDate hoy) {
        if (d == null || d.getId() == null) {
            return Collections.emptyList();
        }
        Map<UUID, Curso> cursos = new LinkedHashMap<>();
        cursoRepo.findByDirectorId(d.getId()).forEach(c -> cursos.put(c.getId(), c));
        horarioRepo.findCursosDocentePorDia(d.getId(), nombreDia(hoy))
                   .forEach(c -> cursos.put(c.getId(), c));
        return cursos.values();
    }

    public static boolean esFinDeSemana(LocalDate f) {
        return f.getDayOfWeek() == DayOfWeek.SATURDAY || f.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static String nombreDia(LocalDate f) {
        return switch (f.getDayOfWeek()) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}

package com.siga.siga_iea.reportes.service;

import com.siga.siga_iea.notificaciones.service.NotificacionService;
import com.siga.siga_iea.reportes.entity.Reporte;
import com.siga.siga_iea.reportes.repository.ReporteRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportesService {

    private final ReporteRepository reporteRepository;
    private final EstudianteRepository estudianteRepository;
    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalRepository;
    private final com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService;
    private final NotificacionService notificacionService;

    @Autowired
    public ReportesService(ReporteRepository reporteRepository,
                           EstudianteRepository estudianteRepository,
                           DocenteRepository docenteRepository,
                           PersonalAdministrativoRepository personalRepository,
                           @Autowired(required = false)
                           com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService,
                           @Autowired(required = false)
                           NotificacionService notificacionService) {
        this.reporteRepository = reporteRepository;
        this.estudianteRepository = estudianteRepository;
        this.docenteRepository = docenteRepository;
        this.personalRepository = personalRepository;
        this.periodoConfigService = periodoConfigService;
        this.notificacionService = notificacionService;
    }

    public ReportesService(ReporteRepository reporteRepository,
                           EstudianteRepository estudianteRepository,
                           DocenteRepository docenteRepository,
                           PersonalAdministrativoRepository personalRepository,
                           com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService) {
        this(reporteRepository, estudianteRepository, docenteRepository, personalRepository, periodoConfigService, null);
    }

    public List<Reporte> listarTodos() {
        return reporteRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Reporte> listarPorDocente(UUID docenteId) {
        return reporteRepository.findByDocenteIdAndReportanteTipoOrderByCreatedAtDesc(docenteId, "DOCENTE");
    }

    public List<Reporte> listarPorEstudiante(UUID estudianteId) {
        return reporteRepository.findByEstudianteIdOrReportanteEstudianteIdOrderByCreatedAtDesc(estudianteId, estudianteId);
    }

    public List<Reporte> filtrarPorEstado(String estado) {
        if (estado == null || estado.isBlank() || "todos".equalsIgnoreCase(estado)) {
            return listarTodos();
        }
        return reporteRepository.findByEstado(estado);
    }

    public Optional<Reporte> buscarPorId(UUID id) {
        return reporteRepository.findById(id);
    }

    @Transactional
    public Reporte crearReporte(UUID estudianteId, UUID docenteId, String categoria, String razon, String descripcionRazon, String detalles) {
        Estudiante est = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));
        Docente doc = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado"));

        String anio = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(com.siga.siga_iea.configuracion.service.PeriodoConfigService.anioCalendarioActual());
        long consecutivo = reporteRepository.count() + 1;

        Reporte r = new Reporte();
        r.setCodigo(String.format("REP-%s-%04d", anio, consecutivo));
        r.setEstudiante(est);
        r.setDocente(doc);
        r.setReportanteTipo("DOCENTE");
        r.setObjetivoTipo("ESTUDIANTE");
        r.setCategoria(categoria);
        r.setRazon(razon);
        r.setDescripcionRazon(descripcionRazon);
        r.setDetalles(detalles);
        r.setEstado("Pendiente");

        Reporte guardado = reporteRepository.save(r);
        notificarCreacionReporte(guardado);
        return guardado;
    }

    @Transactional
    public Reporte crearReporteEstudiantil(UUID reportanteEstudianteId,
                                           Usuario reportanteUsuario,
                                           String objetivoTipo,
                                           UUID objetivoEstudianteId,
                                           UUID objetivoDocenteId,
                                           String razon,
                                           String detalles) {
        Estudiante reportante = estudianteRepository.findById(reportanteEstudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante emisor no encontrado"));

        String tipoObj = "DOCENTE".equalsIgnoreCase(objetivoTipo) ? "DOCENTE" : "ESTUDIANTE";
        String categoria = resolverCategoriaEstudiantil(tipoObj, razon);

        String anio = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(com.siga.siga_iea.configuracion.service.PeriodoConfigService.anioCalendarioActual());
        long consecutivo = reporteRepository.count() + 1;

        Reporte r = new Reporte();
        r.setCodigo(String.format("REP-%s-%04d", anio, consecutivo));
        r.setReportanteTipo("ESTUDIANTE");
        r.setReportanteEstudiante(reportante);
        r.setReportanteUsuario(reportanteUsuario);
        r.setObjetivoTipo(tipoObj);
        r.setCategoria(categoria);
        r.setRazon(razon);
        r.setDetalles(detalles);
        r.setEstado("Pendiente");

        if ("DOCENTE".equals(tipoObj)) {
            if (objetivoDocenteId == null) {
                throw new IllegalArgumentException("Debe seleccionar el docente al cual se dirige el reporte.");
            }
            Docente doc = docenteRepository.findById(objetivoDocenteId)
                    .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado"));
            r.setDocente(doc);
        } else {
            if (objetivoEstudianteId == null) {
                throw new IllegalArgumentException("Debe seleccionar el estudiante reportado.");
            }
            if (objetivoEstudianteId.equals(reportanteEstudianteId)) {
                throw new IllegalArgumentException("No puedes generar un reporte sobre ti mismo.");
            }
            Estudiante objetivo = estudianteRepository.findById(objetivoEstudianteId)
                    .orElseThrow(() -> new IllegalArgumentException("Estudiante reportado no encontrado"));
            r.setEstudiante(objetivo);
        }

        Reporte guardado = reporteRepository.save(r);
        notificarCreacionReporte(guardado);
        return guardado;
    }

    private String resolverCategoriaEstudiantil(String objetivoTipo, String razon) {
        if ("DOCENTE".equals(objetivoTipo)) {
            if ("Inconformidad en evaluación o calificación".equals(razon) || "Incumplimiento o ausencias reiteradas de clase".equals(razon)) {
                return "Proceso Académico (Estudiante)";
            }
            if ("Otro".equals(razon)) return "Otras Razones (Estudiante)";
            return "Convivencia y Trato Docente";
        }
        if ("Ciberacoso o mal uso de redes".equals(razon)) return "Convivencia Digital";
        if ("Otro".equals(razon)) return "Otras Razones (Estudiante)";
        return "Convivencia entre Estudiantes";
    }

    private void notificarCreacionReporte(Reporte r) {
        if (notificacionService == null || r == null) return;
        String emisor = "ESTUDIANTE".equals(r.getReportanteTipo()) && r.getReportanteEstudiante() != null
                ? r.getReportanteEstudiante().getNombreCompleto() + " (Estudiante)"
                : (r.getDocente() != null ? r.getDocente().getNombreCompleto() + " (Docente)" : "Miembro institucional");
        String objetivo = "DOCENTE".equals(r.getObjetivoTipo()) && r.getDocente() != null
                ? r.getDocente().getNombreCompleto() + " (Docente)"
                : (r.getEstudiante() != null ? r.getEstudiante().getNombreCompleto() : "Estudiante");

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Código", r.getCodigo());
        detalles.put("Emitido por", emisor);
        detalles.put("Reportado", objetivo);
        detalles.put("Motivo", r.getRazon() + " (" + r.getCategoria() + ")");
        detalles.put("Estado", r.getEstado());

        notificacionService.notificarAdministrativos(
                "REPORTE_CREADO",
                "Nuevo reporte registrado (" + r.getCodigo() + ")",
                emisor + " registró un reporte hacia " + objetivo + " por: " + r.getRazon() + ".",
                r.getCodigo(),
                "alert-triangle",
                "amber",
                true,
                detalles
        );

        if ("ESTUDIANTE".equals(r.getObjetivoTipo()) && r.getEstudiante() != null) {
            notificacionService.notificarEstudianteYAcudiente(
                    r.getEstudiante(),
                    "REPORTE_CREADO",
                    "Se ha registrado un reporte de convivencia/seguimiento (" + r.getCodigo() + ")",
                    "Coordinación recibió un reporte por: " + r.getRazon() + ". Consulta los detalles en tu módulo de Reportes.",
                    r.getCodigo(),
                    "alert-triangle",
                    "rose",
                    true,
                    detalles
            );
        } else if ("DOCENTE".equals(r.getObjetivoTipo()) && r.getDocente() != null) {
            notificacionService.notificarDocente(
                    r.getDocente(),
                    "REPORTE_CREADO",
                    "Notificación de reporte en revisión (" + r.getCodigo() + ")",
                    "Coordinación recibió una observación estudiantil relacionada con: " + r.getRazon() + ".",
                    r.getCodigo(),
                    "alert-triangle",
                    "amber",
                    true,
                    detalles
            );
        }
    }

    @Transactional
    public Reporte atenderReporte(UUID reporteId, String decision, LocalDateTime fechaCitacion, Boolean requiereAcudiente, String observacionesAdmin, UUID adminId) {
        Reporte r = reporteRepository.findById(reporteId)
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado"));

        r.setEstado(decision != null ? decision : "Aceptado");
        r.setFechaCitacion(fechaCitacion);
        r.setRequiereAcudiente(requiereAcudiente != null ? requiereAcudiente : false);
        r.setObservacionesAdmin(observacionesAdmin);

        if (adminId != null) {
            personalRepository.findById(adminId).ifPresent(r::setAtendidoPor);
        }

        Reporte guardado = reporteRepository.save(r);
        notificarResolucionReporte(guardado);
        return guardado;
    }

    private void notificarResolucionReporte(Reporte r) {
        if (notificacionService == null || r == null) return;
        boolean citado = "Aceptado".equalsIgnoreCase(r.getEstado());
        String fechaStr = r.getFechaCitacion() != null ? r.getFechaCitacion().toString().replace("T", " ") : "Por confirmar";
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Código", r.getCodigo());
        detalles.put("Resolución", r.getEstado());
        if (citado) {
            detalles.put("Fecha de citación", fechaStr);
            detalles.put("Requiere acudiente", Boolean.TRUE.equals(r.getRequiereAcudiente()) ? "Sí, obligatoria" : "No obligatorio");
        }
        if (r.getObservacionesAdmin() != null && !r.getObservacionesAdmin().isBlank()) {
            detalles.put("Observaciones", r.getObservacionesAdmin());
        }

        String titulo = citado
                ? "Citación programada (" + r.getCodigo() + ")"
                : "Reporte " + r.getEstado() + " (" + r.getCodigo() + ")";
        String msg = citado
                ? "Coordinación programó citación para " + fechaStr + ". " + (r.getObservacionesAdmin() != null ? r.getObservacionesAdmin() : "")
                : "El reporte " + r.getCodigo() + " fue actualizado a estado " + r.getEstado() + ".";

        if ("ESTUDIANTE".equals(r.getObjetivoTipo()) && r.getEstudiante() != null) {
            notificacionService.notificarEstudianteYAcudiente(
                    r.getEstudiante(),
                    citado ? "REPORTE_CITACION" : "REPORTE_RESUELTO",
                    titulo,
                    msg,
                    r.getCodigo(),
                    "calendar-clock",
                    citado ? "rose" : "emerald",
                    true,
                    detalles
            );
        }
        if ("ESTUDIANTE".equals(r.getReportanteTipo()) && r.getReportanteEstudiante() != null) {
            notificacionService.notificarEstudianteYAcudiente(
                    r.getReportanteEstudiante(),
                    "REPORTE_RESUELTO",
                    "Tu reporte " + r.getCodigo() + " fue revisado (" + r.getEstado() + ")",
                    msg,
                    r.getCodigo(),
                    "check-circle-2",
                    "emerald",
                    true,
                    detalles
            );
        } else if (r.getDocente() != null) {
            notificacionService.notificarDocente(
                    r.getDocente(),
                    citado ? "REPORTE_CITACION" : "REPORTE_RESUELTO",
                    titulo,
                    msg,
                    r.getCodigo(),
                    "check-circle-2",
                    "emerald",
                    true,
                    detalles
            );
        }
    }
}

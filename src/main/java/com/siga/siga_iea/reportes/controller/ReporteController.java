package com.siga.siga_iea.reportes.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.reportes.entity.Reporte;
import com.siga.siga_iea.reportes.service.ReportesService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.EstudianteService;
import com.siga.siga_iea.usuarios.service.PersonalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class ReporteController {

    private final ReportesService reportesService;
    private final EstudianteService estudianteService;
    private final PersonalService personalService;
    private final CurrentUserContextService currentUserContextService;
    private final com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository;
    private final com.siga.siga_iea.matricula.service.MatriculaService matriculaService;

    public ReporteController(ReportesService reportesService,
                             EstudianteService estudianteService,
                             PersonalService personalService,
                             CurrentUserContextService currentUserContextService,
                             com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository,
                             com.siga.siga_iea.matricula.service.MatriculaService matriculaService) {
        this.reportesService = reportesService;
        this.estudianteService = estudianteService;
        this.personalService = personalService;
        this.currentUserContextService = currentUserContextService;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.matriculaService = matriculaService;
    }

    private Map<UUID, String> cargarMapaGradosEstudiantes() {
        Map<UUID, String> mapa = new HashMap<>();
        for (com.siga.siga_iea.clases.entity.CursoEstudiante ce : cursoEstudianteRepository.findAll()) {
            if (ce.getEstudiante() != null && ce.getCurso() != null) {
                com.siga.siga_iea.clases.entity.Curso c = ce.getCurso();
                mapa.put(ce.getEstudiante().getId(),
                        c.getGrado() + " - " + (c.getGrupo() != null ? c.getGrupo() : "01"));
            }
        }
        return mapa;
    }

    private String resolverGradoEstudiante(Estudiante e, Map<UUID, String> gradosCache) {
        if (e == null) return "Sin grado";
        String cached = gradosCache.get(e.getId());
        if (cached != null) return cached;
        return matriculaService.buscarUltimaMatriculaEstudiante(e.getId())
                .map(m -> (m.getGrado() != null ? m.getGrado() : "Sin grado") + " - " + (m.getSalon() != null ? m.getSalon() : "01"))
                .orElse("Sin grado");
    }

    private Optional<Usuario> getUsuarioLogueado() {
        return currentUserContextService.getUsuarioAutenticado();
    }

    private Optional<Estudiante> getEstudianteLogueado() {
        return getUsuarioLogueado()
                .map(Usuario::getNumeroDocumento)
                .filter(doc -> doc != null && !doc.isBlank())
                .flatMap(estudianteService::buscarPorNumeroDocumento);
    }

    private Optional<Docente> getDocenteLogueado() {
        return getUsuarioLogueado()
                .map(Usuario::getNumeroDocumento)
                .filter(doc -> doc != null && !doc.isBlank())
                .flatMap(doc -> personalService.buscarDocentes(doc, null).stream().findFirst());
    }

    private Optional<PersonalAdministrativo> getPersonalLogueado() {
        return getUsuarioLogueado()
                .map(Usuario::getNumeroDocumento)
                .filter(doc -> doc != null && !doc.isBlank())
                .flatMap(doc -> personalService.buscarPersonal(doc, null, null, null).stream().findFirst());
    }

    @GetMapping("/reportes")
    public String index(Model model) {
        model.addAttribute("title", "Reportes de Estudiantes – IEACI");
        model.addAttribute("activePage", "reportes");

        String userRole = currentUserContextService.getRolAutenticado().name();

        boolean esAdmin = currentUserContextService.esAdmin();
        boolean esPersonal = currentUserContextService.esPersonalAdministrativo();
        boolean esDocente = currentUserContextService.esDocente();
        Optional<Estudiante> estLogueadoOpt = getEstudianteLogueado();
        boolean esEstudiante = currentUserContextService.getRolAutenticado().esEstudiante() || estLogueadoOpt.isPresent();

        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("esPersonal", esPersonal);
        model.addAttribute("esDocente", esDocente);
        model.addAttribute("esEstudiante", esEstudiante);
        model.addAttribute("userRole", userRole);

        // Fetch students for report dropdown solo cuando la vista activa los utiliza (evita renderizar 676 <option> innecesarios)
        Map<UUID, String> gradosCache = cargarMapaGradosEstudiantes();
        UUID miEstudianteId = estLogueadoOpt.map(Estudiante::getId).orElse(null);
        List<Map<String, Object>> estudiantesList = new ArrayList<>();
        List<Map<String, Object>> companerosList = new ArrayList<>();

        if (esAdmin || esDocente || esEstudiante) {
            List<Estudiante> estudiantesDB = estudianteService.listarTodos();
            for (Estudiante e : estudiantesDB) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId().toString());
                map.put("nombre", e.getNombreCompleto());
                map.put("grado", resolverGradoEstudiante(e, gradosCache));
                if (esAdmin || esDocente) {
                    estudiantesList.add(map);
                }
                if ((esAdmin || esEstudiante) && (miEstudianteId == null || !miEstudianteId.equals(e.getId()))) {
                    companerosList.add(map);
                }
            }
        }

        // Fetch reports
        List<Reporte> reportesDB;
        if (esEstudiante && estLogueadoOpt.isPresent()) {
            reportesDB = reportesService.listarPorEstudiante(estLogueadoOpt.get().getId());
        } else if (esDocente && !esAdmin && !esPersonal) {
            Optional<Docente> docOpt = getDocenteLogueado();
            reportesDB = docOpt.isPresent()
                    ? reportesService.listarPorDocente(docOpt.get().getId())
                    : Collections.emptyList();
        } else {
            reportesDB = reportesService.listarTodos();
        }

        List<Map<String, Object>> reportesMock = new ArrayList<>();
        List<Map<String, Object>> reportesEnviadosEst = new ArrayList<>();
        List<Map<String, Object>> reportesRecibidosEst = new ArrayList<>();

        for (Reporte r : reportesDB) {
            Map<String, Object> map = new HashMap<>();
            map.put("dbId", r.getId().toString());
            map.put("id", r.getCodigo());
            boolean objEsDocente = "DOCENTE".equalsIgnoreCase(r.getObjetivoTipo());
            boolean repEsEstudiante = "ESTUDIANTE".equalsIgnoreCase(r.getReportanteTipo());

            String objetivoNombre = objEsDocente
                    ? (r.getDocente() != null ? "Prof. " + r.getDocente().getNombreCompleto() : "Docente")
                    : (r.getEstudiante() != null ? r.getEstudiante().getNombreCompleto() : "N/A");
            String objetivoDetalle = objEsDocente ? "Docente" : resolverGradoEstudiante(r.getEstudiante(), gradosCache);
            String emisorNombre = repEsEstudiante
                    ? (r.getReportanteEstudiante() != null ? r.getReportanteEstudiante().getNombreCompleto() + " (Estudiante)" : "Estudiante")
                    : (r.getDocente() != null ? r.getDocente().getNombreCompleto() : "N/A");

            map.put("estudiante", objetivoNombre);
            map.put("grado", objetivoDetalle);
            map.put("docente", emisorNombre);
            map.put("objetivoTipo", r.getObjetivoTipo() != null ? r.getObjetivoTipo() : "ESTUDIANTE");
            map.put("reportanteTipo", r.getReportanteTipo());
            map.put("categoria", r.getCategoria());
            map.put("razon", r.getRazon());
            map.put("descripcionRazon", r.getDescripcionRazon() != null ? r.getDescripcionRazon() : "");
            map.put("detalles", r.getDetalles());
            map.put("fecha", r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : "");
            map.put("estado", r.getEstado());
            map.put("fechaCitacion", r.getFechaCitacion() != null ? r.getFechaCitacion().toString().replace("T", " ") : null);
            map.put("requiereAcudiente", Boolean.TRUE.equals(r.getRequiereAcudiente()));
            map.put("observacionesAdmin", r.getObservacionesAdmin() != null ? r.getObservacionesAdmin() : "Sin observaciones adicionales de Dirección.");

            if (repEsEstudiante && miEstudianteId != null && r.getReportanteEstudiante() != null
                    && miEstudianteId.equals(r.getReportanteEstudiante().getId())) {
                reportesEnviadosEst.add(map);
            } else {
                reportesRecibidosEst.add(map);
            }
            reportesMock.add(map);
        }

        List<Map<String, Object>> reportesDocente = (esAdmin && !esDocente && reportesMock.size() > 25)
                ? reportesMock.subList(0, 25)
                : reportesMock;
        List<Map<String, Object>> reportesRecibidosVista = (esAdmin && !esEstudiante && reportesRecibidosEst.size() > 10)
                ? reportesRecibidosEst.subList(0, 10)
                : reportesRecibidosEst;

        model.addAttribute("estudiantesList", estudiantesList);
        model.addAttribute("companerosList", companerosList);
        model.addAttribute("docentesList", (esAdmin || esEstudiante) ? personalService.listarDocentes() : Collections.emptyList());
        model.addAttribute("reportesMock", reportesMock);
        model.addAttribute("reportesDocente", reportesDocente);
        model.addAttribute("reportesRecibidosEst", reportesRecibidosVista);
        model.addAttribute("reportesEnviadosEst", reportesEnviadosEst);
        return "reportes/index";
    }

    @PostMapping("/reportes/crear")
    public String crearReporte(
            @RequestParam(value = "objetivoTipo", defaultValue = "ESTUDIANTE") String objetivoTipo,
            @RequestParam(value = "estudianteId", required = false) UUID estudianteId,
            @RequestParam(value = "docenteId", required = false) UUID docenteId,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam("razon") String razon,
            @RequestParam(value = "descripcionRazon", required = false) String descripcionRazon,
            @RequestParam("detalles") String detalles,
            RedirectAttributes redirectAttributes) {

        try {
            boolean soloEstudiante = currentUserContextService.getRolAutenticado().esEstudiante()
                    && !currentUserContextService.esAdminOAdministrativo()
                    && !currentUserContextService.esDocente();

            if (soloEstudiante) {
                Estudiante reportante = getEstudianteLogueado()
                        .orElseThrow(() -> new AccessDeniedException("No se encontró el perfil estudiantil autenticado."));
                Usuario userLogueado = getUsuarioLogueado().orElse(null);
                reportesService.crearReporteEstudiantil(
                        reportante.getId(),
                        userLogueado,
                        objetivoTipo,
                        estudianteId,
                        docenteId,
                        razon,
                        detalles
                );
                redirectAttributes.addFlashAttribute("mensajeExito", "Reporte de convivencia enviado a Coordinación correctamente.");
                return "redirect:/reportes";
            }

            if (estudianteId == null) {
                throw new IllegalArgumentException("Debe seleccionar el estudiante a reportar.");
            }

            UUID targetDocenteId = docenteId;
            if (targetDocenteId == null) {
                Optional<Docente> docOpt = getDocenteLogueado();
                if (docOpt.isPresent()) {
                    targetDocenteId = docOpt.get().getId();
                } else {
                    List<Docente> docentes = personalService.listarDocentes();
                    if (!docentes.isEmpty()) {
                        targetDocenteId = docentes.get(0).getId();
                    } else {
                        throw new IllegalStateException("Debe existir al menos un docente registrado en el sistema para emitir un reporte.");
                    }
                }
            }

            String targetCategoria = categoria;
            if (targetCategoria == null || targetCategoria.isBlank()) {
                if (Arrays.asList("Agresión", "Falta de respeto", "Daño a la escuela").contains(razon)) {
                    targetCategoria = "Convivencia y Disciplina";
                } else if (Arrays.asList("Ausencias", "Llegadas tarde", "Salud o riesgo").contains(razon)) {
                    targetCategoria = "Asistencia y Salud";
                } else if ("Otro".equals(razon)) {
                    targetCategoria = "Otras Razones";
                } else {
                    targetCategoria = "Razones Académicas";
                }
            }

            reportesService.crearReporte(estudianteId, targetDocenteId, targetCategoria, razon, descripcionRazon, detalles);
            redirectAttributes.addFlashAttribute("mensajeExito", "Reporte registrado exitosamente en el sistema.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al registrar reporte: " + ex.getMessage());
        }

        return "redirect:/reportes";
    }

    @PostMapping("/reportes/atender")
    public String atenderReporte(
            @RequestParam("reporteId") UUID reporteId,
            @RequestParam("decision") String decision,
            @RequestParam(value = "fechaCitacionStr", required = false) String fechaCitacionStr,
            @RequestParam(value = "fechaCitacion", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaCitacion,
            @RequestParam(value = "requiereAcudiente", required = false, defaultValue = "false") Boolean requiereAcudiente,
            @RequestParam(value = "observacionesAdmin", required = false) String observacionesAdmin,
            @RequestParam(value = "adminId", required = false) UUID adminId,
            RedirectAttributes redirectAttributes) {

        if (!currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración o Coordinación puede resolver reportes y programar citaciones.");
        }

        try {
            LocalDateTime parsedDate = fechaCitacion;
            if (parsedDate == null && fechaCitacionStr != null && !fechaCitacionStr.isBlank()) {
                try {
                    parsedDate = LocalDateTime.parse(fechaCitacionStr.replace(" ", "T"));
                } catch (Exception ignored) {
                    try {
                        String cleaned = fechaCitacionStr.split(" - ")[0] + "T" + (fechaCitacionStr.contains(" - ") ? fechaCitacionStr.split(" - ")[1].replace(" AM", "").replace(" PM", "") : "08:00");
                        parsedDate = LocalDateTime.parse(cleaned);
                    } catch (Exception ignored2) {
                        parsedDate = LocalDateTime.now().plusDays(2);
                    }
                }
            }

            UUID targetAdminId = adminId;
            if (targetAdminId == null) {
                Optional<PersonalAdministrativo> adminOpt = getPersonalLogueado();
                if (adminOpt.isPresent()) {
                    targetAdminId = adminOpt.get().getId();
                }
            }

            reportesService.atenderReporte(reporteId, decision, parsedDate, requiereAcudiente, observacionesAdmin, targetAdminId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Reporte procesado y citación emitida exitosamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al procesar reporte: " + ex.getMessage());
        }

        return "redirect:/reportes";
    }
}

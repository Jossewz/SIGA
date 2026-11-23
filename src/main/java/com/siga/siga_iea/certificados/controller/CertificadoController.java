package com.siga.siga_iea.certificados.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.certificados.service.CertificadoService;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.service.MatriculaService;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.EstudianteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class CertificadoController {

    private final CertificadoService certificadoService;
    private final EstudianteService estudianteService;
    private final CurrentUserContextService currentUserContextService;
    private final MatriculaService matriculaService;
    private final com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository;
    private final com.siga.siga_iea.certificados.service.CertificadoPdfService certificadoPdfService;
    private final PeriodoConfigService periodoConfigService;

    @Autowired
    public CertificadoController(CertificadoService certificadoService,
                                 EstudianteService estudianteService,
                                 CurrentUserContextService currentUserContextService,
                                 MatriculaService matriculaService,
                                 com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository,
                                 com.siga.siga_iea.certificados.service.CertificadoPdfService certificadoPdfService,
                                 @Autowired(required = false) PeriodoConfigService periodoConfigService) {
        this.certificadoService = certificadoService;
        this.estudianteService = estudianteService;
        this.currentUserContextService = currentUserContextService;
        this.matriculaService = matriculaService;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.certificadoPdfService = certificadoPdfService;
        this.periodoConfigService = periodoConfigService;
    }

    public CertificadoController(CertificadoService certificadoService,
                                 EstudianteService estudianteService,
                                 CurrentUserContextService currentUserContextService,
                                 MatriculaService matriculaService,
                                 com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository,
                                 com.siga.siga_iea.certificados.service.CertificadoPdfService certificadoPdfService) {
        this(certificadoService, estudianteService, currentUserContextService, matriculaService,
                cursoEstudianteRepository, certificadoPdfService, null);
    }

    private String resolverGradoEstudiante(Estudiante e) {
        if (e == null) return "Sin grado";
        List<com.siga.siga_iea.clases.entity.CursoEstudiante> ces = cursoEstudianteRepository.findByEstudianteId(e.getId());
        if (!ces.isEmpty()) {
            com.siga.siga_iea.clases.entity.Curso c = ces.get(ces.size() - 1).getCurso();
            return c.getGrado() + " - " + (c.getGrupo() != null ? c.getGrupo() : "01");
        }
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

    @GetMapping("/certificados")
    public String index(Model model) {
        model.addAttribute("title", "Certificados y Constancias – IEACI");
        model.addAttribute("activePage", "certificados");

        String userRole = currentUserContextService.getRolAutenticado().name();
        boolean esAdmin = currentUserContextService.esAdmin();
        boolean esPersonal = currentUserContextService.esPersonalAdministrativo();
        Optional<Estudiante> estLogueadoOpt = getEstudianteLogueado();
        boolean esEstudiante = currentUserContextService.getRolAutenticado().esEstudiante() || estLogueadoOpt.isPresent();

        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("esPersonal", esPersonal);
        model.addAttribute("esEstudiante", esEstudiante);
        model.addAttribute("userRole", userRole);

        Estudiante estudianteSeleccionado = null;
        List<SolicitudCertificado> solicitudesEstudianteDB;

        if (esEstudiante && !esAdmin && !esPersonal) {
            model.addAttribute("listaEstudiantes", estLogueadoOpt.map(List::of).orElse(Collections.emptyList()));
            if (estLogueadoOpt.isPresent()) {
                estudianteSeleccionado = estLogueadoOpt.get();
                solicitudesEstudianteDB = certificadoService.listarPorEstudiante(estudianteSeleccionado.getId());
            } else {
                solicitudesEstudianteDB = Collections.emptyList();
            }
        } else {
            List<Estudiante> todosEstudiantes = estudianteService.listarTodos();
            model.addAttribute("listaEstudiantes", todosEstudiantes);
            if (!todosEstudiantes.isEmpty()) {
                estudianteSeleccionado = todosEstudiantes.get(0);
                solicitudesEstudianteDB = certificadoService.listarPorEstudiante(estudianteSeleccionado.getId());
            } else {
                solicitudesEstudianteDB = Collections.emptyList();
            }
        }

        if (estudianteSeleccionado != null) {
            model.addAttribute("estudianteNombre", estudianteSeleccionado.getNombreCompleto());
            String grado = resolverGradoEstudiante(estudianteSeleccionado);
            model.addAttribute("estudianteGrado", grado);
            model.addAttribute("estudianteDocumento", estudianteSeleccionado.getNumeroDocumento());
            model.addAttribute("estudianteId", estudianteSeleccionado.getId());
        } else {
            model.addAttribute("estudianteNombre", "Sin estudiantes registrados");
            model.addAttribute("estudianteGrado", "-");
            model.addAttribute("estudianteDocumento", "-");
            model.addAttribute("estudianteId", null);
        }

        // Listar solicitudes del estudiante (privadas)
        List<Map<String, Object>> solicitudesEstudianteMock = mapearSolicitudes(solicitudesEstudianteDB);
        model.addAttribute("solicitudesEstudianteMock", solicitudesEstudianteMock);

        // Listar todas las solicitudes solo para personal administrativo o admin
        List<Map<String, Object>> solicitudesAdminMock = (esAdmin || esPersonal)
                ? mapearSolicitudes(certificadoService.listarTodas())
                : Collections.emptyList();
        model.addAttribute("solicitudesAdminMock", solicitudesAdminMock);

        // Compatibilidad
        model.addAttribute("solicitudesMock", solicitudesEstudianteMock);

        return "certificados/index";
    }

    private List<Map<String, Object>> mapearSolicitudes(List<SolicitudCertificado> lista) {
        Map<UUID, String> gradosCache = new HashMap<>();
        boolean necesitaCache = lista.stream().anyMatch(s -> s.getGradoReferencia() == null || s.getGradoReferencia().isBlank());
        if (necesitaCache) {
            for (com.siga.siga_iea.clases.entity.CursoEstudiante ce : cursoEstudianteRepository.findAll()) {
                if (ce.getEstudiante() != null && ce.getCurso() != null) {
                    com.siga.siga_iea.clases.entity.Curso c = ce.getCurso();
                    gradosCache.put(ce.getEstudiante().getId(),
                            c.getGrado() + " - " + (c.getGrupo() != null ? c.getGrupo() : "01"));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SolicitudCertificado s : lista) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getCodigo());
            map.put("estudiante", s.getEstudiante() != null ? s.getEstudiante().getNombreCompleto() : "N/A");
            map.put("documento", s.getEstudiante() != null ? s.getEstudiante().getNumeroDocumento() : "N/A");
            String grado;
            if (s.getGradoReferencia() != null && !s.getGradoReferencia().isBlank()) {
                grado = s.getGradoReferencia();
            } else if (s.getEstudiante() != null && gradosCache.containsKey(s.getEstudiante().getId())) {
                grado = gradosCache.get(s.getEstudiante().getId());
            } else {
                grado = resolverGradoEstudiante(s.getEstudiante());
            }
            map.put("grado", grado);
            map.put("tipo", s.getTipo());
            map.put("categoria", s.getCategoria());
            map.put("motivo", s.getMotivo());
            map.put("fecha", s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate().toString() : "");
            map.put("estado", s.getEstado());
            map.put("mensajeRespuesta", s.getMensajeRespuesta());
            map.put("archivoAdjunto", s.getArchivoAdjuntoKey() != null ? "/storage/public/view?key=" + s.getArchivoAdjuntoKey() : null);
            map.put("pdfUrl", "/certificados/" + s.getCodigo() + "/pdf");
            map.put("descargaUrl", s.getArchivoAdjuntoKey() != null ? "/storage/public/view?key=" + s.getArchivoAdjuntoKey() : "/certificados/" + s.getCodigo() + "/pdf");
            map.put("respondidoPor", s.getRespondidoPor() != null ? s.getRespondidoPor().getNombreCompleto() : "Secretaría Académica");
            result.add(map);
        }
        return result;
    }

    @PostMapping("/certificados/solicitar")
    public String solicitarCertificado(
            @RequestParam(value = "estudianteId", required = false) UUID estudianteId,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam("motivo") String motivo,
            @RequestParam(value = "anoLectivo", required = false) String anoLectivo,
            @RequestParam(value = "gradoReferencia", required = false, defaultValue = "11°") String gradoReferencia,
            RedirectAttributes redirectAttributes) {

        try {
            boolean esAdminOPersonal = currentUserContextService.esAdminOAdministrativo();
            if (!esAdminOPersonal) {
                Optional<Estudiante> estOpt = getEstudianteLogueado();
                if (estOpt.isEmpty()) {
                    throw new AccessDeniedException("Debe iniciar sesión con un perfil estudiantil para solicitar certificados.");
                }
                estudianteId = estOpt.get().getId();
            } else if (estudianteId == null) {
                Optional<Estudiante> estOpt = getEstudianteLogueado();
                if (estOpt.isPresent()) {
                    estudianteId = estOpt.get().getId();
                } else {
                    throw new IllegalArgumentException("Debe seleccionar un estudiante para generar la solicitud.");
                }
            }

            String anioFinal = (anoLectivo != null && !anoLectivo.isBlank())
                    ? anoLectivo.trim()
                    : (periodoConfigService != null
                            ? periodoConfigService.obtenerAnioLectivoCodigo()
                            : String.valueOf(PeriodoConfigService.anioCalendarioActual()));

            SolicitudCertificado sol = certificadoService.crearSolicitud(estudianteId, tipo, categoria, motivo, anioFinal, gradoReferencia);
            redirectAttributes.addFlashAttribute("mensajeExito", "Solicitud (" + sol.getCodigo() + ") de " + tipo + " enviada correctamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al solicitar certificado: " + ex.getMessage());
        }

        return "redirect:/certificados";
    }

    @PostMapping("/certificados/responder")
    public String responderSolicitud(
            @RequestParam("solicitudId") String solicitudIdStr,
            @RequestParam("mensajeRespuesta") String mensajeRespuesta,
            @RequestParam(value = "archivoPDF", required = false) MultipartFile archivoPDF,
            @RequestParam(value = "decision", required = false, defaultValue = "Resuelto") String decision,
            @RequestParam(value = "adminId", required = false) UUID adminId,
            RedirectAttributes redirectAttributes) {

        if (!currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede responder solicitudes de certificados.");
        }

        try {
            Optional<SolicitudCertificado> solOpt = Optional.empty();

            try {
                UUID uuid = UUID.fromString(solicitudIdStr);
                solOpt = certificadoService.buscarPorId(uuid);
            } catch (IllegalArgumentException ignored) {}

            if (solOpt.isEmpty()) {
                solOpt = certificadoService.buscarPorCodigo(solicitudIdStr);
            }

            if (solOpt.isEmpty()) {
                throw new IllegalArgumentException("No se encontró la solicitud de certificado especificada.");
            }

            SolicitudCertificado sol = solOpt.get();
            certificadoService.responderSolicitud(sol.getId(), mensajeRespuesta, archivoPDF, adminId, decision);
            redirectAttributes.addFlashAttribute("mensajeExito", "Solicitud " + sol.getCodigo() + " procesada exitosamente (" + decision + ").");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al responder solicitud: " + ex.getMessage());
        }

        return "redirect:/certificados";
    }

    @GetMapping({"/certificados/{codigoOId}/pdf", "/certificados/descargar/{codigoOId}"})
    public ResponseEntity<byte[]> descargarCertificadoPdf(@PathVariable("codigoOId") String codigoOId) {
        Optional<SolicitudCertificado> solOpt = Optional.empty();

        try {
            UUID uuid = UUID.fromString(codigoOId);
            solOpt = certificadoService.buscarPorId(uuid);
        } catch (IllegalArgumentException ignored) {}

        if (solOpt.isEmpty()) {
            solOpt = certificadoService.buscarPorCodigo(codigoOId);
        }

        if (solOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SolicitudCertificado sol = solOpt.get();

        // Control de permisos: Estudiante solo descarga sus propios certificados
        if (!currentUserContextService.esAdmin() && !currentUserContextService.esPersonalAdministrativo()) {
            Optional<Estudiante> estLogueado = getEstudianteLogueado();
            if (estLogueado.isEmpty() || sol.getEstudiante() == null ||
                    !estLogueado.get().getId().equals(sol.getEstudiante().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        byte[] pdfBytes = certificadoPdfService.generarCertificadoPdf(sol);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String filename = "Certificado_" + (sol.getCodigo() != null ? sol.getCodigo() : "IEACI") + ".pdf";
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}

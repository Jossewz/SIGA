package com.siga.siga_iea.calificaciones.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.calificaciones.dto.BoletinMateriaDTO;
import com.siga.siga_iea.calificaciones.service.BoletinPdfService;
import com.siga.siga_iea.calificaciones.service.CalificacionesService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.service.CursoService;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.service.MatriculaService;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.EstudianteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
public class CalificacionController {

    private final CalificacionesService calificacionesService;
    private final EstudianteService estudianteService;
    private final CursoService cursoService;
    private final PeriodoConfigService periodoConfigService;
    private final CursoEstudianteRepository cursoEstudianteRepository;
    private final MatriculaService matriculaService;
    private final BoletinPdfService boletinPdfService;
    private final CurrentUserContextService currentUserContextService;

    @Autowired
    public CalificacionController(CalificacionesService calificacionesService,
                                  EstudianteService estudianteService,
                                  CursoService cursoService,
                                  PeriodoConfigService periodoConfigService,
                                  CursoEstudianteRepository cursoEstudianteRepository,
                                  MatriculaService matriculaService,
                                  BoletinPdfService boletinPdfService,
                                  @Autowired(required = false) CurrentUserContextService currentUserContextService) {
        this.calificacionesService = calificacionesService;
        this.estudianteService = estudianteService;
        this.cursoService = cursoService;
        this.periodoConfigService = periodoConfigService;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.matriculaService = matriculaService;
        this.boletinPdfService = boletinPdfService;
        this.currentUserContextService = currentUserContextService;
    }

    public CalificacionController(CalificacionesService calificacionesService,
                                  EstudianteService estudianteService,
                                  CursoService cursoService,
                                  PeriodoConfigService periodoConfigService,
                                  CursoEstudianteRepository cursoEstudianteRepository,
                                  MatriculaService matriculaService,
                                  BoletinPdfService boletinPdfService) {
        this(calificacionesService, estudianteService, cursoService, periodoConfigService,
                cursoEstudianteRepository, matriculaService, boletinPdfService, null);
    }

    private boolean esSoloEstudiante() {
        return currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && currentUserContextService.getRolAutenticado().esEstudiante()
                && !currentUserContextService.esAdminOAdministrativo()
                && !currentUserContextService.esDocente();
    }

    private Optional<Estudiante> resolverEstudianteAutenticado() {
        if (currentUserContextService == null) return Optional.empty();
        return currentUserContextService.getUsuarioAutenticado()
                .map(Usuario::getNumeroDocumento)
                .filter(doc -> doc != null && !doc.isBlank())
                .flatMap(estudianteService::buscarPorNumeroDocumento);
    }

    @GetMapping("/calificaciones")
    public String index(
            @RequestParam(value = "estudianteId", required = false) UUID estudianteId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            Model model) {
        model.addAttribute("title", "Boletín de Calificaciones – IEACI");
        model.addAttribute("activePage", "calificaciones");

        boolean soloEstudiante = esSoloEstudiante();
        model.addAttribute("soloVistaEstudiante", soloEstudiante);

        Estudiante seleccionado = null;
        if (soloEstudiante) {
            Optional<Estudiante> propio = resolverEstudianteAutenticado();
            if (propio.isPresent()) {
                if (estudianteId != null && !estudianteId.equals(propio.get().getId())) {
                    throw new AccessDeniedException("No tiene permiso para consultar calificaciones de otros estudiantes.");
                }
                seleccionado = propio.get();
                model.addAttribute("estudiantes", List.of(seleccionado));
            } else {
                model.addAttribute("estudiantes", Collections.emptyList());
            }
        } else {
            List<Estudiante> estudiantes = estudianteService.listarTodos();
            model.addAttribute("estudiantes", estudiantes);
            if (estudianteId != null) {
                seleccionado = estudianteService.buscarPorId(estudianteId).orElse(null);
            }
            if (seleccionado == null && !estudiantes.isEmpty()) {
                seleccionado = estudiantes.get(0);
            }
        }

        Map<Integer, BigDecimal> ponderaciones = periodoConfigService.getPonderaciones();
        model.addAttribute("ponderaciones", ponderaciones);
        model.addAttribute("pesoP1", periodoConfigService.getPesoPeriodo1());
        model.addAttribute("pesoP2", periodoConfigService.getPesoPeriodo2());
        model.addAttribute("pesoP3", periodoConfigService.getPesoPeriodo3());

        String anioActivo = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());
        model.addAttribute("anoLectivo", anioActivo);

        if (seleccionado != null) {
            model.addAttribute("estudianteSeleccionadoId", seleccionado.getId().toString());
            model.addAttribute("estudianteNombre", seleccionado.getNombreCompleto());
            model.addAttribute("estudianteDocumento", seleccionado.getNumeroDocumento());

            String gradoStr = "Sin grado";
            List<CursoEstudiante> ces = cursoEstudianteRepository.findByEstudianteId(seleccionado.getId());
            if (!ces.isEmpty()) {
                Curso c = ces.get(ces.size() - 1).getCurso();
                gradoStr = c.getGrado() + " - " + (c.getGrupo() != null ? c.getGrupo() : "01");
            } else {
                Optional<Matricula> matOpt = matriculaService.buscarUltimaMatriculaEstudiante(seleccionado.getId());
                if (matOpt.isPresent() && matOpt.get().getGrado() != null) {
                    gradoStr = matOpt.get().getGrado() + " - " + (matOpt.get().getSalon() != null ? matOpt.get().getSalon() : "01");
                }
            }
            model.addAttribute("estudianteGrado", gradoStr);

            List<BoletinMateriaDTO> boletinCompleto = calificacionesService.obtenerBoletinEstudiante(seleccionado.getId(), anioActivo, ponderaciones);
            int safeSize = Math.max(1, Math.min(size, 50));
            int totalAsig = boletinCompleto.size();
            int totalPages = totalAsig == 0 ? 1 : (int) Math.ceil((double) totalAsig / safeSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int fromIdx = safePage * safeSize;
            int toIdx = Math.min(fromIdx + safeSize, totalAsig);
            List<BoletinMateriaDTO> paginaBoletin = fromIdx < totalAsig
                    ? boletinCompleto.subList(fromIdx, toIdx)
                    : Collections.emptyList();
            org.springframework.data.domain.Page<BoletinMateriaDTO> boletinPage = new org.springframework.data.domain.PageImpl<>(
                    paginaBoletin, org.springframework.data.domain.PageRequest.of(safePage, safeSize), totalAsig);
            model.addAttribute("boletin", paginaBoletin);
            model.addAttribute("boletinPage", boletinPage);
            model.addAttribute("totalAsignaturas", totalAsig);
        } else {
            model.addAttribute("estudianteSeleccionadoId", "");
            model.addAttribute("estudianteNombre", "Sin estudiante registrado");
            model.addAttribute("estudianteDocumento", "-");
            model.addAttribute("estudianteGrado", "-");
            model.addAttribute("boletin", Collections.emptyList());
            model.addAttribute("boletinPage", org.springframework.data.domain.Page.empty());
            model.addAttribute("totalAsignaturas", 0);
        }

        List<Curso> cursos = cursoService.listarTodosLosCursos();
        model.addAttribute("cursosList", cursos);

        return "calificaciones/index";
    }

    @GetMapping("/calificaciones/boletin/pdf")
    public ResponseEntity<byte[]> descargarBoletinPdf(
            @RequestParam("estudianteId") UUID estudianteId,
            @RequestParam(value = "anoLectivo", required = false) String anoLectivo) {

        if (esSoloEstudiante()) {
            Optional<Estudiante> propio = resolverEstudianteAutenticado();
            if (propio.isEmpty() || !propio.get().getId().equals(estudianteId)) {
                throw new AccessDeniedException("Solo puedes descargar tu propio boletín de calificaciones.");
            }
        }

        Estudiante estudiante = estudianteService.buscarPorId(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));

        String anio;
        if (anoLectivo != null && !anoLectivo.isBlank()) {
            anio = anoLectivo.trim();
        } else if (periodoConfigService != null) {
            anio = periodoConfigService.obtenerAnioLectivoCodigo();
        } else {
            anio = String.valueOf(PeriodoConfigService.anioCalendarioActual());
        }

        String gradoStr = "Sin grado";
        List<CursoEstudiante> ces = cursoEstudianteRepository.findByEstudianteId(estudiante.getId());
        if (!ces.isEmpty()) {
            Curso c = ces.get(ces.size() - 1).getCurso();
            gradoStr = c.getGrado() + " - " + (c.getGrupo() != null ? c.getGrupo() : "01");
        } else {
            Optional<Matricula> matOpt = matriculaService.buscarUltimaMatriculaEstudiante(estudiante.getId());
            if (matOpt.isPresent() && matOpt.get().getGrado() != null) {
                gradoStr = matOpt.get().getGrado() + " - " + (matOpt.get().getSalon() != null ? matOpt.get().getSalon() : "01");
            }
        }

        Map<Integer, BigDecimal> ponderaciones = periodoConfigService.getPonderaciones();
        List<BoletinMateriaDTO> boletin = calificacionesService.obtenerBoletinEstudiante(estudiante.getId(), anio, ponderaciones);

        byte[] pdfBytes = boletinPdfService.generarBoletinPdf(estudiante, gradoStr, anio, boletin);

        String filename = "Boletin_" + estudiante.getNumeroDocumento() + "_" + anio + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }
}

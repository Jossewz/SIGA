package com.siga.siga_iea.calificaciones.controller;

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
import com.siga.siga_iea.usuarios.service.EstudianteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public CalificacionController(CalificacionesService calificacionesService, 
                                  EstudianteService estudianteService,
                                  CursoService cursoService,
                                  PeriodoConfigService periodoConfigService,
                                  CursoEstudianteRepository cursoEstudianteRepository,
                                  MatriculaService matriculaService,
                                  BoletinPdfService boletinPdfService) {
        this.calificacionesService = calificacionesService;
        this.estudianteService = estudianteService;
        this.cursoService = cursoService;
        this.periodoConfigService = periodoConfigService;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.matriculaService = matriculaService;
        this.boletinPdfService = boletinPdfService;
    }

    @GetMapping("/calificaciones")
    public String index(@RequestParam(value = "estudianteId", required = false) UUID estudianteId, Model model) {
        model.addAttribute("title", "Boletín de Calificaciones – IEACI");
        model.addAttribute("activePage", "calificaciones");

        List<Estudiante> estudiantes = estudianteService.listarTodos();
        model.addAttribute("estudiantes", estudiantes);

        Estudiante seleccionado = null;
        if (estudianteId != null) {
            seleccionado = estudianteService.buscarPorId(estudianteId).orElse(null);
        }
        if (seleccionado == null && !estudiantes.isEmpty()) {
            seleccionado = estudiantes.get(0);
        }

        Map<Integer, BigDecimal> ponderaciones = periodoConfigService.getPonderaciones();
        model.addAttribute("ponderaciones", ponderaciones);
        model.addAttribute("pesoP1", periodoConfigService.getPesoPeriodo1());
        model.addAttribute("pesoP2", periodoConfigService.getPesoPeriodo2());
        model.addAttribute("pesoP3", periodoConfigService.getPesoPeriodo3());

        String anioActivo = "2026";
        try {
            if (periodoConfigService != null && periodoConfigService.obtenerAnioLectivoActual() != null) {
                anioActivo = String.valueOf(periodoConfigService.obtenerAnioLectivoActual().getAnio());
            }
        } catch (Exception ignored) {
        }
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

            List<BoletinMateriaDTO> boletin = calificacionesService.obtenerBoletinEstudiante(seleccionado.getId(), anioActivo, ponderaciones);
            model.addAttribute("boletin", boletin);
        } else {
            model.addAttribute("estudianteSeleccionadoId", "");
            model.addAttribute("estudianteNombre", "Sin estudiante registrado");
            model.addAttribute("estudianteDocumento", "-");
            model.addAttribute("estudianteGrado", "-");
            model.addAttribute("boletin", Collections.emptyList());
        }

        List<Curso> cursos = cursoService.listarTodosLosCursos();
        model.addAttribute("cursosList", cursos);

        return "calificaciones/index";
    }

    @GetMapping("/calificaciones/boletin/pdf")
    public ResponseEntity<byte[]> descargarBoletinPdf(
            @RequestParam("estudianteId") UUID estudianteId,
            @RequestParam(value = "anoLectivo", required = false) String anoLectivo) {

        Estudiante estudiante = estudianteService.buscarPorId(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));

        String anio = "2026";
        if (anoLectivo != null && !anoLectivo.isBlank()) {
            anio = anoLectivo.trim();
        } else if (periodoConfigService != null && periodoConfigService.obtenerAnioLectivoActual() != null) {
            anio = String.valueOf(periodoConfigService.obtenerAnioLectivoActual().getAnio());
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

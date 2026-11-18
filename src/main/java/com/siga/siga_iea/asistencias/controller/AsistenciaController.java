package com.siga.siga_iea.asistencias.controller;

import com.siga.siga_iea.asistencias.dto.AsistenciaItemDto;
import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.asistencias.service.AsistenciaService;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.service.CursoService;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.EstudianteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Controller
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    private final AsistenciaRepository asistenciaRepository;
    private final CurrentUserContextService currentUserContextService;
    private final EstudianteService estudianteService;
    private final CursoService cursoService;

    @Autowired
    public AsistenciaController(AsistenciaService asistenciaService,
                                @Autowired(required = false) AsistenciaRepository asistenciaRepository,
                                @Autowired(required = false) CurrentUserContextService currentUserContextService,
                                @Autowired(required = false) EstudianteService estudianteService,
                                @Autowired(required = false) CursoService cursoService) {
        this.asistenciaService = asistenciaService;
        this.asistenciaRepository = asistenciaRepository;
        this.currentUserContextService = currentUserContextService;
        this.estudianteService = estudianteService;
        this.cursoService = cursoService;
    }

    public AsistenciaController(AsistenciaService asistenciaService) {
        this(asistenciaService, null, null, null, null);
    }

    private void exigirDocenteOAdmin() {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()
                && !currentUserContextService.esDocente()) {
            throw new AccessDeniedException("Solo docentes o administración pueden registrar o consultar matrices de asistencia.");
        }
    }

    @GetMapping("/asistencias")
    public String index(
            @RequestParam(value = "cursoId", required = false) UUID cursoId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            Model model) {
        model.addAttribute("title", "Asistencias – IEACI");
        model.addAttribute("activePage", "asistencias");

        boolean esAdminOAdmin = currentUserContextService != null && currentUserContextService.esAdminOAdministrativo();
        boolean esDocente = currentUserContextService != null && currentUserContextService.esDocente();
        boolean esEstudiante = currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && currentUserContextService.getRolAutenticado().esEstudiante();

        if (esDocente && !esAdminOAdmin) {
            return "redirect:/mi-jornada";
        }

        LocalDate fecha = LocalDate.now(ZoneId.of("America/Bogota"));
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                fecha = LocalDate.parse(fechaStr.trim());
            } catch (Exception ignored) {}
        }
        model.addAttribute("fechaFiltro", fecha.toString());

        if (esEstudiante && !esAdminOAdmin && asistenciaRepository != null && estudianteService != null) {
            Optional<Estudiante> estOpt = currentUserContextService.getUsuarioAutenticado()
                    .map(Usuario::getNumeroDocumento)
                    .filter(d -> d != null && !d.isBlank())
                    .flatMap(estudianteService::buscarPorNumeroDocumento);

            List<Map<String, Object>> historial = new ArrayList<>();
            long cntPresente = 0, cntTarde = 0, cntExcusado = 0, cntAusente = 0;

            if (estOpt.isPresent()) {
                List<Asistencia> registros = new ArrayList<>(asistenciaRepository.findByEstudianteId(estOpt.get().getId()));
                registros.sort((a, b) -> {
                    LocalDate fa = a.getSesion() != null ? a.getSesion().getFecha() : LocalDate.MIN;
                    LocalDate fb = b.getSesion() != null ? b.getSesion().getFecha() : LocalDate.MIN;
                    return fb.compareTo(fa);
                });
                for (Asistencia a : registros) {
                    String st = a.getEstado() != null ? a.getEstado().toUpperCase() : "PRESENTE";
                    if ("PRESENTE".equals(st)) cntPresente++;
                    else if ("TARDE".equals(st)) cntTarde++;
                    else if ("EXCUSADO".equals(st)) cntExcusado++;
                    else if ("AUSENTE".equals(st)) cntAusente++;

                    SesionClase sc = a.getSesion();
                    String materia = (sc != null && sc.getCursoMateria() != null && sc.getCursoMateria().getMateria() != null)
                            ? sc.getCursoMateria().getMateria().getNombre()
                            : (sc != null && sc.getHorario() != null && sc.getHorario().getMateria() != null
                                    ? sc.getHorario().getMateria().getNombre() : "Jornada");
                    Map<String, Object> row = new HashMap<>();
                    row.put("fecha", sc != null && sc.getFecha() != null ? sc.getFecha().toString() : "-");
                    row.put("materia", materia);
                    row.put("docente", sc != null && sc.getDocente() != null ? sc.getDocente().getNombreCompleto() : "-");
                    row.put("estado", st);
                    row.put("observaciones", a.getObservaciones() != null ? a.getObservaciones() : "-");
                    historial.add(row);
                }
            }

            model.addAttribute("modoVista", "ESTUDIANTE");
            model.addAttribute("historialEstudiante", historial);
            model.addAttribute("cntPresente", cntPresente);
            model.addAttribute("cntTarde", cntTarde);
            model.addAttribute("cntExcusado", cntExcusado);
            model.addAttribute("cntAusente", cntAusente);
            return "asistencias/index";
        }

        // Vista consolidada para Admin / Personal Administrativo
        List<Curso> cursos = cursoService != null ? cursoService.listarTodosLosCursos() : Collections.emptyList();
        model.addAttribute("cursos", cursos);
        Curso cursoSeleccionado = null;
        if (cursoId != null) {
            cursoSeleccionado = cursos.stream().filter(c -> c.getId().equals(cursoId)).findFirst().orElse(null);
        }
        if (cursoSeleccionado == null && !cursos.isEmpty()) {
            cursoSeleccionado = cursos.get(0);
        }

        List<Map<String, Object>> registrosCurso = new ArrayList<>();
        long cntPresente = 0, cntTarde = 0, cntExcusado = 0, cntAusente = 0;
        if (cursoSeleccionado != null && asistenciaRepository != null) {
            for (Asistencia a : asistenciaRepository.findByCursoIdAndFecha(cursoSeleccionado.getId(), fecha)) {
                String st = a.getEstado() != null ? a.getEstado().toUpperCase() : "PRESENTE";
                if ("PRESENTE".equals(st)) cntPresente++;
                else if ("TARDE".equals(st)) cntTarde++;
                else if ("EXCUSADO".equals(st)) cntExcusado++;
                else if ("AUSENTE".equals(st)) cntAusente++;

                SesionClase sc = a.getSesion();
                String materia = (sc != null && sc.getCursoMateria() != null && sc.getCursoMateria().getMateria() != null)
                        ? sc.getCursoMateria().getMateria().getNombre()
                        : (sc != null && sc.getHorario() != null && sc.getHorario().getMateria() != null
                                ? sc.getHorario().getMateria().getNombre() : "Jornada");
                Map<String, Object> row = new HashMap<>();
                row.put("estudiante", a.getEstudiante() != null ? a.getEstudiante().getNombreCompleto() : "N/A");
                row.put("documento", a.getEstudiante() != null ? a.getEstudiante().getNumeroDocumento() : "-");
                row.put("materia", materia);
                row.put("docente", sc != null && sc.getDocente() != null ? sc.getDocente().getNombreCompleto() : "-");
                row.put("estado", st);
                registrosCurso.add(row);
            }
        }

        model.addAttribute("modoVista", "ADMIN");
        model.addAttribute("cursoSeleccionado", cursoSeleccionado);
        model.addAttribute("registrosCurso", registrosCurso);
        model.addAttribute("cntPresente", cntPresente);
        model.addAttribute("cntTarde", cntTarde);
        model.addAttribute("cntExcusado", cntExcusado);
        model.addAttribute("cntAusente", cntAusente);
        return "asistencias/index";
    }

    /**
     * Endpoint HTTP para apertura de día de un curso.
     * Idempotente y resiliente ante concurrencia.
     */
    @PostMapping("/asistencias/abrir-dia")
    @ResponseBody
    public ResponseEntity<List<SesionClase>> abrirDia(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "fecha", required = false) String fechaStr) {
        exigirDocenteOAdmin();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now();
        List<SesionClase> sesiones = asistenciaService.abrirDia(cursoId, fecha);
        return ResponseEntity.ok(sesiones);
    }

    /**
     * Endpoint HTTP de alta frecuencia para registro masivo de asistencia (hasta 40 estudiantes por sesión).
     * Realiza upsert y valida pertenencia y autorización.
     */
    @PostMapping("/asistencias/sesiones/{sesionId}/registrar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registrarAsistencia(
            @PathVariable("sesionId") UUID sesionId,
            @RequestBody List<AsistenciaItemDto> items) {
        exigirDocenteOAdmin();
        List<Asistencia> registradas = asistenciaService.registrar(sesionId, items);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sesionId", sesionId,
                "registrosProcesados", registradas.size()
        ));
    }

    /**
     * Endpoint de consulta para lectura de asistencia de un curso en una fecha y materia.
     */
    @GetMapping("/asistencias/curso/{cursoId}/mapa")
    @ResponseBody
    public ResponseEntity<Map<UUID, String>> obtenerMapa(
            @PathVariable("cursoId") UUID cursoId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "materiaId", required = false) UUID materiaId) {
        exigirDocenteOAdmin();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now();
        Map<UUID, String> mapa = asistenciaService.obtenerMapaEstadosAsistencia(cursoId, fecha, materiaId);
        return ResponseEntity.ok(mapa);
    }
}

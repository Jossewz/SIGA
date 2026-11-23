package com.siga.siga_iea.clases.controller;

import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.service.AsistenciaService;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.calificaciones.service.CalificacionExcelService;
import com.siga.siga_iea.calificaciones.service.CalificacionesService;
import com.siga.siga_iea.clases.application.CursoGestionAppService;
import com.siga.siga_iea.clases.dto.CursoGestionDetalleDTO;
import com.siga.siga_iea.clases.dto.CursoTablaNotasDTO;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.entity.CursoMateria;
import com.siga.siga_iea.clases.entity.Horario;
import com.siga.siga_iea.clases.service.CursoService;
import com.siga.siga_iea.clases.service.OptimizadorHorarioService;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.excel.ExcelImportResult;
import com.siga.siga_iea.excel.ExcelPlantillaSupport;
import com.siga.siga_iea.excel.ExcelValidationException;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.service.PersonalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Controlador Web para Cursos y Clases.
 * Delegador liviano de peticiones HTTP hacia la capa de casos de uso y
 * servicios de aplicación.
 */
@Controller
public class CursoController {

    private final CursoService cursoService;
    private final CursoGestionAppService cursoGestionAppService;
    private final PersonalService personalService;
    private final CalificacionesService calificacionesService;
    private final AsistenciaService asistenciaService;
    private final CurrentUserContextService currentUserContextService;
    private final PeriodoConfigService periodoConfigService;
    private final CalificacionExcelService calificacionExcelService;
    private final OptimizadorHorarioService optimizadorHorarioService;

    @Autowired
    public CursoController(CursoService cursoService,
            CursoGestionAppService cursoGestionAppService,
            PersonalService personalService,
            CalificacionesService calificacionesService,
            AsistenciaService asistenciaService,
            CurrentUserContextService currentUserContextService,
            @Autowired(required = false) PeriodoConfigService periodoConfigService,
            @Autowired(required = false) CalificacionExcelService calificacionExcelService,
            @Autowired(required = false) OptimizadorHorarioService optimizadorHorarioService) {
        this.cursoService = cursoService;
        this.cursoGestionAppService = cursoGestionAppService;
        this.personalService = personalService;
        this.calificacionesService = calificacionesService;
        this.asistenciaService = asistenciaService;
        this.currentUserContextService = currentUserContextService;
        this.periodoConfigService = periodoConfigService;
        this.calificacionExcelService = calificacionExcelService;
        this.optimizadorHorarioService = optimizadorHorarioService;
    }

    public CursoController(CursoService cursoService,
            CursoGestionAppService cursoGestionAppService,
            PersonalService personalService,
            CalificacionesService calificacionesService,
            AsistenciaService asistenciaService,
            CurrentUserContextService currentUserContextService,
            PeriodoConfigService periodoConfigService,
            CalificacionExcelService calificacionExcelService) {
        this(cursoService, cursoGestionAppService, personalService, calificacionesService,
                asistenciaService, currentUserContextService, periodoConfigService, calificacionExcelService, null);
    }

    public CursoController(CursoService cursoService,
            CursoGestionAppService cursoGestionAppService,
            PersonalService personalService,
            CalificacionesService calificacionesService,
            AsistenciaService asistenciaService,
            CurrentUserContextService currentUserContextService,
            PeriodoConfigService periodoConfigService) {
        this(cursoService, cursoGestionAppService, personalService, calificacionesService,
                asistenciaService, currentUserContextService, periodoConfigService, null, null);
    }

    public CursoController(CursoService cursoService,
            CursoGestionAppService cursoGestionAppService,
            PersonalService personalService,
            CalificacionesService calificacionesService,
            AsistenciaService asistenciaService,
            CurrentUserContextService currentUserContextService) {
        this(cursoService, cursoGestionAppService, personalService, calificacionesService,
                asistenciaService, currentUserContextService, null, null, null);
    }

    private String resolverAnioActivo() {
        return periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());
    }

    private void exigirAdminOAdministrativo() {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede gestionar cursos y horarios.");
        }
    }

    private void exigirDocenteOAdmin() {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()
                && !currentUserContextService.esDocente()) {
            throw new AccessDeniedException("Solo Docentes y Administración pueden gestionar evaluaciones, calificaciones o asistencias.");
        }
    }

    public static String obtenerNombreDiaEspanol(DayOfWeek dow) {
        return CursoGestionAppService.obtenerNombreDiaEspanol(dow);
    }

    private static final String[] PALETA_COLORES = {
            "#0d4117", // Verde Bosque SIGA
            "#059669", // Esmeralda
            "#0284c7", // Azul Cielo
            "#4f46e5", // Índigo
            "#7c3aed", // Púrpura
            "#d97706", // Ámbar
            "#dc2626", // Carmesí
            "#0d9488", // Verde Azulado
            "#c026d3", // Fucsia
            "#334155" // Pizarra
    };

    @GetMapping("/clases/horarios/datos")
    @ResponseBody
    public List<Map<String, Object>> obtenerHorariosDatosJson(@RequestParam("cursoId") UUID cursoId) {
        List<Horario> horarios = cursoService.listarHorariosDeCurso(cursoId);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Horario h : horarios) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId().toString());
            map.put("dia", h.getDiaSemana());
            map.put("horaInicio", h.getHoraInicio() != null ? h.getHoraInicio().toString() : "07:00");
            map.put("horaFin", h.getHoraFin() != null ? h.getHoraFin().toString() : "08:30");
            map.put("materiaId", h.getMateria() != null ? h.getMateria().getId().toString() : "");
            map.put("materiaNombre", h.getMateria() != null ? h.getMateria().getNombre() : "Sin Asignatura");
            map.put("docenteId", h.getDocente() != null ? h.getDocente().getId().toString() : "");
            map.put("docenteNombre", h.getDocente() != null ? h.getDocente().getNombreCompleto() : "Sin Docente");
            map.put("salon", h.getSalonNombre());
            map.put("salonId", h.getSalonEntidad() != null ? h.getSalonEntidad().getId().toString() : "");

            // Color determinista según el nombre de la materia
            int hash = Math.abs(h.getMateria() != null ? h.getMateria().getNombre().hashCode() : 0);
            String color = PALETA_COLORES[hash % PALETA_COLORES.length];
            map.put("color", color);

            res.add(map);
        }
        return res;
    }

    @PostMapping("/clases/horarios/guardar-item")
    @ResponseBody
    public Map<String, Object> guardarHorarioItem(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam("diaSemana") String diaSemana,
            @RequestParam(value = "horarioId", required = false) String horarioIdStr,
            @RequestParam("materiaId") UUID materiaId,
            @RequestParam(value = "docenteId", required = false) String docenteIdStr,
            @RequestParam("horaInicio") String horaInicio,
            @RequestParam("horaFin") String horaFin,
            @RequestParam(value = "salon", required = false) String salon) {

        exigirAdminOAdministrativo();
        Map<String, Object> res = new HashMap<>();
        try {
            UUID horarioId = (horarioIdStr != null && !horarioIdStr.isBlank() && !horarioIdStr.equals("null"))
                    ? UUID.fromString(horarioIdStr)
                    : null;
            UUID docenteId = (docenteIdStr != null && !docenteIdStr.isBlank() && !docenteIdStr.equals("null"))
                    ? UUID.fromString(docenteIdStr)
                    : null;

            Horario h;
            if (horarioId != null) {
                h = cursoService.actualizarHorario(horarioId, cursoId, diaSemana, materiaId, docenteId, horaInicio,
                        horaFin, salon);
            } else {
                h = cursoService.guardarHorarioBloque(cursoId, diaSemana, materiaId, docenteId, horaInicio, horaFin,
                        salon);
            }

            res.put("success", true);
            res.put("id", h.getId().toString());
            res.put("dia", h.getDiaSemana());
            res.put("horaInicio", h.getHoraInicio() != null ? h.getHoraInicio().toString() : horaInicio);
            res.put("horaFin", h.getHoraFin() != null ? h.getHoraFin().toString() : horaFin);
            res.put("materiaId", h.getMateria() != null ? h.getMateria().getId().toString() : "");
            res.put("materiaNombre", h.getMateria() != null ? h.getMateria().getNombre() : "");
            res.put("docenteId", h.getDocente() != null ? h.getDocente().getId().toString() : "");
            res.put("docenteNombre", h.getDocente() != null ? h.getDocente().getNombreCompleto() : "Sin Docente");
            res.put("salon", h.getSalonNombre());

            int hash = Math.abs(h.getMateria() != null ? h.getMateria().getNombre().hashCode() : 0);
            res.put("color", PALETA_COLORES[hash % PALETA_COLORES.length]);
        } catch (Exception ex) {
            res.put("success", false);
            res.put("error", ex.getMessage());
        }
        return res;
    }

    @PostMapping("/clases/horarios/eliminar-item")
    @ResponseBody
    public Map<String, Object> eliminarHorarioItem(@RequestParam("horarioId") UUID horarioId) {
        exigirAdminOAdministrativo();
        Map<String, Object> res = new HashMap<>();
        try {
            cursoService.eliminarHorario(horarioId);
            res.put("success", true);
        } catch (Exception ex) {
            res.put("success", false);
            res.put("error", ex.getMessage());
        }
        return res;
    }

    @PostMapping("/clases/horarios/limpiar")
    @ResponseBody
    public Map<String, Object> limpiarHorario(@RequestParam("cursoId") UUID cursoId) {
        exigirAdminOAdministrativo();
        Map<String, Object> res = new HashMap<>();
        try {
            cursoService.limpiarHorarioCurso(cursoId);
            res.put("success", true);
        } catch (Exception ex) {
            res.put("success", false);
            res.put("error", ex.getMessage());
        }
        return res;
    }

    @GetMapping("/clases/horarios/modelo-io")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerAnalisisModeloIO(@RequestParam("cursoId") UUID cursoId) {
        if (optimizadorHorarioService == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("estado", "error", "mensaje", "Servicio de optimización IO no disponible."));
        }
        try {
            OptimizadorHorarioService.DatosRealesCursoDto datosReales =
                    optimizadorHorarioService.obtenerDatosRealesCurso(cursoId);
            OptimizadorHorarioService.ResultadoModeloMILP optimo =
                    optimizadorHorarioService.resolverModelo(
                            OptimizadorHorarioService.ParametrosModeloIO.institucionales(), datosReales);
            List<Map<String, Object>> ranking = optimizadorHorarioService.topCombinaciones(
                    5, OptimizadorHorarioService.ParametrosModeloIO.institucionales());

            Map<String, Object> modeloInfo = new LinkedHashMap<>();
            modeloInfo.put("objetivo", "Minimizar Z = 2·X1 + 3·X2");
            modeloInfo.put("variables", List.of(
                    "X1 = Horas semanales teóricas asignadas al curso (c1 = 2 ptos desgaste/h)",
                    "X2 = Horas semanales prácticas, de taller o PRAE asignadas al curso (c2 = 3 ptos desgaste/h)"
            ));
            modeloInfo.put("restricciones", List.of(
                    "X1 + X2 ≤ 30 (Capacidad horaria semanal de la jornada H)",
                    "X1 + X2 ≥ 20 (Carga horaria semanal mínima obligatoria L - Ley 115/1994)",
                    "X1 ≥ 12 (Mínimo pedagógico de horas teóricas M - Fundamentación Saber 11)",
                    "X2 ≥ 6 (Mínimo curricular de horas prácticas/taller/PRAE P - SIEACI 5.4.4)",
                    "X1, X2 ≥ 0 y enteros (Asignación en bloques horarios completos)"
            ));
            modeloInfo.put("articulacionEtapas", Map.of(
                    "etapa1", "Etapa 1 (Táctica – Modelo MILP): Determina la cuota óptima semanal X1* = 14 h teóricas y X2* = 6 h prácticas/PRAE con desgaste institucional mínimo Z* = 46.",
                    "etapa2", "Etapa 2 (Operativa – OptimizadorHorarioService + HorarioValidator): Asigna automáticamente las asignaturas del curso en la grilla Lunes a Viernes dentro de la jornada, garantizando 0 choques de docente, salón y grupo."
            ));

            Map<String, Object> estructura = optimizadorHorarioService.evaluarEstructuraSemanal(cursoId);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("estado", "optimo");
            resp.put("datosReales", datosReales);
            resp.put("optimo", optimo);
            resp.put("pertinencia", optimo.tablaPertinencia());
            resp.put("vertices", optimo.verticesRegionFactible());
            resp.put("ranking", ranking);
            resp.put("modelo", modeloInfo);
            resp.put("estructura", estructura);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("estado", "error", "mensaje", ex.getMessage()));
        }
    }

    @GetMapping("/clases/horarios/modelo-io/evaluar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> evaluarCombinacionModeloIO(
            @RequestParam(defaultValue = "14") int x1,
            @RequestParam(defaultValue = "6") int x2) {
        if (optimizadorHorarioService == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Servicio de optimización IO no disponible."));
        }
        OptimizadorHorarioService.ResultadoEvaluacionPunto eval = optimizadorHorarioService.evaluarCombinacion(
                x1, x2, OptimizadorHorarioService.ParametrosModeloIO.institucionales());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("x1", eval.x1());
        r.put("x2", eval.x2());
        r.put("totalHoras", eval.x1() + eval.x2());
        r.put("desgasteZ", eval.z());
        r.put("diferenciaContraOptimo", eval.diferenciaContraOptimo());
        r.put("factible", eval.factible());
        r.put("detalle", eval.detalleRestricciones());
        return ResponseEntity.ok(r);
    }

    @PostMapping("/clases/horarios/optimizar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> optimizarHorarioCurso(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "reemplazar", defaultValue = "true") boolean reemplazar) {
        exigirAdminOAdministrativo();
        if (optimizadorHorarioService == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "Servicio OptimizadorHorarioService no disponible."));
        }
        try {
            OptimizadorHorarioService.ResultadoOptimizacionCurso resultado =
                    optimizadorHorarioService.optimizarYAsignarHorario(cursoId, reemplazar);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("bloquesCreados", resultado.bloquesCreados());
            res.put("choquesDetectados", resultado.choquesDetectados());
            res.put("x1OptimoTeoricas", resultado.etapa1Modelo().x1OptimoTeoricas());
            res.put("x2OptimoPracticas", resultado.etapa1Modelo().x2OptimoPracticas());
            res.put("zMinimoDesgaste", resultado.etapa1Modelo().zMinimoDesgaste());
            res.put("interpretacion", resultado.etapa1Modelo().interpretacion());
            res.put("datosAntes", resultado.datosAntes());
            res.put("datosDespues", resultado.datosDespues());
            res.put("horarios", obtenerHorariosDatosJson(cursoId));
            return ResponseEntity.ok(res);
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping({"/clases", "/cursos"})
    public String index(Model model) {
        model.addAttribute("title", "Gestión de Cursos – IEACI");
        model.addAttribute("activePage", "clases");

        String anioActivo = resolverAnioActivo();
        List<Curso> cursosDB = cursoService.listarCursosPorAno(anioActivo);
        if (cursosDB.isEmpty()) {
            cursosDB = cursoService.listarTodosLosCursos();
        }

        boolean soloEstudiante = currentUserContextService.esEstudiante()
                && !currentUserContextService.esAdminOAdministrativo()
                && !currentUserContextService.esDocente();
        UUID estudianteAutenticadoId = soloEstudiante
                ? currentUserContextService.getEstudianteAutenticado().map(com.siga.siga_iea.usuarios.entity.Estudiante::getId).orElse(null)
                : null;

        List<Map<String, Object>> cursosList = new ArrayList<>();

        for (Curso c : cursosDB) {
            List<CursoEstudiante> estudiantes = cursoService.listarEstudiantesDeCurso(c.getId());
            if (soloEstudiante) {
                boolean matriculadoEnCurso = estudianteAutenticadoId != null && estudiantes.stream()
                        .anyMatch(ce -> ce.getEstudiante() != null && estudianteAutenticadoId.equals(ce.getEstudiante().getId()));
                if (!matriculadoEnCurso) {
                    continue;
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId().toString());
            map.put("grado", c.getGrado());
            map.put("grupo", c.getGrupo());
            map.put("codigoCurso", c.getCodigoCurso());
            map.put("director", c.getDirector() != null ? c.getDirector().getNombreCompleto() : "Sin asignar");
            map.put("directorId", c.getDirector() != null ? c.getDirector().getId().toString() : "");
            map.put("cuposMaximos", c.getCuposMaximos());
            map.put("estudiantes", estudiantes.size());

            List<Horario> horarios = cursoService.listarHorariosDeCurso(c.getId());
            boolean tieneHorario = !horarios.isEmpty();
            map.put("tieneHorario", tieneHorario);
            map.put("horarioResumen", tieneHorario ? "Lun-Vie: 07:00 - 12:30" : "Sin Horario");
            map.put("jornada", c.getJornada());
            cursosList.add(map);
        }

        model.addAttribute("esSoloEstudiante", soloEstudiante);

        model.addAttribute("cursosList", cursosList);
        model.addAttribute("docentesList", personalService.listarDocentes());
        model.addAttribute("materiasList", cursoService.listarMateriasActivas());
        model.addAttribute("salonesList", cursoService.listarSalonesActivos());
        model.addAttribute("bloquesList", cursoService.listarBloquesPorJornada("Mañana"));
        return "clases/index";
    }

    @PostMapping("/clases/crear")
    public String crearCurso(
            @RequestParam(value = "id", required = false) String idStr,
            @RequestParam("grado") String grado,
            @RequestParam(value = "grupo", required = false) String grupo,
            @RequestParam(value = "jornada", defaultValue = "Mañana") String jornada,
            @RequestParam(value = "cupos", defaultValue = "35") Integer cupos,
            @RequestParam(value = "directorId", required = false) String directorIdStr,
            @RequestParam(value = "anoLectivo", required = false) String anoLectivo,
            RedirectAttributes redirectAttributes) {

        exigirAdminOAdministrativo();
        try {
            String anioEfectivo = (anoLectivo != null && !anoLectivo.isBlank()) ? anoLectivo.trim() : resolverAnioActivo();
            UUID directorId = (directorIdStr != null && !directorIdStr.isBlank()) ? UUID.fromString(directorIdStr)
                    : null;
            if (idStr != null && !idStr.isBlank()) {
                UUID cursoId = UUID.fromString(idStr);
                cursoService.actualizarCurso(cursoId, grado, jornada, cupos, directorId, anioEfectivo);
                redirectAttributes.addFlashAttribute("mensajeExito", "Curso actualizado exitosamente.");
            } else {
                cursoService.crearCurso(grado, grupo, jornada, cupos, directorId, anioEfectivo);
                redirectAttributes.addFlashAttribute("mensajeExito", "Curso creado exitosamente.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al procesar curso: " + ex.getMessage());
        }

        return "redirect:/clases";
    }

    @GetMapping("/clases/gestion")
    public String gestionDetail(@RequestParam(name = "codigo", defaultValue = "11-01") String codigo, Model model) {
        if (!currentUserContextService.esDocente() && !currentUserContextService.esAdminOAdministrativo()) {
            return "redirect:/?sinAcceso=CURSOS_GESTION";
        }
        model.addAttribute("title", "Gestión de Curso " + codigo + " – IEACI");
        model.addAttribute("activePage", "clases");

        Optional<Docente> docLogueadoOpt = currentUserContextService.getDocenteAutenticado();
        boolean esAdmin = currentUserContextService.esAdmin();

        CursoGestionDetalleDTO detalle = cursoGestionAppService.prepararGestionDetalle(codigo, docLogueadoOpt, esAdmin);

        model.addAttribute("cursoId", detalle.getCursoId());
        model.addAttribute("codigoCurso", detalle.getCodigoCurso());
        model.addAttribute("gradoCurso", detalle.getGradoCurso());
        model.addAttribute("directorCurso", detalle.getDirectorCurso());
        model.addAttribute("jornadaCurso", detalle.getJornadaCurso());
        model.addAttribute("estudiantesData", detalle.getEstudiantesData());
        model.addAttribute("estudiantesMock", detalle.getEstudiantesData());
        model.addAttribute("horariosData", detalle.getHorariosData());
        model.addAttribute("esAdmin", detalle.isEsAdmin());
        model.addAttribute("horarioBannerTexto", detalle.getHorarioBannerTexto());

        CursoTablaNotasDTO tablaDTO = detalle.getTablaNotasDTO();
        poblarModeloTablaNotas(model, tablaDTO);

        return "clases/detalle";
    }

    @PostMapping("/clases/horarios/guardar")
    public String guardarHorarioCurso(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam("diaSemana") String diaSemana,
            @RequestParam(value = "materiaId", required = false) String materiaIdStr,
            @RequestParam(value = "docenteId", required = false) String docenteIdStr,
            @RequestParam(value = "horaInicio", defaultValue = "07:00") String horaInicio,
            @RequestParam(value = "horaFin", defaultValue = "08:30") String horaFin,
            @RequestParam(value = "salon", defaultValue = "Aula 101") String salon,
            RedirectAttributes redirectAttributes) {

        exigirAdminOAdministrativo();
        try {
            UUID materiaId = (materiaIdStr != null && !materiaIdStr.isBlank()) ? UUID.fromString(materiaIdStr) : null;
            UUID docenteId = (docenteIdStr != null && !docenteIdStr.isBlank()) ? UUID.fromString(docenteIdStr) : null;

            cursoService.guardarHorarioBloque(cursoId, diaSemana, materiaId, docenteId, horaInicio, horaFin, salon);
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Horario asignado exitosamente para el día " + diaSemana);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al asignar horario: " + ex.getMessage());
        }

        return "redirect:/clases";
    }

    @PostMapping("/clases/horarios/guardar-grid")
    public String guardarHorarioGrid(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        exigirAdminOAdministrativo();
        try {
            int guardados = cursoGestionAppService.procesarGuardadoHorarioGrid(cursoId, allParams);
            if (guardados > 0) {
                redirectAttributes.addFlashAttribute("mensajeExito",
                        "Horario asignado exitosamente (" + guardados + " clases configuradas).");
            } else {
                redirectAttributes.addFlashAttribute("mensajeExito", "Horario del curso actualizado.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al guardar horario: " + ex.getMessage());
        }

        return "redirect:/clases";
    }

    @PostMapping("/clases/guardar")
    public String guardarCurso(
            @RequestParam(value = "id", required = false) String id,
            @RequestParam("grado") String grado,
            @RequestParam(value = "grupo", defaultValue = "01") String grupo,
            @RequestParam("jornada") String jornada,
            @RequestParam(value = "cupos", defaultValue = "35") Integer cupos,
            @RequestParam(value = "directorId", required = false) String directorId,
            RedirectAttributes redirectAttributes) {

        exigirAdminOAdministrativo();
        try {
            UUID dirId = (directorId != null && !directorId.isBlank()) ? UUID.fromString(directorId) : null;
            String anioActivo = resolverAnioActivo();

            if (id != null && !id.isBlank()) {
                cursoService.actualizarCurso(UUID.fromString(id), grado, jornada, cupos, dirId, anioActivo);
                redirectAttributes.addFlashAttribute("mensajeExito", "Curso actualizado exitosamente.");
            } else {
                cursoService.crearCurso(grado, grupo, jornada, cupos, dirId, anioActivo);
                redirectAttributes.addFlashAttribute("mensajeExito", "Curso creado exitosamente.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al procesar el curso: " + ex.getMessage());
        }

        return "redirect:/clases";
    }

    @PostMapping("/clases/eliminar")
    public String eliminarCurso(
            @RequestParam("id") UUID id,
            RedirectAttributes redirectAttributes) {

        exigirAdminOAdministrativo();
        try {
            cursoService.eliminarCurso(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Curso eliminado exitosamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al eliminar el curso: " + ex.getMessage());
        }

        return "redirect:/clases";
    }

    @PostMapping("/clases/mapear-estudiantes")
    public String mapearEstudiantes(@RequestParam("cursoId") UUID cursoId, RedirectAttributes redirectAttributes) {
        if (!currentUserContextService.esAdminOAdministrativo()) {
            redirectAttributes.addFlashAttribute("mensajeError",
                    "Acceso denegado: solo Administradores y Personal Administrativo pueden auto-mapear estudiantes.");
            Optional<Curso> cOpt = cursoService.buscarPorId(cursoId);
            String codigo = cOpt.map(Curso::getCodigoCurso).orElse("11-01");
            return "redirect:/clases/gestion?codigo=" + codigo;
        }

        try {
            int count = cursoService.mapearEstudiantesMatriculados(cursoId);
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Se han auto-mapeado " + count + " estudiantes matriculados a este curso.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al mapear estudiantes: " + ex.getMessage());
        }

        Optional<Curso> cOpt = cursoService.buscarPorId(cursoId);
        String codigo = cOpt.map(Curso::getCodigoCurso).orElse("11-01");
        return "redirect:/clases/gestion?codigo=" + codigo;
    }

    @PostMapping("/clases/promover-estudiantes")
    public String promoverEstudiantes(@RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "notasJson", required = false) String notasJson,
            RedirectAttributes redirectAttributes) {
        if (!currentUserContextService.esAdminOAdministrativo()) {
            redirectAttributes.addFlashAttribute("mensajeError",
                    "Acceso denegado: solo Administradores y Personal Administrativo pueden promover estudiantes.");
            Optional<Curso> cOpt = cursoService.buscarPorId(cursoId);
            String codigo = cOpt.map(Curso::getCodigoCurso).orElse("11-01");
            return "redirect:/clases/gestion?codigo=" + codigo;
        }

        try {
            String resultado = cursoService.promoverEstudiantesAprobados(cursoId, notasJson);
            redirectAttributes.addFlashAttribute("mensajeExito", resultado);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al promover estudiantes: " + ex.getMessage());
        }

        Optional<Curso> cOpt = cursoService.buscarPorId(cursoId);
        String codigo = cOpt.map(Curso::getCodigoCurso).orElse("11-01");
        return "redirect:/clases/gestion?codigo=" + codigo;
    }

    // ==========================================
    // HTMX ENDPOINTS: NOTAS Y ASISTENCIA (POSTGRESQL)
    // ==========================================

    @GetMapping("/clases/fragmento/tabla-notas")
    public String obtenerFragmentoTablaNotas(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "vistaNotas", defaultValue = "FECHA") String vistaNotas,
            Model model) {

        LocalDate fecha;
        try {
            fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now();
        } catch (Exception e) {
            fecha = LocalDate.now();
        }

        Optional<Docente> docLogueadoOpt = currentUserContextService.getDocenteAutenticado();
        boolean esAdmin = currentUserContextService.esAdmin();

        CursoTablaNotasDTO dto = cursoGestionAppService.prepararTablaNotas(
                cursoId, materiaNombre, materiaId, periodo, fecha, docLogueadoOpt, esAdmin, vistaNotas);
        if (currentUserContextService.esAdminOAdministrativo()) {
            dto.setPuedeEditarAsistencia(true);
        }

        poblarModeloTablaNotas(model, dto);

        return "clases/fragments/tabla-detalle-notas :: tablaDetalleNotas";
    }

    private void poblarModeloTablaNotas(Model model, CursoTablaNotasDTO dto) {
        model.addAttribute("cursoId", dto.getCursoId() != null ? dto.getCursoId().toString() : "");
        model.addAttribute("periodo", dto.getPeriodo());
        model.addAttribute("fecha", dto.getFecha().toString());
        model.addAttribute("diaSemana", dto.getDiaSemana());
        model.addAttribute("tieneHorarioHoy", dto.isTieneHorarioHoy());
        model.addAttribute("horarioBannerTexto", dto.getHorarioBannerTexto());
        model.addAttribute("materiaNombre", dto.getMateriaNombre());
        model.addAttribute("materiaId", dto.getMateriaId());
        model.addAttribute("cursoMateriaId", dto.getCursoMateriaId());
        model.addAttribute("estudiantesCE", dto.getEstudiantesCE());
        model.addAttribute("evaluaciones", dto.getEvaluaciones());
        model.addAttribute("totalEvaluacionesPeriodo", dto.getTotalEvaluacionesPeriodo());
        model.addAttribute("vistaNotas", dto.getVistaNotas());
        model.addAttribute("sumaPesos", dto.getSumaPesos());
        model.addAttribute("calificacionesMapa", dto.getCalificacionesMapa());
        model.addAttribute("asistenciasMapa", dto.getAsistenciasMapa());
        model.addAttribute("notasFinales", dto.getNotasFinales());
        model.addAttribute("puedeEditarAsistencia", dto.isPuedeEditarAsistencia());
    }

    @PostMapping("/clases/evaluaciones/crear")
    public String crearEvaluacion(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo,
            @RequestParam(value = "fecha", required = false) String fecha,
            @RequestParam(value = "vistaNotas", defaultValue = "FECHA") String vistaNotas,
            Model model) {

        exigirDocenteOAdmin();
        if ((materiaNombre == null || materiaNombre.isBlank()) && materiaId == null) {
            return obtenerFragmentoTablaNotas(cursoId, materiaNombre, materiaId, periodo, fecha, vistaNotas, model);
        }

        String anioActivo = resolverAnioActivo();
        CursoMateria cm = (materiaId != null)
                ? calificacionesService.obtenerOCrearCursoMateria(cursoId, materiaId, anioActivo)
                : calificacionesService.obtenerOCrearCursoMateriaPorNombre(cursoId, materiaNombre, anioActivo);

        calificacionesService.crearEvaluacionAutoEquitativa(cm.getId(), periodo, fecha);

        return obtenerFragmentoTablaNotas(cursoId, materiaNombre, cm.getMateria().getId(), periodo, fecha, vistaNotas, model);
    }

    @PostMapping("/clases/evaluaciones/eliminar")
    public String eliminarEvaluacion(
            @RequestParam("evaluacionId") UUID evaluacionId,
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo,
            @RequestParam(value = "fecha", required = false) String fecha,
            @RequestParam(value = "vistaNotas", defaultValue = "FECHA") String vistaNotas,
            Model model) {

        exigirDocenteOAdmin();
        calificacionesService.eliminarEvaluacion(evaluacionId);

        return obtenerFragmentoTablaNotas(cursoId, materiaNombre, materiaId, periodo, fecha, vistaNotas, model);
    }

    @PostMapping("/clases/evaluaciones/actualizar-peso")
    public String actualizarPeso(
            @RequestParam("evaluacionId") UUID evaluacionId,
            @RequestParam("peso") String pesoStr,
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo,
            @RequestParam(value = "fecha", required = false) String fecha,
            @RequestParam(value = "vistaNotas", defaultValue = "FECHA") String vistaNotas,
            Model model) {

        exigirDocenteOAdmin();
        BigDecimal peso = BigDecimal.ZERO;
        if (pesoStr != null && !pesoStr.isBlank()) {
            try {
                peso = new BigDecimal(pesoStr.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }

        calificacionesService.actualizarPesoEvaluacion(evaluacionId, peso);

        return obtenerFragmentoTablaNotas(cursoId, materiaNombre, materiaId, periodo, fecha, vistaNotas, model);
    }

    @PostMapping("/clases/calificaciones/guardar")
    public String guardarCalificacion(
            @RequestParam("evaluacionId") UUID evaluacionId,
            @RequestParam("estudianteId") UUID estudianteId,
            @RequestParam("nota") String notaStr,
            @RequestParam("cursoMateriaId") UUID cursoMateriaId,
            @RequestParam("periodo") Integer periodo,
            Model model) {

        exigirDocenteOAdmin();
        BigDecimal nota = BigDecimal.ZERO;
        if (notaStr != null && !notaStr.isBlank()) {
            try {
                nota = new BigDecimal(notaStr.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }

        calificacionesService.registrarONota(evaluacionId, estudianteId, nota, null);
        BigDecimal notaFinal = calificacionesService.calcularNotaFinalPeriodo(estudianteId, cursoMateriaId, periodo);

        model.addAttribute("estId", estudianteId.toString());
        model.addAttribute("notaFinal", notaFinal);

        return "clases/fragments/tabla-detalle-notas :: badgeNotaFinal";
    }

    @GetMapping("/clases/calificaciones/excel/exportar")
    public ResponseEntity<byte[]> exportarNotasExcel(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo) {
        exigirDocenteOAdmin();
        CalificacionExcelService.ExportPayload payload = calificacionExcelService
                .exportarPlantillaNotas(cursoId, materiaId, materiaNombre, periodo);
        return ExcelPlantillaSupport.buildDownloadResponse(payload.bytes(), payload.filename());
    }

    @PostMapping("/clases/calificaciones/excel/importar")
    public String importarNotasExcel(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam(value = "materiaNombre", required = false) String materiaNombre,
            @RequestParam(value = "periodo", defaultValue = "1") Integer periodo,
            @RequestParam(value = "fecha", required = false) String fecha,
            @RequestParam(value = "vistaNotas", defaultValue = "PERIODO") String vistaNotas,
            @RequestParam("archivo") MultipartFile archivo,
            Model model) {
        exigirDocenteOAdmin();
        String effectiveVista = vistaNotas;
        try {
            ExcelImportResult res = calificacionExcelService.importarNotas(
                    cursoId, materiaId, materiaNombre, periodo, archivo);
            model.addAttribute("excelOk", true);
            model.addAttribute("excelMensaje", res.mensaje());
            effectiveVista = "PERIODO";
        } catch (ExcelValidationException ex) {
            model.addAttribute("excelOk", false);
            model.addAttribute("excelMensaje", ex.getMessage());
            model.addAttribute("excelErrores", ex.getErrores());
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            model.addAttribute("excelOk", false);
            model.addAttribute("excelMensaje", "Error al importar notas: " + ex.getMessage());
            model.addAttribute("excelErrores", List.of(ex.getMessage()));
        }

        return obtenerFragmentoTablaNotas(cursoId, materiaNombre, materiaId, periodo, fecha, effectiveVista, model);
    }

    @PostMapping("/clases/asistencias/marcar")
    public String marcarAsistencia(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam("estudianteId") UUID estudianteId,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam("fecha") String fechaStr,
            @RequestParam("estado") String estado,
            Model model) {

        exigirDocenteOAdmin();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now();
        String estadoFinal;
        try {
            Asistencia a = asistenciaService.registrarEstadoEnCurso(cursoId, estudianteId, fecha, materiaId, estado);
            estadoFinal = a != null ? a.getEstado() : null;
        } catch (SecurityException | IllegalStateException ex) {
            Map<UUID, String> mapaActual = asistenciaService.obtenerMapaEstadosAsistencia(cursoId, fecha, materiaId);
            estadoFinal = mapaActual.get(estudianteId);
            model.addAttribute("asistenciaError", ex.getMessage());
        }

        boolean puedeEditar = currentUserContextService.esAdminOAdministrativo()
                || asistenciaService.puedeEditarFecha(fecha, currentUserContextService.getDocenteAutenticado().orElse(null),
                        currentUserContextService.getRolAutenticado());

        model.addAttribute("estId", estudianteId.toString());
        model.addAttribute("cursoId", cursoId.toString());
        model.addAttribute("materiaId", materiaId != null ? materiaId.toString() : "");
        model.addAttribute("fecha", fechaStr);
        model.addAttribute("estado", estadoFinal);
        model.addAttribute("tieneHorarioHoy", true);
        model.addAttribute("puedeEditarAsistencia", puedeEditar);

        return "clases/fragments/tabla-detalle-notas :: botonAsistencia";
    }

    @PostMapping("/clases/asistencias/toggle")
    public String toggleAsistencia(
            @RequestParam("cursoId") UUID cursoId,
            @RequestParam("estudianteId") UUID estudianteId,
            @RequestParam(value = "materiaId", required = false) UUID materiaId,
            @RequestParam("fecha") String fechaStr,
            Model model) {

        exigirDocenteOAdmin();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now();
        Asistencia a = asistenciaService.toggleAsistencia(cursoId, estudianteId, fecha, materiaId);
        boolean puedeEditar = currentUserContextService.esAdminOAdministrativo()
                || asistenciaService.puedeEditarFecha(fecha, currentUserContextService.getDocenteAutenticado().orElse(null),
                        currentUserContextService.getRolAutenticado());

        model.addAttribute("estId", estudianteId.toString());
        model.addAttribute("cursoId", cursoId.toString());
        model.addAttribute("materiaId", materiaId != null ? materiaId.toString() : "");
        model.addAttribute("fecha", fechaStr);
        model.addAttribute("estado", a.getEstado());
        model.addAttribute("tieneHorarioHoy", true);
        model.addAttribute("puedeEditarAsistencia", puedeEditar);

        return "clases/fragments/tabla-detalle-notas :: botonAsistencia";
    }
}

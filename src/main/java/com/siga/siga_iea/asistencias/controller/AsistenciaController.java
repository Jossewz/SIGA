package com.siga.siga_iea.asistencias.controller;

import com.siga.siga_iea.asistencias.dto.AsistenciaItemDto;
import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.asistencias.service.AsistenciaExcelService;
import com.siga.siga_iea.asistencias.service.AsistenciaService;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.service.CursoService;
import com.siga.siga_iea.excel.ExcelImportResult;
import com.siga.siga_iea.excel.ExcelPlantillaSupport;
import com.siga.siga_iea.excel.ExcelValidationException;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.EstudianteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final AsistenciaExcelService asistenciaExcelService;

    @Autowired
    public AsistenciaController(AsistenciaService asistenciaService,
                                @Autowired(required = false) AsistenciaRepository asistenciaRepository,
                                @Autowired(required = false) CurrentUserContextService currentUserContextService,
                                @Autowired(required = false) EstudianteService estudianteService,
                                @Autowired(required = false) CursoService cursoService,
                                @Autowired(required = false) AsistenciaExcelService asistenciaExcelService) {
        this.asistenciaService = asistenciaService;
        this.asistenciaRepository = asistenciaRepository;
        this.currentUserContextService = currentUserContextService;
        this.estudianteService = estudianteService;
        this.cursoService = cursoService;
        this.asistenciaExcelService = asistenciaExcelService;
    }

    public AsistenciaController(AsistenciaService asistenciaService,
                                AsistenciaRepository asistenciaRepository,
                                CurrentUserContextService currentUserContextService,
                                EstudianteService estudianteService,
                                CursoService cursoService) {
        this(asistenciaService, asistenciaRepository, currentUserContextService, estudianteService, cursoService, null);
    }

    public AsistenciaController(AsistenciaService asistenciaService) {
        this(asistenciaService, null, null, null, null, null);
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
            @RequestParam(value = "cursoId", required = false) String cursoIdStr,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "estado", required = false) String estadoFiltro,
            @RequestParam(value = "materia", required = false) String materiaFiltro,
            @RequestParam(value = "q", required = false) String queryFiltro,
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

        ZoneId zona = ZoneId.of("America/Bogota");
        LocalDate hoy = LocalDate.now(zona);
        LocalDate fecha = hoy;
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                fecha = LocalDate.parse(fechaStr.trim());
            } catch (Exception ignored) {}
        }

        String estadoNorm = estadoFiltro != null && !estadoFiltro.isBlank() ? estadoFiltro.trim().toUpperCase() : "";
        String materiaNorm = materiaFiltro != null && !materiaFiltro.isBlank() ? materiaFiltro.trim() : "";
        String queryNorm = queryFiltro != null && !queryFiltro.isBlank() ? queryFiltro.trim() : "";

        model.addAttribute("fechaFiltro", fecha.toString());
        model.addAttribute("fechaHoy", hoy.toString());
        model.addAttribute("fechaAnterior", fecha.minusDays(1).toString());
        model.addAttribute("fechaSiguiente", fecha.plusDays(1).toString());
        model.addAttribute("estadoFiltro", estadoNorm);
        model.addAttribute("materiaFiltro", materiaNorm);
        model.addAttribute("queryFiltro", queryNorm);

        if (esEstudiante && !esAdminOAdmin && asistenciaRepository != null && estudianteService != null) {
            Optional<Estudiante> estOpt = currentUserContextService.getUsuarioAutenticado()
                    .map(Usuario::getNumeroDocumento)
                    .filter(d -> d != null && !d.isBlank())
                    .flatMap(estudianteService::buscarPorNumeroDocumento);

            List<Map<String, Object>> historialBase = new ArrayList<>();
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
                    String materia = resolverNombreMateria(sc);
                    Map<String, Object> row = new HashMap<>();
                    row.put("fecha", sc != null && sc.getFecha() != null ? sc.getFecha().toString() : "-");
                    row.put("materia", materia);
                    row.put("docente", sc != null && sc.getDocente() != null ? sc.getDocente().getNombreCompleto() : "-");
                    row.put("estado", st);
                    row.put("observaciones", a.getObservaciones() != null && !a.getObservaciones().isBlank() ? a.getObservaciones() : "-");
                    historialBase.add(row);
                }
            }

            List<Map<String, Object>> historial = filtrarFilasAsistencia(historialBase, estadoNorm, "", queryNorm);
            long totalBase = historialBase.size();
            long tasa = totalBase > 0 ? Math.round(((cntPresente + cntTarde) * 100.0) / totalBase) : 0;

            model.addAttribute("modoVista", "ESTUDIANTE");
            model.addAttribute("historialEstudiante", historial);
            model.addAttribute("cntPresente", cntPresente);
            model.addAttribute("cntTarde", cntTarde);
            model.addAttribute("cntExcusado", cntExcusado);
            model.addAttribute("cntAusente", cntAusente);
            model.addAttribute("totalRegistrosBase", totalBase);
            model.addAttribute("tasaAsistencia", tasa);
            return "asistencias/index";
        }

        // Vista consolidada para Admin / Personal Administrativo
        List<Curso> cursos = cursoService != null ? cursoService.listarTodosLosCursos() : Collections.emptyList();
        model.addAttribute("cursos", cursos);
        UUID cursoUuid = null;
        if (cursoIdStr != null && !cursoIdStr.isBlank() && !"ALL".equalsIgnoreCase(cursoIdStr.trim())) {
            try {
                cursoUuid = UUID.fromString(cursoIdStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        final UUID cursoUuidFinal = cursoUuid;
        Curso cursoSeleccionado = cursoUuidFinal == null ? null
                : cursos.stream().filter(c -> c.getId().equals(cursoUuidFinal)).findFirst().orElse(null);

        List<Asistencia> fuente = Collections.emptyList();
        if (asistenciaRepository != null) {
            fuente = cursoSeleccionado != null
                    ? asistenciaRepository.findByCursoIdAndFecha(cursoSeleccionado.getId(), fecha)
                    : asistenciaRepository.findByFecha(fecha);
        }

        Set<String> materiasSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<Map<String, Object>> filasMateria = new ArrayList<>();
        for (Asistencia a : fuente) {
            SesionClase sc = a.getSesion();
            String materia = resolverNombreMateria(sc);
            materiasSet.add(materia);
            if (!materiaNorm.isEmpty() && !materia.equalsIgnoreCase(materiaNorm)) {
                continue;
            }
            String st = a.getEstado() != null ? a.getEstado().toUpperCase() : "PRESENTE";
            Curso curso = sc != null ? sc.getCurso() : null;
            String cursoEtiqueta = curso != null
                    ? (curso.getGrado() + " - " + (curso.getGrupo() != null ? curso.getGrupo() : "01"))
                    : "-";
            Map<String, Object> row = new HashMap<>();
            row.put("estudiante", a.getEstudiante() != null ? a.getEstudiante().getNombreCompleto() : "N/A");
            row.put("documento", a.getEstudiante() != null ? a.getEstudiante().getNumeroDocumento() : "-");
            row.put("curso", cursoEtiqueta);
            row.put("materia", materia);
            row.put("docente", sc != null && sc.getDocente() != null ? sc.getDocente().getNombreCompleto() : "-");
            row.put("estado", st);
            row.put("observaciones", a.getObservaciones() != null && !a.getObservaciones().isBlank() ? a.getObservaciones() : null);
            filasMateria.add(row);
        }

        long cntPresente = 0, cntTarde = 0, cntExcusado = 0, cntAusente = 0;
        for (Map<String, Object> r : filasMateria) {
            String st = String.valueOf(r.get("estado"));
            if ("PRESENTE".equals(st)) cntPresente++;
            else if ("TARDE".equals(st)) cntTarde++;
            else if ("EXCUSADO".equals(st)) cntExcusado++;
            else if ("AUSENTE".equals(st)) cntAusente++;
        }

        List<Map<String, Object>> registrosCurso = filtrarFilasAsistencia(filasMateria, estadoNorm, "", queryNorm);
        registrosCurso.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("estudiante", "")), String.CASE_INSENSITIVE_ORDER));

        long totalBase = filasMateria.size();
        long tasa = totalBase > 0 ? Math.round(((cntPresente + cntTarde) * 100.0) / totalBase) : 0;

        model.addAttribute("modoVista", "ADMIN");
        model.addAttribute("cursoSeleccionado", cursoSeleccionado);
        model.addAttribute("materiasDisponibles", new ArrayList<>(materiasSet));
        model.addAttribute("registrosCurso", registrosCurso);
        model.addAttribute("cntPresente", cntPresente);
        model.addAttribute("cntTarde", cntTarde);
        model.addAttribute("cntExcusado", cntExcusado);
        model.addAttribute("cntAusente", cntAusente);
        model.addAttribute("totalRegistrosBase", totalBase);
        model.addAttribute("tasaAsistencia", tasa);
        return "asistencias/index";
    }

    private String resolverNombreMateria(SesionClase sc) {
        if (sc != null && sc.getCursoMateria() != null && sc.getCursoMateria().getMateria() != null) {
            return sc.getCursoMateria().getMateria().getNombre();
        }
        if (sc != null && sc.getHorario() != null && sc.getHorario().getMateria() != null) {
            return sc.getHorario().getMateria().getNombre();
        }
        return "Jornada";
    }

    private List<Map<String, Object>> filtrarFilasAsistencia(List<Map<String, Object>> base,
                                                             String estadoNorm,
                                                             String materiaNorm,
                                                             String queryNorm) {
        if (estadoNorm.isEmpty() && materiaNorm.isEmpty() && queryNorm.isEmpty()) {
            return new ArrayList<>(base);
        }
        String qLower = queryNorm.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : base) {
            if (!estadoNorm.isEmpty() && !estadoNorm.equalsIgnoreCase(String.valueOf(r.getOrDefault("estado", "")))) {
                continue;
            }
            if (!materiaNorm.isEmpty() && !materiaNorm.equalsIgnoreCase(String.valueOf(r.getOrDefault("materia", "")))) {
                continue;
            }
            if (!qLower.isEmpty()) {
                String joined = (String.valueOf(r.getOrDefault("estudiante", "")) + " "
                        + String.valueOf(r.getOrDefault("documento", "")) + " "
                        + String.valueOf(r.getOrDefault("curso", "")) + " "
                        + String.valueOf(r.getOrDefault("materia", "")) + " "
                        + String.valueOf(r.getOrDefault("docente", "")) + " "
                        + String.valueOf(r.getOrDefault("fecha", "")) + " "
                        + String.valueOf(r.getOrDefault("observaciones", ""))).toLowerCase(Locale.ROOT);
                if (!joined.contains(qLower)) {
                    continue;
                }
            }
            out.add(r);
        }
        return out;
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

    @GetMapping("/asistencias/excel/exportar")
    public ResponseEntity<byte[]> exportarExcelAdmin(
            @RequestParam(value = "cursoId", required = false) String cursoIdStr,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "estado", required = false) String estadoFiltro,
            @RequestParam(value = "materia", required = false) String materiaFiltro,
            @RequestParam(value = "q", required = false) String query) {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede exportar consolidados.");
        }
        UUID cursoUuid = null;
        if (cursoIdStr != null && !cursoIdStr.isBlank() && !"ALL".equalsIgnoreCase(cursoIdStr.trim())) {
            try {
                cursoUuid = UUID.fromString(cursoIdStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Bogota"));
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                fecha = LocalDate.parse(fechaStr.trim());
            } catch (Exception ignored) {}
        }
        AsistenciaExcelService.ExportPayload payload = asistenciaExcelService.exportarDesdeAdmin(
                cursoUuid, fecha, estadoFiltro, materiaFiltro, query);
        return ExcelPlantillaSupport.buildDownloadResponse(payload.bytes(), payload.filename());
    }

    @PostMapping("/asistencias/excel/importar")
    public String importarExcelAdmin(
            @RequestParam(value = "cursoId", required = false) String cursoIdStr,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam("archivo") MultipartFile archivo,
            RedirectAttributes redirectAttributes) {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede importar planillas en Consolidado.");
        }
        UUID cursoUuid = null;
        if (cursoIdStr != null && !cursoIdStr.isBlank()) {
            try {
                cursoUuid = UUID.fromString(cursoIdStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Bogota"));
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                fecha = LocalDate.parse(fechaStr.trim());
            } catch (Exception ignored) {}
        }
        try {
            ExcelImportResult res = asistenciaExcelService.importarDesdeAdmin(cursoUuid, fecha, archivo);
            redirectAttributes.addFlashAttribute("mensajeExito", res.mensaje());
        } catch (ExcelValidationException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            redirectAttributes.addFlashAttribute("erroresExcel", ex.getErrores());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al importar Excel: " + ex.getMessage());
        }
        return "redirect:/asistencias?cursoId=" + (cursoUuid != null ? cursoUuid : "") + "&fecha=" + fecha;
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

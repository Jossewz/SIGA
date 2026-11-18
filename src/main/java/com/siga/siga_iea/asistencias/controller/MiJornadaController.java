package com.siga.siga_iea.asistencias.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siga.siga_iea.asistencias.dto.AsistenciaItemDto;
import com.siga.siga_iea.asistencias.dto.FilaAsistenciaDto;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.SesionClaseRepository;
import com.siga.siga_iea.asistencias.service.AsistenciaExcelService;
import com.siga.siga_iea.asistencias.service.AsistenciaService;
import com.siga.siga_iea.asistencias.service.AsistenciaTablaService;
import com.siga.siga_iea.asistencias.service.MiJornadaService;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.excel.ExcelImportResult;
import com.siga.siga_iea.excel.ExcelPlantillaSupport;
import com.siga.siga_iea.excel.ExcelValidationException;
import com.siga.siga_iea.usuarios.entity.Docente;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Controlador de la vista "Mi Jornada" y toma de asistencia docente.
 * No contiene @Transactional para no anular los límites transaccionales de abrirDia.
 * Siempre extrae los datos del operador de CurrentUserContextService, nunca del request.
 */
@Controller
@RequestMapping("/mi-jornada")
public class MiJornadaController {

    private final CurrentUserContextService ctx;
    private final MiJornadaService jornada;
    private final AsistenciaService asistencia;
    private final SesionClaseRepository sesionRepo;
    private final AsistenciaTablaService tabla;
    private final ObjectMapper objectMapper;
    private final AsistenciaExcelService asistenciaExcelService;

    @Autowired
    public MiJornadaController(CurrentUserContextService ctx,
                               MiJornadaService jornada,
                               AsistenciaService asistencia,
                               SesionClaseRepository sesionRepo,
                               AsistenciaTablaService tabla,
                               ObjectMapper objectMapper,
                               @Autowired(required = false) AsistenciaExcelService asistenciaExcelService) {
        this.ctx = ctx;
        this.jornada = jornada;
        this.asistencia = asistencia;
        this.sesionRepo = sesionRepo;
        this.tabla = tabla;
        this.objectMapper = objectMapper;
        this.asistenciaExcelService = asistenciaExcelService;
    }

    public MiJornadaController(CurrentUserContextService ctx,
                               MiJornadaService jornada,
                               AsistenciaService asistencia,
                               SesionClaseRepository sesionRepo,
                               AsistenciaTablaService tabla,
                               ObjectMapper objectMapper) {
        this(ctx, jornada, asistencia, sesionRepo, tabla, objectMapper, null);
    }

    @GetMapping
    public String ver(
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "sesionId", required = false) UUID sesionId,
            Model model) {
        model.addAttribute("activePage", "mi-jornada");
        Docente d = ctx.getDocenteAutenticado().orElse(null);
        if (d == null) {
            model.addAttribute("sinDocente", true);
            model.addAttribute("esAdminOAdmin", ctx.esAdminOAdministrativo());
            return "mi-jornada/index";
        }
        MiJornadaService.JornadaVista vista = (fecha != null) ? jornada.vistaPorFecha(d, fecha) : jornada.vistaHoy(d);
        model.addAttribute("sinDocente", false);
        model.addAttribute("docente", d);
        model.addAttribute("vista", vista);

        // Autoseleccionar la sesión pedida o la primera/única sesión disponible para mostrar de inmediato los estados guardados
        SesionClase sesionInicial = resolverSesionInicial(vista.getSesiones(), sesionId);
        if (sesionInicial != null) {
            model.addAttribute("activeSesionId", sesionInicial.getId());
            poblarModeloTablaSesion(sesionInicial, model);
        }
        return "mi-jornada/index";
    }

    @GetMapping("/sesiones")
    public String listarSesiones(
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "activeSesionId", required = false) UUID activeSesionId,
            Model model) {
        Docente d = ctx.getDocenteAutenticado()
                .orElseThrow(() -> new AccessDeniedException("No se encontró docente autenticado"));
        MiJornadaService.JornadaVista vista = (fecha != null) ? jornada.vistaPorFecha(d, fecha) : jornada.vistaHoy(d);
        model.addAttribute("vista", vista);
        model.addAttribute("activeSesionId", activeSesionId);
        return "mi-jornada/fragments :: sesiones";
    }

    @PostMapping("/iniciar")
    public String iniciar(Model model, HttpServletResponse response) {
        Docente d = ctx.getDocenteAutenticado()
                .orElseThrow(() -> new AccessDeniedException("No se encontró docente autenticado para iniciar jornada"));
        MiJornadaService.JornadaVista vista = jornada.iniciar(d);
        model.addAttribute("vista", vista);
        if (!vista.getSesiones().isEmpty()) {
            model.addAttribute("activeSesionId", vista.getSesiones().get(0).getId());
        }
        if (response != null) {
            response.setHeader("HX-Trigger", "jornadaIniciada");
        }
        return "mi-jornada/fragments :: sesiones";
    }

    @GetMapping("/sesiones/{id}")
    public String sesion(@PathVariable UUID id, Model model) {
        SesionClase s = sesionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada: " + id));
        autorizar(s);
        poblarModeloTablaSesion(s, model);
        return "mi-jornada/fragments :: tabla";
    }

    @PostMapping("/sesiones/{id}/guardar")
    public String guardar(@PathVariable UUID id,
                          @RequestParam(name = "estudianteId", required = false) List<UUID> estudianteId,
                          @RequestParam(name = "estado", required = false) List<String> estado,
                          Model model,
                          HttpServletResponse response) {
        if (estudianteId == null || estado == null || estudianteId.size() != estado.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de estudiantes y estados deben tener la misma dimensión");
        }

        List<AsistenciaItemDto> items = IntStream.range(0, estado.size())
                .mapToObj(i -> new AsistenciaItemDto(estudianteId.get(i), estado.get(i), null))
                .toList();

        try {
            asistencia.registrar(id, items, ctx.getDocenteAutenticado().orElse(null), ctx.getRolAutenticado());
            model.addAttribute("ok", true);
            model.addAttribute("mensaje", "Asistencia registrada exitosamente.");
            if (response != null) {
                response.setHeader("HX-Trigger", "jornadaGuardada");
            }
        } catch (SecurityException e) {
            model.addAttribute("ok", false);
            model.addAttribute("error", "No tienes permiso sobre esta sesión o la ventana de edición está cerrada.");
            return "mi-jornada/fragments :: mensaje";
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("ok", false);
            model.addAttribute("error", e.getMessage());
            return "mi-jornada/fragments :: mensaje";
        }

        return "mi-jornada/fragments :: mensaje";
    }

    @GetMapping("/sesiones/{id}/excel/exportar")
    public ResponseEntity<byte[]> exportarExcel(@PathVariable UUID id) {
        if (asistenciaExcelService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio Excel no disponible");
        }
        AsistenciaExcelService.ExportPayload payload = asistenciaExcelService.exportarPlantillaSesion(id);
        return ExcelPlantillaSupport.buildDownloadResponse(payload.bytes(), payload.filename());
    }

    @PostMapping("/sesiones/{id}/excel/importar")
    @ResponseBody
    public ResponseEntity<ExcelImportResult> importarExcel(
            @PathVariable UUID id,
            @RequestParam("archivo") MultipartFile archivo) {
        if (asistenciaExcelService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ExcelImportResult.fallo("Servicio Excel no disponible.", List.of()));
        }
        try {
            ExcelImportResult result = asistenciaExcelService.importarPlantillaSesion(id, archivo);
            return ResponseEntity.ok(result);
        } catch (ExcelValidationException ex) {
            return ResponseEntity.badRequest()
                    .body(ExcelImportResult.fallo(ex.getMessage(), ex.getErrores()));
        } catch (AccessDeniedException | SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ExcelImportResult.fallo(ex.getMessage(), List.of(ex.getMessage())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                    .body(ExcelImportResult.fallo(ex.getMessage(), List.of(ex.getMessage())));
        }
    }

    private SesionClase resolverSesionInicial(List<SesionClase> sesiones, UUID sesionIdSolicitada) {
        if (sesiones == null || sesiones.isEmpty()) {
            return null;
        }
        if (sesionIdSolicitada != null) {
            for (SesionClase s : sesiones) {
                if (sesionIdSolicitada.equals(s.getId())) {
                    return s;
                }
            }
        }
        return sesiones.get(0);
    }

    private void poblarModeloTablaSesion(SesionClase s, Model model) {
        List<FilaAsistenciaDto> filas = tabla.filas(s);
        Docente docente = ctx.getDocenteAutenticado().orElse(null);
        boolean puedeEditar = jornada.puedeEditarEnJornada(s, docente, ctx.getRolAutenticado());
        boolean cancelada = "CANCELADA".equalsIgnoreCase(s.getEstado());
        boolean soloLectura = !puedeEditar;
        boolean soloLecturaPorFecha = !cancelada && soloLectura;

        model.addAttribute("sesion", s);
        model.addAttribute("filas", filas);
        model.addAttribute("soloLectura", soloLectura);
        model.addAttribute("soloLecturaPorFecha", soloLecturaPorFecha);

        Map<String, String> mapaEstados = new LinkedHashMap<>();
        for (FilaAsistenciaDto f : filas) {
            if (f.estado() != null) {
                mapaEstados.put(f.id().toString(), f.estado());
            }
        }

        try {
            model.addAttribute("estadosJson", objectMapper.writeValueAsString(mapaEstados));
        } catch (JsonProcessingException e) {
            model.addAttribute("estadosJson", "{}");
        }
    }

    private void autorizar(SesionClase s) {
        if (ctx.esAdminOAdministrativo()) {
            return;
        }
        UUID mio = ctx.getDocenteAutenticado().map(Docente::getId).orElse(null);
        if (mio == null || s.getDocente() == null || !mio.equals(s.getDocente().getId())) {
            throw new AccessDeniedException("Sesión ajena");
        }
    }
}

package com.siga.siga_iea.asistencias.service;

import com.siga.siga_iea.asistencias.dto.AsistenciaItemDto;
import com.siga.siga_iea.asistencias.dto.FilaAsistenciaDto;
import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.asistencias.repository.SesionClaseRepository;
import com.siga.siga_iea.auth.enums.RolEnum;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.excel.ExcelImportResult;
import com.siga.siga_iea.excel.ExcelPlantillaSupport;
import com.siga.siga_iea.excel.ExcelValidationException;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Exportación e importación validada (todo o nada) de plantillas Excel (.xlsx) para Asistencias.
 */
@Service
public class AsistenciaExcelService {

    public static final String[] ESTADOS_PERMITIDOS = {"PRESENTE", "TARDE", "EXCUSADO", "AUSENTE"};
    private static final List<String> HEADERS_SESION = List.of("#", "Código", "Documento", "Estudiante", "Estado", "Observaciones");
    private static final List<String> HEADERS_CURSO_FECHA = List.of("#", "Código", "Documento", "Estudiante", "Asignatura", "Estado", "Observaciones");
    private static final List<String> HEADERS_CONSOLIDADO = List.of("#", "Curso", "Código", "Documento", "Estudiante", "Asignatura / Sesión", "Docente", "Estado", "Observaciones");
    private static final ZoneId ZONA_INSTITUCIONAL = ZoneId.of("America/Bogota");

    private final SesionClaseRepository sesionClaseRepository;
    private final AsistenciaTablaService asistenciaTablaService;
    private final AsistenciaService asistenciaService;
    private final AsistenciaRepository asistenciaRepository;
    private final CursoRepository cursoRepository;
    private final CursoEstudianteRepository cursoEstudianteRepository;
    private final CurrentUserContextService currentUserContextService;

    public AsistenciaExcelService(SesionClaseRepository sesionClaseRepository,
                                  AsistenciaTablaService asistenciaTablaService,
                                  AsistenciaService asistenciaService,
                                  AsistenciaRepository asistenciaRepository,
                                  CursoRepository cursoRepository,
                                  CursoEstudianteRepository cursoEstudianteRepository,
                                  CurrentUserContextService currentUserContextService) {
        this.sesionClaseRepository = sesionClaseRepository;
        this.asistenciaTablaService = asistenciaTablaService;
        this.asistenciaService = asistenciaService;
        this.asistenciaRepository = asistenciaRepository;
        this.cursoRepository = cursoRepository;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.currentUserContextService = currentUserContextService;
    }

    public record ExportPayload(byte[] bytes, String filename) {
    }

    @Transactional(readOnly = true)
    public ExportPayload exportarPlantillaSesion(UUID sesionId) {
        SesionClase sesion = sesionClaseRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + sesionId));
        autorizarSesion(sesion);

        List<FilaAsistenciaDto> filas = asistenciaTablaService.filas(sesion);
        Map<UUID, String> codigoPorEst = mapearCodigosPorCurso(sesion.getCurso().getId());

        String cursoNombre = formatearCurso(sesion.getCurso());
        String materiaNombre = resolverNombreMateria(sesion);
        String fechaStr = sesion.getFecha() != null ? sesion.getFecha().toString() : LocalDate.now(ZONA_INSTITUCIONAL).toString();
        String horarioStr = (sesion.getHoraInicio() != null && sesion.getHoraFin() != null)
                ? sesion.getHoraInicio() + " - " + sesion.getHoraFin()
                : "Jornada";

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Asistencia");
            ExcelPlantillaSupport.renderTitleBanner(
                    sheet, wb, HEADERS_SESION.size(),
                    "IEACI SIGA • PLANILLA OFICIAL DE ASISTENCIA",
                    "Curso: " + cursoNombre + "   |   Asignatura: " + materiaNombre + "   |   Fecha: " + fechaStr + "   |   Horario: " + horarioStr,
                    "Instrucciones: Seleccione el Estado (PRESENTE, TARDE, EXCUSADO o AUSENTE; también acepta P, T, E, FI). No modifique Documento ni encabezados."
            );

            crearFilaCabecera(sheet, wb, HEADERS_SESION);

            XSSFCellStyle roCenter = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.CENTER);
            XSSFCellStyle roLeft = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.LEFT);
            XSSFCellStyle edCenter = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.CENTER, false);
            XSSFCellStyle edLeft = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.LEFT, false);

            int rowIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX;
            int num = 1;
            for (FilaAsistenciaDto f : filas) {
                Row r = sheet.createRow(rowIdx++);
                r.setHeightInPoints(20);
                setCell(r, 0, String.valueOf(num++), roCenter);
                setCell(r, 1, codigoPorEst.getOrDefault(f.id(), ""), roCenter);
                setCell(r, 2, f.documento() != null ? f.documento() : "", roCenter);
                setCell(r, 3, f.nombre() != null ? f.nombre() : "", roLeft);
                setCell(r, 4, f.estado() != null ? f.estado() : "", edCenter);
                setCell(r, 5, f.observaciones() != null ? f.observaciones() : "", edLeft);
            }

            if (rowIdx > ExcelPlantillaSupport.DATA_START_ROW_INDEX) {
                ExcelPlantillaSupport.addDropdownValidation(
                        sheet,
                        ExcelPlantillaSupport.DATA_START_ROW_INDEX,
                        rowIdx - 1,
                        4,
                        ESTADOS_PERMITIDOS,
                        "Estado inválido",
                        "Seleccione PRESENTE, TARDE, EXCUSADO o AUSENTE."
                );
            }

            ajustarAnchos(sheet, new int[]{6, 14, 18, 34, 18, 36});

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("templateType", ExcelPlantillaSupport.TIPO_ASISTENCIA);
            meta.put("scope", "SESION");
            meta.put("cursoId", sesion.getCurso().getId().toString());
            meta.put("sesionId", sesion.getId().toString());
            meta.put("fecha", fechaStr);
            ExcelPlantillaSupport.writeMetaSheet(wb, meta);

            String filename = "Asistencia_" + ExcelPlantillaSupport.sanitizeFilenamePart(cursoNombre)
                    + "_" + ExcelPlantillaSupport.sanitizeFilenamePart(materiaNombre)
                    + "_" + fechaStr + ".xlsx";
            return new ExportPayload(ExcelPlantillaSupport.toBytes(wb), filename);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible generar la plantilla Excel de asistencia.", e);
        }
    }

    @Transactional(readOnly = true)
    public ExportPayload exportarDesdeAdmin(UUID cursoId, LocalDate fecha, String estadoFiltro, String materiaFiltro, String queryFiltro) {
        if (!currentUserContextService.esAdminOAdministrativo()
                && currentUserContextService.getAuthentication().isPresent()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede exportar consolidados de asistencia.");
        }

        LocalDate fechaEfectiva = fecha != null ? fecha : LocalDate.now(ZONA_INSTITUCIONAL);
        boolean sinFiltrosEstrechos = (estadoFiltro == null || estadoFiltro.isBlank())
                && (materiaFiltro == null || materiaFiltro.isBlank())
                && (queryFiltro == null || queryFiltro.isBlank());

        if (cursoId != null && sinFiltrosEstrechos) {
            return exportarPlantillaEditableCursoFecha(cursoId, fechaEfectiva);
        }
        return exportarReporteConsolidado(cursoId, fechaEfectiva, estadoFiltro, materiaFiltro, queryFiltro);
    }

    @Transactional
    public ExcelImportResult importarPlantillaSesion(UUID sesionId, MultipartFile file) {
        SesionClase sesion = sesionClaseRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + sesionId));
        autorizarSesion(sesion);

        if ("CANCELADA".equalsIgnoreCase(sesion.getEstado())) {
            throw new ExcelValidationException("No se puede importar asistencia sobre una sesión en estado CANCELADA.");
        }

        boolean esAdmin = currentUserContextService.esAdminOAdministrativo();
        LocalDate hoy = LocalDate.now(ZONA_INSTITUCIONAL);
        if (!esAdmin && currentUserContextService.getAuthentication().isPresent()
                && !sesion.getFecha().equals(hoy)) {
            throw new ExcelValidationException(
                    "Ventana de edición cerrada: los docentes solo pueden importar asistencia para la fecha de hoy (" + hoy + ").");
        }

        ExcelPlantillaSupport.ValidatedWorkbook vw = ExcelPlantillaSupport.openAndValidate(
                file, ExcelPlantillaSupport.TIPO_ASISTENCIA);
        try {
            Map<String, String> meta = vw.meta();
            String scope = meta.getOrDefault("scope", "");
            if ("REPORTE".equals(scope)) {
                throw new ExcelValidationException("El archivo cargado es un reporte consolidado de solo lectura. Descargue una plantilla oficial de sesión o curso.");
            }

            String metaCursoId = meta.getOrDefault("cursoId", "");
            String metaSesionId = meta.getOrDefault("sesionId", "");
            String metaFecha = meta.getOrDefault("fecha", "");

            if (!sesion.getCurso().getId().toString().equals(metaCursoId)
                    || !sesion.getFecha().toString().equals(metaFecha)
                    || (!metaSesionId.isBlank() && !sesion.getId().toString().equals(metaSesionId))) {
                throw new ExcelValidationException(
                        "La plantilla no corresponde a esta sesión (" + formatearCurso(sesion.getCurso())
                                + " / " + resolverNombreMateria(sesion) + " / " + sesion.getFecha()
                                + "). Descargue la plantilla correspondiente.");
            }

            List<String> headers = "CURSO_FECHA".equals(scope) ? HEADERS_CURSO_FECHA : HEADERS_SESION;
            ExcelPlantillaSupport.validateHeaderRow(vw.dataSheet(), ExcelPlantillaSupport.HEADER_ROW_INDEX, headers);

            int colEstado = "CURSO_FECHA".equals(scope) ? 5 : 4;
            int colObs = "CURSO_FECHA".equals(scope) ? 6 : 5;

            Map<String, Estudiante> inscritosPorDoc = mapearInscritosPorDocumento(sesion.getCurso().getId());
            List<AsistenciaItemDto> items = parsearYValidarFilasSesion(
                    vw.dataSheet(), headers.size(), 2, colEstado, colObs, inscritosPorDoc);

            Docente operador = currentUserContextService.getDocenteAutenticado().orElse(null);
            RolEnum rolOperador = currentUserContextService.getAuthentication().isPresent()
                    ? currentUserContextService.getRolAutenticado()
                    : null;

            asistenciaService.registrar(sesionId, items, operador, rolOperador);
            return ExcelImportResult.exito(
                    items.size(),
                    "Importación exitosa: " + items.size() + " registro(s) de asistencia guardados en la sesión."
            );
        } finally {
            ExcelPlantillaSupport.closeQuietly(vw.workbook());
        }
    }

    @Transactional
    public ExcelImportResult importarDesdeAdmin(UUID cursoId, LocalDate fecha, MultipartFile file) {
        if (!currentUserContextService.esAdminOAdministrativo()
                && currentUserContextService.getAuthentication().isPresent()) {
            throw new AccessDeniedException("Solo Administración o Secretaría puede importar planillas en Consolidado.");
        }
        if (cursoId == null) {
            throw new ExcelValidationException("Seleccione un curso específico antes de importar una plantilla Excel.");
        }

        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
        LocalDate fechaObjetivo = fecha != null ? fecha : LocalDate.now(ZONA_INSTITUCIONAL);

        ExcelPlantillaSupport.ValidatedWorkbook vw = ExcelPlantillaSupport.openAndValidate(
                file, ExcelPlantillaSupport.TIPO_ASISTENCIA);
        try {
            Map<String, String> meta = vw.meta();
            String scope = meta.getOrDefault("scope", "");
            if ("REPORTE".equals(scope)) {
                throw new ExcelValidationException("Este archivo es un reporte consolidado de solo lectura. Seleccione un curso específico y descárguelo como plantilla antes de importar.");
            }
            if (!cursoId.toString().equals(meta.getOrDefault("cursoId", ""))
                    || !fechaObjetivo.toString().equals(meta.getOrDefault("fecha", ""))) {
                throw new ExcelValidationException(
                        "La plantilla no coincide con el curso (" + formatearCurso(curso) + ") y fecha (" + fechaObjetivo + ") seleccionados.");
            }

            List<String> headers = "SESION".equals(scope) ? HEADERS_SESION : HEADERS_CURSO_FECHA;
            ExcelPlantillaSupport.validateHeaderRow(vw.dataSheet(), ExcelPlantillaSupport.HEADER_ROW_INDEX, headers);

            Map<String, Estudiante> inscritosPorDoc = mapearInscritosPorDocumento(cursoId);
            List<SesionClase> sesiones = sesionClaseRepository.findByCursoIdAndFechaOrderByHoraInicioAsc(cursoId, fechaObjetivo);
            if (sesiones.isEmpty()) {
                sesiones = asistenciaService.abrirDia(cursoId, fechaObjetivo);
            }
            if (sesiones.isEmpty()) {
                throw new ExcelValidationException(
                        "El curso " + formatearCurso(curso) + " no tiene sesiones ni horarios configurados para la fecha " + fechaObjetivo + ".");
            }

            if ("SESION".equals(scope)) {
                UUID sesionId = UUID.fromString(meta.get("sesionId"));
                SesionClase target = sesiones.stream()
                        .filter(s -> s.getId().equals(sesionId))
                        .findFirst()
                        .orElseThrow(() -> new ExcelValidationException("La sesión indicada en la plantilla ya no existe."));
                List<AsistenciaItemDto> items = parsearYValidarFilasSesion(
                        vw.dataSheet(), HEADERS_SESION.size(), 2, 4, 5, inscritosPorDoc);
                asistenciaService.registrar(target.getId(), items, null, RolEnum.ADMIN);
                return ExcelImportResult.exito(items.size(),
                        "Se importaron " + items.size() + " registro(s) de asistencia para " + formatearCurso(curso) + ".");
            }

            // Scope CURSO_FECHA: agrupa por asignatura y valida todo antes de guardar
            Map<String, SesionClase> sesionPorMateria = new LinkedHashMap<>();
            for (SesionClase s : sesiones) {
                if (!"CANCELADA".equalsIgnoreCase(s.getEstado())) {
                    sesionPorMateria.putIfAbsent(resolverNombreMateria(s).toLowerCase(Locale.ROOT), s);
                }
            }
            if (sesionPorMateria.isEmpty()) {
                throw new ExcelValidationException("Todas las sesiones del curso en esa fecha se encuentran canceladas.");
            }
            SesionClase sesionDefault = sesionPorMateria.values().iterator().next();

            Map<UUID, List<AsistenciaItemDto>> porSesion = new LinkedHashMap<>();
            Set<String> llavesVistas = new HashSet<>();
            List<String> errores = new ArrayList<>();
            Sheet sheet = vw.dataSheet();

            for (int rIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX; rIdx <= sheet.getLastRowNum(); rIdx++) {
                Row row = sheet.getRow(rIdx);
                if (ExcelPlantillaSupport.isRowEmpty(row, HEADERS_CURSO_FECHA.size())) {
                    continue;
                }
                int filaHumana = rIdx + 1;
                String doc = ExcelPlantillaSupport.readCellAsString(row.getCell(2));
                String nombre = ExcelPlantillaSupport.readCellAsString(row.getCell(3));
                String asignatura = ExcelPlantillaSupport.readCellAsString(row.getCell(4));
                String estadoRaw = ExcelPlantillaSupport.readCellAsString(row.getCell(5));
                String obs = ExcelPlantillaSupport.readCellAsString(row.getCell(6));

                if (doc.isBlank()) {
                    errores.add("Fila " + filaHumana + ": Falta el número de documento.");
                    continue;
                }
                Estudiante est = inscritosPorDoc.get(doc.trim());
                if (est == null) {
                    errores.add("Fila " + filaHumana + " (" + nombre + "): El documento '" + doc + "' no pertenece a un estudiante matriculado en este curso.");
                    continue;
                }

                SesionClase targetSesion;
                if (asignatura.isBlank() || sesionPorMateria.size() == 1) {
                    targetSesion = sesionPorMateria.getOrDefault(asignatura.toLowerCase(Locale.ROOT), sesionDefault);
                } else {
                    targetSesion = sesionPorMateria.get(asignatura.trim().toLowerCase(Locale.ROOT));
                    if (targetSesion == null) {
                        errores.add("Fila " + filaHumana + " (" + nombre + "): La asignatura '" + asignatura + "' no tiene sesión abierta en la fecha " + fechaObjetivo + ".");
                        continue;
                    }
                }

                String uniqueKey = targetSesion.getId() + "|" + doc.trim();
                if (!llavesVistas.add(uniqueKey)) {
                    errores.add("Fila " + filaHumana + ": Estudiante duplicado '" + doc + "' para la misma asignatura.");
                    continue;
                }

                String estadoNorm = normalizarEstadoAsistencia(estadoRaw);
                if (estadoNorm == null) {
                    errores.add("Fila " + filaHumana + " (" + est.getNombreCompleto() + "): Estado inválido '"
                            + (estadoRaw.isBlank() ? "vacío" : estadoRaw)
                            + "'. Permitidos: PRESENTE, TARDE, EXCUSADO, AUSENTE (o P, T, E, FI).");
                    continue;
                }

                porSesion.computeIfAbsent(targetSesion.getId(), k -> new ArrayList<>())
                        .add(new AsistenciaItemDto(est.getId(), estadoNorm, obs.isBlank() ? null : obs));
            }

            if (!errores.isEmpty()) {
                throw new ExcelValidationException(
                        "No se importaron registros. Se detectaron " + errores.size() + " error(es) en la plantilla.",
                        errores);
            }
            if (porSesion.isEmpty()) {
                throw new ExcelValidationException("La plantilla no contiene filas de estudiantes para importar.");
            }

            int total = 0;
            RolEnum rolOperador = currentUserContextService.getAuthentication().isPresent()
                    ? currentUserContextService.getRolAutenticado()
                    : RolEnum.ADMIN;
            for (Map.Entry<UUID, List<AsistenciaItemDto>> entry : porSesion.entrySet()) {
                asistenciaService.registrar(entry.getKey(), entry.getValue(), null, rolOperador);
                total += entry.getValue().size();
            }
            return ExcelImportResult.exito(total,
                    "Importación exitosa: " + total + " registro(s) actualizados en " + formatearCurso(curso) + ".");
        } finally {
            ExcelPlantillaSupport.closeQuietly(vw.workbook());
        }
    }

    private ExportPayload exportarPlantillaEditableCursoFecha(UUID cursoId, LocalDate fecha) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
        String cursoNombre = formatearCurso(curso);

        List<SesionClase> sesiones = sesionClaseRepository.findByCursoIdAndFechaOrderByHoraInicioAsc(cursoId, fecha);
        List<CursoEstudiante> inscritos = cursoEstudianteRepository.findByCursoId(cursoId);
        List<Asistencia> existentes = asistenciaRepository.findByCursoIdAndFecha(cursoId, fecha);

        Map<String, Asistencia> mapaPorSesionYEst = new HashMap<>();
        for (Asistencia a : existentes) {
            if (a.getSesion() != null && a.getEstudiante() != null) {
                mapaPorSesionYEst.put(a.getSesion().getId() + "|" + a.getEstudiante().getId(), a);
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Asistencia");
            ExcelPlantillaSupport.renderTitleBanner(
                    sheet, wb, HEADERS_CURSO_FECHA.size(),
                    "IEACI SIGA • PLANILLA OFICIAL DE ASISTENCIA POR CURSO",
                    "Curso: " + cursoNombre + "   |   Fecha: " + fecha,
                    "Instrucciones: Seleccione el Estado (PRESENTE, TARDE, EXCUSADO o AUSENTE; o P, T, E, FI). No altere Documento ni Asignatura."
            );
            crearFilaCabecera(sheet, wb, HEADERS_CURSO_FECHA);

            XSSFCellStyle roCenter = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.CENTER);
            XSSFCellStyle roLeft = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.LEFT);
            XSSFCellStyle edCenter = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.CENTER, false);
            XSSFCellStyle edLeft = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.LEFT, false);

            int rowIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX;
            int num = 1;

            List<SesionClase> sesionesActivas = sesiones.stream()
                    .filter(s -> !"CANCELADA".equalsIgnoreCase(s.getEstado()))
                    .toList();

            if (sesionesActivas.isEmpty()) {
                for (CursoEstudiante ce : inscritos) {
                    Estudiante e = ce.getEstudiante();
                    if (e == null) continue;
                    Row r = sheet.createRow(rowIdx++);
                    r.setHeightInPoints(20);
                    setCell(r, 0, String.valueOf(num++), roCenter);
                    setCell(r, 1, e.getCodigo() != null ? e.getCodigo() : "", roCenter);
                    setCell(r, 2, e.getNumeroDocumento() != null ? e.getNumeroDocumento() : "", roCenter);
                    setCell(r, 3, e.getNombreCompleto(), roLeft);
                    setCell(r, 4, "Jornada", roLeft);
                    setCell(r, 5, "", edCenter);
                    setCell(r, 6, "", edLeft);
                }
            } else {
                for (SesionClase sc : sesionesActivas) {
                    String materiaNombre = resolverNombreMateria(sc);
                    for (CursoEstudiante ce : inscritos) {
                        Estudiante e = ce.getEstudiante();
                        if (e == null) continue;
                        Asistencia a = mapaPorSesionYEst.get(sc.getId() + "|" + e.getId());
                        Row r = sheet.createRow(rowIdx++);
                        r.setHeightInPoints(20);
                        setCell(r, 0, String.valueOf(num++), roCenter);
                        setCell(r, 1, e.getCodigo() != null ? e.getCodigo() : "", roCenter);
                        setCell(r, 2, e.getNumeroDocumento() != null ? e.getNumeroDocumento() : "", roCenter);
                        setCell(r, 3, e.getNombreCompleto(), roLeft);
                        setCell(r, 4, materiaNombre, roLeft);
                        setCell(r, 5, a != null && a.getEstado() != null ? a.getEstado() : "", edCenter);
                        setCell(r, 6, a != null && a.getObservaciones() != null ? a.getObservaciones() : "", edLeft);
                    }
                }
            }

            if (rowIdx > ExcelPlantillaSupport.DATA_START_ROW_INDEX) {
                ExcelPlantillaSupport.addDropdownValidation(
                        sheet,
                        ExcelPlantillaSupport.DATA_START_ROW_INDEX,
                        rowIdx - 1,
                        5,
                        ESTADOS_PERMITIDOS,
                        "Estado inválido",
                        "Seleccione PRESENTE, TARDE, EXCUSADO o AUSENTE."
                );
            }

            ajustarAnchos(sheet, new int[]{6, 14, 18, 32, 24, 18, 34});

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("templateType", ExcelPlantillaSupport.TIPO_ASISTENCIA);
            meta.put("scope", "CURSO_FECHA");
            meta.put("cursoId", cursoId.toString());
            meta.put("fecha", fecha.toString());
            ExcelPlantillaSupport.writeMetaSheet(wb, meta);

            String filename = "Plantilla_Asistencia_" + ExcelPlantillaSupport.sanitizeFilenamePart(cursoNombre)
                    + "_" + fecha + ".xlsx";
            return new ExportPayload(ExcelPlantillaSupport.toBytes(wb), filename);
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar la plantilla Excel de asistencia.", e);
        }
    }

    private ExportPayload exportarReporteConsolidado(UUID cursoId, LocalDate fecha, String estadoFiltro, String materiaFiltro, String queryFiltro) {
        List<Asistencia> registros = (cursoId != null)
                ? asistenciaRepository.findByCursoIdAndFecha(cursoId, fecha)
                : asistenciaRepository.findByFecha(fecha);

        String estadoNorm = estadoFiltro != null ? estadoFiltro.trim().toUpperCase(Locale.ROOT) : "";
        String materiaNorm = materiaFiltro != null ? materiaFiltro.trim() : "";
        String queryNorm = queryFiltro != null ? queryFiltro.trim().toLowerCase(Locale.ROOT) : "";

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Consolidado");
            ExcelPlantillaSupport.renderTitleBanner(
                    sheet, wb, HEADERS_CONSOLIDADO.size(),
                    "IEACI SIGA • REPORTE CONSOLIDADO DE ASISTENCIA",
                    "Fecha: " + fecha + (cursoId == null ? "   |   Todos los cursos" : ""),
                    "Reporte institucional exportado con los filtros activos de consulta."
            );
            crearFilaCabecera(sheet, wb, HEADERS_CONSOLIDADO);

            XSSFCellStyle roCenter = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.CENTER);
            XSSFCellStyle roLeft = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.LEFT);

            int rowIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX;
            int num = 1;
            for (Asistencia a : registros) {
                String st = a.getEstado() != null ? a.getEstado().toUpperCase(Locale.ROOT) : "PRESENTE";
                SesionClase sc = a.getSesion();
                String materia = resolverNombreMateria(sc);
                String cursoEt = sc != null && sc.getCurso() != null ? formatearCurso(sc.getCurso()) : "-";
                String estNombre = a.getEstudiante() != null ? a.getEstudiante().getNombreCompleto() : "N/A";
                String doc = a.getEstudiante() != null && a.getEstudiante().getNumeroDocumento() != null ? a.getEstudiante().getNumeroDocumento() : "-";
                String cod = a.getEstudiante() != null && a.getEstudiante().getCodigo() != null ? a.getEstudiante().getCodigo() : "-";
                String docente = sc != null && sc.getDocente() != null ? sc.getDocente().getNombreCompleto() : "-";

                if (!estadoNorm.isEmpty() && !estadoNorm.equals(st)) continue;
                if (!materiaNorm.isEmpty() && !materiaNorm.equalsIgnoreCase(materia)) continue;
                if (!queryNorm.isEmpty()) {
                    String hay = (estNombre + " " + doc + " " + docente + " " + materia + " " + cursoEt).toLowerCase(Locale.ROOT);
                    if (!hay.contains(queryNorm)) continue;
                }

                Row r = sheet.createRow(rowIdx++);
                r.setHeightInPoints(20);
                setCell(r, 0, String.valueOf(num++), roCenter);
                setCell(r, 1, cursoEt, roCenter);
                setCell(r, 2, cod, roCenter);
                setCell(r, 3, doc, roCenter);
                setCell(r, 4, estNombre, roLeft);
                setCell(r, 5, materia, roLeft);
                setCell(r, 6, docente, roLeft);
                setCell(r, 7, st, roCenter);
                setCell(r, 8, a.getObservaciones() != null ? a.getObservaciones() : "", roLeft);
            }

            ajustarAnchos(sheet, new int[]{6, 18, 14, 16, 30, 24, 26, 16, 32});

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("templateType", ExcelPlantillaSupport.TIPO_ASISTENCIA);
            meta.put("scope", "REPORTE");
            meta.put("fecha", fecha.toString());
            ExcelPlantillaSupport.writeMetaSheet(wb, meta);

            return new ExportPayload(ExcelPlantillaSupport.toBytes(wb), "Consolidado_Asistencia_" + fecha + ".xlsx");
        } catch (IOException e) {
            throw new IllegalStateException("Error al exportar consolidado de asistencia.", e);
        }
    }

    private List<AsistenciaItemDto> parsearYValidarFilasSesion(
            Sheet sheet,
            int totalCols,
            int colDoc,
            int colEstado,
            int colObs,
            Map<String, Estudiante> inscritosPorDoc) {

        List<AsistenciaItemDto> items = new ArrayList<>();
        Set<String> docsVistos = new HashSet<>();
        List<String> errores = new ArrayList<>();

        for (int rIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX; rIdx <= sheet.getLastRowNum(); rIdx++) {
            Row row = sheet.getRow(rIdx);
            if (ExcelPlantillaSupport.isRowEmpty(row, totalCols)) {
                continue;
            }
            int filaHumana = rIdx + 1;
            String doc = ExcelPlantillaSupport.readCellAsString(row.getCell(colDoc));
            String nombre = ExcelPlantillaSupport.readCellAsString(row.getCell(colDoc + 1));
            String estadoRaw = ExcelPlantillaSupport.readCellAsString(row.getCell(colEstado));
            String obs = ExcelPlantillaSupport.readCellAsString(row.getCell(colObs));

            if (doc.isBlank()) {
                errores.add("Fila " + filaHumana + ": Falta el número de documento del estudiante.");
                continue;
            }
            Estudiante est = inscritosPorDoc.get(doc.trim());
            if (est == null) {
                errores.add("Fila " + filaHumana + " (" + nombre + "): El documento '" + doc + "' no pertenece a un estudiante matriculado en este curso.");
                continue;
            }
            if (!docsVistos.add(doc.trim())) {
                errores.add("Fila " + filaHumana + ": Documento '" + doc + "' duplicado en la plantilla.");
                continue;
            }

            String estadoNorm = normalizarEstadoAsistencia(estadoRaw);
            if (estadoNorm == null) {
                errores.add("Fila " + filaHumana + " (" + est.getNombreCompleto() + "): Estado inválido '"
                        + (estadoRaw.isBlank() ? "vacío" : estadoRaw)
                        + "'. Use PRESENTE, TARDE, EXCUSADO o AUSENTE (o P, T, E, FI).");
                continue;
            }

            items.add(new AsistenciaItemDto(est.getId(), estadoNorm, obs.isBlank() ? null : obs));
        }

        if (!errores.isEmpty()) {
            throw new ExcelValidationException(
                    "No se guardó la asistencia. Se encontraron " + errores.size() + " error(es) en el archivo.",
                    errores);
        }
        if (items.isEmpty()) {
            throw new ExcelValidationException("La plantilla no contiene registros de estudiantes para importar.");
        }
        return items;
    }

    public static String normalizarEstadoAsistencia(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        return switch (norm) {
            case "PRESENTE", "P" -> "PRESENTE";
            case "TARDE", "T", "RETARDO" -> "TARDE";
            case "EXCUSADO", "E" -> "EXCUSADO";
            case "AUSENTE", "FI", "INASISTENCIA" -> "AUSENTE";
            default -> null;
        };
    }

    private void autorizarSesion(SesionClase sesion) {
        if (!currentUserContextService.getAuthentication().isPresent()) {
            return;
        }
        if (currentUserContextService.esAdminOAdministrativo()) {
            return;
        }
        UUID mio = currentUserContextService.getDocenteAutenticado().map(Docente::getId).orElse(null);
        if (mio == null || sesion.getDocente() == null || !mio.equals(sesion.getDocente().getId())) {
            throw new AccessDeniedException("No tiene permiso para exportar o importar asistencia de una sesión ajena.");
        }
    }

    private Map<String, Estudiante> mapearInscritosPorDocumento(UUID cursoId) {
        Map<String, Estudiante> mapa = new LinkedHashMap<>();
        for (CursoEstudiante ce : cursoEstudianteRepository.findByCursoId(cursoId)) {
            Estudiante e = ce.getEstudiante();
            if (e != null && e.getNumeroDocumento() != null && !e.getNumeroDocumento().isBlank()) {
                mapa.put(e.getNumeroDocumento().trim(), e);
            }
        }
        return mapa;
    }

    private Map<UUID, String> mapearCodigosPorCurso(UUID cursoId) {
        Map<UUID, String> mapa = new HashMap<>();
        for (CursoEstudiante ce : cursoEstudianteRepository.findByCursoId(cursoId)) {
            if (ce.getEstudiante() != null && ce.getEstudiante().getCodigo() != null) {
                mapa.put(ce.getEstudiante().getId(), ce.getEstudiante().getCodigo());
            }
        }
        return mapa;
    }

    private String resolverNombreMateria(SesionClase sc) {
        if (sc == null) return "Jornada";
        if (sc.getCursoMateria() != null && sc.getCursoMateria().getMateria() != null) {
            return sc.getCursoMateria().getMateria().getNombre();
        }
        if (sc.getHorario() != null && sc.getHorario().getMateria() != null) {
            return sc.getHorario().getMateria().getNombre();
        }
        return "Jornada";
    }

    private String formatearCurso(Curso c) {
        if (c == null) return "-";
        String grupo = c.getGrupo() != null ? c.getGrupo() : "01";
        String jornada = c.getJornada() != null ? c.getJornada() : "";
        return c.getGrado() + " - " + grupo + (jornada.isBlank() ? "" : " (" + jornada + ")");
    }

    private void crearFilaCabecera(XSSFSheet sheet, XSSFWorkbook wb, List<String> headers) {
        XSSFCellStyle headerStyle = ExcelPlantillaSupport.createHeaderStyle(wb);
        Row headerRow = sheet.createRow(ExcelPlantillaSupport.HEADER_ROW_INDEX);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private void setCell(Row r, int col, String value, CellStyle style) {
        Cell cell = r.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void ajustarAnchos(XSSFSheet sheet, int[] widthsInChars) {
        for (int i = 0; i < widthsInChars.length; i++) {
            sheet.setColumnWidth(i, widthsInChars[i] * 256);
        }
    }
}


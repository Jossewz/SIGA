package com.siga.siga_iea.calificaciones.service;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.calificaciones.entity.Calificacion;
import com.siga.siga_iea.calificaciones.entity.Evaluacion;
import com.siga.siga_iea.calificaciones.repository.CalificacionesRepository;
import com.siga.siga_iea.calificaciones.repository.EvaluacionRepository;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.entity.CursoMateria;
import com.siga.siga_iea.clases.entity.Horario;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoMateriaRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.HorarioRepository;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.excel.ExcelImportResult;
import com.siga.siga_iea.excel.ExcelPlantillaSupport;
import com.siga.siga_iea.excel.ExcelValidationException;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Exportación e importación validada (todo o nada) de planillas Excel (.xlsx)
 * de Calificaciones para docentes y administración.
 */
@Service
public class CalificacionExcelService {

    private static final BigDecimal NOTA_MIN = BigDecimal.ZERO;
    private static final BigDecimal NOTA_MAX = new BigDecimal("5.00");

    private final CursoRepository cursoRepository;
    private final CursoEstudianteRepository cursoEstudianteRepository;
    private final CursoMateriaRepository cursoMateriaRepository;
    private final HorarioRepository horarioRepository;
    private final EvaluacionRepository evaluacionRepository;
    private final CalificacionesRepository calificacionesRepository;
    private final CalificacionesService calificacionesService;
    private final CurrentUserContextService currentUserContextService;
    private final PeriodoConfigService periodoConfigService;

    @Autowired
    public CalificacionExcelService(CursoRepository cursoRepository,
                                    CursoEstudianteRepository cursoEstudianteRepository,
                                    CursoMateriaRepository cursoMateriaRepository,
                                    HorarioRepository horarioRepository,
                                    EvaluacionRepository evaluacionRepository,
                                    CalificacionesRepository calificacionesRepository,
                                    CalificacionesService calificacionesService,
                                    CurrentUserContextService currentUserContextService,
                                    @Autowired(required = false) PeriodoConfigService periodoConfigService) {
        this.cursoRepository = cursoRepository;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.cursoMateriaRepository = cursoMateriaRepository;
        this.horarioRepository = horarioRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.calificacionesRepository = calificacionesRepository;
        this.calificacionesService = calificacionesService;
        this.currentUserContextService = currentUserContextService;
        this.periodoConfigService = periodoConfigService;
    }

    public record ExportPayload(byte[] bytes, String filename) {
    }

    private record PendingNota(UUID evaluacionId, UUID estudianteId, BigDecimal nota, String observaciones) {
    }

    @Transactional
    public ExportPayload exportarPlantillaNotas(UUID cursoId, UUID materiaId, String materiaNombre, Integer periodo) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
        String anioActivo = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());

        CursoMateria cm = resolverOCrearCursoMateria(cursoId, materiaId, materiaNombre, anioActivo);
        autorizarOperadorCursoMateria(curso, cm);

        int periodoEf = (periodo != null && periodo >= 1) ? periodo : 1;
        List<Evaluacion> evaluaciones = evaluacionRepository.findByCursoMateriaIdAndPeriodo(cm.getId(), periodoEf);
        List<CursoEstudiante> inscritos = ordenarEstudiantes(cursoEstudianteRepository.findByCursoId(cursoId));

        Map<UUID, Map<UUID, Calificacion>> mapaNotas = new HashMap<>();
        for (Calificacion c : calificacionesRepository.findByCursoMateriaId(cm.getId())) {
            if (c.getEvaluacion() != null && Integer.valueOf(periodoEf).equals(c.getEvaluacion().getPeriodo()) && c.getEstudiante() != null) {
                mapaNotas.computeIfAbsent(c.getEstudiante().getId(), k -> new HashMap<>())
                        .put(c.getEvaluacion().getId(), c);
            }
        }

        boolean esNuevaNotaPorDefecto = evaluaciones.isEmpty();
        List<String> evalHeaders = new ArrayList<>();
        List<String> evalIds = new ArrayList<>();
        List<BigDecimal> evalPesos = new ArrayList<>();

        if (esNuevaNotaPorDefecto) {
            evalHeaders.add("Nota 1 (100%)");
            evalIds.add("NEW");
            evalPesos.add(new BigDecimal("100.00"));
        } else {
            for (Evaluacion ev : evaluaciones) {
                String pct = ev.getPeso() != null ? ev.getPeso().stripTrailingZeros().toPlainString() : "0";
                evalHeaders.add(ev.getNombre() + " (" + pct + "%)");
                evalIds.add(ev.getId().toString());
                evalPesos.add(ev.getPeso() != null ? ev.getPeso() : BigDecimal.ZERO);
            }
        }

        List<String> allHeaders = new ArrayList<>();
        allHeaders.add("#");
        allHeaders.add("Código");
        allHeaders.add("Documento");
        allHeaders.add("Estudiante");
        allHeaders.addAll(evalHeaders);
        allHeaders.add("Nota Final");
        allHeaders.add("Observaciones");

        String matNombreEfectivo = cm.getMateria() != null ? cm.getMateria().getNombre() : "Asignatura";
        String cursoEt = formatearCurso(curso);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Calificaciones");
            ExcelPlantillaSupport.renderTitleBanner(
                    sheet, wb, allHeaders.size(),
                    "IEACI SIGA • PLANILLA OFICIAL DE CALIFICACIONES",
                    "Curso: " + cursoEt + "   |   Asignatura: " + matNombreEfectivo + "   |   Período: " + periodoEf + "   |   Año: " + anioActivo,
                    "Instrucciones: Ingrese notas entre 0.00 y 5.00. Deje vacía la celda que no desee calificar. No altere Documento ni encabezados."
            );

            XSSFCellStyle headerStyle = ExcelPlantillaSupport.createHeaderStyle(wb);
            Row headerRow = sheet.createRow(ExcelPlantillaSupport.HEADER_ROW_INDEX);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < allHeaders.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(allHeaders.get(i));
                cell.setCellStyle(headerStyle);
            }

            XSSFCellStyle roCenter = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.CENTER);
            XSSFCellStyle roLeft = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.LEFT);
            XSSFCellStyle roDecimal = ExcelPlantillaSupport.createReadonlyCellStyle(wb, HorizontalAlignment.CENTER);
            roDecimal.setDataFormat(wb.createDataFormat().getFormat("0.00"));
            XSSFCellStyle edDecimal = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.CENTER, true);
            XSSFCellStyle edLeft = ExcelPlantillaSupport.createEditableCellStyle(wb, HorizontalAlignment.LEFT, false);

            int firstEvalCol = 4;
            int lastEvalCol = firstEvalCol + evalHeaders.size() - 1;
            int colFinal = lastEvalCol + 1;
            int colObs = colFinal + 1;

            int rowIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX;
            int num = 1;
            for (CursoEstudiante ce : inscritos) {
                Estudiante est = ce.getEstudiante();
                if (est == null) continue;
                Row r = sheet.createRow(rowIdx);
                r.setHeightInPoints(20);
                setStringCell(r, 0, String.valueOf(num++), roCenter);
                setStringCell(r, 1, est.getCodigo() != null ? est.getCodigo() : "", roCenter);
                setStringCell(r, 2, est.getNumeroDocumento() != null ? est.getNumeroDocumento() : "", roCenter);
                setStringCell(r, 3, est.getNombreCompleto(), roLeft);

                Map<UUID, Calificacion> notasEst = mapaNotas.getOrDefault(est.getId(), Collections.emptyMap());
                String obsReciente = "";
                StringBuilder formulaSum = new StringBuilder();

                for (int eIdx = 0; eIdx < evalHeaders.size(); eIdx++) {
                    int col = firstEvalCol + eIdx;
                    Cell notaCell = r.createCell(col);
                    notaCell.setCellStyle(edDecimal);

                    if (!esNuevaNotaPorDefecto) {
                        UUID evalId = evaluaciones.get(eIdx).getId();
                        Calificacion c = notasEst.get(evalId);
                        if (c != null && c.getNota() != null) {
                            notaCell.setCellValue(c.getNota().doubleValue());
                            if (c.getObservaciones() != null && !c.getObservaciones().isBlank()) {
                                obsReciente = c.getObservaciones();
                            }
                        }
                    }

                    String colRef = CellReference.convertNumToColString(col) + (rowIdx + 1);
                    BigDecimal peso = evalPesos.get(eIdx);
                    if (formulaSum.length() > 0) formulaSum.append("+");
                    formulaSum.append("IF(ISNUMBER(").append(colRef).append("),").append(colRef).append("*").append(peso.toPlainString()).append("/100,0)");
                }

                Cell finalCell = r.createCell(colFinal);
                finalCell.setCellStyle(roDecimal);
                finalCell.setCellFormula("ROUND(" + formulaSum + ",2)");

                setStringCell(r, colObs, obsReciente, edLeft);
                rowIdx++;
            }

            if (rowIdx > ExcelPlantillaSupport.DATA_START_ROW_INDEX) {
                ExcelPlantillaSupport.addDecimalRangeValidation(
                        sheet,
                        ExcelPlantillaSupport.DATA_START_ROW_INDEX,
                        rowIdx - 1,
                        firstEvalCol,
                        lastEvalCol,
                        "0.00",
                        "5.00"
                );
            }

            sheet.setColumnWidth(0, 6 * 256);
            sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 32 * 256);
            for (int c = firstEvalCol; c <= lastEvalCol; c++) {
                sheet.setColumnWidth(c, 18 * 256);
            }
            sheet.setColumnWidth(colFinal, 14 * 256);
            sheet.setColumnWidth(colObs, 30 * 256);

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("templateType", ExcelPlantillaSupport.TIPO_CALIFICACIONES);
            meta.put("scope", "PERIODO");
            meta.put("cursoId", cursoId.toString());
            meta.put("cursoMateriaId", cm.getId().toString());
            meta.put("materiaId", cm.getMateria() != null ? cm.getMateria().getId().toString() : "");
            meta.put("periodo", String.valueOf(periodoEf));
            meta.put("evaluacionIds", String.join(",", evalIds));
            ExcelPlantillaSupport.writeMetaSheet(wb, meta);

            String filename = "Notas_" + ExcelPlantillaSupport.sanitizeFilenamePart(cursoEt)
                    + "_" + ExcelPlantillaSupport.sanitizeFilenamePart(matNombreEfectivo)
                    + "_P" + periodoEf + ".xlsx";
            return new ExportPayload(ExcelPlantillaSupport.toBytes(wb), filename);
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar la plantilla Excel de calificaciones.", e);
        }
    }

    @Transactional
    public ExcelImportResult importarNotas(UUID cursoId, UUID materiaId, String materiaNombre, Integer periodo, MultipartFile file) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
        String anioActivo = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());

        CursoMateria cm = resolverOCrearCursoMateria(cursoId, materiaId, materiaNombre, anioActivo);
        autorizarOperadorCursoMateria(curso, cm);

        int periodoEf = (periodo != null && periodo >= 1) ? periodo : 1;

        ExcelPlantillaSupport.ValidatedWorkbook vw = ExcelPlantillaSupport.openAndValidate(
                file, ExcelPlantillaSupport.TIPO_CALIFICACIONES);
        try {
            Map<String, String> meta = vw.meta();
            if (!cursoId.toString().equals(meta.getOrDefault("cursoId", ""))
                    || !cm.getId().toString().equals(meta.getOrDefault("cursoMateriaId", ""))
                    || !String.valueOf(periodoEf).equals(meta.getOrDefault("periodo", ""))) {
                String matNombre = cm.getMateria() != null ? cm.getMateria().getNombre() : "Asignatura";
                throw new ExcelValidationException(
                        "La plantilla cargada no corresponde a " + formatearCurso(curso)
                                + " / " + matNombre + " / Período " + periodoEf + ". Descargue la plantilla actualizada.");
            }

            String evalIdsRaw = meta.getOrDefault("evaluacionIds", "");
            if (evalIdsRaw.isBlank()) {
                throw new ExcelValidationException("La plantilla no contiene metadatos válidos de evaluaciones.");
            }

            List<String> evalTokens = Arrays.stream(evalIdsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            boolean esNuevaPorDefecto = evalTokens.size() == 1 && "NEW".equals(evalTokens.get(0));

            List<UUID> evaluacionUuids = new ArrayList<>();
            List<String> evalNombresEsperados = new ArrayList<>();

            if (esNuevaPorDefecto) {
                evalNombresEsperados.add("Nota 1 (100%)");
            } else {
                for (String token : evalTokens) {
                    UUID evId;
                    try {
                        evId = UUID.fromString(token);
                    } catch (IllegalArgumentException e) {
                        throw new ExcelValidationException("Identificador de evaluación inválido en la plantilla.");
                    }
                    Evaluacion ev = evaluacionRepository.findById(evId)
                            .orElseThrow(() -> new ExcelValidationException(
                                    "Una evaluación de esta plantilla fue eliminada en el sistema. Exporte una plantilla nueva."));
                    if (!ev.getCursoMateria().getId().equals(cm.getId()) || !Integer.valueOf(periodoEf).equals(ev.getPeriodo())) {
                        throw new ExcelValidationException("Las evaluaciones de la plantilla ya no coinciden con la asignatura/período.");
                    }
                    evaluacionUuids.add(ev.getId());
                    String pct = ev.getPeso() != null ? ev.getPeso().stripTrailingZeros().toPlainString() : "0";
                    evalNombresEsperados.add(ev.getNombre() + " (" + pct + "%)");
                }
            }

            List<String> expectedHeaders = new ArrayList<>();
            expectedHeaders.add("#");
            expectedHeaders.add("Código");
            expectedHeaders.add("Documento");
            expectedHeaders.add("Estudiante");
            expectedHeaders.addAll(evalNombresEsperados);
            expectedHeaders.add("Nota Final");
            expectedHeaders.add("Observaciones");

            ExcelPlantillaSupport.validateHeaderRow(vw.dataSheet(), ExcelPlantillaSupport.HEADER_ROW_INDEX, expectedHeaders);

            Map<String, Estudiante> inscritosPorDoc = new LinkedHashMap<>();
            for (CursoEstudiante ce : cursoEstudianteRepository.findByCursoId(cursoId)) {
                Estudiante e = ce.getEstudiante();
                if (e != null && e.getNumeroDocumento() != null && !e.getNumeroDocumento().isBlank()) {
                    inscritosPorDoc.put(e.getNumeroDocumento().trim(), e);
                }
            }

            Sheet sheet = vw.dataSheet();
            int firstEvalCol = 4;
            int evalCount = evalNombresEsperados.size();
            int colObs = firstEvalCol + evalCount + 1;

            List<String> errores = new ArrayList<>();
            Set<String> docsVistos = new HashSet<>();
            List<PendingNota> pendientes = new ArrayList<>();

            for (int rIdx = ExcelPlantillaSupport.DATA_START_ROW_INDEX; rIdx <= sheet.getLastRowNum(); rIdx++) {
                Row row = sheet.getRow(rIdx);
                if (ExcelPlantillaSupport.isRowEmpty(row, expectedHeaders.size())) {
                    continue;
                }
                int filaHumana = rIdx + 1;
                String doc = ExcelPlantillaSupport.readCellAsString(row.getCell(2));
                String nombre = ExcelPlantillaSupport.readCellAsString(row.getCell(3));
                String obs = ExcelPlantillaSupport.readCellAsString(row.getCell(colObs));

                if (doc.isBlank()) {
                    errores.add("Fila " + filaHumana + ": Falta el número de documento del estudiante.");
                    continue;
                }
                Estudiante est = inscritosPorDoc.get(doc.trim());
                if (est == null) {
                    errores.add("Fila " + filaHumana + " (" + nombre + "): El documento '" + doc + "' no pertenece a un estudiante matriculado en el curso.");
                    continue;
                }
                if (!docsVistos.add(doc.trim())) {
                    errores.add("Fila " + filaHumana + ": Documento '" + doc + "' duplicado en la plantilla.");
                    continue;
                }

                for (int eIdx = 0; eIdx < evalCount; eIdx++) {
                    int col = firstEvalCol + eIdx;
                    Cell cell = row.getCell(col);
                    try {
                        Optional<BigDecimal> notaOpt = ExcelPlantillaSupport.parseNotaOptional(cell);
                        if (notaOpt.isEmpty()) {
                            continue;
                        }
                        BigDecimal nota = notaOpt.get();
                        if (nota.compareTo(NOTA_MIN) < 0 || nota.compareTo(NOTA_MAX) > 0) {
                            errores.add("Fila " + filaHumana + " (" + est.getNombreCompleto() + "), columna '"
                                    + evalNombresEsperados.get(eIdx) + "': Nota " + nota + " fuera del rango permitido (0.00 a 5.00).");
                            continue;
                        }
                        UUID evId = esNuevaPorDefecto ? null : evaluacionUuids.get(eIdx);
                        pendientes.add(new PendingNota(evId, est.getId(), nota, obs.isBlank() ? null : obs));
                    } catch (IllegalArgumentException parseEx) {
                        errores.add("Fila " + filaHumana + " (" + est.getNombreCompleto() + "), columna '"
                                + evalNombresEsperados.get(eIdx) + "': " + parseEx.getMessage() + " (se esperaba número entre 0.00 y 5.00).");
                    }
                }
            }

            if (!errores.isEmpty()) {
                throw new ExcelValidationException(
                        "No se importaron calificaciones. Se encontraron " + errores.size() + " error(es) en el archivo.",
                        errores);
            }
            if (pendientes.isEmpty()) {
                throw new ExcelValidationException("La plantilla no contiene calificaciones diligenciadas para importar.");
            }

            UUID evaluacionDefaultId = null;
            if (esNuevaPorDefecto) {
                List<Evaluacion> actuales = evaluacionRepository.findByCursoMateriaIdAndPeriodo(cm.getId(), periodoEf);
                if (actuales.isEmpty()) {
                    Evaluacion creada = calificacionesService.crearEvaluacion(
                            cm.getId(), "Nota 1", periodoEf, new BigDecimal("100.00"));
                    evaluacionDefaultId = creada.getId();
                } else {
                    evaluacionDefaultId = actuales.get(0).getId();
                }
            }

            for (PendingNota pn : pendientes) {
                UUID targetEvId = pn.evaluacionId() != null ? pn.evaluacionId() : evaluacionDefaultId;
                calificacionesService.registrarONota(targetEvId, pn.estudianteId(), pn.nota(), pn.observaciones());
            }

            return ExcelImportResult.exito(
                    pendientes.size(),
                    "Importación exitosa: " + pendientes.size() + " calificación(es) registrada(s) en el período " + periodoEf + "."
            );
        } finally {
            ExcelPlantillaSupport.closeQuietly(vw.workbook());
        }
    }

    private CursoMateria resolverOCrearCursoMateria(UUID cursoId, UUID materiaId, String materiaNombre, String anioActivo) {
        if (materiaId != null) {
            return calificacionesService.obtenerOCrearCursoMateria(cursoId, materiaId, anioActivo);
        }
        if (materiaNombre != null && !materiaNombre.isBlank()) {
            return calificacionesService.obtenerOCrearCursoMateriaPorNombre(cursoId, materiaNombre.trim(), anioActivo);
        }
        throw new IllegalArgumentException("Debe seleccionar una asignatura válida para exportar o importar notas.");
    }

    private void autorizarOperadorCursoMateria(Curso curso, CursoMateria cm) {
        if (!currentUserContextService.getAuthentication().isPresent()) {
            return;
        }
        if (currentUserContextService.esAdminOAdministrativo()) {
            return;
        }
        if (!currentUserContextService.esDocente()) {
            throw new AccessDeniedException("Solo Docentes o Administración pueden gestionar calificaciones.");
        }
        UUID mio = currentUserContextService.getDocenteAutenticado().map(Docente::getId).orElse(null);
        if (mio == null) {
            throw new AccessDeniedException("Docente autenticado no encontrado.");
        }
        boolean esDirector = curso.getDirector() != null && mio.equals(curso.getDirector().getId());
        boolean esTitularCm = cm.getDocente() != null && mio.equals(cm.getDocente().getId());
        boolean enHorario = false;
        UUID targetMatId = cm.getMateria() != null ? cm.getMateria().getId() : null;
        for (Horario h : horarioRepository.findByCursoId(curso.getId())) {
            if (h.getDocente() != null && mio.equals(h.getDocente().getId())
                    && (targetMatId == null || (h.getMateria() != null && targetMatId.equals(h.getMateria().getId())))) {
                enHorario = true;
                break;
            }
        }
        if (!esDirector && !esTitularCm && !enHorario) {
            throw new AccessDeniedException("No tienes asignación académica en esta asignatura/curso para gestionar sus calificaciones.");
        }
    }

    private List<CursoEstudiante> ordenarEstudiantes(List<CursoEstudiante> lista) {
        return lista.stream()
                .filter(ce -> ce.getEstudiante() != null)
                .sorted(Comparator.comparing(ce -> ce.getEstudiante().getNombreCompleto(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String formatearCurso(Curso c) {
        if (c == null) return "-";
        String grupo = c.getGrupo() != null ? c.getGrupo() : "01";
        return c.getGrado() + " - " + grupo;
    }

    private void setStringCell(Row r, int col, String value, CellStyle style) {
        Cell cell = r.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
}


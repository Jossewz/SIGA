package com.siga.siga_iea.excel;

import com.siga.siga_iea.asistencias.entity.Asistencia;
import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.asistencias.repository.SesionClaseRepository;
import com.siga.siga_iea.calificaciones.entity.Evaluacion;
import com.siga.siga_iea.calificaciones.repository.CalificacionesRepository;
import com.siga.siga_iea.calificaciones.repository.EvaluacionRepository;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.entity.CursoMateria;
import com.siga.siga_iea.clases.entity.Materia;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoMateriaRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.MateriaRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Transactional
class ExcelImportExportTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private EstudianteRepository estudianteRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private CursoMateriaRepository cursoMateriaRepository;
    @Autowired private CursoEstudianteRepository cursoEstudianteRepository;
    @Autowired private SesionClaseRepository sesionClaseRepository;
    @Autowired private AsistenciaRepository asistenciaRepository;
    @Autowired private EvaluacionRepository evaluacionRepository;
    @Autowired private CalificacionesRepository calificacionesRepository;

    private Docente docenteTitular;
    private Docente docenteAjeno;
    private Estudiante est1;
    private Estudiante est2;
    private Curso curso;
    private Materia materia;
    private CursoMateria cursoMateria;
    private SesionClase sesionHoy;
    private SesionClase sesionAyer;

    @BeforeEach
    void setup() {
        usuarioRepository.save(new Usuario("admin.excel@ieaci.edu.co", "h", "ADMIN", "100001"));

        docenteTitular = new Docente();
        docenteTitular.setNombres("Carlos");
        docenteTitular.setApellidos("Acevedo");
        docenteTitular.setNumeroDocumento("200001");
        docenteTitular.setEstado("Activo");
        docenteTitular = docenteRepository.save(docenteTitular);
        usuarioRepository.save(new Usuario("doc.excel@ieaci.edu.co", "h", "DOCENTE", "200001"));

        docenteAjeno = new Docente();
        docenteAjeno.setNombres("Otro");
        docenteAjeno.setApellidos("Docente");
        docenteAjeno.setNumeroDocumento("200002");
        docenteAjeno.setEstado("Activo");
        docenteAjeno = docenteRepository.save(docenteAjeno);
        usuarioRepository.save(new Usuario("ajeno.excel@ieaci.edu.co", "h", "DOCENTE", "200002"));

        est1 = new Estudiante();
        est1.setNombres("Ana");
        est1.setApellidos("Gómez");
        est1.setCodigo("2026101");
        est1.setNumeroDocumento("300001");
        est1.setEstado("Activo");
        est1 = estudianteRepository.save(est1);
        usuarioRepository.save(new Usuario("ana.excel@ieaci.edu.co", "h", "ESTUDIANTE", "300001"));

        est2 = new Estudiante();
        est2.setNombres("Luis");
        est2.setApellidos("Martínez");
        est2.setCodigo("2026102");
        est2.setNumeroDocumento("300002");
        est2.setEstado("Activo");
        est2 = estudianteRepository.save(est2);

        curso = new Curso();
        curso.setGrado("11°");
        curso.setGrupo("01");
        curso.setJornada("Mañana");
        curso.setCuposMaximos(35);
        curso.setAnoLectivo("2026");
        curso.setDirector(docenteTitular);
        curso = cursoRepository.save(curso);

        cursoEstudianteRepository.save(new CursoEstudiante(curso, est1, "Activo"));
        cursoEstudianteRepository.save(new CursoEstudiante(curso, est2, "Activo"));

        materia = materiaRepository.save(new Materia("Matemáticas", "Exactas"));
        cursoMateria = cursoMateriaRepository.save(new CursoMateria(curso, materia, docenteTitular, "2026"));

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        sesionHoy = new SesionClase(curso, cursoMateria, null, docenteTitular, hoy, LocalTime.of(7, 0), LocalTime.of(8, 0), "ASIGNATURA");
        sesionHoy.setEstado("DICTADA");
        sesionHoy = sesionClaseRepository.save(sesionHoy);

        sesionAyer = new SesionClase(curso, cursoMateria, null, docenteTitular, hoy.minusDays(1), LocalTime.of(7, 0), LocalTime.of(8, 0), "ASIGNATURA");
        sesionAyer.setEstado("DICTADA");
        sesionAyer = sesionClaseRepository.save(sesionAyer);
    }

    @Test
    @DisplayName("Asistencia Excel: exporta plantilla oficial, importa válido todo-o-nada y bloquea plantilla alterada/inválida/fecha pasada docente")
    void testAsistenciaExcelExportImportYValidaciones() throws Exception {
        // 1. Exportar como docente titular
        MvcResult exportRes = mockMvc.perform(get("/mi-jornada/sesiones/" + sesionHoy.getId() + "/excel/exportar")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml.sheet")))
                .andReturn();

        byte[] xlsxBytes = exportRes.getResponse().getContentAsByteArray();
        byte[] modificadoValido;
        byte[] modificadoConErrorAtomico;
        byte[] modificadoFirmaAlterada;

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            int metaIdx = wb.getSheetIndex(ExcelPlantillaSupport.META_SHEET_NAME);
            assertTrue(metaIdx >= 0, "Debe incluir hoja oculta de metadatos");
            assertEquals(SheetVisibility.VERY_HIDDEN, wb.getSheetVisibility(metaIdx));

            Sheet sheet = wb.getSheetAt(0);
            for (int i = ExcelPlantillaSupport.DATA_START_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                String doc = ExcelPlantillaSupport.readCellAsString(r.getCell(2));
                if ("300001".equals(doc)) {
                    r.getCell(4).setCellValue("PRESENTE");
                    r.getCell(5).setCellValue("Puntual");
                } else if ("300002".equals(doc)) {
                    r.getCell(4).setCellValue("FI");
                    r.getCell(5).setCellValue("Sin excusa");
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            modificadoValido = baos.toByteArray();
        }

        // Archivo con fila inválida (debe rechazar TODO sin guardar a est1)
        try (XSSFWorkbook wbErr = new XSSFWorkbook(new ByteArrayInputStream(modificadoValido))) {
            wbErr.getSheetAt(0).getRow(ExcelPlantillaSupport.DATA_START_ROW_INDEX + 1).getCell(4).setCellValue("INVALIDO");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wbErr.write(baos);
            modificadoConErrorAtomico = baos.toByteArray();
        }

        // 2. Importar archivo con error -> 400 y 0 registros guardados
        mockMvc.perform(multipart("/mi-jornada/sesiones/" + sesionHoy.getId() + "/excel/importar")
                        .file(new MockMultipartFile("archivo", "asistencia.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", modificadoConErrorAtomico))
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errores[0]", containsString("Estado inválido")));
        assertEquals(0, asistenciaRepository.findBySesionId(sesionHoy.getId()).size(), "Validación todo-o-nada no debe persistir filas parciales");

        // 3. Archivo externo sin __SIGA_META__ -> rechazado
        try (XSSFWorkbook wbFake = new XSSFWorkbook()) {
            wbFake.createSheet("Hoja1").createRow(0).createCell(0).setCellValue("Hola");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wbFake.write(baos);
            mockMvc.perform(multipart("/mi-jornada/sesiones/" + sesionHoy.getId() + "/excel/importar")
                            .file(new MockMultipartFile("archivo", "externo.xlsx",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray()))
                            .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.mensaje", containsString("plantilla oficial")));
        }

        // 4. Firma alterada -> rechazado
        try (XSSFWorkbook wbTamper = new XSSFWorkbook(new ByteArrayInputStream(modificadoValido))) {
            Sheet meta = wbTamper.getSheet(ExcelPlantillaSupport.META_SHEET_NAME);
            meta.getRow(2).getCell(1).setCellValue("00000000-0000-0000-0000-000000000000");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wbTamper.write(baos);
            modificadoFirmaAlterada = baos.toByteArray();
        }
        mockMvc.perform(multipart("/mi-jornada/sesiones/" + sesionHoy.getId() + "/excel/importar")
                        .file(new MockMultipartFile("archivo", "tampered.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", modificadoFirmaAlterada))
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.mensaje", containsString("integridad")));

        // 5. Importación válida -> persiste PRESENTE y AUSENTE (FI)
        mockMvc.perform(multipart("/mi-jornada/sesiones/" + sesionHoy.getId() + "/excel/importar")
                        .file(new MockMultipartFile("archivo", "asistencia.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", modificadoValido))
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.registrosProcesados").value(2));

        List<Asistencia> guardadas = asistenciaRepository.findBySesionId(sesionHoy.getId());
        assertEquals(2, guardadas.size());
        assertEquals("PRESENTE", guardadas.stream().filter(a -> a.getEstudiante().getId().equals(est1.getId())).findFirst().orElseThrow().getEstado());
        assertEquals("AUSENTE", guardadas.stream().filter(a -> a.getEstudiante().getId().equals(est2.getId())).findFirst().orElseThrow().getEstado());

        // 6. Regla ventana de edición: docente NO puede importar fecha de ayer, ADMIN sí desde /asistencias
        MvcResult exportAyer = mockMvc.perform(get("/asistencias/excel/exportar")
                        .param("cursoId", curso.getId().toString())
                        .param("fecha", sesionAyer.getFecha().toString())
                        .with(user("admin.excel@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] ayerLleno;
        try (XSSFWorkbook wbAyer = new XSSFWorkbook(new ByteArrayInputStream(exportAyer.getResponse().getContentAsByteArray()))) {
            Sheet sh = wbAyer.getSheetAt(0);
            for (int i = ExcelPlantillaSupport.DATA_START_ROW_INDEX; i <= sh.getLastRowNum(); i++) {
                Row r = sh.getRow(i);
                r.getCell(5).setCellValue("TARDE");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wbAyer.write(baos);
            ayerLleno = baos.toByteArray();
        }

        mockMvc.perform(multipart("/mi-jornada/sesiones/" + sesionAyer.getId() + "/excel/importar")
                        .file(new MockMultipartFile("archivo", "ayer.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ayerLleno))
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("Ventana de edición cerrada")));

        mockMvc.perform(multipart("/asistencias/excel/importar")
                        .file(new MockMultipartFile("archivo", "ayer.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ayerLleno))
                        .param("cursoId", curso.getId().toString())
                        .param("fecha", sesionAyer.getFecha().toString())
                        .with(user("admin.excel@ieaci.edu.co").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(2, asistenciaRepository.findBySesionId(sesionAyer.getId()).size());

        // 7. Estudiante bloqueado en exportación
        mockMvc.perform(get("/asistencias/excel/exportar")
                        .with(user("ana.excel@ieaci.edu.co").roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());

        // 8. Renderizado completo de vistas Thymeleaf con registros poblados (/asistencias y /mi-jornada)
        mockMvc.perform(get("/asistencias")
                        .with(user("admin.excel@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exportar Excel")))
                .andExpect(content().string(containsString("Ana Gómez")));

        mockMvc.perform(get("/asistencias")
                        .param("cursoId", curso.getId().toString())
                        .param("fecha", sesionAyer.getFecha().toString())
                        .param("estado", "TARDE")
                        .with(user("admin.excel@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exportar Plantilla (.xlsx)")))
                .andExpect(content().string(containsString("Importar Excel")))
                .andExpect(content().string(containsString("Luis Martínez")));

        mockMvc.perform(get("/asistencias")
                        .with(user("ana.excel@ieaci.edu.co").roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi Historial de Asistencia")))
                .andExpect(content().string(containsString("Matemáticas")));

        mockMvc.perform(get("/mi-jornada")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exportar Excel")));
    }

    @Test
    @DisplayName("Calificaciones Excel: exporta evaluaciones del período, valida notas 0.00-5.00 todo-o-nada e impide acceso ajeno")
    void testCalificacionesExcelExportImportYValidaciones() throws Exception {
        Evaluacion ev1 = evaluacionRepository.save(new Evaluacion(cursoMateria, "Taller 1", 1, new BigDecimal("50.00")));
        Evaluacion ev2 = evaluacionRepository.save(new Evaluacion(cursoMateria, "Parcial", 1, new BigDecimal("50.00")));

        // Docente ajeno rechazado (403)
        mockMvc.perform(get("/clases/calificaciones/excel/exportar")
                        .param("cursoId", curso.getId().toString())
                        .param("materiaId", materia.getId().toString())
                        .param("periodo", "1")
                        .with(user("ajeno.excel@ieaci.edu.co").roles("DOCENTE")))
                .andExpect(status().isForbidden());

        // Docente titular exporta plantilla de notas
        MvcResult exp = mockMvc.perform(get("/clases/calificaciones/excel/exportar")
                        .param("cursoId", curso.getId().toString())
                        .param("materiaId", materia.getId().toString())
                        .param("periodo", "1")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml.sheet")))
                .andReturn();

        byte[] notasValidas;
        byte[] notasFueraDeRango;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exp.getResponse().getContentAsByteArray()))) {
            Sheet sh = wb.getSheetAt(0);
            for (int i = ExcelPlantillaSupport.DATA_START_ROW_INDEX; i <= sh.getLastRowNum(); i++) {
                Row r = sh.getRow(i);
                String doc = ExcelPlantillaSupport.readCellAsString(r.getCell(2));
                if ("300001".equals(doc)) {
                    r.getCell(4).setCellValue(4.50);
                    r.getCell(5).setCellValue("4,80");
                    r.getCell(7).setCellValue("Excelente desempeño");
                } else if ("300002".equals(doc)) {
                    r.getCell(4).setCellValue(3.20);
                    r.getCell(5).setCellValue(3.60);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            notasValidas = baos.toByteArray();
        }

        // Plantilla con nota 5.80 -> debe rechazar todo sin guardar nada
        try (XSSFWorkbook wbBad = new XSSFWorkbook(new ByteArrayInputStream(notasValidas))) {
            wbBad.getSheetAt(0).getRow(ExcelPlantillaSupport.DATA_START_ROW_INDEX + 1).getCell(4).setCellValue(5.80);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wbBad.write(baos);
            notasFueraDeRango = baos.toByteArray();
        }

        mockMvc.perform(multipart("/clases/calificaciones/excel/importar")
                        .file(new MockMultipartFile("archivo", "notas.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", notasFueraDeRango))
                        .param("cursoId", curso.getId().toString())
                        .param("materiaId", materia.getId().toString())
                        .param("periodo", "1")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fuera del rango permitido")));
        assertEquals(0, calificacionesRepository.findByCursoMateriaId(cursoMateria.getId()).size(), "Error todo-o-nada no debe guardar notas parciales");

        // Plantilla de periodo 1 rechazada al importar en periodo 2
        mockMvc.perform(multipart("/clases/calificaciones/excel/importar")
                        .file(new MockMultipartFile("archivo", "notas.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", notasValidas))
                        .param("cursoId", curso.getId().toString())
                        .param("materiaId", materia.getId().toString())
                        .param("periodo", "2")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no corresponde a")));

        // Importación válida en periodo 1 -> guarda 4 calificaciones
        mockMvc.perform(multipart("/clases/calificaciones/excel/importar")
                        .file(new MockMultipartFile("archivo", "notas.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", notasValidas))
                        .param("cursoId", curso.getId().toString())
                        .param("materiaId", materia.getId().toString())
                        .param("periodo", "1")
                        .with(user("doc.excel@ieaci.edu.co").roles("DOCENTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Importación exitosa: 4 calificación(es)")));

        assertEquals(new BigDecimal("4.50"),
                calificacionesRepository.findByEvaluacionIdAndEstudianteId(ev1.getId(), est1.getId()).orElseThrow().getNota());
        assertEquals(new BigDecimal("4.80"),
                calificacionesRepository.findByEvaluacionIdAndEstudianteId(ev2.getId(), est1.getId()).orElseThrow().getNota());
    }
}

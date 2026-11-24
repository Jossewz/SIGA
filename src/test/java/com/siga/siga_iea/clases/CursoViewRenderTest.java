package com.siga.siga_iea.clases;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class CursoViewRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.siga.siga_iea.usuarios.repository.EstudianteRepository estudianteRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /clases debe renderizar la vista sin errores de Thymeleaf")
    void testRenderClasesIndex() throws Exception {
        mockMvc.perform(get("/clases"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /clases/gestion debe renderizar la vista de detalle sin errores")
    void testRenderClasesGestion() throws Exception {
        mockMvc.perform(get("/clases/gestion").param("codigo", "11-01"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /clases/fragmento/tabla-notas debe renderizar el fragmento HTMX correctamente")
    void testRenderFragmentoTablaNotas() throws Exception {
        // Obtenemos el curso creado previamente o buscamos 11-01
        var mvcResult = mockMvc.perform(get("/clases/gestion").param("codigo", "11-01"))
                .andExpect(status().isOk())
                .andReturn();
        String cursoIdStr = (String) mvcResult.getModelAndView().getModel().get("cursoId");

        mockMvc.perform(get("/clases/fragmento/tabla-notas")
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1")
                        .param("fecha", "2026-03-01"))
                .andExpect(status().isOk());
    }

    @Autowired
    private com.siga.siga_iea.clases.repository.CursoRepository cursoRepository;

    @Autowired
    private com.siga.siga_iea.clases.repository.CursoEstudianteRepository cursoEstudianteRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /calificaciones debe renderizar el boletin de calificaciones sin errores de Thymeleaf")
    void testRenderCalificaciones() throws Exception {
        mockMvc.perform(get("/calificaciones"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /calificaciones con estudiante y asignaturas debe renderizar filas, acordeon y detalles")
    void testRenderCalificacionesConDatos() throws Exception {
        // Creamos estudiante
        com.siga.siga_iea.usuarios.entity.Estudiante est = new com.siga.siga_iea.usuarios.entity.Estudiante();
        est.setNombres("Valentina");
        est.setApellidos("Gómez");
        est.setNumeroDocumento("DOC-BOL-" + System.currentTimeMillis());
        est = estudianteRepository.save(est);

        // Obtenemos un curso existente
        java.util.List<com.siga.siga_iea.clases.entity.Curso> clases = cursoRepository.findAll();
        if (!clases.isEmpty()) {
            var curso = clases.get(0);
            com.siga.siga_iea.clases.entity.CursoEstudiante ce = new com.siga.siga_iea.clases.entity.CursoEstudiante();
            ce.setCurso(curso);
            ce.setEstudiante(est);
            ce.setAnoLectivo("2026");
            cursoEstudianteRepository.save(ce);
        }

        mockMvc.perform(get("/calificaciones").param("estudianteId", est.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Valentina Gómez")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Boletín de Calificaciones")));
    }

    @Autowired
    private com.siga.siga_iea.clases.service.CursoService cursoService;

    @Autowired
    private com.siga.siga_iea.clases.repository.MateriaRepository materiaRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /clases/calificaciones/guardar debe procesar decimales con punto y coma correctamente")
    void testGuardarCalificacionConDecimales() throws Exception {
        var mvcResult = mockMvc.perform(get("/clases/gestion").param("codigo", "11-01"))
                .andExpect(status().isOk())
                .andReturn();
        String cursoIdStr = (String) mvcResult.getModelAndView().getModel().get("cursoId");

        // Aseguramos que el curso tenga un horario configurado para el día actual
        com.siga.siga_iea.clases.entity.Materia mat = materiaRepository.findByNombre("Matemáticas")
                .orElseGet(() -> materiaRepository.save(new com.siga.siga_iea.clases.entity.Materia("Matemáticas", "Ciencias Exactas")));
        String diaHoy = com.siga.siga_iea.clases.controller.CursoController.obtenerNombreDiaEspanol(java.time.LocalDate.now().getDayOfWeek());
        cursoService.guardarHorarioBloque(java.util.UUID.fromString(cursoIdStr), diaHoy, mat.getId(), null, "07:00", "08:30", "Aula 101");

        // Crear una evaluacion
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/clases/evaluaciones/crear").with(csrf())
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1"))
                .andExpect(status().isOk());

        // Obtener fragmento para recuperar IDs
        var fragmentResult = mockMvc.perform(get("/clases/fragmento/tabla-notas")
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1"))
                .andExpect(status().isOk())
                .andReturn();

        java.util.List<?> evaluaciones = (java.util.List<?>) fragmentResult.getModelAndView().getModel().get("evaluaciones");
        java.util.UUID cursoMateriaId = (java.util.UUID) fragmentResult.getModelAndView().getModel().get("cursoMateriaId");
        org.junit.jupiter.api.Assertions.assertFalse(evaluaciones.isEmpty());
        com.siga.siga_iea.calificaciones.entity.Evaluacion ev = (com.siga.siga_iea.calificaciones.entity.Evaluacion) evaluaciones.get(0);

        // Insertamos un estudiante para probar
        com.siga.siga_iea.usuarios.entity.Estudiante est = new com.siga.siga_iea.usuarios.entity.Estudiante();
        est.setNombres("Carlos");
        est.setApellidos("Prueba");
        est.setNumeroDocumento("DOC-TEST-" + System.currentTimeMillis());
        est = estudianteRepository.save(est);
        
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/clases/calificaciones/guardar").with(csrf())
                        .param("evaluacionId", ev.getId().toString())
                        .param("estudianteId", est.getId().toString())
                        .param("nota", "4,75")
                        .param("cursoMateriaId", cursoMateriaId.toString())
                        .param("periodo", "1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("badge-final-")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.containsString("4.75"), org.hamcrest.Matchers.containsString("4,75"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /clases/fragmento/tabla-notas sin horario asignado debe mostrar aviso y bloquear acciones")
    void testRenderFragmentoSinHorarioBloqueado() throws Exception {
        var mvcResult = mockMvc.perform(get("/clases/gestion").param("codigo", "11-01"))
                .andExpect(status().isOk())
                .andReturn();
        String cursoIdStr = (String) mvcResult.getModelAndView().getModel().get("cursoId");
        var curso = cursoRepository.findById(java.util.UUID.fromString(cursoIdStr)).orElseThrow();

        // Creamos un estudiante asociado al curso para probar los botones bloqueados
        com.siga.siga_iea.usuarios.entity.Estudiante est = new com.siga.siga_iea.usuarios.entity.Estudiante();
        est.setNombres("Estudiante");
        est.setApellidos("Bloqueado");
        est.setNumeroDocumento("DOC-BLQ-" + System.currentTimeMillis());
        est = estudianteRepository.save(est);

        com.siga.siga_iea.clases.entity.CursoEstudiante ce = new com.siga.siga_iea.clases.entity.CursoEstudiante();
        ce.setCurso(curso);
        ce.setEstudiante(est);
        ce.setAnoLectivo("2026");
        cursoEstudianteRepository.save(ce);

        // Consultamos en una fecha de fin de semana (domingo 2026-03-01) sin horario
        mockMvc.perform(get("/clases/fragmento/tabla-notas")
                        .param("cursoId", cursoIdStr)
                        .param("periodo", "1")
                        .param("fecha", "2026-03-01"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Sin horario asignado para esta fecha")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Bloqueado")));
    }

    @Autowired
    private com.siga.siga_iea.usuarios.repository.UsuarioRepository usuarioRepository;

    @Autowired
    private com.siga.siga_iea.usuarios.repository.DocenteRepository docenteRepository;

    @Test
    @WithMockUser(username = "admin@ieaci.edu.co", roles = "ADMIN")
    @DisplayName("GET /clases/gestion como ADMIN debe renderizar el <select id='select-materia'> y los botones de auto-mapear y promover")
    void testRenderClasesGestionModoAdmin() throws Exception {
        mockMvc.perform(get("/clases/gestion").param("codigo", "11-01"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("<select")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("id=\"select-materia\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Auto-Mapear Matriculados")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Promover Año Lectivo")));
    }

    @Test
    @DisplayName("GET /clases/gestion como DOCENTE NO debe mostrar el <select> ni los botones de auto-mapear y promover")
    void testRenderClasesGestionModoDocente() throws Exception {
        String docEmail = "docente.test." + System.currentTimeMillis() + "@ieaci.edu.co";
        String numDoc = "DOC-TEST-" + System.currentTimeMillis();

        com.siga.siga_iea.usuarios.entity.Docente docEntity = new com.siga.siga_iea.usuarios.entity.Docente();
        docEntity.setNombres("Profesor");
        docEntity.setApellidos("Prueba");
        docEntity.setNumeroDocumento(numDoc);
        docenteRepository.save(docEntity);

        com.siga.siga_iea.usuarios.entity.Usuario userEntity = new com.siga.siga_iea.usuarios.entity.Usuario();
        userEntity.setEmail(docEmail);
        userEntity.setRol("DOCENTE");
        userEntity.setNumeroDocumento(numDoc);
        userEntity.setPassword("secret");
        usuarioRepository.save(userEntity);

        mockMvc.perform(get("/clases/gestion")
                        .param("codigo", "11-01")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(docEmail).roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("badge-materia-estatica")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("type=\"hidden\" id=\"select-materia\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<select id=\"select-materia\""))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Auto-Mapear Matriculados"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Promover Año Lectivo"))));
    }

    @Test
    @DisplayName("GET /clases/gestion como PERSONAL_ADMINISTRATIVO debe mostrar los botones de auto-mapear y promover")
    void testRenderClasesGestionModoAdministrativo() throws Exception {
        String adminEmail = "admin.sec." + System.currentTimeMillis() + "@ieaci.edu.co";
        String numDoc = "ADMIN-TEST-" + System.currentTimeMillis();

        com.siga.siga_iea.usuarios.entity.Usuario userEntity = new com.siga.siga_iea.usuarios.entity.Usuario();
        userEntity.setEmail(adminEmail);
        userEntity.setRol("PERSONAL_ADMINISTRATIVO");
        userEntity.setNumeroDocumento(numDoc);
        userEntity.setPassword("secret");
        usuarioRepository.save(userEntity);

        mockMvc.perform(get("/clases/gestion")
                        .param("codigo", "11-01")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(adminEmail).roles("PERSONAL_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Auto-Mapear Matriculados")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Promover Año Lectivo")));
    }

    @Autowired
    private com.siga.siga_iea.asistencias.service.AsistenciaService asistenciaService;

    @Autowired
    private com.siga.siga_iea.asistencias.repository.SesionClaseRepository sesionClaseRepository;

    @Test
    @WithMockUser(username = "admin@ieaci.edu.co", roles = "ADMIN")
    @DisplayName("Asistencia en Cursos sincroniza estados reales (Pendiente/PRESENTE/TARDE/AUSENTE) y notas filtran por fecha/periodo")
    void testSincronizacionAsistenciaYNotasPorFecha() throws Exception {
        var curso = new com.siga.siga_iea.clases.entity.Curso();
        curso.setGrado("10°");
        curso.setGrupo("99");
        curso.setJornada("Mañana");
        curso.setAnoLectivo("2026");
        curso = cursoRepository.save(curso);
        var cursoId = curso.getId();
        String cursoIdStr = cursoId.toString();

        var mat = materiaRepository.findByNombre("Matemáticas")
                .orElseGet(() -> materiaRepository.save(new com.siga.siga_iea.clases.entity.Materia("Matemáticas", "Ciencias Exactas")));
        java.time.LocalDate hoy = java.time.LocalDate.now();
        String diaHoy = com.siga.siga_iea.clases.application.CursoGestionAppService.obtenerNombreDiaEspanol(hoy.getDayOfWeek());
        cursoService.guardarHorarioBloque(cursoId, diaHoy, mat.getId(), null, "08:30", "10:00", "Aula 101");

        var est = new com.siga.siga_iea.usuarios.entity.Estudiante();
        est.setNombres("Sincro");
        est.setApellidos("Test");
        est.setNumeroDocumento("DOC-SINC-" + System.currentTimeMillis());
        est = estudianteRepository.save(est);

        var ce = new com.siga.siga_iea.clases.entity.CursoEstudiante();
        ce.setCurso(curso);
        ce.setEstudiante(est);
        ce.setAnoLectivo("2026");
        cursoEstudianteRepository.save(ce);

        // 1. Antes de tomar asistencia, la consulta es solo lectura: no crea sesión y muestra "Pendiente" (no "Presente")
        mockMvc.perform(get("/clases/fragmento/tabla-notas")
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1")
                        .param("fecha", hoy.toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Pendiente")));
        org.junit.jupiter.api.Assertions.assertTrue(sesionClaseRepository.findByCursoIdAndFecha(cursoId, hoy).isEmpty());

        // 2. Marcar TARDE desde Cursos crea/abre sesión y retorna estado TARDE
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/clases/asistencias/marcar").with(csrf())
                        .param("cursoId", cursoIdStr)
                        .param("estudianteId", est.getId().toString())
                        .param("materiaId", mat.getId().toString())
                        .param("fecha", hoy.toString())
                        .param("estado", "TARDE"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("TARDE")));

        var mapa = asistenciaService.obtenerMapaEstadosAsistencia(cursoId, hoy, mat.getId());
        org.junit.jupiter.api.Assertions.assertEquals("TARDE", mapa.get(est.getId()));

        // 3. Evaluaciones separadas por fecha vs vista PERIODO
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/clases/evaluaciones/crear").with(csrf())
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1")
                        .param("fecha", hoy.toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Nota 1")));

        // Vista en fecha pasada sin evaluaciones: vistaNotas=FECHA no muestra Nota 1 pero sí informa total del período
        java.time.LocalDate hace7Dias = hoy.minusDays(7);
        mockMvc.perform(get("/clases/fragmento/tabla-notas")
                        .param("cursoId", cursoIdStr)
                        .param("materiaNombre", "Matemáticas")
                        .param("periodo", "1")
                        .param("fecha", hace7Dias.toString())
                        .param("vistaNotas", "FECHA"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Nota 1<"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Todo el periodo (1)")));
    }

    @Autowired
    private com.siga.siga_iea.calificaciones.service.CalificacionesService calificacionesService;

    @Test
    @DisplayName("crearCurso sin grupo genera grupos secuenciales (01, 02) para el mismo grado y año")
    void testCrearCursoGruposSecuenciales() {
        var c1 = cursoService.crearCurso("9°", null, "Mañana", 35, null, "2026");
        var c2 = cursoService.crearCurso("9°", null, "Tarde", 35, null, "2026");

        org.junit.jupiter.api.Assertions.assertEquals("01", c1.getGrupo());
        org.junit.jupiter.api.Assertions.assertEquals("02", c2.getGrupo());
        org.junit.jupiter.api.Assertions.assertEquals("9-02", c2.getCodigoCurso());
    }

    @Test
    @DisplayName("ESTUDIANTE en /clases solo ve su curso sin botones de edición, es redirigido en /clases/gestion y ve Dashboard personalizado")
    void testVistaEstudianteCursosYDashboard() throws Exception {
        String estEmail = "est.vista." + System.currentTimeMillis() + "@ieaci.edu.co";
        String numDoc = "EST-V-" + System.currentTimeMillis();

        var cursoMio = cursoService.crearCurso("8°", null, "Mañana", 30, null, "2026");
        var cursoAjeno = cursoService.crearCurso("7°", null, "Tarde", 30, null, "2026");

        var est = new com.siga.siga_iea.usuarios.entity.Estudiante();
        est.setNombres("Sofía");
        est.setApellidos("Torres Test");
        est.setNumeroDocumento(numDoc);
        est = estudianteRepository.save(est);

        var ce = new com.siga.siga_iea.clases.entity.CursoEstudiante();
        ce.setCurso(cursoMio);
        ce.setEstudiante(est);
        ce.setAnoLectivo("2026");
        cursoEstudianteRepository.save(ce);

        var userEst = new com.siga.siga_iea.usuarios.entity.Usuario();
        userEst.setEmail(estEmail);
        userEst.setRol("ESTUDIANTE");
        userEst.setNumeroDocumento(numDoc);
        userEst.setPassword("secret");
        usuarioRepository.save(userEst);

        var authUser = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(estEmail).roles("ESTUDIANTE");

        mockMvc.perform(get("/clases").with(authUser))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute("cursosList", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute("cursosList", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasEntry("codigoCurso", cursoMio.getCodigoCurso()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("data-codigo=\"" + cursoMio.getCodigoCurso() + "\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(">" + cursoMio.getCodigoCurso() + "<")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-codigo=\"" + cursoAjeno.getCodigoCurso() + "\""))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">" + cursoAjeno.getCodigoCurso() + "<"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nuevo Curso"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Programar Clase"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Limpiar Todo"))));

        mockMvc.perform(get("/clases/gestion").param("codigo", cursoMio.getCodigoCurso()).with(authUser))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/?sinAcceso=CURSOS_GESTION"));

        mockMvc.perform(get("/").with(authUser))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Bienvenido, Sofía Torres Test.")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("¿Cómo voy en mis materias?")));
    }

    @Test
    @DisplayName("DOCENTE selecciona asignatura automáticamente según la hora actual en CursoGestionAppService y ve Dashboard Docente")
    void testDocenteSeleccionAutomaticaPorHoraYDashboard() throws Exception {
        String docEmail = "doc.hora." + System.currentTimeMillis() + "@ieaci.edu.co";
        String numDoc = "DOC-HR-" + System.currentTimeMillis();

        var doc = new com.siga.siga_iea.usuarios.entity.Docente();
        doc.setNombres("Carlos");
        doc.setApellidos("Mendoza Hora");
        doc.setNumeroDocumento(numDoc);
        doc = docenteRepository.save(doc);

        var userDoc = new com.siga.siga_iea.usuarios.entity.Usuario();
        userDoc.setEmail(docEmail);
        userDoc.setRol("DOCENTE");
        userDoc.setNumeroDocumento(numDoc);
        userDoc.setPassword("secret");
        usuarioRepository.save(userDoc);

        var curso = cursoService.crearCurso("6°", null, "Mañana", 30, doc.getId(), "2026");
        var mat1 = materiaRepository.findByNombre("Matemáticas")
                .orElseGet(() -> materiaRepository.save(new com.siga.siga_iea.clases.entity.Materia("Matemáticas", "Exactas")));
        var mat2 = materiaRepository.findByNombre("Ciencias Naturales")
                .orElseGet(() -> materiaRepository.save(new com.siga.siga_iea.clases.entity.Materia("Ciencias Naturales", "Naturales")));

        // Lunes 2026-11-16
        cursoService.guardarHorarioBloque(curso.getId(), "Lunes", mat1.getId(), doc.getId(), "07:00", "08:00", "Aula 101");
        cursoService.guardarHorarioBloque(curso.getId(), "Lunes", mat2.getId(), doc.getId(), "08:00", "09:30", "Aula 101");

        java.time.ZoneId zona = java.time.ZoneId.of("America/Bogota");
        java.time.Clock relojSegundoBloque = java.time.Clock.fixed(
                java.time.LocalDateTime.of(2026, 11, 16, 8, 30).atZone(zona).toInstant(), zona);
        var appConReloj = new com.siga.siga_iea.clases.application.CursoGestionAppService(
                cursoService, calificacionesService, asistenciaService, null, relojSegundoBloque);

        var tabla = appConReloj.prepararTablaNotas(
                curso.getId(), null, null, 1, java.time.LocalDate.of(2026, 11, 16),
                java.util.Optional.of(doc), false);

        org.junit.jupiter.api.Assertions.assertTrue(tabla.isTieneHorarioHoy());
        org.junit.jupiter.api.Assertions.assertEquals("Ciencias Naturales", tabla.getMateriaNombre());
        org.junit.jupiter.api.Assertions.assertTrue(tabla.getHorarioBannerTexto().contains("En curso ahora"));

        mockMvc.perform(get("/")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(docEmail).roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Bienvenido, Carlos Mendoza Hora.")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Mis Clases de Hoy")));
    }

    @Test
    @DisplayName("GET /actividad renderiza logs para ADMIN/ADMINISTRATIVO y bloquea a ESTUDIANTE")
    void testVistaActividadYLogs() throws Exception {
        mockMvc.perform(get("/actividad")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Registro de Actividad y Logs")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Actividad y Logs")));

        mockMvc.perform(get("/actividad")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("est.act@ieaci.edu.co").roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }
}

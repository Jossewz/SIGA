package com.siga.siga_iea.usuarios;

import com.siga.siga_iea.clases.entity.Materia;
import com.siga.siga_iea.clases.repository.MateriaRepository;
import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.repository.MatriculaRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class PersonalYEstudiantesFilterViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private DocenteRepository docenteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private MatriculaRepository matriculaRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /estudiantes debe renderizar buscador, nivel, estado, botones compactos y popover flotante estilo SEGI")
    void testRenderEstudiantesFiltros() throws Exception {
        mockMvc.perform(get("/estudiantes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Buscar por nombre, documento o código")))
                .andExpect(content().string(containsString("name=\"search\"")))
                .andExpect(content().string(containsString("name=\"nivel\"")))
                .andExpect(content().string(containsString("name=\"estado\"")))
                .andExpect(content().string(containsString("x-data=\"{ showFilters: false }\"")))
                .andExpect(content().string(containsString("@click.outside=\"showFilters = false\"")))
                .andExpect(content().string(containsString("x-show=\"showFilters\"")))
                .andExpect(content().string(containsString("Aplicar filtro")))
                .andExpect(content().string(containsString("h-9 px-3.5 text-xs font-black text-white bg-sidebar")))
                .andExpect(content().string(containsString("h-9 px-3.5 text-xs font-black text-text-secondary bg-surface-sunken")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /personal debe renderizar buscador, tipo, estado, botones compactos y popover flotante estilo SEGI")
    void testRenderPersonalFiltros() throws Exception {
        mockMvc.perform(get("/personal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Buscar por nombre, documento o cargo")))
                .andExpect(content().string(containsString("name=\"search\"")))
                .andExpect(content().string(containsString("name=\"tipo\"")))
                .andExpect(content().string(containsString("name=\"estado\"")))
                .andExpect(content().string(containsString("x-data=\"{ showFilters: false }\"")))
                .andExpect(content().string(containsString("@click.outside=\"showFilters = false\"")))
                .andExpect(content().string(containsString("x-show=\"showFilters\"")))
                .andExpect(content().string(containsString("Aplicar filtro")))
                .andExpect(content().string(containsString("h-9 px-3.5 text-xs font-black text-white bg-sidebar")))
                .andExpect(content().string(containsString("h-9 px-3.5 text-xs font-black text-text-secondary bg-surface-sunken")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET / (Dashboard) no debe contener la etiqueta 'En BD' y debe incluir el sidebar colapsable con Alpine.js")
    void testRenderDashboardSinEnBd() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estudiantes Activos")))
                .andExpect(content().string(containsString("Docentes Activos")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("En BD"))))
                .andExpect(content().string(containsString("id=\"sidebar-toggle-btn\"")))
                .andExpect(content().string(containsString("@click.stop.prevent=\"toggleSidebar()\"")))
                .andExpect(content().string(containsString("alpine.min.js")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /matricula debe renderizar con sidebar colapsable, Alpine.js y Matrícula como ítem activo")
    void testRenderMatriculaSidebar() throws Exception {
        mockMvc.perform(get("/matricula"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Formulario de Matrícula")))
                .andExpect(content().string(containsString("id=\"sidebar-toggle-btn\"")))
                .andExpect(content().string(containsString("@click.stop.prevent=\"toggleSidebar()\"")))
                .andExpect(content().string(containsString("alpine.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*href=\"/matricula\"\\s+class=\"sidebar-nav-item is-active\".*")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*href=\"/\"\\s+class=\"sidebar-nav-item\".*")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @Transactional
    @DisplayName("Registro de docente permite múltiples asignaturas del sistema, omite correo/titulación y activa cuenta luego")
    void testRegistroDocenteMultiAsignaturasYActivacionCuenta() throws Exception {
        Materia mat1 = materiaRepository.save(new Materia("Biología Marina", "Ciencias Naturales"));
        Materia mat2 = materiaRepository.save(new Materia("Química Ambiental", "Ciencias Exactas"));

        mockMvc.perform(get("/personal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"materiaIds\"")))
                .andExpect(content().string(containsString("Biología Marina")))
                .andExpect(content().string(not(containsString("Correo Electrónico Institucional"))))
                .andExpect(content().string(not(containsString("Titulación Profesional"))));

        mockMvc.perform(post("/personal/registrar")
                        .with(csrf())
                        .param("tipo", "DOCENTE")
                        .param("nombres", "Lucía")
                        .param("apellidos", "Herrera")
                        .param("tipoDocumento", "CC")
                        .param("numeroDocumento", "1045999888")
                        .param("telefono", "3005556677")
                        .param("materiaIds", mat1.getId().toString(), mat2.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal"));

        Docente guardado = docenteRepository.findByNumeroDocumento("1045999888").orElseThrow();
        assertEquals(2, guardado.getAsignaturas().size());
        assertEquals("Biología Marina, Química Ambiental", guardado.getEspecialidad());
        assertNull(guardado.getTitulo());
        assertTrue(usuarioRepository.findByNumeroDocumento("1045999888").isEmpty(),
                "No debe crearse cuenta institucional automáticamente al registrar el docente");

        mockMvc.perform(post("/personal/" + guardado.getId() + "/crear-cuenta")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal/perfil/" + guardado.getId() + "?tab=cuenta"));

        var cuenta = usuarioRepository.findByNumeroDocumento("1045999888").orElseThrow();
        assertEquals("lucia.herrera@ieaci.edu.co", cuenta.getEmail());
        assertEquals("DOCENTE", cuenta.getRol());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @Transactional
    @DisplayName("GET /estudiantes debe listar estudiantes recién matriculados sin CursoEstudiante asignado aún")
    void testListadoEstudiantesIncluyeRecienMatriculadosSinCurso() throws Exception {
        Estudiante est = new Estudiante();
        est.setNombres("Jose");
        est.setApellidos("Ayola");
        est.setCodigo("2026999");
        est.setNumeroDocumento("1010761175");
        est.setEstado("Activo");
        est = estudianteRepository.save(est);

        Matricula mat = new Matricula();
        mat.setEstudiante(est);
        mat.setGrado("11°");
        mat.setSalon("01");
        mat.setAnoLectivo("2026");
        mat.setEstado("PENDIENTE_DE_REVISION");
        matriculaRepository.save(mat);

        mockMvc.perform(get("/estudiantes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jose Ayola")))
                .andExpect(content().string(containsString("11°")));

        mockMvc.perform(get("/estudiantes").param("nivel", "BACHILLERATO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jose Ayola")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @Transactional
    @DisplayName("Acciones unificadas en /personal y /estudiantes: menú de 3 puntos, deshabilitar sincronizando cuenta y eliminar docente")
    void testAccionesUnificadasPersonalYEstudiantes() throws Exception {
        Materia mat = materiaRepository.save(new Materia("Ecología Aplicada", "Ciencias Naturales"));
        mockMvc.perform(post("/personal/registrar")
                        .with(csrf())
                        .param("tipo", "DOCENTE")
                        .param("nombres", "Mario")
                        .param("apellidos", "Cárdenas")
                        .param("tipoDocumento", "CC")
                        .param("numeroDocumento", "1045777666")
                        .param("materiaIds", mat.getId().toString()))
                .andExpect(status().is3xxRedirection());

        Docente doc = docenteRepository.findByNumeroDocumento("1045777666").orElseThrow();
        mockMvc.perform(post("/personal/" + doc.getId() + "/crear-cuenta").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/personal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gestionar usuario")))
                .andExpect(content().string(containsString("Deshabilitar docente")))
                .andExpect(content().string(containsString("Eliminar docente")))
                .andExpect(content().string(not(containsString("Ver hoja de vida"))))
                .andExpect(content().string(not(containsString("Activar o gestionar cuenta"))));

        // Deshabilitar docente sincroniza su Usuario a Inactivo
        mockMvc.perform(post("/personal/" + doc.getId() + "/toggle-estado")
                        .with(csrf())
                        .param("redirectTo", "/personal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal"));

        assertEquals("Inactivo", docenteRepository.findById(doc.getId()).orElseThrow().getEstado());
        assertEquals("Inactivo", usuarioRepository.findByNumeroDocumento("1045777666").orElseThrow().getEstado());

        // Rehabilitar docente restaura Usuario a Activo
        mockMvc.perform(post("/personal/" + doc.getId() + "/toggle-estado")
                        .with(csrf())
                        .param("redirectTo", "/personal"))
                .andExpect(status().is3xxRedirection());
        assertEquals("Activo", docenteRepository.findById(doc.getId()).orElseThrow().getEstado());
        assertEquals("Activo", usuarioRepository.findByNumeroDocumento("1045777666").orElseThrow().getEstado());

        // Eliminar docente borra docente y su cuenta vinculada
        mockMvc.perform(post("/personal/" + doc.getId() + "/eliminar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal"));
        assertTrue(docenteRepository.findById(doc.getId()).isEmpty());
        assertTrue(usuarioRepository.findByNumeroDocumento("1045777666").isEmpty());

        // Estudiantes: un solo link de gestionar + deshabilitar sincroniza Usuario
        Estudiante est = new Estudiante();
        est.setNombres("Sofía");
        est.setApellidos("Mendoza");
        est.setCodigo("2026888");
        est.setNumeroDocumento("1045111222");
        est.setEstado("Activo");
        est = estudianteRepository.save(est);

        mockMvc.perform(post("/estudiantes/" + est.getId() + "/crear-cuenta").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/estudiantes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gestionar usuario")))
                .andExpect(content().string(containsString("Deshabilitar estudiante")))
                .andExpect(content().string(containsString("Eliminar estudiante")))
                .andExpect(content().string(not(containsString("Ver expediente"))));

        mockMvc.perform(post("/estudiantes/" + est.getId() + "/toggle-estado")
                        .with(csrf())
                        .param("redirectTo", "/estudiantes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/estudiantes"));

        assertEquals("Retirado", estudianteRepository.findById(est.getId()).orElseThrow().getEstado());
        assertEquals("Inactivo", usuarioRepository.findByNumeroDocumento("1045111222").orElseThrow().getEstado());
    }
}

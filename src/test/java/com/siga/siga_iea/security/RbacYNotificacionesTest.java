package com.siga.siga_iea.security;

import com.siga.siga_iea.asistencias.entity.SesionClase;
import com.siga.siga_iea.asistencias.repository.SesionClaseRepository;
import com.siga.siga_iea.asistencias.service.AsistenciaService;
import com.siga.siga_iea.asistencias.dto.AsistenciaItemDto;
import com.siga.siga_iea.auth.entity.PasswordResetToken;
import com.siga.siga_iea.auth.enums.RolEnum;
import com.siga.siga_iea.auth.repository.PasswordResetTokenRepository;
import com.siga.siga_iea.auth.service.PasswordResetService;
import com.siga.siga_iea.certificados.service.CertificadoService;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoEstudiante;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.notificaciones.entity.Notificacion;
import com.siga.siga_iea.notificaciones.repository.NotificacionRepository;
import com.siga.siga_iea.reportes.entity.Reporte;
import com.siga.siga_iea.reportes.repository.ReporteRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RbacYNotificacionesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EstudianteRepository estudianteRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private CursoEstudianteRepository cursoEstudianteRepository;
    @Autowired private SesionClaseRepository sesionClaseRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ReporteRepository reporteRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CertificadoService certificadoService;
    @Autowired private AsistenciaService asistenciaService;

    private Usuario userAdmin;
    private Usuario userDocente;
    private Usuario userEstudiante1;
    private Usuario userEstudiante2;
    private Estudiante est1;
    private Estudiante est2;
    private Docente doc1;

    @BeforeEach
    void setup() {
        userAdmin = usuarioRepository.save(new Usuario("coord.rbac@ieaci.edu.co", passwordEncoder.encode("Secret123*"), "PERSONAL_ADMINISTRATIVO", "900001"));
        userDocente = usuarioRepository.save(new Usuario("doc.rbac@ieaci.edu.co", passwordEncoder.encode("Secret123*"), "DOCENTE", "900002"));
        userEstudiante1 = usuarioRepository.save(new Usuario("est1.rbac@ieaci.edu.co", passwordEncoder.encode("Secret123*"), "ESTUDIANTE", "900003"));
        userEstudiante2 = usuarioRepository.save(new Usuario("est2.rbac@ieaci.edu.co", passwordEncoder.encode("Secret123*"), "ESTUDIANTE", "900004"));

        doc1 = new Docente();
        doc1.setNombres("Carlos");
        doc1.setApellidos("Acevedo");
        doc1.setNumeroDocumento("900002");
        doc1.setEstado("Activo");
        doc1 = docenteRepository.save(doc1);

        est1 = new Estudiante();
        est1.setNombres("Jose");
        est1.setApellidos("Ayola");
        est1.setNumeroDocumento("900003");
        est1.setEstado("Activo");
        est1 = estudianteRepository.save(est1);

        est2 = new Estudiante();
        est2.setNombres("Maria");
        est2.setApellidos("Perez");
        est2.setNumeroDocumento("900004");
        est2.setEstado("Activo");
        est2 = estudianteRepository.save(est2);
    }

    @Test
    @DisplayName("RBAC: ESTUDIANTE recibe 403 en mutaciones administrativas, docentes y consultas IDOR de otros estudiantes")
    void testEstudianteBloqueadoEnAccionesPrivilegiadasEIdor() throws Exception {
        mockMvc.perform(post("/estudiantes/" + est2.getId() + "/eliminar")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/personal")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("nombres", "Hacker")
                        .param("apellidos", "Test")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/clases/crear")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("grado", "11°")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reportes/atender")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("reporteId", UUID.randomUUID().toString())
                        .param("decision", "Aceptado")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/certificados/responder")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("solicitudId", UUID.randomUUID().toString())
                        .param("mensajeRespuesta", "Autoaprobado")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // IDOR calificaciones y boletín PDF de otro estudiante
        mockMvc.perform(get("/calificaciones").param("estudianteId", est2.getId().toString())
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/calificaciones/boletin/pdf").param("estudianteId", est2.getId().toString())
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());

        // IDOR perfil de otro estudiante
        mockMvc.perform(get("/estudiantes/perfil/" + est2.getId())
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());

        // Selector oculto en su propia vista de calificaciones
        mockMvc.perform(get("/calificaciones")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"select-estudiante\""))));
    }

    @Test
    @DisplayName("RBAC: DOCENTE bloqueado en administración de cursos/personal/citaciones y permitido en reportes")
    void testDocenteBloqueadoEnAdminCursos() throws Exception {
        mockMvc.perform(post("/clases/crear")
                        .with(user(userDocente.getEmail()).roles("DOCENTE"))
                        .param("grado", "10°")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reportes/atender")
                        .with(user(userDocente.getEmail()).roles("DOCENTE"))
                        .param("reporteId", UUID.randomUUID().toString())
                        .param("decision", "Aceptado")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reportes/crear")
                        .with(user(userDocente.getEmail()).roles("DOCENTE"))
                        .param("estudianteId", est1.getId().toString())
                        .param("razon", "Incumplimiento")
                        .param("detalles", "No entregó actividad de clase.")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ESTUDIANTE puede ver /reportes y reportar a otro estudiante o a un docente con causas diferenciadas")
    void testEstudianteReportaCompaneroYDocente() throws Exception {
        mockMvc.perform(get("/reportes")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reportar Novedad de Convivencia")))
                .andExpect(content().string(containsString("Acoso escolar o bullying")));

        mockMvc.perform(post("/reportes/crear")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("objetivoTipo", "ESTUDIANTE")
                        .param("estudianteId", est2.getId().toString())
                        .param("razon", "Acoso escolar o bullying")
                        .param("detalles", "Situación reiterada en el descanso.")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/reportes/crear")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("objetivoTipo", "DOCENTE")
                        .param("docenteId", doc1.getId().toString())
                        .param("razon", "Inconformidad en evaluación o calificación")
                        .param("detalles", "No se computó la actividad del lunes.")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<Reporte> reportes = reporteRepository.findAll();
        assertTrue(reportes.stream().anyMatch(r -> "ESTUDIANTE".equals(r.getReportanteTipo()) && "ESTUDIANTE".equals(r.getObjetivoTipo())));
        assertTrue(reportes.stream().anyMatch(r -> "ESTUDIANTE".equals(r.getReportanteTipo()) && "DOCENTE".equals(r.getObjetivoTipo())));
        // Debe haber notificado al administrativo y al estudiante reportado
        assertTrue(notificacionRepository.countByUsuarioIdAndLeidaEnIsNull(userAdmin.getId()) >= 2);
        assertTrue(notificacionRepository.countByUsuarioIdAndLeidaEnIsNull(userEstudiante2.getId()) >= 1);
    }

    @Test
    @DisplayName("Notificaciones in-app (campana): certificado, inasistencia, lectura y aislamiento")
    void testNotificacionesCampanaCertificadosYAsistencia() throws Exception {
        certificadoService.crearSolicitud(est1.getId(), "Constancia de matrícula", "Constancias Administrativas", "EPS", null, "11°");

        assertTrue(notificacionRepository.countByUsuarioIdAndLeidaEnIsNull(userAdmin.getId()) >= 1);

        // Asistencia AUSENTE genera notificación para est1
        Curso curso = new Curso();
        curso.setGrado("11°");
        curso.setGrupo("01");
        curso.setJornada("Mañana");
        curso.setAnoLectivo("2026");
        curso = cursoRepository.save(curso);
        cursoEstudianteRepository.save(new CursoEstudiante(curso, est1, "2026"));

        SesionClase sesion = sesionClaseRepository.save(new SesionClase(
                curso, null, null, doc1, LocalDate.now(), LocalTime.of(7, 0), LocalTime.of(8, 30), "JORNADA"));

        asistenciaService.registrar(
                sesion.getId(),
                List.of(new AsistenciaItemDto(est1.getId(), "AUSENTE", "No asistió")),
                doc1,
                RolEnum.DOCENTE
        );

        assertTrue(notificacionRepository.countByUsuarioIdAndLeidaEnIsNull(userEstudiante1.getId()) >= 1);

        mockMvc.perform(get("/api/notificaciones")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("inasistencia")));

        Notificacion notifEst1 = notificacionRepository.findAll().stream()
                .filter(n -> n.getUsuario().getId().equals(userEstudiante1.getId()))
                .findFirst().orElseThrow();

        // Otro estudiante NO puede marcar como leída una notificación ajena
        mockMvc.perform(post("/api/notificaciones/" + notifEst1.getId() + "/leer")
                        .with(user(userEstudiante2.getEmail()).roles("ESTUDIANTE"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // El dueño SÍ puede marcarla
        mockMvc.perform(post("/api/notificaciones/" + notifEst1.getId() + "/leer")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("Password Reset: solicitud, validación y uso único del token de restablecimiento")
    void testPasswordResetFlujoCompleto() throws Exception {
        String rawToken = "test-secure-reset-token-123456";
        String hash = PasswordResetService.sha256Hex(rawToken);
        tokenRepository.save(new PasswordResetToken(userEstudiante1, hash, java.time.LocalDateTime.now().plusMinutes(25)));

        mockMvc.perform(get("/reset-password").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Guardar Nueva Contraseña")));

        mockMvc.perform(post("/reset-password")
                        .param("token", rawToken)
                        .param("password", "NuevaClaveSegura2026*")
                        .param("confirmPassword", "NuevaClaveSegura2026*")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset=true"));

        Usuario actualizado = usuarioRepository.findById(userEstudiante1.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NuevaClaveSegura2026*", actualizado.getPassword()));

        // Reutilizar el token debe fallar porque ya fue consumido
        assertFalse(passwordResetService.validarToken(rawToken));
    }

    @Test
    @WithMockUser(roles = "ESTUDIANTE")
    @DisplayName("Vistas /asistencias, /ambiental PRAE y /matricula Ley 1581 renderizan sin literales obsoletos")
    void testVistasCerradasRenderizanCorrectamente() throws Exception {
        mockMvc.perform(get("/ambiental"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Portal Ambiental Institucional (PRAE)")));

        mockMvc.perform(get("/matricula"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ley Estatutaria 1581 de 2012")))
                .andExpect(content().string(not(containsString("registro estudiantil 2024"))));
    }

    @Test
    @DisplayName("Gestión de Usuarios integrada en /configuracion?tab=cfg-usuarios y protegida por rol")
    void testGestionUsuariosDentroDeConfiguracion() throws Exception {
        mockMvc.perform(get("/configuracion").param("tab", "cfg-usuarios")
                        .with(user("admin@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gestión de Usuarios")))
                .andExpect(content().string(containsString("Total Cuentas")))
                .andExpect(content().string(containsString(userDocente.getEmail())));

        mockMvc.perform(get("/usuarios")
                        .with(user("admin@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/configuracion?tab=cfg-usuarios"));

        mockMvc.perform(post("/usuarios/" + userDocente.getId() + "/cambiar-rol")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE"))
                        .param("nuevoRol", "ADMIN")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sidebar sectorizado: ADMIN ve ambos sectores, ESTUDIANTE y DOCENTE no ven Gestión Administrativa")
    void testSidebarSectorizadoPorRol() throws Exception {
        mockMvc.perform(get("/")
                        .with(user("admin@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Académico y Estudiantil")))
                .andExpect(content().string(containsString("Gestión Administrativa")))
                .andExpect(content().string(containsString("Matrículas")))
                .andExpect(content().string(containsString("href=\"/estudiantes\"")));

        mockMvc.perform(get("/")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Académico y Estudiantil")))
                .andExpect(content().string(containsString("Mis Asistencias")))
                .andExpect(content().string(not(containsString("Gestión Administrativa"))))
                .andExpect(content().string(not(containsString("Mi Jornada"))))
                .andExpect(content().string(not(containsString("href=\"/estudiantes\""))));

        mockMvc.perform(get("/")
                        .with(user(userDocente.getEmail()).roles("DOCENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi Jornada")))
                .andExpect(content().string(containsString("Académico y Estudiantil")))
                .andExpect(content().string(not(containsString("Gestión Administrativa"))))
                .andExpect(content().string(not(containsString("href=\"/estudiantes\""))));

        mockMvc.perform(get("/estudiantes")
                        .with(user(userDocente.getEmail()).roles("DOCENTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estudiantes/perfil/" + est1.getId())
                        .with(user(userDocente.getEmail()).roles("DOCENTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estudiantes")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/asistencias")
                        .param("cursoId", "")
                        .param("estado", "PRESENTE")
                        .param("q", "")
                        .with(user("admin@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consolidado de Asistencia")))
                .andExpect(content().string(containsString("Todos los cursos")))
                .andExpect(content().string(containsString("Estudiante o documento")));

        mockMvc.perform(get("/asistencias")
                        .param("estado", "AUSENTE")
                        .with(user(userEstudiante1.getEmail()).roles("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi Historial de Asistencia")));
    }
}


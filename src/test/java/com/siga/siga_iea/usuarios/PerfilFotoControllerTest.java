package com.siga.siga_iea.usuarios;

import com.siga.siga_iea.storage.StorageFolder;
import com.siga.siga_iea.storage.StorageService;
import com.siga.siga_iea.storage.dto.UploadResult;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class PerfilFotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DocenteRepository docenteRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @MockitoBean
    private StorageService storageService;

    @Test
    @DisplayName("Perfiles (/perfil, /personal/perfil y /estudiantes/perfil) permiten subir y quitar foto de perfil")
    void testSubirYQuitarFotosEnPerfiles() throws Exception {
        Usuario admin = new Usuario("admin.foto@ieaci.edu.co", "hash", "ADMIN", "900111222");
        admin = usuarioRepository.save(admin);

        Docente docente = new Docente();
        docente.setNombres("Elena");
        docente.setApellidos("Rojas");
        docente.setNumeroDocumento("800111222");
        docente.setEstado("Activo");
        docente = docenteRepository.save(docente);
        Usuario docUsr = usuarioRepository.save(new Usuario("elena.rojas@ieaci.edu.co", "hash", "DOCENTE", "800111222"));

        Estudiante est = new Estudiante();
        est.setNombres("Mateo");
        est.setApellidos("Pérez");
        est.setCodigo("2026555");
        est.setNumeroDocumento("700111222");
        est.setEstado("Activo");
        est = estudianteRepository.save(est);
        Usuario estUsr = usuarioRepository.save(new Usuario("mateo.perez@ieaci.edu.co", "hash", "ESTUDIANTE", "700111222"));

        MockMultipartFile pngFile = new MockMultipartFile(
                "fotoFile", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        when(storageService.upload(any(), eq(StorageFolder.USUARIOS), anyString()))
                .thenReturn(new UploadResult("usuarios/" + admin.getId() + "/avatar.png", null));
        when(storageService.upload(any(), eq(StorageFolder.DOCENTES), anyString()))
                .thenReturn(new UploadResult("docentes/800111222/doc.png", null));
        when(storageService.upload(any(), eq(StorageFolder.ESTUDIANTES), anyString()))
                .thenReturn(new UploadResult("estudiantes/700111222/est.png", null));

        // 1. /perfil renderiza opción Agregar foto, sin duplicar rol/cargo, y sube foto propia
        mockMvc.perform(get("/perfil").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agregar foto")))
                .andExpect(content().string(containsString(">Administrador</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("text-text-secondary font-bold mt-1\">ADMIN</span>"))))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));

        mockMvc.perform(multipart("/perfil/foto")
                        .file(pngFile)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"));

        assertEquals("usuarios/" + admin.getId() + "/avatar.png",
                usuarioRepository.findById(admin.getId()).orElseThrow().getFotoKey());

        mockMvc.perform(get("/perfil").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cambiar foto")))
                .andExpect(content().string(containsString("/storage/public/view?key=usuarios/" + admin.getId() + "/avatar.png")));

        // 2. /personal/{id}/foto actualiza docente y sincroniza usuario
        mockMvc.perform(multipart("/personal/" + docente.getId() + "/foto")
                        .file(pngFile)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personal/perfil/" + docente.getId()));

        assertEquals("docentes/800111222/doc.png",
                docenteRepository.findById(docente.getId()).orElseThrow().getFotoKey());
        assertEquals("docentes/800111222/doc.png",
                usuarioRepository.findById(docUsr.getId()).orElseThrow().getFotoKey());

        // 3. /estudiantes/{id}/foto actualiza estudiante y sincroniza usuario, y estudiante no puede invocar POST admin
        mockMvc.perform(multipart("/estudiantes/" + est.getId() + "/foto")
                        .file(pngFile)
                        .with(user(estUsr.getEmail()).roles("ESTUDIANTE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/estudiantes/" + est.getId() + "/foto")
                        .file(pngFile)
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/estudiantes/perfil/" + est.getId()));

        assertEquals("estudiantes/700111222/est.png",
                estudianteRepository.findById(est.getId()).orElseThrow().getFotoKey());
        assertEquals("estudiantes/700111222/est.png",
                usuarioRepository.findById(estUsr.getId()).orElseThrow().getFotoKey());

        // 4. Quitar foto limpia tanto entidad como almacenamiento
        mockMvc.perform(post("/estudiantes/" + est.getId() + "/foto/eliminar")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/estudiantes/perfil/" + est.getId()));

        assertNull(estudianteRepository.findById(est.getId()).orElseThrow().getFotoKey());
        assertNull(usuarioRepository.findById(estUsr.getId()).orElseThrow().getFotoKey());
        verify(storageService).delete("estudiantes/700111222/est.png");
    }
}

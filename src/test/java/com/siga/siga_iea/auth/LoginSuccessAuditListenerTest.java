package com.siga.siga_iea.auth;

import com.siga.siga_iea.auth.security.LoginSuccessAuditListener;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginSuccessAuditListenerTest {

    @Test
    @DisplayName("Actualiza ultimoAcceso del usuario cuando se completa una autenticación exitosa")
    void testActualizaUltimoAccesoEnLoginExitoso() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        Usuario usuario = new Usuario("admin@ieaci.edu.co", "hash", "ADMIN", "0000000000");
        when(repo.findByEmail("admin@ieaci.edu.co")).thenReturn(Optional.of(usuario));

        LoginSuccessAuditListener listener = new LoginSuccessAuditListener(repo);
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(
                new UsernamePasswordAuthenticationToken("admin@ieaci.edu.co", "N/A", Collections.emptyList())
        );

        listener.onAuthenticationSuccess(event);

        assertNotNull(usuario.getUltimoAcceso());
        verify(repo, times(1)).save(usuario);
    }

    @Test
    @DisplayName("Ignora de forma segura si el usuario autenticado no existe en repositorio")
    void testIgnoraSiUsuarioNoExiste() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        when(repo.findByEmail(any())).thenReturn(Optional.empty());

        LoginSuccessAuditListener listener = new LoginSuccessAuditListener(repo);
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(
                new UsernamePasswordAuthenticationToken("fantasma@ieaci.edu.co", "N/A", Collections.emptyList())
        );

        listener.onAuthenticationSuccess(event);

        verify(repo, never()).save(any());
    }
}

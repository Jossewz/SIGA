package com.siga.siga_iea.auth.security;

import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Registra la marca de tiempo de último acceso en la tabla usuarios
 * cada vez que una autenticación se completa satisfactoriamente.
 */
@Component
public class LoginSuccessAuditListener {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessAuditListener.class);
    private static final ZoneId ZONA_INSTITUCIONAL = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;

    public LoginSuccessAuditListener(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return;
        }
        try {
            usuarioRepository.findByEmail(auth.getName().trim().toLowerCase())
                    .or(() -> usuarioRepository.findByEmail(auth.getName().trim()))
                    .ifPresent(usuario -> {
                        usuario.setUltimoAcceso(LocalDateTime.now(ZONA_INSTITUCIONAL));
                        usuarioRepository.save(usuario);
                    });
        } catch (Exception ex) {
            log.debug("No se pudo actualizar ultimoAcceso para {}: {}", auth.getName(), ex.getMessage());
        }
    }
}

package com.siga.siga_iea.usuarios.service;

import com.siga.siga_iea.auditoria.service.AuditoriaService;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                          @Autowired(required = false) AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> buscarPorId(UUID id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorNumeroDocumento(String doc) {
        if (doc == null || doc.isBlank()) return Optional.empty();
        return usuarioRepository.findByNumeroDocumento(doc.trim());
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return usuarioRepository.findByEmail(email.trim());
    }

    public boolean verificarPassword(Usuario usuario, String rawPassword) {
        if (usuario == null || usuario.getPassword() == null || rawPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, usuario.getPassword());
    }

    @Transactional
    public Usuario guardar(Usuario usuario) {
        boolean esNuevo = (usuario.getId() == null || !usuarioRepository.existsById(usuario.getId()));
        if (usuario.getPassword() != null && !usuario.getPassword().startsWith("$2a$") && !usuario.getPassword().startsWith("$2b$")) {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }
        Usuario guardado = usuarioRepository.save(usuario);
        if (auditoriaService != null && guardado != null) {
            auditoriaService.registrarCambio("USUARIO", guardado.getId(),
                    esNuevo ? "creacion" : "actualizacion",
                    null,
                    guardado.getEmail() + " (" + guardado.getRol() + ")");
        }
        return guardado;
    }

    @Transactional
    public void cambiarEstado(UUID id, String nuevoEstado) {
        usuarioRepository.findById(id).ifPresent(u -> {
            String anterior = u.getEstado();
            u.setEstado(nuevoEstado);
            usuarioRepository.save(u);
            if (auditoriaService != null) {
                auditoriaService.registrarCambio("USUARIO", u.getId(), "estado", anterior, nuevoEstado);
            }
        });
    }

    @Transactional
    public void cambiarPassword(UUID id, String nuevaPassword) {
        usuarioRepository.findById(id).ifPresent(u -> {
            u.setPassword(passwordEncoder.encode(nuevaPassword));
            usuarioRepository.save(u);
            if (auditoriaService != null) {
                auditoriaService.registrarCambio("USUARIO", u.getId(), "password", null, "CAMBIO_PASSWORD");
            }
        });
    }

    @Transactional
    public void cambiarRol(UUID id, String nuevoRol) {
        usuarioRepository.findById(id).ifPresent(u -> {
            String anterior = u.getRol();
            u.setRol(nuevoRol);
            usuarioRepository.save(u);
            if (auditoriaService != null) {
                auditoriaService.registrarCambio("USUARIO", u.getId(), "rol", anterior, nuevoRol);
            }
        });
    }

    @Transactional
    public void eliminar(UUID id) {
        usuarioRepository.findById(id).ifPresent(u -> {
            if (auditoriaService != null) {
                auditoriaService.registrarCambio("USUARIO", u.getId(), "eliminacion", u.getEmail(), "ELIMINADO");
            }
            usuarioRepository.delete(u);
        });
    }
}

package com.siga.siga_iea.auth.service;

import com.siga.siga_iea.auth.entity.PasswordResetToken;
import com.siga.siga_iea.auth.repository.PasswordResetTokenRepository;
import com.siga.siga_iea.notificaciones.service.CorreoInstitucionalService;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int EXPIRACION_MINUTOS = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoInstitucionalService correoService;

    public PasswordResetService(UsuarioRepository usuarioRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                CorreoInstitucionalService correoService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.correoService = correoService;
    }

    /**
     * Genera un token criptográfico de un solo uso y envía el enlace de restablecimiento
     * si existe una cuenta activa asociada al correo proporcionado.
     */
    @Transactional
    public void solicitarRestablecimiento(String email) {
        if (email == null || email.isBlank()) return;
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            userOpt = usuarioRepository.findByEmail(email.trim());
        }
        if (userOpt.isEmpty()) return;

        Usuario usuario = userOpt.get();
        if (!"Activo".equalsIgnoreCase(usuario.getEstado())) return;

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hash = sha256Hex(rawToken);

        LocalDateTime expira = LocalDateTime.now().plusMinutes(EXPIRACION_MINUTOS);
        tokenRepository.save(new PasswordResetToken(usuario, hash, expira));

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Cuenta", usuario.getEmail());
        detalles.put("Vigencia del enlace", EXPIRACION_MINUTOS + " minutos");

        correoService.enviarCorreoInstitucional(
                usuario.getEmail(),
                "Restablecimiento de Contraseña – SIGA IEACI",
                "Seguridad de la Cuenta",
                "Solicitud de Nueva Contraseña",
                usuario.getEmail(),
                "Recibimos una solicitud para restablecer la contraseña de tu cuenta en SIGA. Haz clic en el botón inferior para definir una nueva clave.",
                detalles,
                "Restablecer Contraseña",
                "/reset-password?token=" + rawToken
        );
    }

    @Transactional(readOnly = true)
    public boolean validarToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        return tokenRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .map(t -> t.estaVigente(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional
    public void restablecerPassword(String rawToken, String nuevaPassword, String confirmacion) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("El enlace de restablecimiento es inválido o ha expirado.");
        }
        if (nuevaPassword == null || nuevaPassword.length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        }
        if (!nuevaPassword.equals(confirmacion)) {
            throw new IllegalArgumentException("Las contraseñas ingresadas no coinciden.");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .orElseThrow(() -> new IllegalArgumentException("El enlace de restablecimiento es inválido."));

        LocalDateTime ahora = LocalDateTime.now();
        if (!token.estaVigente(ahora)) {
            throw new IllegalArgumentException("Este enlace ya fue utilizado o expiró. Solicita uno nuevo.");
        }

        Usuario usuario = token.getUsuario();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        token.setUsadoEn(ahora);
        tokenRepository.save(token);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}


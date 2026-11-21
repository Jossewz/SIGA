package com.siga.siga_iea.notificaciones.service;

import com.siga.siga_iea.notificaciones.entity.Notificacion;
import com.siga.siga_iea.notificaciones.repository.NotificacionRepository;
import com.siga.siga_iea.usuarios.entity.Acudiente;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.EstudianteAcudiente;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.EstudianteAcudienteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio central de notificaciones in-app (campana) y envío de correos institucionales.
 * Sigue la arquitectura de SEGI: persistencia en BD por usuario destinatario + reconstrucción dinámica de rutas.
 */
@Service
public class NotificacionService {

    private static final int MAX_LISTADO = 20;

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstudianteAcudienteRepository estudianteAcudienteRepository;
    private final CorreoInstitucionalService correoService;

    @Autowired
    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository,
                               EstudianteAcudienteRepository estudianteAcudienteRepository,
                               CorreoInstitucionalService correoService) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.estudianteAcudienteRepository = estudianteAcudienteRepository;
        this.correoService = correoService;
    }

    @Transactional
    public void notificarUsuario(Usuario usuario,
                                 String tipo,
                                 String titulo,
                                 String mensaje,
                                 String referenciaId,
                                 String icono,
                                 String color,
                                 boolean enviarCorreo,
                                 Map<String, String> detallesCorreo) {
        if (usuario == null || usuario.getId() == null) return;

        Notificacion n = new Notificacion(usuario, tipo, titulo, mensaje, referenciaId, icono, color);
        notificacionRepository.save(n);

        if (enviarCorreo && correoService != null && usuario.getEmail() != null) {
            correoService.enviarCorreoInstitucional(
                    usuario.getEmail(),
                    "[SIGA IEACI] " + titulo,
                    etiquetaPorTipo(tipo),
                    titulo,
                    usuario.getEmail(),
                    mensaje,
                    detallesCorreo,
                    "Abrir en SIGA",
                    resolverDestino(tipo, referenciaId)
            );
        }
    }

    @Transactional
    public void notificarAdministrativos(String tipo,
                                         String titulo,
                                         String mensaje,
                                         String referenciaId,
                                         String icono,
                                         String color,
                                         boolean enviarCorreo,
                                         Map<String, String> detallesCorreo) {
        List<Usuario> todos = usuarioRepository.findAll();
        Set<UUID> notificados = new HashSet<>();
        for (Usuario u : todos) {
            if (u == null || !"Activo".equalsIgnoreCase(u.getEstado())) continue;
            if (u.getRolEnum().esAdminOAdministrativo() && notificados.add(u.getId())) {
                notificarUsuario(u, tipo, titulo, mensaje, referenciaId, icono, color, enviarCorreo, detallesCorreo);
            }
        }
    }

    @Transactional
    public void notificarEstudianteYAcudiente(Estudiante est,
                                              String tipo,
                                              String titulo,
                                              String mensaje,
                                              String referenciaId,
                                              String icono,
                                              String color,
                                              boolean enviarCorreo,
                                              Map<String, String> detallesCorreo) {
        if (est == null) return;

        Set<String> correosEnviados = new HashSet<>();
        if (est.getNumeroDocumento() != null && !est.getNumeroDocumento().isBlank()) {
            usuarioRepository.findByNumeroDocumento(est.getNumeroDocumento()).ifPresent(u -> {
                notificarUsuario(u, tipo, titulo, mensaje, referenciaId, icono, color, false, null);
                if (enviarCorreo && u.getEmail() != null && correosEnviados.add(u.getEmail().toLowerCase(Locale.ROOT))) {
                    correoService.enviarCorreoInstitucional(
                            u.getEmail(),
                            "[SIGA IEACI] " + titulo,
                            etiquetaPorTipo(tipo),
                            titulo,
                            est.getNombreCompleto(),
                            mensaje,
                            detallesCorreo,
                            "Ver en SIGA",
                            resolverDestino(tipo, referenciaId)
                    );
                }
            });
        }

        if (enviarCorreo && correoService != null) {
            Acudiente acudiente = resolverAcudientePrincipal(est);
            if (acudiente != null && Boolean.TRUE.equals(acudiente.getAutorizaNotificaciones())) {
                enviarCorreoAcudiente(acudiente.getEmail(), acudiente, est, tipo, titulo, mensaje, referenciaId, detallesCorreo, correosEnviados);
                enviarCorreoAcudiente(acudiente.getEmailSecundario(), acudiente, est, tipo, titulo, mensaje, referenciaId, detallesCorreo, correosEnviados);
            }
        }
    }

    @Transactional
    public void notificarDocente(Docente docente,
                                 String tipo,
                                 String titulo,
                                 String mensaje,
                                 String referenciaId,
                                 String icono,
                                 String color,
                                 boolean enviarCorreo,
                                 Map<String, String> detallesCorreo) {
        if (docente == null || docente.getNumeroDocumento() == null || docente.getNumeroDocumento().isBlank()) return;
        usuarioRepository.findByNumeroDocumento(docente.getNumeroDocumento()).ifPresent(u ->
                notificarUsuario(u, tipo, titulo, mensaje, referenciaId, icono, color, enviarCorreo, detallesCorreo));
    }

    private void enviarCorreoAcudiente(String email,
                                       Acudiente acudiente,
                                       Estudiante est,
                                       String tipo,
                                       String titulo,
                                       String mensaje,
                                       String referenciaId,
                                       Map<String, String> detallesCorreo,
                                       Set<String> correosEnviados) {
        if (email == null || email.isBlank()) return;
        String clean = email.trim();
        if (!correosEnviados.add(clean.toLowerCase(Locale.ROOT))) return;
        correoService.enviarCorreoInstitucional(
                clean,
                "[SIGA IEACI] " + titulo + " – " + est.getNombreCompleto(),
                etiquetaPorTipo(tipo),
                titulo,
                acudiente.getNombreCompleto() + " (Acudiente de " + est.getNombreCompleto() + ")",
                mensaje,
                detallesCorreo,
                "Ingresar a SIGA",
                resolverDestino(tipo, referenciaId)
        );
    }

    private Acudiente resolverAcudientePrincipal(Estudiante est) {
        if (est.getId() != null && estudianteAcudienteRepository != null) {
            Optional<EstudianteAcudiente> principal = estudianteAcudienteRepository.findByEstudianteIdAndEsPrincipalTrue(est.getId());
            if (principal.isPresent() && principal.get().getAcudiente() != null) {
                return principal.get().getAcudiente();
            }
            List<EstudianteAcudiente> rels = estudianteAcudienteRepository.findByEstudianteId(est.getId());
            if (!rels.isEmpty() && rels.get(0).getAcudiente() != null) {
                return rels.get(0).getAcudiente();
            }
        }
        return est.getAcudiente();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarRecientesParaUsuario(UUID usuarioId) {
        if (usuarioId == null) return Collections.emptyList();
        List<Notificacion> lista = notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(
                usuarioId, PageRequest.of(0, MAX_LISTADO));
        List<Map<String, Object>> res = new ArrayList<>();
        for (Notificacion n : lista) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId().toString());
            m.put("tipo", n.getTipo());
            m.put("titulo", n.getTitulo());
            m.put("mensaje", n.getMensaje());
            m.put("icono", n.getIcono());
            m.put("color", n.getColor());
            m.put("leida", n.getLeidaEn() != null);
            m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            m.put("destinoUrl", resolverDestino(n.getTipo(), n.getReferenciaId()));
            res.add(m);
        }
        return res;
    }

    @Transactional(readOnly = true)
    public long contarSinLeer(UUID usuarioId) {
        if (usuarioId == null) return 0L;
        return notificacionRepository.countByUsuarioIdAndLeidaEnIsNull(usuarioId);
    }

    @Transactional
    public Optional<String> marcarComoLeida(UUID notificacionId, UUID usuarioId) {
        Optional<Notificacion> opt = notificacionRepository.findByIdAndUsuarioId(notificacionId, usuarioId);
        if (opt.isEmpty()) return Optional.empty();
        Notificacion n = opt.get();
        if (n.getLeidaEn() == null) {
            n.setLeidaEn(LocalDateTime.now());
            notificacionRepository.save(n);
        }
        return Optional.of(resolverDestino(n.getTipo(), n.getReferenciaId()));
    }

    @Transactional
    public int marcarTodasLeidas(UUID usuarioId) {
        if (usuarioId == null) return 0;
        return notificacionRepository.marcarTodasComoLeidas(usuarioId, LocalDateTime.now());
    }

    public String resolverDestino(String tipo, String referenciaId) {
        if (tipo == null) return "/";
        return switch (tipo) {
            case "CERTIFICADO_SOLICITADO", "CERTIFICADO_RESPONDIDO" -> "/certificados";
            case "ASISTENCIA_INASISTENCIA", "ASISTENCIA_TARDANZA" -> "/asistencias";
            case "REPORTE_CREADO", "REPORTE_CITACION", "REPORTE_RESUELTO" -> "/reportes";
            case "MATRICULA_PENDIENTE", "MATRICULA_APROBADA" -> "/matricula";
            default -> "/";
        };
    }

    private String etiquetaPorTipo(String tipo) {
        if (tipo == null) return "Aviso Institucional";
        return switch (tipo) {
            case "CERTIFICADO_SOLICITADO", "CERTIFICADO_RESPONDIDO" -> "Certificados y Constancias";
            case "ASISTENCIA_INASISTENCIA", "ASISTENCIA_TARDANZA" -> "Control de Asistencia";
            case "REPORTE_CREADO", "REPORTE_CITACION", "REPORTE_RESUELTO" -> "Reportes y Citaciones";
            default -> "Notificación SIGA";
        };
    }
}


package com.siga.siga_iea.notificaciones.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.notificaciones.service.NotificacionService;
import com.siga.siga_iea.usuarios.entity.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final CurrentUserContextService currentUserContextService;

    public NotificacionController(NotificacionService notificacionService,
                                  CurrentUserContextService currentUserContextService) {
        this.notificacionService = notificacionService;
        this.currentUserContextService = currentUserContextService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar() {
        Optional<Usuario> userOpt = currentUserContextService.getUsuarioAutenticado();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID usuarioId = userOpt.get().getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sinLeer", notificacionService.contarSinLeer(usuarioId));
        body.put("notificaciones", notificacionService.listarRecientesParaUsuario(usuarioId));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/leer")
    public ResponseEntity<Map<String, Object>> marcarLeida(@PathVariable("id") UUID id) {
        Optional<Usuario> userOpt = currentUserContextService.getUsuarioAutenticado();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<String> destino = notificacionService.marcarComoLeida(id, userOpt.get().getId());
        if (destino.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "error", "Notificación no disponible"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "destinoUrl", destino.get()));
    }

    @PostMapping("/leer-todas")
    public ResponseEntity<Map<String, Object>> marcarTodasLeidas() {
        Optional<Usuario> userOpt = currentUserContextService.getUsuarioAutenticado();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int actualizadas = notificacionService.marcarTodasLeidas(userOpt.get().getId());
        return ResponseEntity.ok(Map.of("ok", true, "actualizadas", actualizadas));
    }
}


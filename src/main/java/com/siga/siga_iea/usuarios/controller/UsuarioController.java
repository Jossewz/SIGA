package com.siga.siga_iea.usuarios.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.usuarios.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final CurrentUserContextService currentUserContextService;

    @Autowired
    public UsuarioController(UsuarioService usuarioService,
                             @Autowired(required = false) CurrentUserContextService currentUserContextService) {
        this.usuarioService = usuarioService;
        this.currentUserContextService = currentUserContextService;
    }

    public UsuarioController(UsuarioService usuarioService) {
        this(usuarioService, null);
    }

    private void exigirAdminOAdministrativo() {
        if (currentUserContextService != null
                && currentUserContextService.getAuthentication().isPresent()
                && !currentUserContextService.esAdminOAdministrativo()) {
            throw new AccessDeniedException("Solo Administración puede gestionar cuentas de usuarios.");
        }
    }

    @GetMapping
    public String index(@RequestParam(required = false) String search,
                        @RequestParam(required = false) String estado,
                        @RequestParam(required = false) String rol) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/configuracion")
                .queryParam("tab", "cfg-usuarios");
        if (search != null && !search.isBlank()) uri.queryParam("search", search);
        if (estado != null && !estado.isBlank()) uri.queryParam("estado", estado);
        if (rol != null && !rol.isBlank()) uri.queryParam("userRol", rol);
        return "redirect:" + uri.toUriString();
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id,
                                @RequestParam(required = false) String nuevaPassword,
                                RedirectAttributes redirectAttributes) {
        exigirAdminOAdministrativo();
        String pass = (nuevaPassword != null && !nuevaPassword.isBlank())
                ? nuevaPassword.trim()
                : "IEACI" + java.time.Year.now().getValue() + "*";

        usuarioService.cambiarPassword(id, pass);
        redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña restablecida exitosamente a: " + pass);
        redirectAttributes.addFlashAttribute("activeTab", "cfg-usuarios");
        return "redirect:/configuracion?tab=cfg-usuarios";
    }

    @PostMapping("/{id}/cambiar-estado")
    public String cambiarEstado(@PathVariable UUID id,
                                @RequestParam String nuevoEstado,
                                RedirectAttributes redirectAttributes) {
        exigirAdminOAdministrativo();
        usuarioService.cambiarEstado(id, nuevoEstado);
        redirectAttributes.addFlashAttribute("mensajeExito", "Estado del usuario actualizado a '" + nuevoEstado + "'");
        redirectAttributes.addFlashAttribute("activeTab", "cfg-usuarios");
        return "redirect:/configuracion?tab=cfg-usuarios";
    }

    @PostMapping("/{id}/cambiar-rol")
    public String cambiarRol(@PathVariable UUID id,
                             @RequestParam String nuevoRol,
                             RedirectAttributes redirectAttributes) {
        exigirAdminOAdministrativo();
        usuarioService.cambiarRol(id, nuevoRol);
        redirectAttributes.addFlashAttribute("mensajeExito", "Rol de seguridad actualizado a " + nuevoRol);
        redirectAttributes.addFlashAttribute("activeTab", "cfg-usuarios");
        return "redirect:/configuracion?tab=cfg-usuarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id,
                           RedirectAttributes redirectAttributes) {
        exigirAdminOAdministrativo();
        usuarioService.eliminar(id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Usuario eliminado permanentemente del sistema.");
        redirectAttributes.addFlashAttribute("activeTab", "cfg-usuarios");
        return "redirect:/configuracion?tab=cfg-usuarios";
    }
}

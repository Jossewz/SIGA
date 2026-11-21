package com.siga.siga_iea.auth.controller;

import com.siga.siga_iea.auth.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final PasswordResetService passwordResetService;

    @Autowired
    public AuthController(@Autowired(required = false) PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    public AuthController() {
        this(null);
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "forgotSent", required = false) String forgotSent,
            @RequestParam(value = "reset", required = false) String reset,
            Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Credenciales incorrectas o cuenta inactiva. Por favor intente de nuevo.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Ha cerrado sesión correctamente.");
        }
        if (forgotSent != null) {
            model.addAttribute("logoutMessage", "Si el correo pertenece a una cuenta activa, recibirás un enlace para restablecer tu contraseña (vigencia de 30 minutos).");
        }
        if (reset != null) {
            model.addAttribute("logoutMessage", "Tu contraseña ha sido actualizada correctamente. Ya puedes iniciar sesión.");
        }

        model.addAttribute("title", "Ingresar – SIGA IEACI");
        return "auth/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordRedirect() {
        return "redirect:/login";
    }

    @PostMapping("/forgot-password")
    public String solicitarRestablecimiento(@RequestParam("email") String email) {
        if (passwordResetService != null) {
            passwordResetService.solicitarRestablecimiento(email);
        }
        return "redirect:/login?forgotSent=true";
    }

    @GetMapping("/reset-password")
    public String vistaResetPassword(@RequestParam(value = "token", required = false) String token, Model model) {
        boolean valido = passwordResetService != null && passwordResetService.validarToken(token);
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", valido);
        model.addAttribute("title", "Restablecer Contraseña – SIGA IEACI");
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String procesarResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (passwordResetService == null) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValido", false);
            model.addAttribute("errorMessage", "El servicio de restablecimiento no está disponible.");
            return "auth/reset-password";
        }
        try {
            passwordResetService.restablecerPassword(token, password, confirmPassword);
            redirectAttributes.addFlashAttribute("logoutMessage", "Tu contraseña se actualizó correctamente.");
            return "redirect:/login?reset=true";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValido", passwordResetService.validarToken(token));
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/reset-password";
        }
    }
}


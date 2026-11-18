package com.siga.siga_iea.usuarios.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import com.siga.siga_iea.usuarios.service.PerfilFotoService;
import com.siga.siga_iea.usuarios.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final CurrentUserContextService currentUserContextService;
    private final UsuarioService usuarioService;
    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;
    private final EstudianteRepository estudianteRepository;
    private final PerfilFotoService perfilFotoService;

    @Autowired
    public PerfilController(CurrentUserContextService currentUserContextService,
                            UsuarioService usuarioService,
                            DocenteRepository docenteRepository,
                            PersonalAdministrativoRepository personalAdministrativoRepository,
                            EstudianteRepository estudianteRepository,
                            @Autowired(required = false) PerfilFotoService perfilFotoService) {
        this.currentUserContextService = currentUserContextService;
        this.usuarioService = usuarioService;
        this.docenteRepository = docenteRepository;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
        this.estudianteRepository = estudianteRepository;
        this.perfilFotoService = perfilFotoService;
    }

    public PerfilController(CurrentUserContextService currentUserContextService,
                            UsuarioService usuarioService,
                            DocenteRepository docenteRepository,
                            PersonalAdministrativoRepository personalAdministrativoRepository,
                            EstudianteRepository estudianteRepository) {
        this(currentUserContextService, usuarioService, docenteRepository, personalAdministrativoRepository, estudianteRepository, null);
    }

    @GetMapping
    public String verPerfil(Model model) {
        Optional<Usuario> usuarioOpt = currentUserContextService.getUsuarioAutenticado();
        if (usuarioOpt.isEmpty()) {
            return "redirect:/login";
        }

        Usuario usuario = usuarioOpt.get();
        PerfilPersonaInfo info = resolverDatosPersona(usuario);
        String fotoUrl = perfilFotoService != null
                ? perfilFotoService.construirUrl(perfilFotoService.resolverFotoKeyPorUsuario(usuario))
                : null;

        model.addAttribute("usuario", usuario);
        model.addAttribute("personaInfo", info);
        model.addAttribute("fotoUrl", fotoUrl);
        return "usuarios/perfil";
    }

    @PostMapping("/foto")
    public String actualizarFoto(@RequestParam("fotoFile") MultipartFile fotoFile,
                                 RedirectAttributes redirectAttributes) {
        Optional<Usuario> usuarioOpt = currentUserContextService.getUsuarioAutenticado();
        if (usuarioOpt.isEmpty()) {
            return "redirect:/login";
        }
        try {
            if (perfilFotoService == null) {
                throw new IllegalStateException("Servicio de almacenamiento de fotos no disponible.");
            }
            perfilFotoService.actualizarFotoUsuarioPropio(usuarioOpt.get(), fotoFile);
            redirectAttributes.addFlashAttribute("success", "¡Foto de perfil actualizada exitosamente!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo actualizar la foto: " + ex.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/foto/eliminar")
    public String eliminarFoto(RedirectAttributes redirectAttributes) {
        Optional<Usuario> usuarioOpt = currentUserContextService.getUsuarioAutenticado();
        if (usuarioOpt.isEmpty()) {
            return "redirect:/login";
        }
        try {
            if (perfilFotoService != null) {
                perfilFotoService.eliminarFotoUsuarioPropio(usuarioOpt.get());
            }
            redirectAttributes.addFlashAttribute("success", "Foto de perfil eliminada correctamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar la foto: " + ex.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/cambiar-password")
    public String cambiarPassword(@RequestParam String passwordActual,
                                  @RequestParam String passwordNueva,
                                  @RequestParam String passwordConfirmar,
                                  RedirectAttributes redirectAttributes) {
        Optional<Usuario> usuarioOpt = currentUserContextService.getUsuarioAutenticado();
        if (usuarioOpt.isEmpty()) {
            return "redirect:/login";
        }

        Usuario usuario = usuarioOpt.get();

        if (passwordActual == null || passwordActual.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Debe ingresar su contraseña actual.");
            return "redirect:/perfil";
        }

        if (passwordNueva == null || passwordNueva.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres.");
            return "redirect:/perfil";
        }

        if (!passwordNueva.equals(passwordConfirmar)) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña y su confirmación no coinciden.");
            return "redirect:/perfil";
        }

        if (!usuarioService.verificarPassword(usuario, passwordActual)) {
            redirectAttributes.addFlashAttribute("error", "La contraseña actual no es correcta.");
            return "redirect:/perfil";
        }

        usuarioService.cambiarPassword(usuario.getId(), passwordNueva.trim());
        redirectAttributes.addFlashAttribute("success", "¡Contraseña actualizada exitosamente!");
        return "redirect:/perfil";
    }

    private PerfilPersonaInfo resolverDatosPersona(Usuario usuario) {
        String doc = usuario.getNumeroDocumento();
        if (doc != null && !doc.isBlank()) {
            Optional<Docente> docOpt = docenteRepository.findByNumeroDocumento(doc);
            if (docOpt.isPresent()) {
                Docente d = docOpt.get();
                return new PerfilPersonaInfo(
                        d.getNombres(),
                        d.getApellidos(),
                        d.getTipoDocumento() != null ? d.getTipoDocumento() : "CC",
                        d.getNumeroDocumento(),
                        d.getTelefono() != null ? d.getTelefono() : "No registrado",
                        d.getDireccion() != null ? d.getDireccion() : "No registrada",
                        (d.getEspecialidad() != null && !d.getEspecialidad().isBlank()) ? d.getEspecialidad() : null,
                        usuario.getEmail(),
                        usuario.getRol()
                );
            }

            Optional<PersonalAdministrativo> paOpt = personalAdministrativoRepository.findByNumeroDocumento(doc);
            if (paOpt.isPresent()) {
                PersonalAdministrativo p = paOpt.get();
                return new PerfilPersonaInfo(
                        p.getNombres(),
                        p.getApellidos(),
                        p.getTipoDocumento() != null ? p.getTipoDocumento() : "CC",
                        p.getNumeroDocumento(),
                        p.getTelefono() != null ? p.getTelefono() : "No registrado",
                        p.getDireccion() != null ? p.getDireccion() : "No registrada",
                        p.getCargo() + (p.getArea() != null ? " (" + p.getArea() + ")" : ""),
                        usuario.getEmail(),
                        usuario.getRol()
                );
            }

            Optional<Estudiante> estOpt = estudianteRepository.findByNumeroDocumento(doc);
            if (estOpt.isPresent()) {
                Estudiante e = estOpt.get();
                return new PerfilPersonaInfo(
                        e.getNombres(),
                        e.getApellidos(),
                        e.getTipoDocumento() != null ? e.getTipoDocumento() : "TI",
                        e.getNumeroDocumento(),
                        e.getTelefono() != null ? e.getTelefono() : "No registrado",
                        e.getDireccion() != null ? e.getDireccion() : "No registrada",
                        "Estudiante (Código: " + e.getCodigo() + ")",
                        usuario.getEmail(),
                        usuario.getRol()
                );
            }
        }

        return new PerfilPersonaInfo(
                "Usuario",
                "Institucional",
                "CC",
                doc != null ? doc : "N/A",
                "PBX IEACI",
                "Cra 4 # 10-25, Cartagena del Chairá",
                null,
                usuario.getEmail(),
                usuario.getRol()
        );
    }

    public static class PerfilPersonaInfo {
        private final String nombres;
        private final String apellidos;
        private final String tipoDocumento;
        private final String numeroDocumento;
        private final String telefono;
        private final String direccion;
        private final String cargo;
        private final String email;
        private final String rol;

        public PerfilPersonaInfo(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                                 String telefono, String direccion, String cargo, String email, String rol) {
            this.nombres = nombres;
            this.apellidos = apellidos;
            this.tipoDocumento = tipoDocumento;
            this.numeroDocumento = numeroDocumento;
            this.telefono = telefono;
            this.direccion = direccion;
            this.cargo = cargo;
            this.email = email;
            this.rol = rol;
        }

        public String getNombres() { return nombres; }
        public String getApellidos() { return apellidos; }
        public String getNombreCompleto() { return nombres + " " + apellidos; }
        public String getTipoDocumento() { return tipoDocumento; }
        public String getNumeroDocumento() { return numeroDocumento; }
        public String getTelefono() { return telefono; }
        public String getDireccion() { return direccion; }
        public String getCargo() { return cargo; }
        public String getEmail() { return email; }
        public String getRol() { return rol; }
    }
}

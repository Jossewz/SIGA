package com.siga.siga_iea.usuarios.controller;

import com.siga.siga_iea.usuarios.dto.UsuarioDTO;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import com.siga.siga_iea.usuarios.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;
    private final EstudianteRepository estudianteRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    public UsuarioController(UsuarioService usuarioService,
                             DocenteRepository docenteRepository,
                             PersonalAdministrativoRepository personalAdministrativoRepository,
                             EstudianteRepository estudianteRepository) {
        this.usuarioService = usuarioService;
        this.docenteRepository = docenteRepository;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
        this.estudianteRepository = estudianteRepository;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String search,
                        @RequestParam(required = false) String estado,
                        @RequestParam(required = false) String rol,
                        Model model) {
        List<Usuario> todos = usuarioService.listarTodos();
        List<UsuarioDTO> dtoList = new ArrayList<>();

        long activosCount = 0;
        long bloqueadosCount = 0;

        for (Usuario u : todos) {
            String est = (u.getEstado() != null && !u.getEstado().isBlank()) ? u.getEstado() : "Activo";
            if ("Activo".equalsIgnoreCase(est)) {
                activosCount++;
            } else {
                bloqueadosCount++;
            }

            // Filtro por estado
            if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase(est)) {
                continue;
            }

            // Filtro por rol
            if (rol != null && !rol.isBlank() && !rol.equalsIgnoreCase(u.getRol())) {
                continue;
            }

            // Buscar nombre de la persona asociada
            String persona = resolverNombrePersona(u.getNumeroDocumento(), u.getRol());

            // Filtro por búsqueda (email, persona, documento)
            if (search != null && !search.isBlank()) {
                String s = search.toLowerCase().trim();
                boolean matchesEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(s);
                boolean matchesPersona = persona != null && persona.toLowerCase().contains(s);
                boolean matchesDoc = u.getNumeroDocumento() != null && u.getNumeroDocumento().toLowerCase().contains(s);
                if (!matchesEmail && !matchesPersona && !matchesDoc) {
                    continue;
                }
            }

            String ultimoAccesoStr = u.getUltimoAcceso() != null
                    ? u.getUltimoAcceso().format(FORMATTER)
                    : "Sin registros";

            dtoList.add(new UsuarioDTO(
                    u.getId(),
                    u.getEmail(),
                    persona,
                    u.getRol(),
                    ultimoAccesoStr,
                    est,
                    u.getNumeroDocumento()
            ));
        }

        model.addAttribute("usuariosList", dtoList);
        model.addAttribute("totalUsuarios", todos.size());
        model.addAttribute("activosCount", activosCount);
        model.addAttribute("bloqueadosCount", bloqueadosCount);
        model.addAttribute("search", search);
        model.addAttribute("selectedEstado", estado);
        model.addAttribute("selectedRol", rol);

        return "usuarios/index";
    }

    private String resolverNombrePersona(String doc, String rol) {
        if (doc == null || doc.isBlank()) {
            return "Cuenta de Sistema";
        }
        Optional<Docente> docOpt = docenteRepository.findByNumeroDocumento(doc);
        if (docOpt.isPresent()) {
            return docOpt.get().getNombres() + " " + docOpt.get().getApellidos();
        }

        Optional<PersonalAdministrativo> paOpt = personalAdministrativoRepository.findByNumeroDocumento(doc);
        if (paOpt.isPresent()) {
            return paOpt.get().getNombres() + " " + paOpt.get().getApellidos();
        }

        Optional<Estudiante> estOpt = estudianteRepository.findByNumeroDocumento(doc);
        if (estOpt.isPresent()) {
            return estOpt.get().getNombres() + " " + estOpt.get().getApellidos();
        }

        return "Funcionario (" + doc + ")";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id,
                                @RequestParam(required = false) String nuevaPassword,
                                RedirectAttributes redirectAttributes) {
        String pass = (nuevaPassword != null && !nuevaPassword.isBlank())
                ? nuevaPassword.trim()
                : "IEACI" + java.time.Year.now().getValue() + "*";

        usuarioService.cambiarPassword(id, pass);
        redirectAttributes.addFlashAttribute("success", "Contraseña restablecida exitosamente a: " + pass);
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/cambiar-estado")
    public String cambiarEstado(@PathVariable UUID id,
                                @RequestParam String nuevoEstado,
                                RedirectAttributes redirectAttributes) {
        usuarioService.cambiarEstado(id, nuevoEstado);
        redirectAttributes.addFlashAttribute("success", "Estado del usuario actualizado a '" + nuevoEstado + "'");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/cambiar-rol")
    public String cambiarRol(@PathVariable UUID id,
                             @RequestParam String nuevoRol,
                             RedirectAttributes redirectAttributes) {
        usuarioService.cambiarRol(id, nuevoRol);
        redirectAttributes.addFlashAttribute("success", "Rol de seguridad actualizado a " + nuevoRol);
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable UUID id,
                           RedirectAttributes redirectAttributes) {
        usuarioService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Usuario eliminado permanentemente del sistema.");
        return "redirect:/usuarios";
    }
}

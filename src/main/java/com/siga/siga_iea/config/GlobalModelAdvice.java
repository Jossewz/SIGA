package com.siga.siga_iea.config;

import com.siga.siga_iea.auth.enums.RolEnum;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.configuracion.entity.ConfiguracionInstitucional;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.configuracion.service.RolPermisoService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.PerfilFotoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Resuelve en una sola pasada por petición HTTP los atributos globales de vista
 * para evitar múltiples consultas redundantes a PostgreSQL entre fragmentos Thymeleaf.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUserContextService userContextService;
    private final RolPermisoService rolPermisoService;
    private final PeriodoConfigService periodoConfigService;
    private final PerfilFotoService perfilFotoService;

    @Autowired
    public GlobalModelAdvice(CurrentUserContextService userContextService,
                             RolPermisoService rolPermisoService,
                             @Autowired(required = false) PeriodoConfigService periodoConfigService,
                             @Autowired(required = false) PerfilFotoService perfilFotoService) {
        this.userContextService = userContextService;
        this.rolPermisoService = rolPermisoService;
        this.periodoConfigService = periodoConfigService;
        this.perfilFotoService = perfilFotoService;
    }

    public GlobalModelAdvice(CurrentUserContextService userContextService,
                             RolPermisoService rolPermisoService,
                             PeriodoConfigService periodoConfigService) {
        this(userContextService, rolPermisoService, periodoConfigService, null);
    }

    public GlobalModelAdvice(CurrentUserContextService userContextService,
                             RolPermisoService rolPermisoService) {
        this(userContextService, rolPermisoService, null, null);
    }

    @ModelAttribute
    public void populateGlobalAttributes(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request != null ? request.getRequestURI() : "");

        boolean isHxRequest = request != null && "true".equalsIgnoreCase(request.getHeader("HX-Request"));
        boolean authenticated = userContextService.getAuthentication().isPresent();

        Usuario currentUser = authenticated ? userContextService.getUsuarioAutenticado().orElse(null) : null;
        RolEnum rol = null;
        if (authenticated) {
            rol = currentUser != null ? currentUser.getRolEnum() : userContextService.getRolAutenticado();
        }

        String rolCode = rol != null ? rol.name() : "INVITADO";
        String roleName = rol != null ? rol.getEtiqueta() : "";
        boolean esAdmin = rol != null && rol.esAdmin();
        boolean esDocente = rol != null && rol.esDocente();
        boolean esAdministrativo = rol != null && rol.esPersonalAdministrativo();
        boolean esAdminOAdmin = rol != null && rol.esAdminOAdministrativo();
        boolean esEstudiante = rol != null && rol.esEstudiante() && !esAdminOAdmin && !esDocente;

        Set<String> modulosPermitidos = (authenticated && rol != null)
                ? rolPermisoService.obtenerModulosPermitidos(rolCode)
                : Collections.emptySet();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentRolCode", rolCode);
        model.addAttribute("currentRoleName", roleName);
        model.addAttribute("modulosPermitidos", modulosPermitidos);
        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("esDocente", esDocente);
        model.addAttribute("esAdministrativo", esAdministrativo);
        model.addAttribute("esEstudiante", esEstudiante);
        model.addAttribute("puedeAdministrarCursos", esAdminOAdmin);

        // Atributos de barra/cabecera que solo requieren resolverse en vistas completas (no en parciales HTMX)
        Docente docenteLogueado = null;
        String firstName = "";
        String photoUrl = null;

        if (authenticated && !isHxRequest) {
            if (esDocente) {
                Optional<Docente> docOpt = userContextService.getDocenteAutenticado();
                docenteLogueado = docOpt.orElse(null);
                if (docenteLogueado != null) {
                    firstName = extraerPrimerNombre(docenteLogueado.getNombres());
                }
            } else if (esEstudiante) {
                Optional<Estudiante> estOpt = userContextService.getEstudianteAutenticado();
                if (estOpt.isPresent()) {
                    firstName = extraerPrimerNombre(estOpt.get().getNombres());
                }
            }

            if (firstName.isBlank() && currentUser != null && currentUser.getEmail() != null) {
                String em = currentUser.getEmail();
                String local = em.contains("@") ? em.substring(0, em.indexOf('@')) : em;
                String base = local.contains(".") ? local.substring(0, local.indexOf('.')) : local;
                if (!base.isEmpty()) {
                    firstName = Character.toUpperCase(base.charAt(0)) + base.substring(1);
                }
            }

            if (perfilFotoService != null && currentUser != null) {
                String key = perfilFotoService.resolverFotoKeyPorUsuario(currentUser);
                photoUrl = key != null ? perfilFotoService.construirUrl(key) : null;
            }
        } else if (authenticated && esDocente) {
            docenteLogueado = userContextService.getDocenteAutenticado().orElse(null);
        }

        model.addAttribute("docenteLogueado", docenteLogueado);
        model.addAttribute("currentUserFirstName", firstName);
        model.addAttribute("currentUserPhotoUrl", photoUrl);

        String anioLectivoActivo = periodoConfigService != null
                ? periodoConfigService.obtenerAnioLectivoCodigo()
                : String.valueOf(PeriodoConfigService.anioCalendarioActual());
        ConfiguracionInstitucional configInst = (!isHxRequest && periodoConfigService != null)
                ? periodoConfigService.obtenerConfiguracionInstitucional()
                : null;

        model.addAttribute("anioLectivoActivo", anioLectivoActivo);
        model.addAttribute("configInstitucional", configInst);
    }

    private String extraerPrimerNombre(String nombres) {
        if (nombres == null || nombres.isBlank()) {
            return "";
        }
        return nombres.trim().split("\\s+")[0];
    }
}

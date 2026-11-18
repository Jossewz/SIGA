package com.siga.siga_iea.config;

import com.siga.siga_iea.auth.enums.RolEnum;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.configuracion.entity.ConfiguracionInstitucional;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.configuracion.service.RolPermisoService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.PerfilFotoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.Set;

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

    @ModelAttribute("currentUri")
    public String populateCurrentUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }

    @ModelAttribute("currentRolCode")
    public String populateRolCode() {
        if (userContextService.getAuthentication().isPresent()) {
            return userContextService.getRolAutenticado().name();
        }
        return "INVITADO";
    }

    @ModelAttribute("modulosPermitidos")
    public Set<String> populateModulosPermitidos() {
        if (userContextService.getAuthentication().isPresent()) {
            RolEnum rol = userContextService.getRolAutenticado();
            return rolPermisoService.obtenerModulosPermitidos(rol.name());
        }
        return Collections.emptySet();
    }

    @ModelAttribute("currentUser")
    public Usuario populateCurrentUser() {
        return userContextService.getUsuarioAutenticado().orElse(null);
    }

    @ModelAttribute("currentUserPhotoUrl")
    public String populateCurrentUserPhotoUrl() {
        if (perfilFotoService == null) {
            return null;
        }
        return userContextService.getUsuarioAutenticado()
                .map(perfilFotoService::resolverFotoKeyPorUsuario)
                .map(perfilFotoService::construirUrl)
                .orElse(null);
    }

    @ModelAttribute("currentRoleName")
    public String populateRoleName() {
        if (userContextService.getAuthentication().isPresent()) {
            return userContextService.getRolAutenticado().getEtiqueta();
        }
        return "";
    }

    @ModelAttribute("esAdmin")
    public boolean populateEsAdmin() {
        return userContextService.esAdmin();
    }

    @ModelAttribute("esDocente")
    public boolean populateEsDocente() {
        return userContextService.esDocente();
    }

    @ModelAttribute("esAdministrativo")
    public boolean populateEsAdministrativo() {
        return userContextService.esPersonalAdministrativo();
    }

    @ModelAttribute("esEstudiante")
    public boolean populateEsEstudiante() {
        return userContextService.esEstudiante()
                && !userContextService.esAdminOAdministrativo()
                && !userContextService.esDocente();
    }

    @ModelAttribute("puedeAdministrarCursos")
    public boolean populatePuedeAdministrarCursos() {
        return userContextService.esAdminOAdministrativo();
    }

    @ModelAttribute("docenteLogueado")
    public Docente populateDocenteLogueado() {
        return userContextService.getDocenteAutenticado().orElse(null);
    }

    @ModelAttribute("anioLectivoActivo")
    public String populateAnioLectivoActivo() {
        if (periodoConfigService != null) {
            return periodoConfigService.obtenerAnioLectivoCodigo();
        }
        return String.valueOf(PeriodoConfigService.anioCalendarioActual());
    }

    @ModelAttribute("configInstitucional")
    public ConfiguracionInstitucional populateConfigInstitucional() {
        if (periodoConfigService != null) {
            return periodoConfigService.obtenerConfiguracionInstitucional();
        }
        return null;
    }
}

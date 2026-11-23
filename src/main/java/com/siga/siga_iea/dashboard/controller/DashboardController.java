package com.siga.siga_iea.dashboard.controller;

import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.dashboard.dto.DashboardStatsDTO;
import com.siga.siga_iea.dashboard.service.DashboardService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final String powerBiUrl;
    private final CurrentUserContextService currentUserContextService;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;

    @Autowired
    public DashboardController(DashboardService dashboardService,
                               @Value("${siga.bi.powerbi-url:}") String powerBiUrl,
                               @Autowired(required = false) CurrentUserContextService currentUserContextService,
                               @Autowired(required = false) PersonalAdministrativoRepository personalAdministrativoRepository) {
        this.dashboardService = dashboardService;
        this.powerBiUrl = powerBiUrl;
        this.currentUserContextService = currentUserContextService;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
    }

    public DashboardController(DashboardService dashboardService,
                               @Value("${siga.bi.powerbi-url:}") String powerBiUrl) {
        this(dashboardService, powerBiUrl, null, null);
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("title", "Inicio – IEACI");
        model.addAttribute("activePage", "dashboard");

        boolean esDocente = currentUserContextService != null
                && currentUserContextService.esDocente()
                && !currentUserContextService.esAdminOAdministrativo();
        boolean esSoloEstudiante = currentUserContextService != null
                && currentUserContextService.esEstudiante()
                && !currentUserContextService.esAdminOAdministrativo()
                && !currentUserContextService.esDocente();

        if (esDocente) {
            Optional<Docente> docOpt = currentUserContextService.getDocenteAutenticado();
            String nombre = docOpt.map(Docente::getNombreCompleto).orElseGet(this::resolverNombreFallback);
            model.addAttribute("vistaRolDashboard", "DOCENTE");
            model.addAttribute("nombreBienvenida", nombre);
            model.addAttribute("subtituloBienvenida", "Resumen de tus clases de hoy, cursos asignados y actividad académica.");
            model.addAttribute("resumenDocente", dashboardService.obtenerResumenDocente(docOpt.orElse(null)));
            return "dashboard/index";
        }

        if (esSoloEstudiante) {
            Optional<Estudiante> estOpt = currentUserContextService.getEstudianteAutenticado();
            String nombre = estOpt.map(Estudiante::getNombreCompleto).orElseGet(this::resolverNombreFallback);
            model.addAttribute("vistaRolDashboard", "ESTUDIANTE");
            model.addAttribute("nombreBienvenida", nombre);
            model.addAttribute("subtituloBienvenida", "Tu espacio escolar: aquí puedes ver tus materias, notas, asistencia y horario de clases.");
            model.addAttribute("resumenEstudiante", dashboardService.obtenerResumenEstudiante(estOpt.orElse(null)));
            return "dashboard/index";
        }

        DashboardStatsDTO stats = dashboardService.obtenerEstadisticas();
        String nombreAdmin = resolverNombreAdministrativo();
        model.addAttribute("vistaRolDashboard", "ADMIN");
        model.addAttribute("nombreBienvenida", nombreAdmin);
        model.addAttribute("subtituloBienvenida", "Resumen analítico de la gestión institucional de hoy.");
        model.addAttribute("stats", stats);

        return "dashboard/index";
    }

    private String resolverNombreAdministrativo() {
        if (currentUserContextService != null && personalAdministrativoRepository != null) {
            Optional<String> doc = currentUserContextService.getUsuarioAutenticado()
                    .map(Usuario::getNumeroDocumento)
                    .filter(d -> d != null && !d.isBlank());
            if (doc.isPresent()) {
                Optional<PersonalAdministrativo> pa = personalAdministrativoRepository.findByNumeroDocumento(doc.get());
                if (pa.isPresent()) {
                    return pa.get().getNombreCompleto();
                }
            }
        }
        if (currentUserContextService != null && currentUserContextService.esAdmin()) {
            return "Administrador";
        }
        return resolverNombreFallback();
    }

    private String resolverNombreFallback() {
        if (currentUserContextService == null) return "Administrador";
        return currentUserContextService.getUsuarioAutenticado()
                .map(Usuario::getEmail)
                .filter(e -> e != null && e.contains("@"))
                .map(e -> e.substring(0, e.indexOf('@')))
                .orElse("Usuario");
    }

    @GetMapping("/analitica")
    @PreAuthorize("hasRole('ADMIN')")
    public String analiticaPowerBi(Model model) {
        model.addAttribute("title", "Analítica BI – IEACI");
        model.addAttribute("activePage", "analitica");
        model.addAttribute("powerBiUrl", powerBiUrl != null ? powerBiUrl.trim() : "");

        return "dashboard/analitica";
    }
}

package com.siga.siga_iea.auditoria.controller;

import com.siga.siga_iea.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class ActividadController {

    private final DashboardService dashboardService;

    public ActividadController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/actividad")
    @PreAuthorize("hasAnyRole('ADMIN', 'PERSONAL_ADMINISTRATIVO')")
    public String verTodosLosLogs(
            @RequestParam(value = "categoria", required = false, defaultValue = "TODAS") String categoria,
            @RequestParam(value = "q", required = false, defaultValue = "") String busqueda,
            Model model) {

        List<Map<String, Object>> todosLosLogs = dashboardService.obtenerTodosLosLogsActividad("TODAS", "");
        List<Map<String, Object>> logsFiltrados = dashboardService.obtenerTodosLosLogsActividad(categoria, busqueda);

        long conteoAuditoria = todosLosLogs.stream().filter(l -> "AUDITORIA".equals(l.get("categoria"))).count();
        long conteoMatriculas = todosLosLogs.stream().filter(l -> "MATRICULA".equals(l.get("categoria"))).count();
        long conteoReportes = todosLosLogs.stream().filter(l -> "REPORTE".equals(l.get("categoria"))).count();
        long conteoCertificados = todosLosLogs.stream().filter(l -> "CERTIFICADO".equals(l.get("categoria"))).count();

        model.addAttribute("title", "Actividad y Logs – IEACI");
        model.addAttribute("activePage", "actividad");
        model.addAttribute("logs", logsFiltrados);
        model.addAttribute("totalLogs", todosLosLogs.size());
        model.addAttribute("conteoAuditoria", conteoAuditoria);
        model.addAttribute("conteoMatriculas", conteoMatriculas);
        model.addAttribute("conteoReportes", conteoReportes);
        model.addAttribute("conteoCertificados", conteoCertificados);
        model.addAttribute("categoriaActiva", categoria != null && !categoria.isBlank() ? categoria.toUpperCase() : "TODAS");
        model.addAttribute("busqueda", busqueda != null ? busqueda.trim() : "");

        return "actividad/index";
    }
}

package com.siga.siga_iea.dashboard.controller;

import com.siga.siga_iea.dashboard.dto.DashboardStatsDTO;
import com.siga.siga_iea.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final String powerBiUrl;

    public DashboardController(DashboardService dashboardService,
                               @Value("${siga.bi.powerbi-url:}") String powerBiUrl) {
        this.dashboardService = dashboardService;
        this.powerBiUrl = powerBiUrl;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        DashboardStatsDTO stats = dashboardService.obtenerEstadisticas();

        model.addAttribute("title", "Dashboard – IEACI");
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("stats", stats);

        return "dashboard/index";
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

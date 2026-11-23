package com.siga.siga_iea.auditoria.controller;

import com.siga.siga_iea.dashboard.service.DashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            Model model) {

        List<Map<String, Object>> todosLosLogs = dashboardService.obtenerTodosLosLogsActividad("TODAS", "");
        String categoriaActiva = categoria != null && !categoria.isBlank() ? categoria.trim().toUpperCase(Locale.ROOT) : "TODAS";
        String busquedaLimpia = busqueda != null ? busqueda.trim() : "";
        String qLower = busquedaLimpia.toLowerCase(Locale.ROOT);

        List<Map<String, Object>> logsFiltrados = todosLosLogs.stream()
                .filter(item -> "TODAS".equals(categoriaActiva) || categoriaActiva.equals(item.get("categoria")))
                .filter(item -> {
                    if (qLower.isEmpty()) return true;
                    String t = String.valueOf(item.getOrDefault("titulo", "")).toLowerCase(Locale.ROOT);
                    String d = String.valueOf(item.getOrDefault("detalle", "")).toLowerCase(Locale.ROOT);
                    String u = String.valueOf(item.getOrDefault("usuario", "")).toLowerCase(Locale.ROOT);
                    return t.contains(qLower) || d.contains(qLower) || u.contains(qLower);
                })
                .toList();

        int safeSize = Math.max(1, Math.min(size, 100));
        int totalFiltrados = logsFiltrados.size();
        int totalPages = totalFiltrados == 0 ? 1 : (int) Math.ceil((double) totalFiltrados / safeSize);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIdx = safePage * safeSize;
        int toIdx = Math.min(fromIdx + safeSize, totalFiltrados);
        List<Map<String, Object>> paginaLogs = fromIdx < totalFiltrados
                ? logsFiltrados.subList(fromIdx, toIdx)
                : Collections.emptyList();
        Page<Map<String, Object>> logsPage = new PageImpl<>(paginaLogs, PageRequest.of(safePage, safeSize), totalFiltrados);

        long conteoAuditoria = todosLosLogs.stream().filter(l -> "AUDITORIA".equals(l.get("categoria"))).count();
        long conteoMatriculas = todosLosLogs.stream().filter(l -> "MATRICULA".equals(l.get("categoria"))).count();
        long conteoReportes = todosLosLogs.stream().filter(l -> "REPORTE".equals(l.get("categoria"))).count();
        long conteoCertificados = todosLosLogs.stream().filter(l -> "CERTIFICADO".equals(l.get("categoria"))).count();

        model.addAttribute("title", "Actividad y Logs – IEACI");
        model.addAttribute("activePage", "actividad");
        model.addAttribute("logs", paginaLogs);
        model.addAttribute("logsPage", logsPage);
        model.addAttribute("totalLogs", todosLosLogs.size());
        model.addAttribute("conteoAuditoria", conteoAuditoria);
        model.addAttribute("conteoMatriculas", conteoMatriculas);
        model.addAttribute("conteoReportes", conteoReportes);
        model.addAttribute("conteoCertificados", conteoCertificados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("busqueda", busquedaLimpia);

        return "actividad/index";
    }
}

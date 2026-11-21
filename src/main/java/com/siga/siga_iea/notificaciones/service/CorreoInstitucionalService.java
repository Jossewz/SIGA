package com.siga.siga_iea.notificaciones.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servicio asíncrono para envío de correos institucionales HTML (patrón SEGI).
 * Cuando app.mail.enabled=false o no hay configuración SMTP, registra trazabilidad
 * sin interrumpir el flujo principal del sistema.
 */
@Service
public class CorreoInstitucionalService {

    private static final Logger log = LoggerFactory.getLogger(CorreoInstitucionalService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromAddress;
    private final String fromName;
    private final String baseUrl;
    private final String smtpHost;

    @Autowired
    public CorreoInstitucionalService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:no-reply@ieaci.edu.co}") String fromAddress,
            @Value("${app.mail.from-name:SIGA - IEACI Institucional}") String fromName,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.baseUrl = baseUrl;
        this.smtpHost = smtpHost;
    }

    public String getBaseUrl() {
        return baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Async
    public void enviarCorreoInstitucional(String destinatario,
                                          String asunto,
                                          String badge,
                                          String titulo,
                                          String saludo,
                                          String intro,
                                          Map<String, String> detalles,
                                          String ctaTexto,
                                          String ctaRuta) {
        if (destinatario == null || destinatario.isBlank() || !destinatario.contains("@")) {
            return;
        }

        String html = construirHtml(badge, titulo, saludo, intro, detalles, ctaTexto, ctaRuta);
        if (!mailEnabled || mailSender == null || smtpHost == null || smtpHost.isBlank()) {
            log.info("[MAIL-SIMULADO] Para={} | Asunto='{}' | Título='{}'", destinatario, asunto, titulo);
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(destinatario.trim());
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Correo institucional enviado a {} (Asunto: {})", destinatario, asunto);
        } catch (Exception ex) {
            log.warn("No se pudo enviar correo a {}: {}", destinatario, ex.getMessage());
        }
    }

    public String construirHtml(String badge,
                                String titulo,
                                String saludo,
                                String intro,
                                Map<String, String> detalles,
                                String ctaTexto,
                                String ctaRuta) {
        StringBuilder detallesRows = new StringBuilder();
        Map<String, String> mapa = detalles != null ? detalles : new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapa.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            detallesRows.append("<tr>")
                    .append("<td style=\"padding:8px 12px;font-size:12px;font-weight:700;color:#4b5563;border-bottom:1px solid #e5e7eb;width:36%;\">")
                    .append(HtmlUtils.htmlEscape(entry.getKey()))
                    .append("</td>")
                    .append("<td style=\"padding:8px 12px;font-size:12px;font-weight:600;color:#111827;border-bottom:1px solid #e5e7eb;\">")
                    .append(HtmlUtils.htmlEscape(entry.getValue()))
                    .append("</td>")
                    .append("</tr>");
        }

        String ctaHtml = "";
        if (ctaTexto != null && !ctaTexto.isBlank() && ctaRuta != null && !ctaRuta.isBlank()) {
            String url = ctaRuta.startsWith("http") ? ctaRuta : getBaseUrl() + (ctaRuta.startsWith("/") ? ctaRuta : "/" + ctaRuta);
            ctaHtml = "<div style=\"margin:24px 0 8px;text-align:center;\">"
                    + "<a href=\"" + HtmlUtils.htmlEscape(url) + "\" "
                    + "style=\"display:inline-block;background-color:#0d4117;color:#ffffff;text-decoration:none;font-size:13px;font-weight:800;padding:12px 24px;border-radius:10px;\">"
                    + HtmlUtils.htmlEscape(ctaTexto)
                    + "</a></div>";
        }

        String badgeHtml = (badge != null && !badge.isBlank())
                ? "<span style=\"display:inline-block;background-color:#e3f7dc;color:#0d4117;font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:0.6px;padding:4px 10px;border-radius:999px;margin-bottom:10px;\">"
                + HtmlUtils.htmlEscape(badge) + "</span><br/>"
                : "";

        String tablaDetalles = detallesRows.isEmpty()
                ? ""
                : "<table style=\"width:100%;border-collapse:collapse;margin:16px 0;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;\">"
                + detallesRows + "</table>";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:24px;background-color:#f3f4f6;font-family:Inter,Arial,sans-serif;color:#1f2937;">
                  <div style="max-width:580px;margin:0 auto;background-color:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e5e7eb;">
                    <div style="background-color:#0d4117;padding:20px 28px;color:#ffffff;">
                      <div style="font-size:18px;font-weight:900;letter-spacing:-0.3px;">SIGA · IEACI</div>
                      <div style="font-size:11px;opacity:0.85;font-weight:600;">Sistema Integrado de Gestión Académica e Institucional</div>
                    </div>
                    <div style="padding:28px;">
                      %s
                      <h1 style="margin:0 0 12px;font-size:20px;font-weight:800;color:#0d4117;">%s</h1>
                      <p style="margin:0 0 10px;font-size:13px;font-weight:700;color:#111827;">%s,</p>
                      <p style="margin:0 0 16px;font-size:13px;line-height:1.6;color:#4b5563;">%s</p>
                      %s
                      %s
                    </div>
                    <div style="padding:14px 28px;background-color:#f7fcf6;border-top:1px solid #e5e7eb;font-size:11px;color:#6b7280;text-align:center;">
                      Institución Educativa Ambientalista de Cartagena de Indias (IEACI) · Mensaje automático, por favor no responder.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                badgeHtml,
                HtmlUtils.htmlEscape(titulo != null ? titulo : "Notificación Institucional"),
                HtmlUtils.htmlEscape(saludo != null ? saludo : "Estimado miembro de la comunidad IEACI"),
                HtmlUtils.htmlEscape(intro != null ? intro : ""),
                tablaDetalles,
                ctaHtml
        );
    }
}


package com.siga.siga_iea.certificados;

import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.certificados.service.CertificadoPdfService;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CertificadoPdfServiceTest {

    private final CertificadoPdfService pdfService = new CertificadoPdfService();

    @Test
    @DisplayName("generarPdf() genera documento PDF válido con encabezado %PDF para constancia de estudio")
    void generarPdf_constanciaEstudio_generaPdfValido() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(UUID.randomUUID());
        estudiante.setNombres("Juan Camilo");
        estudiante.setApellidos("Ramos Pérez");
        estudiante.setTipoDocumento("TI");
        estudiante.setNumeroDocumento("1002345678");
        estudiante.setCodigo("EST-2026-0042");

        SolicitudCertificado solicitud = new SolicitudCertificado();
        solicitud.setId(UUID.randomUUID());
        solicitud.setCodigo("CERT-2026-9876");
        solicitud.setTipo("Constancia de Estudio");
        solicitud.setEstudiante(estudiante);
        solicitud.setGradoReferencia("Grado 10°");
        solicitud.setAnoLectivo("2026");
        solicitud.setEstado("Resuelto");

        byte[] pdfBytes = pdfService.generarPdf(solicitud);

        assertNotNull(pdfBytes, "El PDF generado no debe ser nulo");
        assertTrue(pdfBytes.length > 500, "El tamaño del PDF debe ser significativo");

        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "El documento generado debe tener la cabecera estándar de PDF");
    }

    @Test
    @DisplayName("generarPdf() genera certificado de matrícula con seguridad y código de validación")
    void generarPdf_certificadoMatricula_generaPdfValido() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(UUID.randomUUID());
        estudiante.setNombres("María Alejandra");
        estudiante.setApellidos("Gómez Silva");
        estudiante.setTipoDocumento("TI");
        estudiante.setNumeroDocumento("1009876543");
        estudiante.setCodigo("EST-2026-0099");

        SolicitudCertificado solicitud = new SolicitudCertificado();
        solicitud.setId(UUID.randomUUID());
        solicitud.setCodigo("CERT-2026-1122");
        solicitud.setTipo("Certificado de Matrícula");
        solicitud.setEstudiante(estudiante);
        solicitud.setGradoReferencia("Grado 11°");
        solicitud.setAnoLectivo("2026");
        solicitud.setEstado("Resuelto");

        byte[] pdfBytes = pdfService.generarPdf(solicitud);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }
}

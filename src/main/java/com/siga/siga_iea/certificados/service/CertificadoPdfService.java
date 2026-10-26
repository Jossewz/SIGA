package com.siga.siga_iea.certificados.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Servicio generador de Certificados y Constancias Oficiales en formato PDF (OpenPDF).
 * Cumple con la normativa de la Ley 115 de 1994, Decreto 1075 de 2015 y estándares oficiales de la IEACI.
 */
@Service
public class CertificadoPdfService {

    // Paleta institucional SIGA
    private static final Color COLOR_PRIMARIO = new Color(13, 65, 23);       // #0d4117 verde bosque
    private static final Color COLOR_SECUNDARIO = new Color(234, 251, 228);   // #eafbe4 verde claro
    private static final Color COLOR_BORDE = new Color(196, 238, 192);        // #c4eec0 verde menta
    private static final Color COLOR_TEXTO = new Color(30, 41, 59);           // slate-800
    private static final Color COLOR_GRIS = new Color(100, 116, 139);        // slate-500

    public byte[] generarPdf(SolicitudCertificado solicitud) {
        return generarCertificadoPdf(solicitud);
    }

    public byte[] generarCertificadoPdf(SolicitudCertificado solicitud) {
        Document document = new Document(PageSize.A4, 40, 40, 45, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Membrete Institucional Oficial
            agregarMembrete(document);

            // 2. Título Oficial del Certificado
            agregarTitulo(document, solicitud);

            // 3. Cuerpo Oficial de la Constancia / Certificado
            agregarCuerpo(document, solicitud);

            // 4. Firmas Institucionales (Rectoría y Secretaría)
            agregarFirmas(document);

            // 5. Bloque de Seguridad y Validación Única
            agregarPieSeguridad(document, solicitud);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF del certificado oficial: " + e.getMessage(), e);
        }
    }

    private void agregarMembrete(Document doc) throws DocumentException {
        Font fontPais = FontFactory.getFont(FontFactory.HELVETICA, 8f, COLOR_GRIS);
        Font fontSecretaria = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_TEXTO);
        Font fontColegio = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, COLOR_PRIMARIO);
        Font fontDatos = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_GRIS);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(13);

        p.add(new Chunk("REPÚBLICA DE COLOMBIA\n", fontPais));
        p.add(new Chunk("SECRETARÍA DE EDUCACIÓN DISTRITAL DE CARTAGENA\n", fontSecretaria));
        p.add(new Chunk("INSTITUCIÓN EDUCATIVA AMBIENTAL DE CARTAGENA - IEACI\n", fontColegio));
        p.add(new Chunk("Reconocimiento Oficial según Resolución No. 0845 de la SED Cartagena\n", fontDatos));
        p.add(new Chunk("Código DANE: 113001002345 | NIT: 806.012.345-1 | Régimen Oficial Mixto\n\n", fontDatos));

        doc.add(p);

        // Línea decorativa verde bosque
        PdfPTable separador = new PdfPTable(1);
        separador.setWidthPercentage(100);
        separador.setSpacingAfter(18);
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(COLOR_PRIMARIO);
        c.setBorderWidthBottom(2.0f);
        c.setFixedHeight(2f);
        separador.addCell(c);
        doc.add(separador);
    }

    private void agregarTitulo(Document doc, SolicitudCertificado solicitud) throws DocumentException {
        String tipo = (solicitud.getTipo() != null && !solicitud.getTipo().isBlank())
                ? solicitud.getTipo().toUpperCase(Locale.ROOT)
                : "CONSTANCIA DE ESTUDIOS";

        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, COLOR_PRIMARIO);
        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, COLOR_TEXTO);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(20);
        p.setLeading(16);

        p.add(new Chunk("EL SUSCRITO RECTOR Y LA SECRETARÍA ACADÉMICA DE LA\n", fontSub));
        p.add(new Chunk("INSTITUCIÓN EDUCATIVA AMBIENTAL DE CARTAGENA (IEACI)\n\n", fontSub));
        p.add(new Chunk(tipo + "\n\n", fontTitulo));
        p.add(new Chunk("CERTIFICAN:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, COLOR_PRIMARIO)));

        doc.add(p);
    }

    private void agregarCuerpo(Document doc, SolicitudCertificado sol) throws DocumentException {
        Estudiante est = sol.getEstudiante();
        String nombreEstudiante = est != null ? est.getNombreCompleto().toUpperCase(Locale.ROOT) : "ESTUDIANTE REGISTRADO";
        String tipoDoc = (est != null && est.getTipoDocumento() != null && !est.getTipoDocumento().isBlank())
                ? est.getTipoDocumento().toUpperCase(Locale.ROOT) : "T.I.";
        String numDoc = est != null && est.getNumeroDocumento() != null ? est.getNumeroDocumento() : "S/D";
        String grado = sol.getGradoReferencia() != null && !sol.getGradoReferencia().isBlank() ? sol.getGradoReferencia() : "11°";
        String anio = sol.getAnoLectivo() != null && !sol.getAnoLectivo().isBlank() ? sol.getAnoLectivo() : "2026";
        String motivo = sol.getMotivo() != null && !sol.getMotivo().isBlank() ? sol.getMotivo().trim() : null;

        Font fontTexto = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, COLOR_TEXTO);
        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, COLOR_TEXTO);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(20);
        p.setSpacingAfter(18);

        p.add(new Chunk("Que el(la) estudiante ", fontTexto));
        p.add(new Chunk(nombreEstudiante, fontNegrita));
        p.add(new Chunk(", identificado(a) con documento de identidad tipo ", fontTexto));
        p.add(new Chunk(tipoDoc + " No. " + numDoc, fontNegrita));
        p.add(new Chunk(", se encuentra legalmente MATRICULADO(A) y cursando de manera activa el grado escolar ", fontTexto));
        p.add(new Chunk(grado, fontNegrita));
        p.add(new Chunk(" en la jornada diurna ordinaria, durante el año lectivo escolar ", fontTexto));
        p.add(new Chunk(anio, fontNegrita));
        p.add(new Chunk(" de esta Institución Educativa oficial de carácter ambiental.\n\n", fontTexto));

        p.add(new Chunk("Se hace constar igualmente que el(la) mencionado(a) estudiante observa una intachable conducta y estricto cumplimiento del Manual de Convivencia Escolar y de las disposiciones pedagógicas y ambientales institucionales.", fontTexto));

        if (motivo != null) {
            p.add(new Chunk("\n\nEl presente documento se expide a solicitud de la parte interesada con destino a: ", fontTexto));
            p.add(new Chunk(motivo.toUpperCase(Locale.ROOT) + ".", fontNegrita));
        }

        doc.add(p);

        // Fecha de expedición formal en letras
        LocalDate hoy = LocalDate.now();
        int dia = hoy.getDayOfMonth();
        String mes = hoy.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
        int anioActual = hoy.getYear();

        Paragraph pFecha = new Paragraph();
        pFecha.setAlignment(Element.ALIGN_JUSTIFIED);
        pFecha.setLeading(18);
        pFecha.setSpacingAfter(40);
        pFecha.add(new Chunk(String.format("Para mayor constancia y fines pertinentes, se firma la presente certificación en la ciudad de Cartagena de Indias D.T. y C., a los %d días del mes de %s de %d.",
                dia, mes, anioActual), fontTexto));

        doc.add(pFecha);
    }

    private void agregarFirmas(Document doc) throws DocumentException {
        PdfPTable tablaFirmas = new PdfPTable(2);
        tablaFirmas.setWidthPercentage(95);
        tablaFirmas.setWidths(new float[]{50, 50});
        tablaFirmas.setSpacingAfter(25);

        Font fontCargo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, COLOR_PRIMARIO);
        Font fontNombre = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, COLOR_TEXTO);
        Font fontInst = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_GRIS);

        // Firma Rectoría
        PdfPCell cRector = new PdfPCell();
        cRector.setBorder(Rectangle.NO_BORDER);
        cRector.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph pR = new Paragraph();
        pR.setAlignment(Element.ALIGN_CENTER);
        pR.setLeading(12);
        pR.add(new Chunk("_____________________________________\n", FontFactory.getFont(FontFactory.HELVETICA, 9f, COLOR_BORDE)));
        pR.add(new Chunk("RECTOR(A) INSTITUCIONAL\n", fontCargo));
        pR.add(new Chunk("Lic. Directiva Rectoral IEACI\n", fontNombre));
        pR.add(new Chunk("Institución Educativa Ambiental de Cartagena", fontInst));
        cRector.addElement(pR);
        tablaFirmas.addCell(cRector);

        // Firma Secretaría Académica
        PdfPCell cSec = new PdfPCell();
        cSec.setBorder(Rectangle.NO_BORDER);
        cSec.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph pS = new Paragraph();
        pS.setAlignment(Element.ALIGN_CENTER);
        pS.setLeading(12);
        pS.add(new Chunk("_____________________________________\n", FontFactory.getFont(FontFactory.HELVETICA, 9f, COLOR_BORDE)));
        pS.add(new Chunk("SECRETARÍA ACADÉMICA\n", fontCargo));
        pS.add(new Chunk("Ventanilla Única de Certificaciones\n", fontNombre));
        pS.add(new Chunk("Registro y Control Escolar - IEACI", fontInst));
        cSec.addElement(pS);
        tablaFirmas.addCell(cSec);

        doc.add(tablaFirmas);
    }

    private void agregarPieSeguridad(Document doc, SolicitudCertificado sol) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(10);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_SECUNDARIO);
        cell.setBorderColor(COLOR_BORDE);
        cell.setBorderWidth(1f);
        cell.setPadding(9);

        Font fontCode = FontFactory.getFont(FontFactory.COURIER_BOLD, 8.5f, COLOR_PRIMARIO);
        Font fontAviso = FontFactory.getFont(FontFactory.HELVETICA, 7.2f, COLOR_TEXTO);

        Paragraph p = new Paragraph();
        p.setLeading(11);
        p.add(new Chunk("CÓDIGO DE VALIDACIÓN DIGITAL ÚNICA: ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_PRIMARIO)));
        p.add(new Chunk(sol.getCodigo() != null ? sol.getCodigo() : "CERT-OFICIAL", fontCode));
        p.add(new Chunk("\nDocumento oficial expedido digitalmente por el Sistema Integral de Gestión Académica (SIGA). Para validar la autenticidad de esta constancia ante cualquier entidad pública o privada, verifique el código en el módulo institucional de Secretaría.", fontAviso));

        cell.addElement(p);
        box.addCell(cell);

        doc.add(box);
    }
}

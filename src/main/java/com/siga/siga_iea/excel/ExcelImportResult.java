package com.siga.siga_iea.excel;

import java.util.Collections;
import java.util.List;

/**
 * Resultado estructurado de una importación Excel validada (todo o nada).
 */
public record ExcelImportResult(
        boolean ok,
        int registrosProcesados,
        String mensaje,
        List<String> errores
) {
    public static ExcelImportResult exito(int registrosProcesados, String mensaje) {
        return new ExcelImportResult(true, registrosProcesados, mensaje, Collections.emptyList());
    }

    public static ExcelImportResult fallo(String mensaje, List<String> errores) {
        return new ExcelImportResult(false, 0, mensaje, errores != null ? List.copyOf(errores) : List.of());
    }
}


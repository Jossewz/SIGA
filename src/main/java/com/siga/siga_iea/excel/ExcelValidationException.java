package com.siga.siga_iea.excel;

import java.util.List;

/**
 * Excepción de validación atómica para plantillas Excel (.xlsx).
 * Transporta el resumen general y el listado exacto de errores por fila/columna.
 */
public class ExcelValidationException extends IllegalArgumentException {

    private final List<String> errores;

    public ExcelValidationException(String message) {
        this(message, List.of(message));
    }

    public ExcelValidationException(String message, List<String> errores) {
        super(message);
        this.errores = (errores != null && !errores.isEmpty()) ? List.copyOf(errores) : List.of(message);
    }

    public List<String> getErrores() {
        return errores;
    }
}


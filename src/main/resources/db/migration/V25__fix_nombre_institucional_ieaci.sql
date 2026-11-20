-- Actualizar nombre institucional al oficial IEACI cuando aún conserve el valor inicial de plantilla
UPDATE configuracion_institucional
SET nombre_inst = 'Institución Educativa Ambientalista de Cartagena de Indias (IEACI)',
    updated_at = CURRENT_TIMESTAMP
WHERE nombre_inst = 'Institución Educativa Ateneo de la Ciencia e Innovación (IEACI)';

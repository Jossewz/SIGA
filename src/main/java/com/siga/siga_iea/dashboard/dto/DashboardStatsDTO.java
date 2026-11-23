package com.siga.siga_iea.dashboard.dto;

import java.util.List;
import java.util.Map;

public class DashboardStatsDTO {
    private long estudiantesActivos;
    private long docentesActivos;
    private String asistenciaHoy;
    private long alertasSistema;
    private long totalReportes;
    private long totalMatriculas;
    private long totalCursos;
    private List<Map<String, Object>> estudiantesAtencion;
    private List<Map<String, Object>> actividadTimeline;
    private int totalActividadTimeline;
    private List<Map<String, Object>> alertasOperativas;

    // Indicadores dinámicos para gráficos y semáforos
    private List<SemaforoItemDTO> semaforos;
    private Double promedioAsistencia;
    private List<String> asistenciaLabels;
    private List<Double> asistenciaValores;
    private String asistenciaLabelsCsv;
    private String asistenciaValoresCsv;
    private int porcentajePrimaria;
    private int porcentajeSecundaria;
    private int porcentajeMedia;
    private long totalEstudiantesPrimaria;
    private long totalEstudiantesSecundaria;
    private long totalEstudiantesMedia;

    // Indicadores de desempeño institucional y analítica estratégica
    private double indiceAsistenciaPorcentaje;
    private String indiceAsistenciaTexto = "0%";
    private long inasistenciasSemana;
    private String promedioGeneral = "0.0";
    private long posibleDesercion;
    private double tasaDesercion;
    private double tasaRepitencia;
    private double tasaAprobacion;
    private double tasaReprobacion;
    private double tasaGraduacion;

    public DashboardStatsDTO() {}

    public DashboardStatsDTO(long estudiantesActivos, long docentesActivos, String asistenciaHoy, long alertasSistema,
                             long totalReportes, long totalMatriculas, long totalCursos,
                             List<Map<String, Object>> estudiantesAtencion,
                             List<Map<String, Object>> actividadTimeline,
                             List<Map<String, Object>> alertasOperativas,
                             List<SemaforoItemDTO> semaforos,
                             Double promedioAsistencia,
                             List<String> asistenciaLabels,
                             List<Double> asistenciaValores,
                             String asistenciaLabelsCsv,
                             String asistenciaValoresCsv,
                             int porcentajePrimaria,
                             int porcentajeSecundaria,
                             int porcentajeMedia,
                             long totalEstudiantesPrimaria,
                             long totalEstudiantesSecundaria,
                             long totalEstudiantesMedia) {
        this.estudiantesActivos = estudiantesActivos;
        this.docentesActivos = docentesActivos;
        this.asistenciaHoy = asistenciaHoy;
        this.alertasSistema = alertasSistema;
        this.totalReportes = totalReportes;
        this.totalMatriculas = totalMatriculas;
        this.totalCursos = totalCursos;
        this.estudiantesAtencion = estudiantesAtencion;
        this.actividadTimeline = actividadTimeline;
        this.alertasOperativas = alertasOperativas;
        this.semaforos = semaforos;
        this.promedioAsistencia = promedioAsistencia;
        this.asistenciaLabels = asistenciaLabels;
        this.asistenciaValores = asistenciaValores;
        this.asistenciaLabelsCsv = asistenciaLabelsCsv;
        this.asistenciaValoresCsv = asistenciaValoresCsv;
        this.porcentajePrimaria = porcentajePrimaria;
        this.porcentajeSecundaria = porcentajeSecundaria;
        this.porcentajeMedia = porcentajeMedia;
        this.totalEstudiantesPrimaria = totalEstudiantesPrimaria;
        this.totalEstudiantesSecundaria = totalEstudiantesSecundaria;
        this.totalEstudiantesMedia = totalEstudiantesMedia;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long estudiantesActivos;
        private long docentesActivos;
        private String asistenciaHoy;
        private long alertasSistema;
        private long totalReportes;
        private long totalMatriculas;
        private long totalCursos;
        private List<Map<String, Object>> estudiantesAtencion;
        private List<Map<String, Object>> actividadTimeline;
        private int totalActividadTimeline;
        private List<Map<String, Object>> alertasOperativas;
        private List<SemaforoItemDTO> semaforos;
        private Double promedioAsistencia;
        private List<String> asistenciaLabels;
        private List<Double> asistenciaValores;
        private String asistenciaLabelsCsv;
        private String asistenciaValoresCsv;
        private int porcentajePrimaria;
        private int porcentajeSecundaria;
        private int porcentajeMedia;
        private long totalEstudiantesPrimaria;
        private long totalEstudiantesSecundaria;
        private long totalEstudiantesMedia;
        private double indiceAsistenciaPorcentaje;
        private String indiceAsistenciaTexto = "0%";
        private long inasistenciasSemana;
        private String promedioGeneral = "0.0";
        private long posibleDesercion;
        private double tasaDesercion;
        private double tasaRepitencia;
        private double tasaAprobacion;
        private double tasaReprobacion;
        private double tasaGraduacion;

        public Builder estudiantesActivos(long val) { this.estudiantesActivos = val; return this; }
        public Builder docentesActivos(long val) { this.docentesActivos = val; return this; }
        public Builder asistenciaHoy(String val) { this.asistenciaHoy = val; return this; }
        public Builder alertasSistema(long val) { this.alertasSistema = val; return this; }
        public Builder totalReportes(long val) { this.totalReportes = val; return this; }
        public Builder totalMatriculas(long val) { this.totalMatriculas = val; return this; }
        public Builder totalCursos(long val) { this.totalCursos = val; return this; }
        public Builder estudiantesAtencion(List<Map<String, Object>> val) { this.estudiantesAtencion = val; return this; }
        public Builder actividadTimeline(List<Map<String, Object>> val) { this.actividadTimeline = val; return this; }
        public Builder totalActividadTimeline(int val) { this.totalActividadTimeline = val; return this; }
        public Builder alertasOperativas(List<Map<String, Object>> val) { this.alertasOperativas = val; return this; }
        public Builder semaforos(List<SemaforoItemDTO> val) { this.semaforos = val; return this; }
        public Builder promedioAsistencia(Double val) { this.promedioAsistencia = val; return this; }
        public Builder asistenciaLabels(List<String> val) { this.asistenciaLabels = val; return this; }
        public Builder asistenciaValores(List<Double> val) { this.asistenciaValores = val; return this; }
        public Builder asistenciaLabelsCsv(String val) { this.asistenciaLabelsCsv = val; return this; }
        public Builder asistenciaValoresCsv(String val) { this.asistenciaValoresCsv = val; return this; }
        public Builder porcentajePrimaria(int val) { this.porcentajePrimaria = val; return this; }
        public Builder porcentajeSecundaria(int val) { this.porcentajeSecundaria = val; return this; }
        public Builder porcentajeMedia(int val) { this.porcentajeMedia = val; return this; }
        public Builder totalEstudiantesPrimaria(long val) { this.totalEstudiantesPrimaria = val; return this; }
        public Builder totalEstudiantesSecundaria(long val) { this.totalEstudiantesSecundaria = val; return this; }
        public Builder totalEstudiantesMedia(long val) { this.totalEstudiantesMedia = val; return this; }
        public Builder indiceAsistenciaPorcentaje(double val) { this.indiceAsistenciaPorcentaje = val; return this; }
        public Builder indiceAsistenciaTexto(String val) { this.indiceAsistenciaTexto = val; return this; }
        public Builder inasistenciasSemana(long val) { this.inasistenciasSemana = val; return this; }
        public Builder promedioGeneral(String val) { this.promedioGeneral = val; return this; }
        public Builder posibleDesercion(long val) { this.posibleDesercion = val; return this; }
        public Builder tasaDesercion(double val) { this.tasaDesercion = val; return this; }
        public Builder tasaRepitencia(double val) { this.tasaRepitencia = val; return this; }
        public Builder tasaAprobacion(double val) { this.tasaAprobacion = val; return this; }
        public Builder tasaReprobacion(double val) { this.tasaReprobacion = val; return this; }
        public Builder tasaGraduacion(double val) { this.tasaGraduacion = val; return this; }

        public DashboardStatsDTO build() {
            DashboardStatsDTO dto = new DashboardStatsDTO(estudiantesActivos, docentesActivos, asistenciaHoy, alertasSistema,
                    totalReportes, totalMatriculas, totalCursos, estudiantesAtencion, actividadTimeline, alertasOperativas,
                    semaforos, promedioAsistencia, asistenciaLabels, asistenciaValores, asistenciaLabelsCsv, asistenciaValoresCsv,
                    porcentajePrimaria, porcentajeSecundaria, porcentajeMedia,
                    totalEstudiantesPrimaria, totalEstudiantesSecundaria, totalEstudiantesMedia);
            dto.setTotalActividadTimeline(totalActividadTimeline);
            dto.setIndiceAsistenciaPorcentaje(indiceAsistenciaPorcentaje);
            dto.setIndiceAsistenciaTexto(indiceAsistenciaTexto != null ? indiceAsistenciaTexto : "0%");
            dto.setInasistenciasSemana(inasistenciasSemana);
            dto.setPromedioGeneral(promedioGeneral != null ? promedioGeneral : "0.0");
            dto.setPosibleDesercion(posibleDesercion);
            dto.setTasaDesercion(tasaDesercion);
            dto.setTasaRepitencia(tasaRepitencia);
            dto.setTasaAprobacion(tasaAprobacion);
            dto.setTasaReprobacion(tasaReprobacion);
            dto.setTasaGraduacion(tasaGraduacion);
            return dto;
        }
    }

    public long getEstudiantesActivos() { return estudiantesActivos; }
    public void setEstudiantesActivos(long estudiantesActivos) { this.estudiantesActivos = estudiantesActivos; }

    public long getDocentesActivos() { return docentesActivos; }
    public void setDocentesActivos(long docentesActivos) { this.docentesActivos = docentesActivos; }

    public String getAsistenciaHoy() { return asistenciaHoy; }
    public void setAsistenciaHoy(String asistenciaHoy) { this.asistenciaHoy = asistenciaHoy; }

    public long getAlertasSistema() { return alertasSistema; }
    public void setAlertasSistema(long alertasSistema) { this.alertasSistema = alertasSistema; }

    public long getTotalReportes() { return totalReportes; }
    public void setTotalReportes(long totalReportes) { this.totalReportes = totalReportes; }

    public long getTotalMatriculas() { return totalMatriculas; }
    public void setTotalMatriculas(long totalMatriculas) { this.totalMatriculas = totalMatriculas; }

    public long getTotalCursos() { return totalCursos; }
    public void setTotalCursos(long totalCursos) { this.totalCursos = totalCursos; }

    public List<Map<String, Object>> getEstudiantesAtencion() { return estudiantesAtencion; }
    public void setEstudiantesAtencion(List<Map<String, Object>> estudiantesAtencion) { this.estudiantesAtencion = estudiantesAtencion; }

    public List<Map<String, Object>> getActividadTimeline() { return actividadTimeline; }
    public void setActividadTimeline(List<Map<String, Object>> actividadTimeline) { this.actividadTimeline = actividadTimeline; }

    public int getTotalActividadTimeline() { return totalActividadTimeline; }
    public void setTotalActividadTimeline(int totalActividadTimeline) { this.totalActividadTimeline = totalActividadTimeline; }

    public List<Map<String, Object>> getAlertasOperativas() { return alertasOperativas; }
    public void setAlertasOperativas(List<Map<String, Object>> alertasOperativas) { this.alertasOperativas = alertasOperativas; }

    public List<SemaforoItemDTO> getSemaforos() { return semaforos; }
    public void setSemaforos(List<SemaforoItemDTO> semaforos) { this.semaforos = semaforos; }

    public Double getPromedioAsistencia() { return promedioAsistencia; }
    public void setPromedioAsistencia(Double promedioAsistencia) { this.promedioAsistencia = promedioAsistencia; }

    public List<String> getAsistenciaLabels() { return asistenciaLabels; }
    public void setAsistenciaLabels(List<String> asistenciaLabels) { this.asistenciaLabels = asistenciaLabels; }

    public List<Double> getAsistenciaValores() { return asistenciaValores; }
    public void setAsistenciaValores(List<Double> asistenciaValores) { this.asistenciaValores = asistenciaValores; }

    public String getAsistenciaLabelsCsv() { return asistenciaLabelsCsv; }
    public void setAsistenciaLabelsCsv(String asistenciaLabelsCsv) { this.asistenciaLabelsCsv = asistenciaLabelsCsv; }

    public String getAsistenciaValoresCsv() { return asistenciaValoresCsv; }
    public void setAsistenciaValoresCsv(String asistenciaValoresCsv) { this.asistenciaValoresCsv = asistenciaValoresCsv; }

    public int getPorcentajePrimaria() { return porcentajePrimaria; }
    public void setPorcentajePrimaria(int porcentajePrimaria) { this.porcentajePrimaria = porcentajePrimaria; }

    public int getPorcentajeSecundaria() { return porcentajeSecundaria; }
    public void setPorcentajeSecundaria(int porcentajeSecundaria) { this.porcentajeSecundaria = porcentajeSecundaria; }

    public int getPorcentajeMedia() { return porcentajeMedia; }
    public void setPorcentajeMedia(int porcentajeMedia) { this.porcentajeMedia = porcentajeMedia; }

    public long getTotalEstudiantesPrimaria() { return totalEstudiantesPrimaria; }
    public void setTotalEstudiantesPrimaria(long totalEstudiantesPrimaria) { this.totalEstudiantesPrimaria = totalEstudiantesPrimaria; }

    public long getTotalEstudiantesSecundaria() { return totalEstudiantesSecundaria; }
    public void setTotalEstudiantesSecundaria(long totalEstudiantesSecundaria) { this.totalEstudiantesSecundaria = totalEstudiantesSecundaria; }

    public long getTotalEstudiantesMedia() { return totalEstudiantesMedia; }
    public void setTotalEstudiantesMedia(long totalEstudiantesMedia) { this.totalEstudiantesMedia = totalEstudiantesMedia; }

    public double getIndiceAsistenciaPorcentaje() { return indiceAsistenciaPorcentaje; }
    public void setIndiceAsistenciaPorcentaje(double indiceAsistenciaPorcentaje) { this.indiceAsistenciaPorcentaje = indiceAsistenciaPorcentaje; }

    public String getIndiceAsistenciaTexto() { return indiceAsistenciaTexto; }
    public void setIndiceAsistenciaTexto(String indiceAsistenciaTexto) { this.indiceAsistenciaTexto = indiceAsistenciaTexto; }

    public long getInasistenciasSemana() { return inasistenciasSemana; }
    public void setInasistenciasSemana(long inasistenciasSemana) { this.inasistenciasSemana = inasistenciasSemana; }

    public String getPromedioGeneral() { return promedioGeneral; }
    public void setPromedioGeneral(String promedioGeneral) { this.promedioGeneral = promedioGeneral; }

    public long getPosibleDesercion() { return posibleDesercion; }
    public void setPosibleDesercion(long posibleDesercion) { this.posibleDesercion = posibleDesercion; }

    public double getTasaDesercion() { return tasaDesercion; }
    public void setTasaDesercion(double tasaDesercion) { this.tasaDesercion = tasaDesercion; }

    public double getTasaRepitencia() { return tasaRepitencia; }
    public void setTasaRepitencia(double tasaRepitencia) { this.tasaRepitencia = tasaRepitencia; }

    public double getTasaAprobacion() { return tasaAprobacion; }
    public void setTasaAprobacion(double tasaAprobacion) { this.tasaAprobacion = tasaAprobacion; }

    public double getTasaReprobacion() { return tasaReprobacion; }
    public void setTasaReprobacion(double tasaReprobacion) { this.tasaReprobacion = tasaReprobacion; }

    public double getTasaGraduacion() { return tasaGraduacion; }
    public void setTasaGraduacion(double tasaGraduacion) { this.tasaGraduacion = tasaGraduacion; }
}

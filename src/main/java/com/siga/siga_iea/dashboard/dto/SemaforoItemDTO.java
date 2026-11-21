package com.siga.siga_iea.dashboard.dto;

public class SemaforoItemDTO {
    private String titulo;
    private String estado;
    private String dotColor;
    private String badgeClass;

    public SemaforoItemDTO() {}

    public SemaforoItemDTO(String titulo, String estado, String dotColor, String badgeClass) {
        this.titulo = titulo;
        this.estado = estado;
        this.dotColor = dotColor;
        this.badgeClass = badgeClass;
    }

    public static SemaforoItemDTO optimo(String titulo, String estado) {
        return new SemaforoItemDTO(titulo, estado, "#1b6327", "text-sidebar bg-pill-green border-[#cbe5c7]");
    }

    public static SemaforoItemDTO advertencia(String titulo, String estado) {
        return new SemaforoItemDTO(titulo, estado, "#8f5b12", "text-[#8f5b12] bg-pill-warning border-[#f0dec0]");
    }

    public static SemaforoItemDTO atencion(String titulo, String estado) {
        return new SemaforoItemDTO(titulo, estado, "#a82a2a", "text-alert-red bg-pill-red border-[#f5cece]");
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getDotColor() {
        return dotColor;
    }

    public void setDotColor(String dotColor) {
        this.dotColor = dotColor;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public void setBadgeClass(String badgeClass) {
        this.badgeClass = badgeClass;
    }
}

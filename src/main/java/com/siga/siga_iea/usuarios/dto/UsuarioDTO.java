package com.siga.siga_iea.usuarios.dto;

import java.util.UUID;

public class UsuarioDTO {
    private UUID id;
    private String username;
    private String persona;
    private String rol;
    private String ultimoAcceso;
    private String estado;
    private String documento;

    public UsuarioDTO() {}

    public UsuarioDTO(UUID id, String username, String persona, String rol, String ultimoAcceso, String estado, String documento) {
        this.id = id;
        this.username = username;
        this.persona = persona;
        this.rol = rol;
        this.ultimoAcceso = ultimoAcceso;
        this.estado = estado;
        this.documento = documento;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(String ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }
}

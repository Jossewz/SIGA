package com.siga.siga_iea.reportes.entity;

import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reportes")
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "VARCHAR", nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estudiante_id", nullable = true)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "docente_id", nullable = true)
    private Docente docente;

    @Column(name = "reportante_tipo", length = 30, nullable = false)
    private String reportanteTipo = "DOCENTE";

    @Column(name = "objetivo_tipo", length = 30, nullable = false)
    private String objetivoTipo = "ESTUDIANTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportante_usuario_id")
    private Usuario reportanteUsuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reportante_estudiante_id")
    private Estudiante reportanteEstudiante;

    @Column(columnDefinition = "VARCHAR", nullable = false)
    private String categoria;

    @Column(columnDefinition = "VARCHAR", nullable = false)
    private String razon;

    @Column(name = "descripcion_razon", columnDefinition = "TEXT")
    private String descripcionRazon;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String detalles;

    @Column(columnDefinition = "VARCHAR", nullable = false)
    private String estado = "Pendiente"; // Pendiente, Aceptado, Rechazado

    @Column(name = "fecha_citacion")
    private LocalDateTime fechaCitacion;

    @Column(name = "requiere_acudiente")
    private Boolean requiereAcudiente = false;

    @Column(name = "observaciones_admin", columnDefinition = "TEXT")
    private String observacionesAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendido_por")
    private PersonalAdministrativo atendidoPor;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Reporte() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Estudiante getEstudiante() { return estudiante; }
    public void setEstudiante(Estudiante estudiante) { this.estudiante = estudiante; }

    public Docente getDocente() { return docente; }
    public void setDocente(Docente docente) { this.docente = docente; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getRazon() { return razon; }
    public void setRazon(String razon) { this.razon = razon; }

    public String getDescripcionRazon() { return descripcionRazon; }
    public void setDescripcionRazon(String descripcionRazon) { this.descripcionRazon = descripcionRazon; }

    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCitacion() { return fechaCitacion; }
    public void setFechaCitacion(LocalDateTime fechaCitacion) { this.fechaCitacion = fechaCitacion; }

    public Boolean getRequiereAcudiente() { return requiereAcudiente != null ? requiereAcudiente : false; }
    public void setRequiereAcudiente(Boolean requiereAcudiente) { this.requiereAcudiente = requiereAcudiente; }

    public String getObservacionesAdmin() { return observacionesAdmin; }
    public void setObservacionesAdmin(String observacionesAdmin) { this.observacionesAdmin = observacionesAdmin; }

    public PersonalAdministrativo getAtendidoPor() { return atendidoPor; }
    public void setAtendidoPor(PersonalAdministrativo atendidoPor) { this.atendidoPor = atendidoPor; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getReportanteTipo() { return reportanteTipo != null ? reportanteTipo : "DOCENTE"; }
    public void setReportanteTipo(String reportanteTipo) { this.reportanteTipo = reportanteTipo; }

    public String getObjetivoTipo() { return objetivoTipo != null ? objetivoTipo : "ESTUDIANTE"; }
    public void setObjetivoTipo(String objetivoTipo) { this.objetivoTipo = objetivoTipo; }

    public Usuario getReportanteUsuario() { return reportanteUsuario; }
    public void setReportanteUsuario(Usuario reportanteUsuario) { this.reportanteUsuario = reportanteUsuario; }

    public Estudiante getReportanteEstudiante() { return reportanteEstudiante; }
    public void setReportanteEstudiante(Estudiante reportanteEstudiante) { this.reportanteEstudiante = reportanteEstudiante; }
}

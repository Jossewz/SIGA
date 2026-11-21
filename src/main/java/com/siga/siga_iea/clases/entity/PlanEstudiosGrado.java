package com.siga.siga_iea.clases.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Define la intensidad horaria semanal específica de una asignatura para cada grado (1° a 11°).
 */
@Entity
@Table(name = "plan_estudios_grado", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_grado_materia", columnNames = {"grado", "materia_id"})
})
public class PlanEstudiosGrado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "grado", nullable = false, length = 20)
    private String grado;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "materia_id", nullable = false)
    private Materia materia;

    @Column(name = "intensidad_horaria", nullable = false)
    private Integer intensidadHoraria = 2;

    public PlanEstudiosGrado() {}

    public PlanEstudiosGrado(String grado, Materia materia, Integer intensidadHoraria) {
        this.grado = normalizarGrado(grado);
        this.materia = materia;
        this.intensidadHoraria = intensidadHoraria != null ? intensidadHoraria : 2;
    }

    public static String normalizarGrado(String grado) {
        if (grado == null || grado.isBlank()) return "11°";
        String limpio = grado.trim().replace("º", "°");
        if (!limpio.endsWith("°") && limpio.matches("\\d+")) {
            return limpio + "°";
        }
        return limpio;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getGrado() { return grado; }
    public void setGrado(String grado) { this.grado = normalizarGrado(grado); }

    public Materia getMateria() { return materia; }
    public void setMateria(Materia materia) { this.materia = materia; }

    public Integer getIntensidadHoraria() { return intensidadHoraria; }
    public void setIntensidadHoraria(Integer intensidadHoraria) { this.intensidadHoraria = intensidadHoraria; }
}

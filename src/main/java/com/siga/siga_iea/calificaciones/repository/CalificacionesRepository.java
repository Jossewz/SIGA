package com.siga.siga_iea.calificaciones.repository;

import com.siga.siga_iea.calificaciones.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalificacionesRepository extends JpaRepository<Calificacion, UUID> {

    List<Calificacion> findByEstudianteId(UUID estudianteId);

    Optional<Calificacion> findByEvaluacionIdAndEstudianteId(UUID evaluacionId, UUID estudianteId);

    List<Calificacion> findByEvaluacionId(UUID evaluacionId);

    void deleteByEvaluacionId(UUID evaluacionId);

    @Query("SELECT c FROM Calificacion c WHERE c.evaluacion.cursoMateria.curso.id = :cursoId AND c.evaluacion.periodo = :periodo")
    List<Calificacion> findByCursoAndPeriodo(@Param("cursoId") UUID cursoId, @Param("periodo") Integer periodo);

    @Query("SELECT c FROM Calificacion c WHERE c.evaluacion.cursoMateria.id = :cursoMateriaId")
    List<Calificacion> findByCursoMateriaId(@Param("cursoMateriaId") UUID cursoMateriaId);

    @Query("SELECT AVG(c.nota) FROM Calificacion c")
    Double findPromedioGeneralInstitucional();

    @Query("SELECT c.estudiante.id, AVG(c.nota) FROM Calificacion c GROUP BY c.estudiante.id")
    List<Object[]> findPromedioPorEstudiante();

    @Query("SELECT c.estudiante.id, c.evaluacion.cursoMateria.materia.id " +
           "FROM Calificacion c " +
           "GROUP BY c.estudiante.id, c.evaluacion.cursoMateria.materia.id " +
           "HAVING AVG(c.nota) < 3.0")
    List<Object[]> findMateriasReprobadasPorEstudiante();
}

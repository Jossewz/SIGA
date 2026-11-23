package com.siga.siga_iea.clases.repository;

import com.siga.siga_iea.clases.entity.CursoEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CursoEstudianteRepository extends JpaRepository<CursoEstudiante, UUID> {
    List<CursoEstudiante> findByCursoId(UUID cursoId);
    Optional<CursoEstudiante> findByCursoIdAndEstudianteIdAndAnoLectivo(UUID cursoId, UUID estudianteId, String anoLectivo);
    Optional<CursoEstudiante> findByEstudianteIdAndAnoLectivo(UUID estudianteId, String anoLectivo);
    List<CursoEstudiante> findByEstudianteId(UUID estudianteId);
    boolean existsByEstudianteIdAndAnoLectivo(UUID estudianteId, String anoLectivo);
    boolean existsByCursoIdAndEstudianteId(UUID cursoId, UUID estudianteId);

    @Query("SELECT ce.curso.id, COUNT(ce) FROM CursoEstudiante ce GROUP BY ce.curso.id")
    List<Object[]> contarEstudiantesPorCurso();

    @Query("SELECT ce.curso.id FROM CursoEstudiante ce WHERE ce.estudiante.id = :estudianteId")
    List<UUID> findCursoIdsByEstudianteId(@Param("estudianteId") UUID estudianteId);
}

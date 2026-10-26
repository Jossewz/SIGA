package com.siga.siga_iea.asistencias.repository;

import com.siga.siga_iea.asistencias.entity.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, UUID> {

    List<Asistencia> findBySesionId(UUID sesionId);

    Optional<Asistencia> findBySesionIdAndEstudianteId(UUID sesionId, UUID estudianteId);

    boolean existsBySesionIdAndEstudianteId(UUID sesionId, UUID estudianteId);

    List<Asistencia> findByEstudianteId(UUID estudianteId);

    @Query("SELECT a FROM Asistencia a WHERE a.sesion.curso.id = :cursoId AND a.sesion.fecha = :fecha")
    List<Asistencia> findByCursoIdAndFecha(@Param("cursoId") UUID cursoId, @Param("fecha") LocalDate fecha);

    @Query("SELECT a FROM Asistencia a WHERE a.sesion.curso.id = :cursoId AND a.sesion.cursoMateria.materia.id = :materiaId AND a.sesion.fecha = :fecha")
    List<Asistencia> findByCursoIdAndMateriaIdAndFecha(@Param("cursoId") UUID cursoId,
                                                      @Param("materiaId") UUID materiaId,
                                                      @Param("fecha") LocalDate fecha);

    @Query("SELECT a.sesion.fecha, " +
           "COUNT(a.id), " +
           "SUM(CASE WHEN a.estado IN ('PRESENTE', 'TARDE') THEN 1L ELSE 0L END) " +
           "FROM Asistencia a " +
           "WHERE a.sesion.fecha >= :desde " +
           "GROUP BY a.sesion.fecha " +
           "ORDER BY a.sesion.fecha ASC")
    List<Object[]> findResumenAsistenciaPorFecha(@Param("desde") LocalDate desde);

    @Query("SELECT COUNT(a.id), " +
           "SUM(CASE WHEN a.estado IN ('PRESENTE', 'TARDE') THEN 1L ELSE 0L END) " +
           "FROM Asistencia a " +
           "WHERE a.sesion.fecha = :fecha")
    List<Object[]> findResumenAsistenciaPorDia(@Param("fecha") LocalDate fecha);
}

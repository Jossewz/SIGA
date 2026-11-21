package com.siga.siga_iea.clases.repository;

import com.siga.siga_iea.clases.entity.PlanEstudiosGrado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanEstudiosGradoRepository extends JpaRepository<PlanEstudiosGrado, UUID> {
    Optional<PlanEstudiosGrado> findByGradoAndMateriaId(String grado, UUID materiaId);
    List<PlanEstudiosGrado> findByGradoOrderByMateriaNombreAsc(String grado);
    List<PlanEstudiosGrado> findByMateriaId(UUID materiaId);
}

package com.siga.siga_iea.certificados.repository;

import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SolicitudCertificadoRepository extends JpaRepository<SolicitudCertificado, UUID> {
    @EntityGraph(attributePaths = {"estudiante", "respondidoPor"})
    List<SolicitudCertificado> findByEstudianteIdOrderByCreatedAtDesc(UUID estudianteId);

    @EntityGraph(attributePaths = {"estudiante", "respondidoPor"})
    List<SolicitudCertificado> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"estudiante", "respondidoPor"})
    Optional<SolicitudCertificado> findByCodigo(String codigo);

    @EntityGraph(attributePaths = {"estudiante", "respondidoPor"})
    List<SolicitudCertificado> findByEstado(String estado);
}

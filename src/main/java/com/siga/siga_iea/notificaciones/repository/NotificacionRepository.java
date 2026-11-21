package com.siga.siga_iea.notificaciones.repository;

import com.siga.siga_iea.notificaciones.entity.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaEnIsNull(UUID usuarioId);

    Optional<Notificacion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leidaEn = :ahora WHERE n.usuario.id = :usuarioId AND n.leidaEn IS NULL")
    int marcarTodasComoLeidas(@Param("usuarioId") UUID usuarioId, @Param("ahora") LocalDateTime ahora);
}


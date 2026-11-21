package com.siga.siga_iea.certificados.service;

import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.certificados.repository.SolicitudCertificadoRepository;
import com.siga.siga_iea.storage.StorageFolder;
import com.siga.siga_iea.storage.StorageService;
import com.siga.siga_iea.storage.dto.UploadResult;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class CertificadoService {

    private final SolicitudCertificadoRepository solicitudRepository;
    private final EstudianteRepository estudianteRepository;
    private final PersonalAdministrativoRepository personalRepository;
    private final StorageService storageService;
    private final com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService;
    private final com.siga.siga_iea.auditoria.service.AuditoriaService auditoriaService;
    private final com.siga.siga_iea.notificaciones.service.NotificacionService notificacionService;

    @org.springframework.beans.factory.annotation.Autowired
    public CertificadoService(SolicitudCertificadoRepository solicitudRepository,
                              EstudianteRepository estudianteRepository,
                              PersonalAdministrativoRepository personalRepository,
                              StorageService storageService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false)
                              com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false)
                              com.siga.siga_iea.auditoria.service.AuditoriaService auditoriaService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false)
                              com.siga.siga_iea.notificaciones.service.NotificacionService notificacionService) {
        this.solicitudRepository = solicitudRepository;
        this.estudianteRepository = estudianteRepository;
        this.personalRepository = personalRepository;
        this.storageService = storageService;
        this.periodoConfigService = periodoConfigService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    public CertificadoService(SolicitudCertificadoRepository solicitudRepository,
                              EstudianteRepository estudianteRepository,
                              PersonalAdministrativoRepository personalRepository,
                              StorageService storageService,
                              com.siga.siga_iea.configuracion.service.PeriodoConfigService periodoConfigService,
                              com.siga.siga_iea.auditoria.service.AuditoriaService auditoriaService) {
        this(solicitudRepository, estudianteRepository, personalRepository, storageService, periodoConfigService, auditoriaService, null);
    }

    public List<SolicitudCertificado> listarTodas() {
        return solicitudRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<SolicitudCertificado> listarPorEstudiante(UUID estudianteId) {
        return solicitudRepository.findByEstudianteIdOrderByCreatedAtDesc(estudianteId);
    }

    public List<SolicitudCertificado> filtrarPorEstado(String estado) {
        if (estado == null || estado.isBlank() || "todos".equalsIgnoreCase(estado)) {
            return listarTodas();
        }
        return solicitudRepository.findByEstado(estado);
    }

    public Optional<SolicitudCertificado> buscarPorId(UUID id) {
        return solicitudRepository.findById(id);
    }

    public Optional<SolicitudCertificado> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return solicitudRepository.findByCodigo(codigo.trim());
    }

    @Transactional
    public SolicitudCertificado crearSolicitud(UUID estudianteId, String tipo, String categoria, String motivo, String anoLectivo, String gradoReferencia) {
        Estudiante est = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));

        String anio = (anoLectivo != null && !anoLectivo.isBlank()) ? anoLectivo.trim() :
                (periodoConfigService != null
                        ? periodoConfigService.obtenerAnioLectivoCodigo()
                        : String.valueOf(com.siga.siga_iea.configuracion.service.PeriodoConfigService.anioCalendarioActual()));

        long consecutivo = solicitudRepository.count() + 1;
        SolicitudCertificado s = new SolicitudCertificado();
        s.setCodigo(String.format("CERT-%s-%04d", anio, consecutivo));
        s.setEstudiante(est);
        s.setTipo(tipo);
        s.setCategoria(categoria != null ? categoria : "Constancias Administrativas");
        s.setMotivo(motivo);
        s.setAnoLectivo(anio);
        s.setGradoReferencia(gradoReferencia != null ? gradoReferencia : "11°");
        s.setEstado("Pendiente");

        SolicitudCertificado guardada = solicitudRepository.save(s);
        if (auditoriaService != null) {
            auditoriaService.registrarCambio("SOLICITUD_CERTIFICADO", guardada.getId(), "creacion", null, guardada.getTipo() + " (" + guardada.getCodigo() + ")");
        }
        if (notificacionService != null) {
            java.util.Map<String, String> detalles = new java.util.LinkedHashMap<>();
            detalles.put("Código", guardada.getCodigo());
            detalles.put("Estudiante", est.getNombreCompleto());
            detalles.put("Tipo de trámite", guardada.getTipo());
            detalles.put("Grado", guardada.getGradoReferencia());
            detalles.put("Motivo", guardada.getMotivo());
            notificacionService.notificarAdministrativos(
                    "CERTIFICADO_SOLICITADO",
                    "Nueva solicitud de certificado (" + guardada.getCodigo() + ")",
                    est.getNombreCompleto() + " solicitó: " + guardada.getTipo() + ".",
                    guardada.getCodigo(),
                    "badge-check",
                    "sky",
                    true,
                    detalles
            );
        }
        return guardada;
    }

    @Transactional
    public SolicitudCertificado responderSolicitud(UUID solicitudId, String mensajeRespuesta, MultipartFile archivoPDF, UUID adminId) {
        return responderSolicitud(solicitudId, mensajeRespuesta, archivoPDF, adminId, "Resuelto");
    }

    @Transactional
    public SolicitudCertificado responderSolicitud(UUID solicitudId, String mensajeRespuesta, MultipartFile archivoPDF, UUID adminId, String decision) {
        SolicitudCertificado s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        String estadoPrevio = s.getEstado();
        String estadoNuevo = (decision != null && !decision.isBlank()) ? decision.trim() : "Resuelto";
        s.setEstado(estadoNuevo);

        if (mensajeRespuesta != null && !mensajeRespuesta.isBlank()) {
            s.setMensajeRespuesta(mensajeRespuesta.trim());
        } else if ("Resuelto".equalsIgnoreCase(estadoNuevo)) {
            s.setMensajeRespuesta("Certificado oficial generado y validado digitalmente por Secretaría Académica.");
        } else {
            s.setMensajeRespuesta("Solicitud revisada por Secretaría Académica.");
        }

        if (adminId != null) {
            personalRepository.findById(adminId).ifPresent(s::setRespondidoPor);
        }

        if (archivoPDF != null && !archivoPDF.isEmpty()) {
            storageService.validateExtension(archivoPDF, "pdf");
            UploadResult result = storageService.upload(archivoPDF, StorageFolder.ESTUDIANTES, s.getCodigo());
            s.setArchivoAdjuntoKey(result.getKey());
        }

        SolicitudCertificado guardada = solicitudRepository.save(s);
        if (auditoriaService != null) {
            auditoriaService.registrarCambio("SOLICITUD_CERTIFICADO", guardada.getId(), "resolucion", estadoPrevio, estadoNuevo + " (" + guardada.getCodigo() + ")");
        }
        if (notificacionService != null && guardada.getEstudiante() != null) {
            java.util.Map<String, String> detalles = new java.util.LinkedHashMap<>();
            detalles.put("Código", guardada.getCodigo());
            detalles.put("Tipo de certificado", guardada.getTipo());
            detalles.put("Estado", estadoNuevo);
            detalles.put("Observaciones", guardada.getMensajeRespuesta());
            boolean aprobado = "Resuelto".equalsIgnoreCase(estadoNuevo) || "Aprobado".equalsIgnoreCase(estadoNuevo);
            notificacionService.notificarEstudianteYAcudiente(
                    guardada.getEstudiante(),
                    "CERTIFICADO_RESPONDIDO",
                    "Tu solicitud " + guardada.getCodigo() + " fue marcada como " + estadoNuevo,
                    guardada.getMensajeRespuesta(),
                    guardada.getCodigo(),
                    "badge-check",
                    aprobado ? "emerald" : "rose",
                    true,
                    detalles
            );
        }
        return guardada;
    }
}

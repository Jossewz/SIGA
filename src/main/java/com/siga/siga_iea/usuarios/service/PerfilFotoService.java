package com.siga.siga_iea.usuarios.service;

import com.siga.siga_iea.storage.StorageFolder;
import com.siga.siga_iea.storage.StorageService;
import com.siga.siga_iea.storage.dto.UploadResult;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Servicio centralizado para validar, subir, sincronizar y eliminar fotos de perfil
 * entre cuentas de {@link Usuario} y sus fichas asociadas (Docente, Personal o Estudiante).
 */
@Service
public class PerfilFotoService {

    private static final Logger log = LoggerFactory.getLogger(PerfilFotoService.class);
    private static final long MAX_FOTO_BYTES = 5L * 1024 * 1024;
    private static final String[] EXTENSIONES_PERMITIDAS = {"jpg", "jpeg", "png", "webp"};

    private final StorageService storageService;
    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;
    private final EstudianteRepository estudianteRepository;

    public PerfilFotoService(StorageService storageService,
                             UsuarioRepository usuarioRepository,
                             DocenteRepository docenteRepository,
                             PersonalAdministrativoRepository personalAdministrativoRepository,
                             EstudianteRepository estudianteRepository) {
        this.storageService = storageService;
        this.usuarioRepository = usuarioRepository;
        this.docenteRepository = docenteRepository;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
        this.estudianteRepository = estudianteRepository;
    }

    public String construirUrl(String fotoKey) {
        if (fotoKey == null || fotoKey.isBlank()) {
            return null;
        }
        return fotoKey.startsWith("http") ? fotoKey : "/storage/public/view?key=" + fotoKey;
    }

    @Transactional
    public String actualizarFotoUsuarioPropio(Usuario usuario, MultipartFile archivo) {
        validarArchivo(archivo);
        UploadResult res = storageService.upload(archivo, StorageFolder.USUARIOS, usuario.getId().toString());
        String nuevaKey = res.getKey();
        String anteriorKey = usuario.getFotoKey();

        usuario.setFotoKey(nuevaKey);
        usuarioRepository.save(usuario);
        sincronizarEntidadPorDocumento(usuario.getNumeroDocumento(), nuevaKey);
        eliminarAnteriorSilenciosamente(anteriorKey);
        return nuevaKey;
    }

    @Transactional
    public void eliminarFotoUsuarioPropio(Usuario usuario) {
        String anteriorKey = usuario.getFotoKey();
        usuario.setFotoKey(null);
        usuarioRepository.save(usuario);
        sincronizarEntidadPorDocumento(usuario.getNumeroDocumento(), null);
        eliminarAnteriorSilenciosamente(anteriorKey);
    }

    @Transactional
    public String actualizarFotoEstudiante(UUID estudianteId, MultipartFile archivo) {
        validarArchivo(archivo);
        var estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado."));
        String entityFolderKey = (estudiante.getNumeroDocumento() != null && !estudiante.getNumeroDocumento().isBlank())
                ? estudiante.getNumeroDocumento().trim()
                : estudianteId.toString();
        UploadResult res = storageService.upload(archivo, StorageFolder.ESTUDIANTES, entityFolderKey);
        String nuevaKey = res.getKey();
        String anteriorKey = estudiante.getFotoKey();

        estudiante.setFotoKey(nuevaKey);
        estudianteRepository.save(estudiante);
        sincronizarUsuarioPorDocumento(estudiante.getNumeroDocumento(), nuevaKey);
        eliminarAnteriorSilenciosamente(anteriorKey);
        return nuevaKey;
    }

    @Transactional
    public void eliminarFotoEstudiante(UUID estudianteId) {
        var estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado."));
        String anteriorKey = estudiante.getFotoKey();
        estudiante.setFotoKey(null);
        estudianteRepository.save(estudiante);
        sincronizarUsuarioPorDocumento(estudiante.getNumeroDocumento(), null);
        eliminarAnteriorSilenciosamente(anteriorKey);
    }

    @Transactional
    public String actualizarFotoPersonal(UUID personalId, MultipartFile archivo) {
        validarArchivo(archivo);
        var docenteOpt = docenteRepository.findById(personalId);
        if (docenteOpt.isPresent()) {
            var doc = docenteOpt.get();
            String docKey = (doc.getNumeroDocumento() != null && !doc.getNumeroDocumento().isBlank())
                    ? doc.getNumeroDocumento().trim() : personalId.toString();
            UploadResult res = storageService.upload(archivo, StorageFolder.DOCENTES, docKey);
            String nuevaKey = res.getKey();
            String anteriorKey = doc.getFotoKey();
            doc.setFotoKey(nuevaKey);
            docenteRepository.save(doc);
            sincronizarUsuarioPorDocumento(doc.getNumeroDocumento(), nuevaKey);
            eliminarAnteriorSilenciosamente(anteriorKey);
            return nuevaKey;
        }

        var admin = personalAdministrativoRepository.findById(personalId)
                .orElseThrow(() -> new IllegalArgumentException("Miembro del personal no encontrado."));
        String docKey = (admin.getNumeroDocumento() != null && !admin.getNumeroDocumento().isBlank())
                ? admin.getNumeroDocumento().trim() : personalId.toString();
        UploadResult res = storageService.upload(archivo, StorageFolder.USUARIOS, docKey);
        String nuevaKey = res.getKey();
        String anteriorKey = admin.getFotoKey();
        admin.setFotoKey(nuevaKey);
        personalAdministrativoRepository.save(admin);
        sincronizarUsuarioPorDocumento(admin.getNumeroDocumento(), nuevaKey);
        eliminarAnteriorSilenciosamente(anteriorKey);
        return nuevaKey;
    }

    @Transactional
    public void eliminarFotoPersonal(UUID personalId) {
        var docenteOpt = docenteRepository.findById(personalId);
        if (docenteOpt.isPresent()) {
            var doc = docenteOpt.get();
            String anteriorKey = doc.getFotoKey();
            doc.setFotoKey(null);
            docenteRepository.save(doc);
            sincronizarUsuarioPorDocumento(doc.getNumeroDocumento(), null);
            eliminarAnteriorSilenciosamente(anteriorKey);
            return;
        }

        var admin = personalAdministrativoRepository.findById(personalId)
                .orElseThrow(() -> new IllegalArgumentException("Miembro del personal no encontrado."));
        String anteriorKey = admin.getFotoKey();
        admin.setFotoKey(null);
        personalAdministrativoRepository.save(admin);
        sincronizarUsuarioPorDocumento(admin.getNumeroDocumento(), null);
        eliminarAnteriorSilenciosamente(anteriorKey);
    }

    public String resolverFotoKeyPorUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        if (usuario.getFotoKey() != null && !usuario.getFotoKey().isBlank()) {
            return usuario.getFotoKey();
        }
        String doc = usuario.getNumeroDocumento();
        if (doc == null || doc.isBlank()) {
            return null;
        }
        return docenteRepository.findByNumeroDocumento(doc).map(d -> d.getFotoKey())
                .filter(k -> k != null && !k.isBlank())
                .or(() -> personalAdministrativoRepository.findByNumeroDocumento(doc).map(p -> p.getFotoKey())
                        .filter(k -> k != null && !k.isBlank()))
                .or(() -> estudianteRepository.findByNumeroDocumento(doc).map(e -> e.getFotoKey())
                        .filter(k -> k != null && !k.isBlank()))
                .orElse(null);
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar una imagen válida.");
        }
        storageService.validateSize(archivo, MAX_FOTO_BYTES);
        storageService.validateExtension(archivo, EXTENSIONES_PERMITIDAS);
    }

    private void sincronizarUsuarioPorDocumento(String numeroDocumento, String nuevaKey) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            return;
        }
        usuarioRepository.findByNumeroDocumento(numeroDocumento.trim()).ifPresent(u -> {
            u.setFotoKey(nuevaKey);
            usuarioRepository.save(u);
        });
    }

    private void sincronizarEntidadPorDocumento(String numeroDocumento, String nuevaKey) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            return;
        }
        String doc = numeroDocumento.trim();
        docenteRepository.findByNumeroDocumento(doc).ifPresent(d -> {
            d.setFotoKey(nuevaKey);
            docenteRepository.save(d);
        });
        personalAdministrativoRepository.findByNumeroDocumento(doc).ifPresent(p -> {
            p.setFotoKey(nuevaKey);
            personalAdministrativoRepository.save(p);
        });
        estudianteRepository.findByNumeroDocumento(doc).ifPresent(e -> {
            e.setFotoKey(nuevaKey);
            estudianteRepository.save(e);
        });
    }

    private void eliminarAnteriorSilenciosamente(String oldKey) {
        if (oldKey == null || oldKey.isBlank() || oldKey.startsWith("http") || oldKey.startsWith("temp/")) {
            return;
        }
        try {
            storageService.delete(oldKey);
        } catch (Exception e) {
            log.warn("No se pudo eliminar foto anterior '{}' de almacenamiento: {}", oldKey, e.getMessage());
        }
    }
}


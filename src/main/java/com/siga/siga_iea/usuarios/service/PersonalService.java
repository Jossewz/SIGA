package com.siga.siga_iea.usuarios.service;

import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import com.siga.siga_iea.auditoria.service.AuditoriaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PersonalService {

    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;
    private final UsuarioRepository usuarioRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    @PersistenceContext
    private EntityManager entityManager;

    public PersonalService(DocenteRepository docenteRepository,
                           PersonalAdministrativoRepository personalAdministrativoRepository,
                           UsuarioRepository usuarioRepository,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                           @Autowired(required = false) AuditoriaService auditoriaService) {
        this.docenteRepository = docenteRepository;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public List<Docente> listarDocentes() {
        return docenteRepository.findAll();
    }

    public List<PersonalAdministrativo> listarPersonalAdministrativo() {
        return personalAdministrativoRepository.findAll();
    }

    public List<Docente> buscarDocentes(String search, String estado) {
        return docenteRepository.searchDocentes(search, estado);
    }

    public List<PersonalAdministrativo> buscarPersonal(String search, String cargo, String area, String estado) {
        return personalAdministrativoRepository.searchPersonal(search, cargo, area, estado);
    }

    public Optional<Docente> buscarDocentePorId(UUID id) {
        return docenteRepository.findById(id);
    }

    public Optional<PersonalAdministrativo> buscarPersonalPorId(UUID id) {
        return personalAdministrativoRepository.findById(id);
    }

    public Optional<Docente> buscarDocentePorDocumento(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) return Optional.empty();
        return docenteRepository.findByNumeroDocumento(numeroDocumento.trim());
    }

    public Optional<PersonalAdministrativo> buscarPersonalPorDocumento(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) return Optional.empty();
        return personalAdministrativoRepository.findByNumeroDocumento(numeroDocumento.trim());
    }

    @Transactional
    public Docente guardarDocente(Docente docente) {
        boolean esNuevo = (docente.getId() == null || !docenteRepository.existsById(docente.getId()));
        Docente guardado = docenteRepository.save(docente);
        if (auditoriaService != null && guardado != null) {
            auditoriaService.registrarCambio("DOCENTE", guardado.getId(),
                    esNuevo ? "creacion" : "actualizacion",
                    null,
                    guardado.getNombres() + " " + guardado.getApellidos() + " (" + guardado.getEstado() + ")");
        }
        return guardado;
    }

    @Transactional
    public PersonalAdministrativo guardarPersonal(PersonalAdministrativo personal) {
        boolean esNuevo = (personal.getId() == null || !personalAdministrativoRepository.existsById(personal.getId()));
        PersonalAdministrativo guardado = personalAdministrativoRepository.save(personal);
        if (auditoriaService != null && guardado != null) {
            auditoriaService.registrarCambio("PERSONAL_ADMINISTRATIVO", guardado.getId(),
                    esNuevo ? "creacion" : "actualizacion",
                    null,
                    guardado.getNombres() + " " + guardado.getApellidos() + " (" + guardado.getCargo() + ")");
        }
        return guardado;
    }

    public String generarEmailSugerido(String nombres, String apellidos) {
        String primerNombre = (nombres != null && !nombres.isBlank()) ? nombres.trim().split("\\s+")[0] : "personal";
        String primerApellido = (apellidos != null && !apellidos.isBlank()) ? apellidos.trim().split("\\s+")[0] : "ieaci";

        String base = slugify(primerNombre) + "." + slugify(primerApellido);
        String domain = "@ieaci.edu.co";
        String candidate = base + domain;

        int counter = 1;
        while (usuarioRepository.existsByEmail(candidate)) {
            candidate = base + counter + domain;
            counter++;
        }

        return candidate;
    }

    @Transactional
    public Usuario crearOCambiarCuentaAcceso(String numeroDocumento, String nombres, String apellidos, String email, String password, String rol) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            throw new IllegalArgumentException("Número de documento es obligatorio para crear usuario.");
        }

        String doc = numeroDocumento.trim();
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNumeroDocumento(doc);

        String encodedPassword = (password != null && !password.isBlank())
                ? (password.startsWith("$2a$") || password.startsWith("$2b$") ? password : passwordEncoder.encode(password))
                : null;

        Usuario usuario;
        if (usuarioOpt.isPresent()) {
            usuario = usuarioOpt.get();
            if (email != null && !email.isBlank()) usuario.setEmail(email.trim());
            if (encodedPassword != null) usuario.setPassword(encodedPassword);
            if (rol != null && !rol.isBlank()) usuario.setRol(rol.trim());
        } else {
            String targetEmail = (email != null && !email.isBlank())
                    ? email.trim()
                    : generarEmailSugerido(nombres, apellidos);

            usuario = new Usuario(targetEmail, encodedPassword, rol != null ? rol : "DOCENTE", doc);
        }

        Usuario guardado = usuarioRepository.save(usuario);
        if (auditoriaService != null && guardado != null) {
            auditoriaService.registrarCambio("USUARIO", guardado.getId(),
                    usuarioOpt.isPresent() ? "actualizacion_credenciales" : "creacion_cuenta",
                    usuarioOpt.isPresent() ? usuarioOpt.get().getRol() : null,
                    guardado.getEmail() + " (" + guardado.getRol() + ")");
        }
        return guardado;
    }

    public Optional<Usuario> obtenerUsuarioAcceso(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) return Optional.empty();
        return usuarioRepository.findByNumeroDocumento(numeroDocumento);
    }

    @Transactional
    public String toggleEstadoPersonal(UUID id) {
        Optional<Docente> docenteOpt = docenteRepository.findById(id);
        if (docenteOpt.isPresent()) {
            Docente docente = docenteOpt.get();
            String nuevoEstado = "Activo".equalsIgnoreCase(docente.getEstado()) ? "Inactivo" : "Activo";
            docente.setEstado(nuevoEstado);
            docenteRepository.save(docente);
            sincronizarEstadoUsuario(docente.getNumeroDocumento(), nuevoEstado);
            if (auditoriaService != null) {
                auditoriaService.registrarCambio("DOCENTE", docente.getId(), "estado", null,
                        docente.getNombreCompleto() + " -> " + nuevoEstado);
            }
            return nuevoEstado;
        }

        PersonalAdministrativo personal = personalAdministrativoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Miembro de personal no encontrado."));
        String nuevoEstado = "Activo".equalsIgnoreCase(personal.getEstado()) ? "Inactivo" : "Activo";
        personal.setEstado(nuevoEstado);
        personalAdministrativoRepository.save(personal);
        sincronizarEstadoUsuario(personal.getNumeroDocumento(), nuevoEstado);
        if (auditoriaService != null) {
            auditoriaService.registrarCambio("PERSONAL_ADMINISTRATIVO", personal.getId(), "estado", null,
                    personal.getNombreCompleto() + " -> " + nuevoEstado);
        }
        return nuevoEstado;
    }

    @Transactional
    public void eliminarPersonal(UUID id, String currentEmail) {
        Optional<Docente> docenteOpt = docenteRepository.findById(id);
        if (docenteOpt.isPresent()) {
            Docente docente = docenteOpt.get();
            validarNoAutoEliminacion(docente.getNumeroDocumento(), currentEmail);

            if (entityManager != null) {
                entityManager.createQuery("UPDATE Curso c SET c.director = null WHERE c.director.id = :id")
                        .setParameter("id", id).executeUpdate();
                entityManager.createQuery("UPDATE CursoMateria cm SET cm.docente = null WHERE cm.docente.id = :id")
                        .setParameter("id", id).executeUpdate();
                entityManager.createQuery("UPDATE Horario h SET h.docente = null WHERE h.docente.id = :id")
                        .setParameter("id", id).executeUpdate();
                entityManager.createQuery("UPDATE SesionClase s SET s.docente = null WHERE s.docente.id = :id")
                        .setParameter("id", id).executeUpdate();
                entityManager.createQuery("UPDATE Documento d SET d.docente = null WHERE d.docente.id = :id")
                        .setParameter("id", id).executeUpdate();
                entityManager.createQuery("UPDATE Reporte r SET r.docente = null WHERE r.docente.id = :id")
                        .setParameter("id", id).executeUpdate();
            }

            if (docente.getAsignaturas() != null) {
                docente.getAsignaturas().clear();
                docenteRepository.saveAndFlush(docente);
            }

            String nombre = docente.getNombreCompleto();
            String numDoc = docente.getNumeroDocumento();
            docenteRepository.delete(docente);
            eliminarCuentaUsuarioSiExiste(numDoc);

            if (auditoriaService != null) {
                auditoriaService.registrarCambio("DOCENTE", id, "eliminacion", nombre, "ELIMINADO");
            }
            return;
        }

        PersonalAdministrativo personal = personalAdministrativoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Miembro de personal no encontrado."));
        validarNoAutoEliminacion(personal.getNumeroDocumento(), currentEmail);

        if (entityManager != null) {
            entityManager.createQuery("UPDATE Documento d SET d.personal = null WHERE d.personal.id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createQuery("UPDATE Reporte r SET r.atendidoPor = null WHERE r.atendidoPor.id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createQuery("UPDATE SolicitudCertificado s SET s.respondidoPor = null WHERE s.respondidoPor.id = :id")
                    .setParameter("id", id).executeUpdate();
        }

        String nombre = personal.getNombreCompleto();
        String numDoc = personal.getNumeroDocumento();
        personalAdministrativoRepository.delete(personal);
        eliminarCuentaUsuarioSiExiste(numDoc);

        if (auditoriaService != null) {
            auditoriaService.registrarCambio("PERSONAL_ADMINISTRATIVO", id, "eliminacion", nombre, "ELIMINADO");
        }
    }

    private void sincronizarEstadoUsuario(String numeroDocumento, String nuevoEstado) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            return;
        }
        usuarioRepository.findByNumeroDocumento(numeroDocumento.trim()).ifPresent(usuario -> {
            usuario.setEstado(nuevoEstado);
            usuarioRepository.save(usuario);
        });
    }

    private void validarNoAutoEliminacion(String numeroDocumento, String currentEmail) {
        if (numeroDocumento == null || numeroDocumento.isBlank() || currentEmail == null || currentEmail.isBlank()) {
            return;
        }
        usuarioRepository.findByNumeroDocumento(numeroDocumento.trim()).ifPresent(usuario -> {
            if (usuario.getEmail() != null && usuario.getEmail().equalsIgnoreCase(currentEmail.trim())) {
                throw new IllegalStateException("No puedes eliminar tu propia cuenta activa.");
            }
        });
    }

    private void eliminarCuentaUsuarioSiExiste(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            return;
        }
        usuarioRepository.findByNumeroDocumento(numeroDocumento.trim()).ifPresent(usuario -> {
            if (entityManager != null) {
                entityManager.createQuery("UPDATE Reporte r SET r.reportanteUsuario = null WHERE r.reportanteUsuario.id = :uid")
                        .setParameter("uid", usuario.getId()).executeUpdate();
            }
            usuarioRepository.delete(usuario);
        });
    }

    private String slugify(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                         .replaceAll("[^a-z0-9]", "");
    }
}

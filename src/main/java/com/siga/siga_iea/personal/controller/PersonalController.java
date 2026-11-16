package com.siga.siga_iea.personal.controller;

import com.siga.siga_iea.clases.entity.Materia;
import com.siga.siga_iea.clases.repository.MateriaRepository;
import com.siga.siga_iea.storage.StorageFolder;
import com.siga.siga_iea.storage.StorageService;
import com.siga.siga_iea.storage.dto.UploadResult;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.service.PersonalService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class PersonalController {

    private final PersonalService personalService;
    private final StorageService storageService;
    private final MateriaRepository materiaRepository;

    public PersonalController(PersonalService personalService,
                              StorageService storageService,
                              MateriaRepository materiaRepository) {
        this.personalService = personalService;
        this.storageService = storageService;
        this.materiaRepository = materiaRepository;
    }

    @GetMapping("/personal")
    @Transactional(readOnly = true)
    public String index(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "cargo", required = false) String cargo,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "tipo", required = false) String tipo,
            Model model) {

        model.addAttribute("title", "Gestión de Personal – IEACI");
        model.addAttribute("activePage", "personal");
        model.addAttribute("search", search);
        model.addAttribute("cargo", cargo);
        model.addAttribute("area", area);
        model.addAttribute("estado", estado);
        model.addAttribute("tipo", tipo);
        model.addAttribute("materiasList", materiaRepository.findByEstadoOrderByNombreAsc("Activo"));

        List<Map<String, Object>> personalList = new ArrayList<>();

        // Fetch Docentes
        if (tipo == null || tipo.isBlank() || "todos".equalsIgnoreCase(tipo) || "DOCENTE".equalsIgnoreCase(tipo)) {
            List<Docente> docentes = personalService.buscarDocentes(search, estado);
            for (Docente d : docentes) {
                List<String> asignaturasNombres = resolverNombresAsignaturas(d);
                String resumenAsignaturas = asignaturasNombres.isEmpty() ? "Docente" : String.join(", ", asignaturasNombres);

                if (cargo != null && !cargo.isBlank()
                        && !"Docente".equalsIgnoreCase(cargo)
                        && !resumenAsignaturas.toLowerCase().contains(cargo.toLowerCase())) {
                    continue;
                }
                if (area != null && !area.isBlank() && !"Académica".equalsIgnoreCase(area)) continue;

                Map<String, Object> map = new HashMap<>();
                map.put("id", d.getId().toString());
                map.put("tipo", "DOCENTE");
                map.put("tipoPersona", "docente");
                map.put("nombres", d.getNombres() != null ? d.getNombres() : "Docente");
                map.put("apellidos", d.getApellidos() != null ? d.getApellidos() : "");
                map.put("nombreCompleto", d.getNombreCompleto());
                map.put("asignaturasList", asignaturasNombres);
                map.put("cargo", resumenAsignaturas);
                map.put("area", "Académica");
                map.put("estado", d.getEstado() != null ? d.getEstado() : "Activo");
                map.put("tipoDocumento", d.getTipoDocumento() != null ? d.getTipoDocumento() : "CC");
                map.put("numeroDocumento", d.getNumeroDocumento());
                map.put("telefono", d.getTelefono() != null ? d.getTelefono() : "Sin teléfono");
                map.put("foto", d.getFotoKey() != null ? "/storage/public/view?key=" + d.getFotoKey() : null);

                Optional<Usuario> usrOpt = personalService.obtenerUsuarioAcceso(d.getNumeroDocumento());
                map.put("tieneCuenta", usrOpt.isPresent());
                map.put("email", usrOpt.map(Usuario::getEmail).orElse(null));
                map.put("correo", usrOpt.map(Usuario::getEmail).orElse(null));
                map.put("usuarioEstado", usrOpt.map(Usuario::getEstado).orElse(null));

                personalList.add(map);
            }
        }

        // Fetch PersonalAdministrativo
        if (tipo == null || tipo.isBlank() || "todos".equalsIgnoreCase(tipo) || "ADMINISTRATIVO".equalsIgnoreCase(tipo)) {
            List<PersonalAdministrativo> personalAdmin = personalService.buscarPersonal(search, cargo, area, estado);
            for (PersonalAdministrativo p : personalAdmin) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId().toString());
                map.put("tipo", "ADMINISTRATIVO");
                map.put("tipoPersona", "personal");
                map.put("nombres", p.getNombres() != null ? p.getNombres() : "Personal");
                map.put("apellidos", p.getApellidos() != null ? p.getApellidos() : "");
                map.put("nombreCompleto", p.getNombreCompleto());
                map.put("asignaturasList", List.of());
                map.put("cargo", p.getCargo() != null ? p.getCargo() : "Administrativo");
                map.put("area", p.getArea() != null ? p.getArea() : "Administrativa");
                map.put("estado", p.getEstado() != null ? p.getEstado() : "Activo");
                map.put("tipoDocumento", p.getTipoDocumento() != null ? p.getTipoDocumento() : "CC");
                map.put("numeroDocumento", p.getNumeroDocumento());
                map.put("telefono", p.getTelefono() != null ? p.getTelefono() : "Sin teléfono");
                map.put("foto", p.getFotoKey() != null ? "/storage/public/view?key=" + p.getFotoKey() : null);

                Optional<Usuario> usrOpt = personalService.obtenerUsuarioAcceso(p.getNumeroDocumento());
                map.put("tieneCuenta", usrOpt.isPresent());
                map.put("email", usrOpt.map(Usuario::getEmail).orElse(null));
                map.put("correo", usrOpt.map(Usuario::getEmail).orElse(null));
                map.put("usuarioEstado", usrOpt.map(Usuario::getEstado).orElse(null));

                personalList.add(map);
            }
        }

        model.addAttribute("personalList", personalList);
        return "personal/index";
    }

    @PostMapping({"/personal", "/personal/registrar"})
    public String registrarPersonal(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "nombres", required = false) String nombres,
            @RequestParam(value = "apellidos", required = false) String apellidos,
            @RequestParam(value = "tipoDocumento", required = false) String tipoDocumento,
            @RequestParam(value = "numeroDocumento", required = false) String numeroDocumento,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "telefono", required = false) String telefono,
            @RequestParam(value = "direccion", required = false) String direccion,
            @RequestParam(value = "cargo", required = false) String cargo,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "estudios", required = false) String estudios,
            @RequestParam(value = "profesion", required = false) String profesion,
            @RequestParam(value = "especialidad", required = false) String especialidad,
            @RequestParam(value = "materiaIds", required = false) List<UUID> materiaIds,
            @RequestParam(value = "sexo", required = false) String sexo,
            @RequestParam(value = "fotoFile", required = false) MultipartFile fotoFile,
            @RequestParam(value = "createUser", required = false, defaultValue = "false") boolean createUser,
            @RequestParam(value = "userEmail", required = false) String userEmail,
            @RequestParam(value = "passwordTemp", required = false) String passwordTemp,
            @RequestParam(value = "role", required = false) String role,

            @RequestParam(value = "primerNombre", required = false) String primerNombre,
            @RequestParam(value = "segundoNombre", required = false) String segundoNombre,
            @RequestParam(value = "primerApellido", required = false) String primerApellido,
            @RequestParam(value = "segundoApellido", required = false) String segundoApellido,
            @RequestParam(value = "tipoDoc", required = false) String tipoDoc,
            @RequestParam(value = "numDoc", required = false) String numDoc,
            RedirectAttributes redirectAttributes) {

        try {
            String nombresFull = (nombres != null && !nombres.isBlank()) ? nombres.trim() :
                    ((primerNombre != null ? primerNombre : "") + (segundoNombre != null && !segundoNombre.isBlank() ? " " + segundoNombre : "")).trim();

            String apellidosFull = (apellidos != null && !apellidos.isBlank()) ? apellidos.trim() :
                    ((primerApellido != null ? primerApellido : "") + (segundoApellido != null && !segundoApellido.isBlank() ? " " + segundoApellido : "")).trim();

            String docType = (tipoDocumento != null && !tipoDocumento.isBlank()) ? tipoDocumento.trim() : (tipoDoc != null ? tipoDoc.trim() : "CC");
            String docNum = (numeroDocumento != null && !numeroDocumento.isBlank()) ? numeroDocumento.trim() : (numDoc != null ? numDoc.trim() : "");

            if (nombresFull.isBlank() || apellidosFull.isBlank() || docNum.isBlank()) {
                throw new IllegalArgumentException("Nombres, apellidos y número de documento son obligatorios.");
            }

            if (personalService.buscarDocentePorDocumento(docNum).isPresent()
                    || personalService.buscarPersonalPorDocumento(docNum).isPresent()) {
                throw new IllegalArgumentException("Ya existe un miembro del personal registrado con el documento " + docNum + ".");
            }

            String uploadedFotoKey = null;
            if (fotoFile != null && !fotoFile.isEmpty()) {
                storageService.validateSize(fotoFile, 10 * 1024 * 1024);
                storageService.validateExtension(fotoFile, "jpg", "jpeg", "png");
                UploadResult result = storageService.upload(fotoFile, StorageFolder.DOCENTES, docNum);
                uploadedFotoKey = result.getKey();
            }

            boolean esDocente = "DOCENTE".equalsIgnoreCase(tipo) || "Docente".equalsIgnoreCase(cargo);

            if (esDocente) {
                Docente d = new Docente();
                d.setNombres(nombresFull);
                d.setApellidos(apellidosFull);
                d.setTipoDocumento(docType);
                d.setNumeroDocumento(docNum);
                d.setGenero(sexo);
                d.setTelefono(telefono != null ? telefono.trim() : null);
                d.setDireccion(direccion);
                String tituloVal = (estudios != null && !estudios.isBlank()) ? estudios.trim()
                        : (profesion != null && !profesion.isBlank() ? profesion.trim() : null);
                d.setTitulo(tituloVal);
                asignarMateriasADocente(d, materiaIds, especialidad, true);
                d.setFotoKey(uploadedFotoKey);
                d.setEstado("Activo");
                personalService.guardarDocente(d);
            } else {
                PersonalAdministrativo p = new PersonalAdministrativo();
                p.setNombres(nombresFull);
                p.setApellidos(apellidosFull);
                p.setTipoDocumento(docType);
                p.setNumeroDocumento(docNum);
                p.setCargo((cargo != null && !cargo.isBlank()) ? cargo.trim() : ("DIRECTIVO".equalsIgnoreCase(tipo) ? "Directivo" : "Administrativo"));
                p.setArea((area != null && !area.isBlank()) ? area.trim() : ("DIRECTIVO".equalsIgnoreCase(tipo) ? "Directiva" : "Administrativa"));
                p.setGenero(sexo);
                p.setTelefono(telefono != null ? telefono.trim() : null);
                p.setDireccion(direccion);
                p.setFotoKey(uploadedFotoKey);
                p.setEstado("Activo");
                personalService.guardarPersonal(p);
            }

            String targetEmail = (email != null && !email.isBlank()) ? email.trim() : userEmail;
            if (createUser || (targetEmail != null && !targetEmail.isBlank())) {
                String pass = (passwordTemp != null && !passwordTemp.isBlank()) ? passwordTemp : "IEACI" + java.time.Year.now().getValue() + "*";
                String targetRole = (role != null && !role.isBlank()) ? role : (esDocente ? "DOCENTE" : "PERSONAL_ADMINISTRATIVO");
                personalService.crearOCambiarCuentaAcceso(docNum, nombresFull, apellidosFull, targetEmail, pass, targetRole);
            }

            redirectAttributes.addFlashAttribute("mensajeExito",
                    esDocente
                            ? "Docente registrado exitosamente. Puede activar su cuenta institucional desde su ficha."
                            : "Personal registrado exitosamente. Puede activar su cuenta institucional desde su ficha.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al registrar personal: " + ex.getMessage());
        }

        return "redirect:/personal";
    }

    @GetMapping("/personal/perfil/{id}")
    @Transactional(readOnly = true)
    public String perfil(
            @PathVariable String id,
            @RequestParam(value = "tab", required = false) String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        model.addAttribute("title", "Perfil de Personal – IEACI");
        model.addAttribute("activePage", "personal");

        Optional<Docente> docenteOpt = Optional.empty();
        Optional<PersonalAdministrativo> adminOpt = Optional.empty();

        try {
            UUID uuid = UUID.fromString(id);
            docenteOpt = personalService.buscarDocentePorId(uuid);
            if (docenteOpt.isEmpty()) {
                adminOpt = personalService.buscarPersonalPorId(uuid);
            }
        } catch (IllegalArgumentException ignored) {}

        Map<String, Object> staff = new HashMap<>();

        if (docenteOpt.isPresent()) {
            Docente d = docenteOpt.get();
            List<String> asignaturasNombres = resolverNombresAsignaturas(d);
            List<String> asignaturasIds = d.getAsignaturas() != null
                    ? d.getAsignaturas().stream().map(m -> m.getId().toString()).toList()
                    : List.of();

            staff.put("id", d.getId().toString());
            staff.put("tipoPersona", "docente");
            staff.put("primerNombre", d.getNombres());
            staff.put("primerApellido", d.getApellidos());
            staff.put("nombreCompleto", d.getNombreCompleto());
            staff.put("cargo", "Docente");
            staff.put("area", "Académica");
            staff.put("tipoDoc", d.getTipoDocumento() != null ? d.getTipoDocumento() : "CC");
            staff.put("numDoc", d.getNumeroDocumento() != null ? d.getNumeroDocumento() : "Sin documento");
            staff.put("numeroDocumento", d.getNumeroDocumento());
            staff.put("telefono", d.getTelefono() != null ? d.getTelefono() : "N/A");
            staff.put("direccion", d.getDireccion() != null ? d.getDireccion() : "N/A");
            staff.put("profesion", (d.getTitulo() != null && !d.getTitulo().isBlank()) ? d.getTitulo() : null);
            staff.put("asignaturasList", asignaturasNombres);
            staff.put("asignaturasIds", asignaturasIds);
            staff.put("especialidad", asignaturasNombres.isEmpty() ? null : String.join(", ", asignaturasNombres));
            staff.put("estado", d.getEstado() != null ? d.getEstado() : "Activo");
            staff.put("foto", d.getFotoKey() != null ? "/storage/public/view?key=" + d.getFotoKey() : null);

            Optional<Usuario> usrOpt = personalService.obtenerUsuarioAcceso(d.getNumeroDocumento());
            if (usrOpt.isPresent()) {
                Usuario u = usrOpt.get();
                staff.put("tieneCuenta", true);
                staff.put("correo", u.getEmail());
                staff.put("usuarioRol", u.getRol() != null ? u.getRol() : "DOCENTE");
                staff.put("usuarioEstado", u.getEstado() != null ? u.getEstado() : "Activo");
                staff.put("emailSugerido", u.getEmail());
            } else {
                staff.put("tieneCuenta", false);
                staff.put("correo", "Sin cuenta creada");
                staff.put("usuarioRol", "DOCENTE");
                staff.put("usuarioEstado", "Inactivo");
                staff.put("emailSugerido", personalService.generarEmailSugerido(d.getNombres(), d.getApellidos()));
            }
        } else if (adminOpt.isPresent()) {
            PersonalAdministrativo p = adminOpt.get();

            staff.put("id", p.getId().toString());
            staff.put("tipoPersona", "personal");
            staff.put("primerNombre", p.getNombres());
            staff.put("primerApellido", p.getApellidos());
            staff.put("nombreCompleto", p.getNombreCompleto());
            staff.put("cargo", p.getCargo() != null ? p.getCargo() : "Administrativo");
            staff.put("area", p.getArea() != null ? p.getArea() : "Administrativa");
            staff.put("tipoDoc", p.getTipoDocumento() != null ? p.getTipoDocumento() : "CC");
            staff.put("numDoc", p.getNumeroDocumento() != null ? p.getNumeroDocumento() : "Sin documento");
            staff.put("numeroDocumento", p.getNumeroDocumento());
            staff.put("telefono", p.getTelefono() != null ? p.getTelefono() : "N/A");
            staff.put("direccion", p.getDireccion() != null ? p.getDireccion() : "N/A");
            staff.put("profesion", null);
            staff.put("asignaturasList", List.of());
            staff.put("asignaturasIds", List.of());
            staff.put("especialidad", null);
            staff.put("estado", p.getEstado() != null ? p.getEstado() : "Activo");
            staff.put("foto", p.getFotoKey() != null ? "/storage/public/view?key=" + p.getFotoKey() : null);

            Optional<Usuario> usrOpt = personalService.obtenerUsuarioAcceso(p.getNumeroDocumento());
            if (usrOpt.isPresent()) {
                Usuario u = usrOpt.get();
                staff.put("tieneCuenta", true);
                staff.put("correo", u.getEmail());
                staff.put("usuarioRol", u.getRol() != null ? u.getRol() : "PERSONAL_ADMINISTRATIVO");
                staff.put("usuarioEstado", u.getEstado() != null ? u.getEstado() : "Activo");
                staff.put("emailSugerido", u.getEmail());
            } else {
                staff.put("tieneCuenta", false);
                staff.put("correo", "Sin cuenta creada");
                staff.put("usuarioRol", "PERSONAL_ADMINISTRATIVO");
                staff.put("usuarioEstado", "Inactivo");
                staff.put("emailSugerido", personalService.generarEmailSugerido(p.getNombres(), p.getApellidos()));
            }
        } else {
            redirectAttributes.addFlashAttribute("mensajeError", "Personal no encontrado.");
            return "redirect:/personal";
        }

        String resolvedTab = "cuenta".equalsIgnoreCase(tab) ? "cuenta"
                : ("laboral".equalsIgnoreCase(tab) ? "laboral" : "informacion");
        model.addAttribute("staff", staff);
        model.addAttribute("materiasList", materiaRepository.findByEstadoOrderByNombreAsc("Activo"));
        model.addAttribute("activeTab", resolvedTab);
        return "personal/perfil";
    }

    @PostMapping("/personal/{id}/asignaturas")
    @Transactional
    public String actualizarAsignaturasDocente(
            @PathVariable String id,
            @RequestParam(value = "materiaIds", required = false) List<UUID> materiaIds,
            RedirectAttributes redirectAttributes) {
        try {
            UUID uuid = UUID.fromString(id);
            Docente docente = personalService.buscarDocentePorId(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Docente no encontrado."));

            asignarMateriasADocente(docente, materiaIds, null, false);
            personalService.guardarDocente(docente);

            redirectAttributes.addFlashAttribute("mensajeExito", "Asignaturas del docente actualizadas correctamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al actualizar asignaturas: " + ex.getMessage());
        }
        return "redirect:/personal/perfil/" + id + "?tab=laboral";
    }

    @PostMapping("/personal/{id}/crear-cuenta")
    public String crearCuentaPersonal(
            @PathVariable String id,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "rol", required = false) String rol,
            RedirectAttributes redirectAttributes) {

        try {
            UUID uuid = UUID.fromString(id);
            String numDoc = null;
            String nombres = "";
            String apellidos = "";

            Optional<Docente> docOpt = personalService.buscarDocentePorId(uuid);
            if (docOpt.isPresent()) {
                numDoc = docOpt.get().getNumeroDocumento();
                nombres = docOpt.get().getNombres();
                apellidos = docOpt.get().getApellidos();
                if (rol == null || rol.isBlank()) rol = "DOCENTE";
            } else {
                PersonalAdministrativo p = personalService.buscarPersonalPorId(uuid)
                        .orElseThrow(() -> new IllegalArgumentException("Personal no encontrado"));
                numDoc = p.getNumeroDocumento();
                nombres = p.getNombres();
                apellidos = p.getApellidos();
                if (rol == null || rol.isBlank()) rol = "ADMIN";
            }

            String pass = (password != null && !password.isBlank()) ? password : "IEACI" + java.time.Year.now().getValue() + "*";
            Usuario u = personalService.crearOCambiarCuentaAcceso(numDoc, nombres, apellidos, email, pass, rol);

            redirectAttributes.addFlashAttribute("mensajeExito", "Cuenta de usuario generada: " + u.getEmail());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error al crear cuenta: " + ex.getMessage());
        }

        return "redirect:/personal/perfil/" + id + "?tab=cuenta";
    }

    private void asignarMateriasADocente(Docente docente, List<UUID> materiaIds, String especialidadFallback, boolean permitirFallbackTexto) {
        if (materiaIds != null && !materiaIds.isEmpty()) {
            List<Materia> seleccionadas = materiaRepository.findAllById(materiaIds).stream()
                    .sorted(Comparator.comparing(Materia::getNombre, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (seleccionadas.isEmpty()) {
                throw new IllegalArgumentException("Las asignaturas seleccionadas no son válidas.");
            }
            docente.setAsignaturas(new LinkedHashSet<>(seleccionadas));
            docente.setEspecialidad(seleccionadas.stream()
                    .map(Materia::getNombre)
                    .collect(Collectors.joining(", ")));
            return;
        }

        if (permitirFallbackTexto && especialidadFallback != null && !especialidadFallback.isBlank()) {
            docente.setEspecialidad(especialidadFallback.trim());
            return;
        }

        if (!materiaRepository.findByEstadoOrderByNombreAsc("Activo").isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una asignatura para el docente.");
        }

        docente.getAsignaturas().clear();
        docente.setEspecialidad(null);
    }

    private List<String> resolverNombresAsignaturas(Docente docente) {
        if (docente.getAsignaturas() != null && !docente.getAsignaturas().isEmpty()) {
            return docente.getAsignaturas().stream()
                    .map(Materia::getNombre)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (docente.getEspecialidad() != null && !docente.getEspecialidad().isBlank()) {
            return Arrays.stream(docente.getEspecialidad().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }
}

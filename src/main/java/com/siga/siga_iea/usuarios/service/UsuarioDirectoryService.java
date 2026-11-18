package com.siga.siga_iea.usuarios.service;

import com.siga.siga_iea.usuarios.dto.UsuarioDTO;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.entity.PersonalAdministrativo;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import com.siga.siga_iea.usuarios.repository.PersonalAdministrativoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioDirectoryService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    public record ResumenUsuarios(
            List<UsuarioDTO> usuarios,
            int totalUsuarios,
            long activosCount,
            long bloqueadosCount
    ) {}

    private final UsuarioService usuarioService;
    private final DocenteRepository docenteRepository;
    private final PersonalAdministrativoRepository personalAdministrativoRepository;
    private final EstudianteRepository estudianteRepository;

    public UsuarioDirectoryService(UsuarioService usuarioService,
                                   DocenteRepository docenteRepository,
                                   PersonalAdministrativoRepository personalAdministrativoRepository,
                                   EstudianteRepository estudianteRepository) {
        this.usuarioService = usuarioService;
        this.docenteRepository = docenteRepository;
        this.personalAdministrativoRepository = personalAdministrativoRepository;
        this.estudianteRepository = estudianteRepository;
    }

    @Transactional(readOnly = true)
    public ResumenUsuarios consultarResumen(String search, String estado, String rol) {
        List<Usuario> todos = usuarioService.listarTodos();
        List<UsuarioDTO> dtoList = new ArrayList<>();

        long activosCount = 0;
        long bloqueadosCount = 0;

        for (Usuario u : todos) {
            String est = (u.getEstado() != null && !u.getEstado().isBlank()) ? u.getEstado() : "Activo";
            if ("Activo".equalsIgnoreCase(est)) {
                activosCount++;
            } else {
                bloqueadosCount++;
            }

            if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase(est)) {
                continue;
            }
            if (rol != null && !rol.isBlank() && !rol.equalsIgnoreCase(u.getRol())) {
                continue;
            }

            String persona = resolverNombrePersona(u.getNumeroDocumento());
            if (search != null && !search.isBlank()) {
                String s = search.toLowerCase().trim();
                boolean matchesEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(s);
                boolean matchesPersona = persona != null && persona.toLowerCase().contains(s);
                boolean matchesDoc = u.getNumeroDocumento() != null && u.getNumeroDocumento().toLowerCase().contains(s);
                if (!matchesEmail && !matchesPersona && !matchesDoc) {
                    continue;
                }
            }

            String ultimoAccesoStr = u.getUltimoAcceso() != null
                    ? u.getUltimoAcceso().format(FORMATTER)
                    : "Sin registros";

            dtoList.add(new UsuarioDTO(
                    u.getId(),
                    u.getEmail(),
                    persona,
                    u.getRol(),
                    ultimoAccesoStr,
                    est,
                    u.getNumeroDocumento()
            ));
        }

        return new ResumenUsuarios(dtoList, todos.size(), activosCount, bloqueadosCount);
    }

    private String resolverNombrePersona(String doc) {
        if (doc == null || doc.isBlank()) {
            return "Cuenta de Sistema";
        }
        Optional<Docente> docOpt = docenteRepository.findByNumeroDocumento(doc);
        if (docOpt.isPresent()) {
            return docOpt.get().getNombres() + " " + docOpt.get().getApellidos();
        }
        Optional<PersonalAdministrativo> paOpt = personalAdministrativoRepository.findByNumeroDocumento(doc);
        if (paOpt.isPresent()) {
            return paOpt.get().getNombres() + " " + paOpt.get().getApellidos();
        }
        Optional<Estudiante> estOpt = estudianteRepository.findByNumeroDocumento(doc);
        if (estOpt.isPresent()) {
            return estOpt.get().getNombres() + " " + estOpt.get().getApellidos();
        }
        return "Funcionario (" + doc + ")";
    }
}


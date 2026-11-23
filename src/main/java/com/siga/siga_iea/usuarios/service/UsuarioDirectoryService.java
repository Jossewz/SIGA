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
import java.util.*;

@Service
public class UsuarioDirectoryService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    public record ResumenUsuarios(
            List<UsuarioDTO> usuarios,
            int totalUsuarios,
            long activosCount,
            long bloqueadosCount,
            int totalFiltrados,
            int paginaActual,
            int totalPaginas,
            int tamanoPagina
    ) {
        public ResumenUsuarios(List<UsuarioDTO> usuarios, int totalUsuarios, long activosCount, long bloqueadosCount) {
            this(usuarios, totalUsuarios, activosCount, bloqueadosCount, usuarios.size(), 0, 1, Math.max(1, usuarios.size()));
        }
    }

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
        return consultarResumen(search, estado, rol, 0, Integer.MAX_VALUE);
    }

    @Transactional(readOnly = true)
    public ResumenUsuarios consultarResumen(String search, String estado, String rol, int page, int size) {
        List<Usuario> todos = usuarioService.listarTodos();
        Map<String, String> nombresPorDoc = cargarMapaNombresPorDocumento();
        List<UsuarioDTO> filtrados = new ArrayList<>();

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

            String doc = u.getNumeroDocumento();
            String persona = (doc == null || doc.isBlank())
                    ? "Cuenta de Sistema"
                    : nombresPorDoc.getOrDefault(doc.trim(), "Funcionario (" + doc + ")");

            if (search != null && !search.isBlank()) {
                String s = search.toLowerCase().trim();
                boolean matchesEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(s);
                boolean matchesPersona = persona.toLowerCase().contains(s);
                boolean matchesDoc = doc != null && doc.toLowerCase().contains(s);
                if (!matchesEmail && !matchesPersona && !matchesDoc) {
                    continue;
                }
            }

            String ultimoAccesoStr = u.getUltimoAcceso() != null
                    ? u.getUltimoAcceso().format(FORMATTER)
                    : "Sin registros";

            filtrados.add(new UsuarioDTO(
                    u.getId(),
                    u.getEmail(),
                    persona,
                    u.getRol(),
                    ultimoAccesoStr,
                    est,
                    doc
            ));
        }

        int totalFiltrados = filtrados.size();
        int safeSize = Math.max(1, size);
        int totalPaginas = Math.max(1, (int) Math.ceil((double) totalFiltrados / safeSize));
        int safePage = Math.max(0, Math.min(page, totalPaginas - 1));

        List<UsuarioDTO> paginaSlice;
        if (safeSize >= totalFiltrados) {
            paginaSlice = filtrados;
        } else {
            int from = safePage * safeSize;
            int to = Math.min(from + safeSize, totalFiltrados);
            paginaSlice = filtrados.subList(from, to);
        }

        return new ResumenUsuarios(
                paginaSlice,
                todos.size(),
                activosCount,
                bloqueadosCount,
                totalFiltrados,
                safePage,
                totalPaginas,
                safeSize
        );
    }

    private Map<String, String> cargarMapaNombresPorDocumento() {
        Map<String, String> mapa = new HashMap<>();
        for (Estudiante e : estudianteRepository.findAll()) {
            if (e.getNumeroDocumento() != null && !e.getNumeroDocumento().isBlank()) {
                mapa.put(e.getNumeroDocumento().trim(), e.getNombres() + " " + e.getApellidos());
            }
        }
        for (PersonalAdministrativo pa : personalAdministrativoRepository.findAll()) {
            if (pa.getNumeroDocumento() != null && !pa.getNumeroDocumento().isBlank()) {
                mapa.put(pa.getNumeroDocumento().trim(), pa.getNombres() + " " + pa.getApellidos());
            }
        }
        for (Docente d : docenteRepository.findAll()) {
            if (d.getNumeroDocumento() != null && !d.getNumeroDocumento().isBlank()) {
                mapa.put(d.getNumeroDocumento().trim(), d.getNombres() + " " + d.getApellidos());
            }
        }
        return mapa;
    }
}

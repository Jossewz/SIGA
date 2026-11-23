package com.siga.siga_iea.clases.service;

import com.siga.siga_iea.clases.dto.HorarioDto;
import com.siga.siga_iea.clases.entity.*;
import com.siga.siga_iea.clases.repository.*;
import com.siga.siga_iea.clases.validation.HorarioValidator;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.repository.MatriculaRepository;

import java.time.LocalTime;
import java.util.*;

import com.siga.siga_iea.calificaciones.entity.Calificacion;
import com.siga.siga_iea.calificaciones.repository.CalificacionesRepository;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final MateriaRepository materiaRepository;
    private final CursoMateriaRepository cursoMateriaRepository;
    private final CursoEstudianteRepository cursoEstudianteRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaRepository matriculaRepository;
    private final CalificacionesRepository calificacionesRepository;
    private final SalonRepository salonRepository;
    private final BloqueRepository bloqueRepository;
    private final HorarioValidator horarioValidator;
    private final com.siga.siga_iea.configuracion.repository.AnioLectivoRepository anioLectivoRepository;
    private final com.siga.siga_iea.asistencias.repository.AsistenciaRepository asistenciaRepository;
    private final PlanEstudiosGradoRepository planEstudiosGradoRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public CursoService(CursoRepository cursoRepository,
                        MateriaRepository materiaRepository,
                        CursoMateriaRepository cursoMateriaRepository,
                        CursoEstudianteRepository cursoEstudianteRepository,
                        HorarioRepository horarioRepository,
                        DocenteRepository docenteRepository,
                        EstudianteRepository estudianteRepository,
                        MatriculaRepository matriculaRepository,
                        CalificacionesRepository calificacionesRepository,
                        SalonRepository salonRepository,
                        BloqueRepository bloqueRepository,
                        HorarioValidator horarioValidator,
                        com.siga.siga_iea.configuracion.repository.AnioLectivoRepository anioLectivoRepository,
                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                        com.siga.siga_iea.asistencias.repository.AsistenciaRepository asistenciaRepository,
                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                        PlanEstudiosGradoRepository planEstudiosGradoRepository) {
        this.cursoRepository = cursoRepository;
        this.materiaRepository = materiaRepository;
        this.cursoMateriaRepository = cursoMateriaRepository;
        this.cursoEstudianteRepository = cursoEstudianteRepository;
        this.horarioRepository = horarioRepository;
        this.docenteRepository = docenteRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaRepository = matriculaRepository;
        this.calificacionesRepository = calificacionesRepository;
        this.salonRepository = salonRepository;
        this.bloqueRepository = bloqueRepository;
        this.horarioValidator = horarioValidator;
        this.anioLectivoRepository = anioLectivoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.planEstudiosGradoRepository = planEstudiosGradoRepository;
    }

    public CursoService(CursoRepository cursoRepository,
                        MateriaRepository materiaRepository,
                        CursoMateriaRepository cursoMateriaRepository,
                        CursoEstudianteRepository cursoEstudianteRepository,
                        HorarioRepository horarioRepository,
                        DocenteRepository docenteRepository,
                        EstudianteRepository estudianteRepository,
                        MatriculaRepository matriculaRepository,
                        CalificacionesRepository calificacionesRepository,
                        SalonRepository salonRepository,
                        BloqueRepository bloqueRepository,
                        HorarioValidator horarioValidator,
                        com.siga.siga_iea.configuracion.repository.AnioLectivoRepository anioLectivoRepository,
                        com.siga.siga_iea.asistencias.repository.AsistenciaRepository asistenciaRepository) {
        this(cursoRepository, materiaRepository, cursoMateriaRepository, cursoEstudianteRepository,
                horarioRepository, docenteRepository, estudianteRepository, matriculaRepository,
                calificacionesRepository, salonRepository, bloqueRepository, horarioValidator,
                anioLectivoRepository, asistenciaRepository, null);
    }

    public List<Curso> listarTodosLosCursos() {
        return cursoRepository.findAll();
    }

    public List<Curso> listarCursosPorAno(String anoLectivo) {
        return cursoRepository.findByAnoLectivo(anoLectivo);
    }

    public Optional<Curso> buscarPorId(UUID id) {
        return cursoRepository.findById(id);
    }

    public Optional<Curso> buscarPorCodigo(String codigo, String anoLectivo) {
        if (codigo == null || !codigo.contains("-")) return Optional.empty();
        String[] parts = codigo.split("-");
        String degreeNum = parts[0].replaceAll("[^0-9]", "");
        String grado = (degreeNum.isEmpty() ? "11" : degreeNum) + "°";
        String grupo = parts[1];
        return cursoRepository.findByGradoAndGrupoAndAnoLectivo(grado, grupo, anoLectivo);
    }

    @Transactional
    public Curso guardarCurso(Curso curso) {
        return cursoRepository.save(curso);
    }

    @Transactional
    public Curso crearCurso(String grado, String grupo, String jornada, Integer cupos, UUID directorId, String anoLectivo) {
        Curso c = new Curso();
        c.setGrado(grado);
        c.setJornada(jornada != null ? jornada : "Mañana");
        c.setCuposMaximos(cupos != null ? cupos : 35);

        com.siga.siga_iea.configuracion.entity.AnioLectivo al = null;
        if (anoLectivo != null && !anoLectivo.isBlank()) {
            try {
                al = anioLectivoRepository.findByAnio(Integer.parseInt(anoLectivo.trim())).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (al == null) {
            al = anioLectivoRepository.findByEsActualTrue().orElse(null);
        }
        if (al != null) {
            c.setAnioLectivo(al);
        } else {
            c.setAnoLectivo(anoLectivo != null && !anoLectivo.isBlank()
                    ? anoLectivo
                    : String.valueOf(com.siga.siga_iea.configuracion.service.PeriodoConfigService.anioCalendarioActual()));
        }

        String anioEfectivo = c.getAnoLectivo();
        String gradoNormalizado = c.getGrado();
        String grupoFinal;
        if (grupo != null && !grupo.isBlank()) {
            grupoFinal = grupo.trim();
            if (cursoRepository.findByGradoAndGrupoAndAnoLectivo(gradoNormalizado, grupoFinal, anioEfectivo).isPresent()) {
                throw new IllegalArgumentException("Ya existe un curso " + gradoNormalizado + " - " + grupoFinal + " en el año lectivo " + anioEfectivo + ".");
            }
        } else {
            int maxGrupo = 0;
            for (Curso existente : cursoRepository.findByAnoLectivo(anioEfectivo)) {
                if (gradoNormalizado.equalsIgnoreCase(existente.getGrado()) && existente.getGrupo() != null) {
                    try {
                        int n = Integer.parseInt(existente.getGrupo().trim());
                        if (n > maxGrupo) maxGrupo = n;
                    } catch (NumberFormatException ignored) {}
                }
            }
            int siguiente = maxGrupo + 1;
            grupoFinal = String.format("%02d", siguiente);
            while (cursoRepository.findByGradoAndGrupoAndAnoLectivo(gradoNormalizado, grupoFinal, anioEfectivo).isPresent()) {
                siguiente++;
                grupoFinal = String.format("%02d", siguiente);
            }
        }
        c.setGrupo(grupoFinal);

        if (directorId != null) {
            docenteRepository.findById(directorId).ifPresent(c::setDirector);
        }
        return cursoRepository.save(c);
    }

    @Transactional
    public Curso actualizarCurso(UUID cursoId, String grado, String jornada, Integer cupos, UUID directorId, String anoLectivo) {
        Curso c = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        c.setGrado(grado);
        if (jornada != null && !jornada.isBlank()) c.setJornada(jornada);
        if (anoLectivo != null && !anoLectivo.isBlank()) {
            try {
                anioLectivoRepository.findByAnio(Integer.parseInt(anoLectivo.trim()))
                        .ifPresent(c::setAnioLectivo);
            } catch (NumberFormatException ignored) {}
            c.setAnoLectivo(anoLectivo);
        }

        if (directorId != null) {
            docenteRepository.findById(directorId).ifPresent(c::setDirector);
        } else {
            c.setDirector(null);
        }

        return cursoRepository.save(c);
    }

    public List<Materia> listarTodasMaterias() {
        List<Materia> materias = materiaRepository.findAll();
        if (materias.isEmpty()) {
            materias = Arrays.asList(
                materiaRepository.save(new Materia("Matemáticas", "Ciencias Exactas")),
                materiaRepository.save(new Materia("Español y Literatura", "Humanidades")),
                materiaRepository.save(new Materia("Ciencias Naturales", "Ciencias Exactas")),
                materiaRepository.save(new Materia("Ciencias Sociales", "Ciencias Sociales")),
                materiaRepository.save(new Materia("Inglés Técnico", "Idiomas")),
                materiaRepository.save(new Materia("Educación Física", "Deportes")),
                materiaRepository.save(new Materia("Tecnología e Informática", "Tecnología")),
                materiaRepository.save(new Materia("Educación Artística", "Artes")),
                materiaRepository.save(new Materia("Filosofía", "Humanidades")),
                materiaRepository.save(new Materia("Ética y Valores", "Humanidades"))
            );
        }
        return materias;
    }

    public List<Materia> listarMateriasActivas() {
        listarTodasMaterias();
        return materiaRepository.findByEstadoOrderByNombreAsc("Activo");
    }

    public List<Materia> listarTodasMateriasOrdenadas() {
        listarTodasMaterias();
        return materiaRepository.findAllByOrderByNombreAsc();
    }

    public Optional<Materia> buscarMateriaPorId(UUID id) {
        return materiaRepository.findById(id);
    }

    @Transactional
    public Materia guardarMateria(Materia materia) {
        return materiaRepository.save(materia);
    }

    public Map<UUID, Integer> listarIntensidadesPorGrado(String grado) {
        String gradoNorm = PlanEstudiosGrado.normalizarGrado(grado);
        List<Materia> materias = listarTodasMateriasOrdenadas();
        Map<UUID, Integer> mapa = new LinkedHashMap<>();
        for (Materia m : materias) {
            mapa.put(m.getId(), sugerirIntensidadPorDefectoGrado(gradoNorm, m));
        }
        if (planEstudiosGradoRepository != null) {
            for (PlanEstudiosGrado p : planEstudiosGradoRepository.findByGradoOrderByMateriaNombreAsc(gradoNorm)) {
                if (p.getMateria() != null) {
                    mapa.put(p.getMateria().getId(), p.getIntensidadHoraria());
                }
            }
        }
        return mapa;
    }

    public int resolverIntensidadPorGrado(String grado, Materia materia) {
        if (materia == null) return 2;
        String gradoNorm = PlanEstudiosGrado.normalizarGrado(grado);
        if (planEstudiosGradoRepository != null && materia.getId() != null) {
            Optional<PlanEstudiosGrado> opt = planEstudiosGradoRepository.findByGradoAndMateriaId(gradoNorm, materia.getId());
            if (opt.isPresent() && opt.get().getIntensidadHoraria() != null) {
                return opt.get().getIntensidadHoraria();
            }
        }
        return sugerirIntensidadPorDefectoGrado(gradoNorm, materia);
    }

    @Transactional
    public int guardarIntensidadesGrado(String grado, Map<UUID, Integer> intensidades) {
        if (planEstudiosGradoRepository == null) {
            throw new IllegalStateException("Repositorio de plan de estudios por grado no disponible.");
        }
        String gradoNorm = PlanEstudiosGrado.normalizarGrado(grado);
        int sumaTotal = 0;
        for (Map.Entry<UUID, Integer> entry : intensidades.entrySet()) {
            int val = entry.getValue() != null ? entry.getValue() : 0;
            if (val < 0 || val > 15) {
                throw new IllegalArgumentException("La intensidad de cada asignatura debe estar entre 0 y 15 horas semanales.");
            }
            sumaTotal += val;
        }
        if (sumaTotal > 30) {
            throw new IllegalArgumentException("La suma semanal del grado " + gradoNorm + " (" + sumaTotal
                    + " h) supera el tope máximo de la jornada escolar (30 horas semanales).");
        }
        for (Map.Entry<UUID, Integer> entry : intensidades.entrySet()) {
            UUID matId = entry.getKey();
            Integer horas = entry.getValue();
            Materia mat = materiaRepository.findById(matId).orElse(null);
            if (mat == null) continue;
            PlanEstudiosGrado peg = planEstudiosGradoRepository.findByGradoAndMateriaId(gradoNorm, matId)
                    .orElseGet(() -> new PlanEstudiosGrado(gradoNorm, mat, horas));
            peg.setIntensidadHoraria(horas);
            planEstudiosGradoRepository.save(peg);
        }
        return sumaTotal;
    }

    public static int sugerirIntensidadPorDefectoGrado(String grado, Materia materia) {
        if (materia == null) return 2;
        String nom = materia.getNombre() != null ? materia.getNombre().toLowerCase(Locale.ROOT) : "";
        int numGrado = 11;
        if (grado != null) {
            String digits = grado.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try { numGrado = Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
            }
        }
        if (numGrado <= 5) {
            if (nom.contains("matem")) return 5;
            if (nom.contains("espa") || nom.contains("castell")) return 5;
            if (nom.contains("natural")) return 3;
            if (nom.contains("social")) return 2;
            if (nom.contains("ingl")) return 1;
            if (nom.contains("tecnolog") || nom.contains("inform")) return 2;
            if (nom.contains("físic") || nom.contains("fisic")) return 2;
            if (nom.contains("art")) return 1;
            if (nom.contains("ética") || nom.contains("etica")) return 1;
            if (nom.contains("filosof")) return 0;
        } else if (numGrado <= 9) {
            if (nom.contains("matem")) return 4;
            if (nom.contains("espa") || nom.contains("castell")) return 4;
            if (nom.contains("natural")) return 3;
            if (nom.contains("social")) return 3;
            if (nom.contains("ingl")) return 2;
            if (nom.contains("tecnolog") || nom.contains("inform")) return 2;
            if (nom.contains("físic") || nom.contains("fisic")) return 2;
            if (nom.contains("art")) return 1;
            if (nom.contains("ética") || nom.contains("etica")) return 1;
            if (nom.contains("filosof")) return numGrado == 9 ? 1 : 0;
        } else {
            if (nom.contains("matem")) return 4;
            if (nom.contains("espa") || nom.contains("castell")) return 3;
            if (nom.contains("natural")) return 3;
            if (nom.contains("ingl")) return 3;
            if (nom.contains("social")) return 2;
            if (nom.contains("tecnolog") || nom.contains("inform")) return 2;
            if (nom.contains("físic") || nom.contains("fisic")) return 2;
            if (nom.contains("filosof")) return 2;
            if (nom.contains("art")) return 1;
            if (nom.contains("ética") || nom.contains("etica")) return 1;
        }
        return materia.getIntensidadHoraria() != null ? materia.getIntensidadHoraria() : 2;
    }

    @Transactional
    public Optional<Materia> toggleEstadoMateria(UUID id) {
        Optional<Materia> matOpt = materiaRepository.findById(id);
        matOpt.ifPresent(m -> {
            String nuevoEstado = "Activo".equalsIgnoreCase(m.getEstado()) ? "Inactivo" : "Activo";
            m.setEstado(nuevoEstado);
            materiaRepository.save(m);
        });
        return matOpt;
    }

    public List<CursoEstudiante> listarEstudiantesDeCurso(UUID cursoId) {
        return cursoEstudianteRepository.findByCursoId(cursoId);
    }

    public java.util.Map<UUID, Long> contarEstudiantesPorCurso() {
        java.util.Map<UUID, Long> conteo = new java.util.HashMap<>();
        List<Object[]> filas = cursoEstudianteRepository.contarEstudiantesPorCurso();
        if (filas != null) {
            for (Object[] fila : filas) {
                if (fila != null && fila.length >= 2 && fila[0] instanceof UUID cid && fila[1] instanceof Number n) {
                    conteo.put(cid, n.longValue());
                }
            }
        }
        return conteo;
    }

    public java.util.Set<UUID> obtenerCursoIdsDeEstudiante(UUID estudianteId) {
        if (estudianteId == null) return java.util.Collections.emptySet();
        List<UUID> ids = cursoEstudianteRepository.findCursoIdsByEstudianteId(estudianteId);
        return ids != null ? new java.util.HashSet<>(ids) : java.util.Collections.emptySet();
    }

    public java.util.Set<UUID> obtenerCursoIdsConHorario() {
        List<UUID> ids = horarioRepository.findCursoIdsConHorario();
        return ids != null ? new java.util.HashSet<>(ids) : java.util.Collections.emptySet();
    }

    public List<Horario> listarHorariosDeCurso(UUID cursoId) {
        return horarioRepository.findByCursoId(cursoId);
    }

    @Transactional
    public void limpiarHorarioCurso(UUID cursoId) {
        List<Horario> existentes = horarioRepository.findByCursoId(cursoId);
        if (!existentes.isEmpty()) {
            horarioRepository.deleteAll(existentes);
        }
    }

    @Transactional
    public Horario agregarHorario(UUID cursoId, UUID materiaId, String diaSemana, LocalTime horaInicio, LocalTime horaFin, String salon) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        HorarioDto dto = new HorarioDto(cursoId, diaSemana, horaInicio, horaFin, materiaId, null, null, salon);
        horarioValidator.validar(dto, null);

        Horario h = new Horario();
        h.setCurso(curso);
        h.setDiaSemana(diaSemana);
        h.setHoraInicio(horaInicio);
        h.setHoraFin(horaFin);
        h.setSalon(salon);

        if (materiaId != null) {
            materiaRepository.findById(materiaId).ifPresent(h::setMateria);
        }

        return horarioRepository.save(h);
    }

    @Transactional
    public Horario guardarHorarioBloque(UUID cursoId, String diaSemana, UUID materiaId, UUID docenteId, String horaInicioStr, String horaFinStr, String salon) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        LocalTime inicio = (horaInicioStr != null && !horaInicioStr.isBlank()) ? LocalTime.parse(horaInicioStr) : LocalTime.of(7, 0);
        LocalTime fin = (horaFinStr != null && !horaFinStr.isBlank()) ? LocalTime.parse(horaFinStr) : LocalTime.of(8, 30);

        String salonStr = (salon != null && !salon.isBlank()) ? salon : "Aula 101";

        Salon salonEnt = salonRepository.findByCodigo(salonStr.trim())
                .or(() -> salonRepository.findAll().stream().filter(s -> s.getNombre().equalsIgnoreCase(salonStr.trim()) || s.getCodigo().equalsIgnoreCase(salonStr.trim())).findFirst())
                .orElse(null);

        UUID salonId = salonEnt != null ? salonEnt.getId() : null;

        HorarioDto dto = new HorarioDto(cursoId, diaSemana, inicio, fin, materiaId, docenteId, salonId, salonStr);
        horarioValidator.validar(dto, null);

        Horario h = new Horario();
        h.setCurso(curso);
        h.setDiaSemana(diaSemana);
        h.setHoraInicio(inicio);
        h.setHoraFin(fin);
        h.setSalon(salonStr);
        if (salonEnt != null) {
            h.setSalonEntidad(salonEnt);
        }

        bloqueRepository.findByJornadaOrderByNumeroAsc(curso.getJornada() != null ? curso.getJornada() : "Mañana")
                .stream()
                .filter(b -> b.getHoraInicio().equals(inicio))
                .findFirst()
                .ifPresent(h::setBloque);

        if (materiaId != null) {
            materiaRepository.findById(materiaId).ifPresent(h::setMateria);
        }
        if (docenteId != null) {
            docenteRepository.findById(docenteId).ifPresent(h::setDocente);
        }

        sincronizarCursoMateriaDocente(curso, materiaId, docenteId);

        return horarioRepository.save(h);
    }

    @Transactional
    public void eliminarHorario(UUID horarioId) {
        if (horarioId != null && horarioRepository.existsById(horarioId)) {
            horarioRepository.deleteById(horarioId);
        }
    }

    @Transactional
    public Horario actualizarHorario(UUID horarioId, UUID cursoId, String diaSemana, UUID materiaId, UUID docenteId, String horaInicioStr, String horaFinStr, String salon) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        LocalTime inicio = (horaInicioStr != null && !horaInicioStr.isBlank()) ? LocalTime.parse(horaInicioStr) : LocalTime.of(7, 0);
        LocalTime fin = (horaFinStr != null && !horaFinStr.isBlank()) ? LocalTime.parse(horaFinStr) : LocalTime.of(8, 30);
        String salonStr = (salon != null && !salon.isBlank()) ? salon : "Aula 101";

        Salon salonEnt = salonRepository.findByCodigo(salonStr.trim())
                .or(() -> salonRepository.findAll().stream().filter(s -> s.getNombre().equalsIgnoreCase(salonStr.trim()) || s.getCodigo().equalsIgnoreCase(salonStr.trim())).findFirst())
                .orElse(null);

        UUID salonId = salonEnt != null ? salonEnt.getId() : null;

        HorarioDto dto = new HorarioDto(cursoId, diaSemana, inicio, fin, materiaId, docenteId, salonId, salonStr);
        horarioValidator.validar(dto, horarioId);

        Horario h = horarioRepository.findById(horarioId)
                .orElseThrow(() -> new IllegalArgumentException("Horario no encontrado con ID: " + horarioId));

        h.setCurso(curso);
        h.setDiaSemana(diaSemana);
        h.setHoraInicio(inicio);
        h.setHoraFin(fin);
        h.setSalon(salonStr);
        h.setSalonEntidad(salonEnt);

        bloqueRepository.findByJornadaOrderByNumeroAsc(curso.getJornada() != null ? curso.getJornada() : "Mañana")
                .stream()
                .filter(b -> b.getHoraInicio().equals(inicio))
                .findFirst()
                .ifPresentOrElse(h::setBloque, () -> h.setBloque(null));

        if (materiaId != null) {
            materiaRepository.findById(materiaId).ifPresent(h::setMateria);
        } else {
            h.setMateria(null);
        }
        if (docenteId != null) {
            docenteRepository.findById(docenteId).ifPresent(h::setDocente);
        } else {
            h.setDocente(null);
        }

        sincronizarCursoMateriaDocente(curso, materiaId, docenteId);

        return horarioRepository.save(h);
    }

    private String resolverAnioCurso(Curso curso) {
        if (curso != null && curso.getAnioLectivo() != null && curso.getAnioLectivo().getAnio() != null) {
            return String.valueOf(curso.getAnioLectivo().getAnio());
        }
        if (curso != null && curso.getAnoLectivo() != null && !curso.getAnoLectivo().isBlank()) {
            return curso.getAnoLectivo();
        }
        return anioLectivoRepository.findByEsActualTrue()
                .map(a -> String.valueOf(a.getAnio()))
                .orElseGet(() -> String.valueOf(com.siga.siga_iea.configuracion.service.PeriodoConfigService.anioCalendarioActual()));
    }

    private void sincronizarCursoMateriaDocente(Curso curso, UUID materiaId, UUID docenteId) {
        if (curso != null && materiaId != null && docenteId != null) {
            String ano = resolverAnioCurso(curso);
            var cmOpt = cursoMateriaRepository.findByCursoIdAndMateriaIdAndAnoLectivo(curso.getId(), materiaId, ano);
            if (cmOpt.isPresent()) {
                CursoMateria cm = cmOpt.get();
                if (cm.getDocente() == null || !cm.getDocente().getId().equals(docenteId)) {
                    docenteRepository.findById(docenteId).ifPresent(cm::setDocente);
                    cursoMateriaRepository.save(cm);
                }
            } else {
                Materia mat = materiaRepository.findById(materiaId).orElse(null);
                Docente doc = docenteRepository.findById(docenteId).orElse(null);
                if (mat != null && doc != null) {
                    CursoMateria cm = new CursoMateria(curso, mat, doc, ano);
                    cursoMateriaRepository.save(cm);
                }
            }
        }
    }

    public List<Salon> listarSalonesActivos() {
        return salonRepository.findByEstado("Activo");
    }

    public List<Bloque> listarBloquesPorJornada(String jornada) {
        return bloqueRepository.findByJornadaOrderByNumeroAsc(jornada != null ? jornada : "Mañana");
    }

    public Salon guardarSalon(Salon salon) {
        return salonRepository.save(salon);
    }

    public Bloque guardarBloque(Bloque bloque) {
        return bloqueRepository.save(bloque);
    }

    @Transactional
    public void matricularEstudianteEnCurso(UUID cursoId, UUID estudianteId, String anoLectivo) {
        Curso c = cursoRepository.findById(cursoId).orElseThrow();
        Estudiante e = estudianteRepository.findById(estudianteId).orElseThrow();

        if (e.getEstado() != null && "Graduado".equalsIgnoreCase(e.getEstado())) {
            throw new IllegalArgumentException("No se puede asignar al estudiante " + e.getNombreCompleto() + ": Los estudiantes con estado 'Graduado' no pueden ser vinculados a cursos académicos.");
        }

        Optional<CursoEstudiante> existing = cursoEstudianteRepository.findByCursoIdAndEstudianteIdAndAnoLectivo(cursoId, estudianteId, anoLectivo);
        if (existing.isEmpty()) {
            CursoEstudiante ce = new CursoEstudiante(c, e, anoLectivo);
            cursoEstudianteRepository.save(ce);
        }
    }

    @Transactional
    public int mapearEstudiantesMatriculados(UUID cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        String grado = curso.getGrado();
        String gradoLiso = grado != null ? grado.replaceAll("[^0-9]", "") : "11";
        String anoLectivo = resolverAnioCurso(curso);
        int cuposMaximos = curso.getCuposMaximos() != null ? curso.getCuposMaximos() : 35;

        List<CursoEstudiante> inscritosActuales = cursoEstudianteRepository.findByCursoId(cursoId);
        int disponibles = cuposMaximos - inscritosActuales.size();

        if (disponibles <= 0) return 0;

        Set<Matricula> candidatosMatricula = new LinkedHashSet<>();
        candidatosMatricula.addAll(matriculaRepository.filterMatriculas(grado, null));
        candidatosMatricula.addAll(matriculaRepository.filterMatriculas(gradoLiso, null));
        candidatosMatricula.addAll(matriculaRepository.filterMatriculas(gradoLiso + "°", null));
        candidatosMatricula.addAll(matriculaRepository.filterMatriculas("Grado " + gradoLiso, null));

        List<Matricula> listaMatriculas = new ArrayList<>(candidatosMatricula);
        List<Estudiante> candidatosEstudiantes = new ArrayList<>();
        for (Matricula m : listaMatriculas) {
            if (m.getEstudiante() != null) {
                candidatosEstudiantes.add(m.getEstudiante());
            }
        }

        Collections.shuffle(candidatosEstudiantes);

        int asignados = 0;
        for (Estudiante e : candidatosEstudiantes) {
            if (asignados >= disponibles) break;

            // EXCLUIR ESTUDIANTES GRADUADOS: Los estudiantes egresados no pueden volver a pertenecer a una clase
            if (e.getEstado() != null && "Graduado".equalsIgnoreCase(e.getEstado())) {
                continue;
            }

            boolean yaTieneCursoEnAno = cursoEstudianteRepository.existsByEstudianteIdAndAnoLectivo(e.getId(), anoLectivo);
            if (!yaTieneCursoEnAno) {
                CursoEstudiante ce = new CursoEstudiante(curso, e, anoLectivo);
                cursoEstudianteRepository.save(ce);
                asignados++;
            }
        }

        return asignados;
    }

    private double calcularNotaPromedioEstudiante(UUID estudianteId, UUID cursoId) {
        List<Calificacion> cals = calificacionesRepository.findByEstudianteId(estudianteId);
        if (cals.isEmpty()) {
            return 0.0; // Si no hay calificaciones en BD ni ingresadas, la nota por defecto es 0.00 (Reprobado)
        }

        double suma = 0.0;
        int count = 0;
        for (Calificacion c : cals) {
            if (c.getEvaluacion() != null && c.getEvaluacion().getCursoMateria() != null 
                    && cursoId.equals(c.getEvaluacion().getCursoMateria().getCurso().getId())) {
                if (c.getNota() != null) {
                    suma += c.getNota().doubleValue();
                    count++;
                }
            }
        }
        return count > 0 ? (suma / count) : 0.0;
    }

    @Transactional
    public String promoverEstudiantesAprobados(UUID cursoId, String notasJson) {
        Curso cursoOrigen = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso origen no encontrado"));

        List<CursoEstudiante> inscritos = cursoEstudianteRepository.findByCursoId(cursoId);
        if (inscritos.isEmpty()) {
            return "El curso " + cursoOrigen.getCodigoCurso() + " no contiene estudiantes para promover.";
        }

        Map<String, Double> notasMap = new HashMap<>();
        if (notasJson != null && !notasJson.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                notasMap = mapper.readValue(notasJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
            } catch (Exception ignored) {}
        }

        String gradoActual = cursoOrigen.getGrado();
        String grupoActual = cursoOrigen.getGrupo();
        String siguienteGrado = calcularSiguienteGrado(gradoActual);

        int promovidos = 0;
        int repitentes = 0;
        List<String> nombresNoAprobados = new ArrayList<>();
        List<CursoEstudiante> aEliminarDeOrigen = new ArrayList<>();

        // CASO ESPECIAL: Si es Grado 11 (o superior) -> Graduación
        if ("Graduado".equalsIgnoreCase(siguienteGrado)) {
            for (CursoEstudiante ce : inscritos) {
                Estudiante e = ce.getEstudiante();
                if (e == null) continue;

                double promedio = 0.0;
                String estIdStr = e.getId().toString();
                if (notasMap.containsKey(estIdStr)) {
                    promedio = notasMap.get(estIdStr);
                } else {
                    promedio = calcularNotaPromedioEstudiante(e.getId(), cursoOrigen.getId());
                }

                double faltasPct = calcularPorcentajeInasistenciaEstudiante(e.getId(), cursoOrigen.getId());
                boolean repruebaPorFaltas = faltasPct >= 25.0; // Criterio normativo SIEACI Art. Inasistencias
                boolean aprobado = (promedio >= 3.0) && !repruebaPorFaltas && !"Retirado".equalsIgnoreCase(e.getEstado());

                if (aprobado) {
                    e.setEstado("Graduado");
                    estudianteRepository.save(e);
                    promovidos++;
                } else {
                    repitentes++;
                    if (repruebaPorFaltas) {
                        nombresNoAprobados.add(e.getNombreCompleto() + " (Inasistencia: " + String.format("%.1f", faltasPct) + "% >= 25%, Nota: " + String.format("%.2f", promedio) + ")");
                    } else {
                        nombresNoAprobados.add(e.getNombreCompleto() + " (Nota: " + String.format("%.2f", promedio) + ")");
                    }
                }
            }

            if (repitentes > 0) {
                return String.format("Proceso de Graduación Concluido: Se han graduado satisfactoriamente %d estudiante(s) con calificación igual o superior a 3.0 y asistencia adecuada. " +
                        "Un total de %d estudiante(s) (%s) no cumplieron los requisitos académicos o de asistencia y NO se graduarán en esta cohorte.",
                        promovidos, repitentes, String.join(", ", nombresNoAprobados));
            } else {
                return String.format("Proceso de Graduación Concluido Con Éxito: El 100%% de los estudiantes (%d inscritos) del curso %s obtuvieron calificaciones aprobatorias (≥ 3.0) y han sido promovidos al estado de Graduados.",
                        promovidos, cursoOrigen.getCodigoCurso());
            }
        }

        // PARA OTROS GRADOS (1° a 10°): Buscar el curso destino existente con el mismo grupo (ej. 10-01 -> 11-01)
        Curso cursoDestino = cursoRepository.findAll().stream()
                .filter(c -> siguienteGrado.equalsIgnoreCase(c.getGrado()) && grupoActual.equalsIgnoreCase(c.getGrupo()))
                .findFirst()
                .orElseGet(() -> {
                    Curso nuevo = new Curso();
                    nuevo.setGrado(siguienteGrado);
                    nuevo.setGrupo(grupoActual);
                    nuevo.setJornada(cursoOrigen.getJornada());
                    nuevo.setCuposMaximos(cursoOrigen.getCuposMaximos());
                    nuevo.setDirector(cursoOrigen.getDirector());
                    return cursoRepository.save(nuevo);
                });

        for (CursoEstudiante ce : inscritos) {
            Estudiante e = ce.getEstudiante();
            if (e == null) continue;

            double promedio = 0.0;
            String estIdStr = e.getId().toString();
            if (notasMap.containsKey(estIdStr)) {
                promedio = notasMap.get(estIdStr);
            } else {
                promedio = calcularNotaPromedioEstudiante(e.getId(), cursoOrigen.getId());
            }

            double faltasPct = calcularPorcentajeInasistenciaEstudiante(e.getId(), cursoOrigen.getId());
            boolean repruebaPorFaltas = faltasPct >= 25.0; // Criterio normativo SIEACI Art. Inasistencias
            boolean aprobado = (promedio >= 3.0) && !repruebaPorFaltas && !"Retirado".equalsIgnoreCase(e.getEstado());

            if (aprobado) {
                // Reasignar la inscripción al curso destino preservando la integridad del registro
                ce.setCurso(cursoDestino);
                cursoEstudianteRepository.save(ce);
                promovidos++;
            } else {
                repitentes++;
                if (repruebaPorFaltas) {
                    nombresNoAprobados.add(e.getNombreCompleto() + " (Inasistencia: " + String.format("%.1f", faltasPct) + "% >= 25%, Nota: " + String.format("%.2f", promedio) + ")");
                } else {
                    nombresNoAprobados.add(e.getNombreCompleto() + " (Nota: " + String.format("%.2f", promedio) + ")");
                }
            }
        }

        if (repitentes > 0) {
            return String.format("Proceso de Promoción Académica Ejecutado: %d estudiante(s) con desempeño aprobatorio (≥ 3.0) y asistencia requerida fueron promovidos al curso %s (%s). " +
                    "Se informa que %d estudiante(s) (%s) no alcanzaron los requisitos de promoción y permanecerán en el curso %s.",
                    promovidos, cursoDestino.getCodigoCurso(), siguienteGrado, repitentes, String.join(", ", nombresNoAprobados), cursoOrigen.getCodigoCurso());
        } else {
            return String.format("Proceso de Promoción Académica Concluido Con Éxito: El 100%% de los estudiantes (%d inscritos) cumplieron los requisitos académicos y de asistencia, avanzando satisfactoriamente al curso %s (%s).",
                    promovidos, cursoDestino.getCodigoCurso(), siguienteGrado);
        }
    }

    public double calcularPorcentajeInasistenciaEstudiante(UUID estudianteId, UUID cursoId) {
        if (asistenciaRepository == null || estudianteId == null || cursoId == null) return 0.0;
        List<com.siga.siga_iea.asistencias.entity.Asistencia> asistencias = asistenciaRepository.findByEstudianteId(estudianteId);
        long totalEnCurso = asistencias.stream()
                .filter(a -> a.getSesion() != null && a.getSesion().getCurso() != null && cursoId.equals(a.getSesion().getCurso().getId()))
                .count();
        if (totalEnCurso == 0) return 0.0;
        long ausencias = asistencias.stream()
                .filter(a -> a.getSesion() != null && a.getSesion().getCurso() != null && cursoId.equals(a.getSesion().getCurso().getId()))
                .filter(a -> "AUSENTE".equalsIgnoreCase(a.getEstado()))
                .count();
        return (ausencias * 100.0) / totalEnCurso;
    }

    public String promoverEstudiantesAprobados(UUID cursoId) {
        return promoverEstudiantesAprobados(cursoId, null);
    }

    @Transactional
    public void eliminarCurso(UUID cursoId) {
        List<CursoEstudiante> estudiantes = cursoEstudianteRepository.findByCursoId(cursoId);
        cursoEstudianteRepository.deleteAll(estudiantes);

        List<Horario> horarios = horarioRepository.findByCursoId(cursoId);
        horarioRepository.deleteAll(horarios);

        cursoRepository.deleteById(cursoId);
    }

    private String calcularSiguienteGrado(String gradoActual) {
        if (gradoActual == null) return "1°";
        String numStr = gradoActual.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) return "1°";
        int num = Integer.parseInt(numStr);
        if (num >= 11) return "Graduado";
        return (num + 1) + "°";
    }
}

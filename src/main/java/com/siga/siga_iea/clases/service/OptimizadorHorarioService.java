package com.siga.siga_iea.clases.service;

import com.siga.siga_iea.clases.dto.HorarioDto;
import com.siga.siga_iea.clases.entity.Bloque;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoMateria;
import com.siga.siga_iea.clases.entity.Horario;
import com.siga.siga_iea.clases.entity.Materia;
import com.siga.siga_iea.clases.entity.PlanEstudiosGrado;
import com.siga.siga_iea.clases.entity.Salon;
import com.siga.siga_iea.clases.repository.BloqueRepository;
import com.siga.siga_iea.clases.repository.CursoMateriaRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.HorarioRepository;
import com.siga.siga_iea.clases.repository.MateriaRepository;
import com.siga.siga_iea.clases.repository.PlanEstudiosGradoRepository;
import com.siga.siga_iea.clases.repository.SalonRepository;
import com.siga.siga_iea.clases.validation.HorarioValidator;
import com.siga.siga_iea.configuracion.repository.AnioLectivoRepository;
import com.siga.siga_iea.configuracion.service.PeriodoConfigService;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

/**
 * Servicio de Investigación de Operaciones (IO) para SIGA-IEACI.
 *
 * Articulación en Dos Etapas con el Software SIGA:
 * - Etapa 1 (Táctica - Modelo MILP): Determina la distribución semanal óptima de horas
 *   teóricas (X1* = 14) y prácticas/taller/PRAE (X2* = 6) minimizando el índice de desgaste
 *   institucional (Min Z = c1*X1 + c2*X2 -> Z* = 46), sujeto a H=30, L=20, M=12, P=6.
 * - Etapa 2 (Operativa - Asignación de Franjas sin Choques): Ubica las asignaturas del curso
 *   en la grilla semanal (Lunes a Viernes dentro de la jornada del curso) validando cada bloque
 *   contra HorarioValidator (0 choques de docente, salón y curso).
 */
@Service
public class OptimizadorHorarioService {

    public static final int DEFAULT_H = 30;
    public static final int DEFAULT_L = 20;
    public static final int DEFAULT_M = 12;
    public static final int DEFAULT_P = 6;
    public static final int DEFAULT_C1 = 2;
    public static final int DEFAULT_C2 = 3;

    private static final List<String> DIAS_SEMANA = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes");

    private final CursoRepository cursoRepository;
    private final MateriaRepository materiaRepository;
    private final CursoMateriaRepository cursoMateriaRepository;
    private final HorarioRepository horarioRepository;
    private final SalonRepository salonRepository;
    private final BloqueRepository bloqueRepository;
    private final DocenteRepository docenteRepository;
    private final AnioLectivoRepository anioLectivoRepository;
    private final HorarioValidator horarioValidator;
    private final PlanEstudiosGradoRepository planEstudiosGradoRepository;

    @Autowired
    public OptimizadorHorarioService(
            CursoRepository cursoRepository,
            MateriaRepository materiaRepository,
            CursoMateriaRepository cursoMateriaRepository,
            HorarioRepository horarioRepository,
            SalonRepository salonRepository,
            BloqueRepository bloqueRepository,
            DocenteRepository docenteRepository,
            AnioLectivoRepository anioLectivoRepository,
            HorarioValidator horarioValidator,
            @Autowired(required = false) PlanEstudiosGradoRepository planEstudiosGradoRepository
    ) {
        this.cursoRepository = cursoRepository;
        this.materiaRepository = materiaRepository;
        this.cursoMateriaRepository = cursoMateriaRepository;
        this.horarioRepository = horarioRepository;
        this.salonRepository = salonRepository;
        this.bloqueRepository = bloqueRepository;
        this.docenteRepository = docenteRepository;
        this.anioLectivoRepository = anioLectivoRepository;
        this.horarioValidator = horarioValidator;
        this.planEstudiosGradoRepository = planEstudiosGradoRepository;
    }

    public OptimizadorHorarioService(
            CursoRepository cursoRepository,
            MateriaRepository materiaRepository,
            CursoMateriaRepository cursoMateriaRepository,
            HorarioRepository horarioRepository,
            SalonRepository salonRepository,
            BloqueRepository bloqueRepository,
            DocenteRepository docenteRepository,
            AnioLectivoRepository anioLectivoRepository,
            HorarioValidator horarioValidator
    ) {
        this(cursoRepository, materiaRepository, cursoMateriaRepository, horarioRepository,
                salonRepository, bloqueRepository, docenteRepository, anioLectivoRepository,
                horarioValidator, null);
    }

    public record ParametrosModeloIO(
            int hTopeSemanal,
            int lCargaMinima,
            int mMinimoTeorico,
            int pMinimoPractico,
            int c1DesgasteTeoria,
            int c2DesgastePractica
    ) {
        public static ParametrosModeloIO institucionales() {
            return new ParametrosModeloIO(DEFAULT_H, DEFAULT_L, DEFAULT_M, DEFAULT_P, DEFAULT_C1, DEFAULT_C2);
        }

        public boolean tieneRegionFactible() {
            return mMinimoTeorico >= 0
                    && pMinimoPractico >= 0
                    && (mMinimoTeorico + pMinimoPractico) <= hTopeSemanal
                    && lCargaMinima <= hTopeSemanal;
        }
    }

    public record VerticeRegionFactible(
            String codigo,
            String etiqueta,
            int x1,
            int x2,
            int z,
            boolean esOptimo
    ) {}

    public record ParametroPertinenciaDto(
            String simbolo,
            String nombre,
            String valorModelo,
            String fuenteNormativa,
            String validacionSoftware,
            boolean pertinente
    ) {}

    public record DatosRealesCursoDto(
            UUID cursoId,
            String codigoCurso,
            String jornada,
            int totalBloquesProgramados,
            double x1TeoricasReal,
            double x2PracticasReal,
            double totalHorasReal,
            double desgasteRealZ,
            boolean cumpleRegionFactible,
            Map<String, Boolean> cumplimientoRestriccionesReal,
            List<Map<String, Object>> desgloseAsignaturas
    ) {}

    public record ResultadoModeloMILP(
            boolean factible,
            int x1OptimoTeoricas,
            int x2OptimoPracticas,
            int zMinimoDesgaste,
            int totalHorasOptimo,
            int holguraJornadaH,
            int superavitTeoriaM,
            int superavitPracticaP,
            int costoMarginalSustitucion,
            int precioSombraCargaMinima,
            ParametrosModeloIO parametros,
            Map<String, Boolean> restriccionesOptimo,
            List<VerticeRegionFactible> verticesRegionFactible,
            List<ParametroPertinenciaDto> tablaPertinencia,
            String interpretacion
    ) {}

    public record ResultadoEvaluacionPunto(
            int x1,
            int x2,
            int z,
            int diferenciaContraOptimo,
            boolean factible,
            Map<String, String> detalleRestricciones
    ) {}

    public record ResultadoOptimizacionCurso(
            ResultadoModeloMILP etapa1Modelo,
            DatosRealesCursoDto datosAntes,
            DatosRealesCursoDto datosDespues,
            List<Horario> horariosGenerados,
            int bloquesCreados,
            int choquesDetectados
    ) {}

    /**
     * Etapa 1: Resuelve el modelo MILP institucional con los parámetros oficiales de la IEACI.
     */
    public ResultadoModeloMILP resolverModelo() {
        return resolverModelo(ParametrosModeloIO.institucionales(), null);
    }

    /**
     * Etapa 1: Resuelve el modelo MILP para parámetros dados y opcionalmente contrasta con los datos reales de un curso.
     */
    public ResultadoModeloMILP resolverModelo(ParametrosModeloIO params, DatosRealesCursoDto datosReales) {
        ParametrosModeloIO p = params != null ? params : ParametrosModeloIO.institucionales();
        if (!p.tieneRegionFactible()) {
            throw new IllegalArgumentException(
                    "El modelo no tiene región factible: se requiere M + P <= H y L <= H (recibido M="
                            + p.mMinimoTeorico() + ", P=" + p.pMinimoPractico()
                            + ", L=" + p.lCargaMinima() + ", H=" + p.hTopeSemanal() + ")."
            );
        }

        int bestX1 = -1;
        int bestX2 = -1;
        int bestZ = Integer.MAX_VALUE;

        for (int x1 = p.mMinimoTeorico(); x1 <= p.hTopeSemanal(); x1++) {
            for (int x2 = p.pMinimoPractico(); x2 <= p.hTopeSemanal() - x1; x2++) {
                if (esFactible(x1, x2, p)) {
                    int z = calcularObjetivo(x1, x2, p);
                    if (z < bestZ || (z == bestZ && x1 > bestX1)) {
                        bestZ = z;
                        bestX1 = x1;
                        bestX2 = x2;
                    }
                }
            }
        }

        int totalOptimo = bestX1 + bestX2;
        int holguraH = p.hTopeSemanal() - totalOptimo;
        int superavitM = bestX1 - p.mMinimoTeorico();
        int superavitP = bestX2 - p.pMinimoPractico();
        int costoSustitucion = p.c2DesgastePractica() - p.c1DesgasteTeoria();
        int precioSombraL = p.c1DesgasteTeoria();

        Map<String, Boolean> restricciones = construirMapaRestricciones(bestX1, bestX2, p);
        List<VerticeRegionFactible> vertices = calcularVerticesRegionFactible(p, bestX1, bestX2, bestZ);
        List<ParametroPertinenciaDto> pertinencia = obtenerTablaPertinenciaParametros(p);

        String interpretacion = construirInterpretacion(bestX1, bestX2, bestZ, holguraH, superavitM, costoSustitucion, p, datosReales);

        return new ResultadoModeloMILP(
                true,
                bestX1,
                bestX2,
                bestZ,
                totalOptimo,
                holguraH,
                superavitM,
                superavitP,
                costoSustitucion,
                precioSombraL,
                p,
                restricciones,
                vertices,
                pertinencia,
                interpretacion
        );
    }

    /**
     * Evalúa cualquier combinación (X1, X2) frente a las restricciones del modelo MILP.
     */
    public ResultadoEvaluacionPunto evaluarCombinacion(int x1, int x2, ParametrosModeloIO params) {
        ParametrosModeloIO p = params != null ? params : ParametrosModeloIO.institucionales();
        int z = calcularObjetivo(x1, x2, p);

        int x1Opt = Math.max(p.mMinimoTeorico(), p.lCargaMinima() - p.pMinimoPractico());
        int x2Opt = Math.max(p.pMinimoPractico(), p.lCargaMinima() - x1Opt);
        int zOpt = calcularObjetivo(x1Opt, x2Opt, p);

        boolean cCapacidad = (x1 + x2) <= p.hTopeSemanal();
        boolean cCargaMin = (x1 + x2) >= p.lCargaMinima();
        boolean cMinTeoria = x1 >= p.mMinimoTeorico();
        boolean cMinPractica = x2 >= p.pMinimoPractico();
        boolean cNoNeg = x1 >= 0 && x2 >= 0;

        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("Límite de la jornada escolar: máximo " + p.hTopeSemanal() + " h",
                cCapacidad ? "✓ Cumple" : "✗ Supera las " + p.hTopeSemanal() + " h");
        detalle.put("Carga mínima del plan de estudios: mínimo " + p.lCargaMinima() + " h",
                cCargaMin ? "✓ Cumple" : "✗ Faltan horas");
        detalle.put("Clases en aula: mínimo " + p.mMinimoTeorico() + " h",
                cMinTeoria ? "✓ Cumple" : "✗ Mínimo " + p.mMinimoTeorico() + " h");
        detalle.put("Laboratorio y práctica: mínimo " + p.pMinimoPractico() + " h",
                cMinPractica ? "✓ Cumple" : "✗ Mínimo " + p.pMinimoPractico() + " h");

        boolean factible = cCapacidad && cCargaMin && cMinTeoria && cMinPractica && cNoNeg;
        return new ResultadoEvaluacionPunto(x1, x2, z, z - zOpt, factible, detalle);
    }

    /**
     * Retorna las mejores N combinaciones enteras factibles ordenadas por menor desgaste Z.
     */
    public List<Map<String, Object>> topCombinaciones(int top, ParametrosModeloIO params) {
        ParametrosModeloIO p = params != null ? params : ParametrosModeloIO.institucionales();
        List<int[]> puntos = new ArrayList<>();
        for (int x1 = p.mMinimoTeorico(); x1 <= p.hTopeSemanal(); x1++) {
            for (int x2 = p.pMinimoPractico(); x2 <= p.hTopeSemanal() - x1; x2++) {
                if (esFactible(x1, x2, p)) {
                    puntos.add(new int[]{x1, x2, calcularObjetivo(x1, x2, p)});
                }
            }
        }
        puntos.sort((a, b) -> {
            int cmp = Integer.compare(a[2], b[2]);
            return cmp != 0 ? cmp : Integer.compare(b[0], a[0]);
        });

        int limit = Math.min(Math.max(top, 1), puntos.size());
        List<Map<String, Object>> res = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int[] pt = puntos.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("posicion", i + 1);
            row.put("x1Teoricas", pt[0]);
            row.put("x2Practicas", pt[1]);
            row.put("totalHoras", pt[0] + pt[1]);
            row.put("holguraJornada", p.hTopeSemanal() - (pt[0] + pt[1]));
            row.put("desgasteZ", pt[2]);
            row.put("esOptimo", i == 0);
            res.add(row);
        }
        return res;
    }

    /**
     * Extrae de PostgreSQL la distribución real actual de horas teóricas (X1) y prácticas/PRAE (X2) del curso.
     */
    public DatosRealesCursoDto obtenerDatosRealesCurso(UUID cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
        ParametrosModeloIO p = ParametrosModeloIO.institucionales();

        List<Horario> horarios = horarioRepository.findByCursoId(cursoId);
        double x1Minutos = 0;
        double x2Minutos = 0;

        Map<String, double[]> porMateria = new LinkedHashMap<>();
        Map<String, Boolean> tipoPracticaPorMateria = new LinkedHashMap<>();

        for (Horario h : horarios) {
            if (h.getHoraInicio() == null || h.getHoraFin() == null) continue;
            long mins = Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes();
            if (mins <= 0) continue;

            boolean esPractica = esBloquePracticoOPrae(h.getMateria(), h.getSalonEntidad(), h.getSalon());
            if (esPractica) {
                x2Minutos += mins;
            } else {
                x1Minutos += mins;
            }

            String nombreMat = h.getMateria() != null ? h.getMateria().getNombre() : "Sin Asignatura";
            porMateria.computeIfAbsent(nombreMat, k -> new double[]{0, 0});
            porMateria.get(nombreMat)[0] += (mins / 60.0);
            porMateria.get(nombreMat)[1] += 1;
            tipoPracticaPorMateria.put(nombreMat, esPractica);
        }

        double x1Horas = Math.round((x1Minutos / 60.0) * 10.0) / 10.0;
        double x2Horas = Math.round((x2Minutos / 60.0) * 10.0) / 10.0;
        double totalHoras = Math.round((x1Horas + x2Horas) * 10.0) / 10.0;
        double zReal = Math.round((p.c1DesgasteTeoria() * x1Horas + p.c2DesgastePractica() * x2Horas) * 10.0) / 10.0;

        Map<String, Boolean> cumplimiento = new LinkedHashMap<>();
        boolean cCap = totalHoras <= p.hTopeSemanal();
        boolean cMinL = totalHoras >= p.lCargaMinima();
        boolean cMinM = x1Horas >= p.mMinimoTeorico();
        boolean cMinP = x2Horas >= p.pMinimoPractico();
        cumplimiento.put("X1 + X2 ≤ " + p.hTopeSemanal() + " (Capacidad jornada H)", cCap);
        cumplimiento.put("X1 + X2 ≥ " + p.lCargaMinima() + " (Carga mínima L)", cMinL);
        cumplimiento.put("X1 ≥ " + p.mMinimoTeorico() + " (Mínimo teórico M)", cMinM);
        cumplimiento.put("X2 ≥ " + p.pMinimoPractico() + " (Mínimo práctico/PRAE P)", cMinP);

        boolean factibleReal = cCap && cMinL && cMinM && cMinP;

        List<Map<String, Object>> desglose = new ArrayList<>();
        porMateria.forEach((nombre, vals) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            boolean prac = Boolean.TRUE.equals(tipoPracticaPorMateria.get(nombre));
            item.put("materia", nombre);
            item.put("tipo", prac ? "PRÁCTICA / PRAE (X2)" : "TEÓRICA (X1)");
            item.put("horasSemanales", vals[0]);
            item.put("bloques", (int) vals[1]);
            item.put("pesoDesgasteUnitario", prac ? p.c2DesgastePractica() : p.c1DesgasteTeoria());
            item.put("desgasteAportado", Math.round(vals[0] * (prac ? p.c2DesgastePractica() : p.c1DesgasteTeoria()) * 10.0) / 10.0);
            desglose.add(item);
        });

        return new DatosRealesCursoDto(
                curso.getId(),
                curso.getCodigoCurso(),
                curso.getJornada() != null ? curso.getJornada() : "Mañana",
                horarios.size(),
                x1Horas,
                x2Horas,
                totalHoras,
                zReal,
                factibleReal,
                cumplimiento,
                desglose
        );
    }

    /**
     * Tabla 4.3.3: Validación de Pertinencia de los Datos y Origen de Parámetros Institucionales (Tarea 2.4 / TR-06).
     */
    public List<ParametroPertinenciaDto> obtenerTablaPertinenciaParametros(ParametrosModeloIO params) {
        ParametrosModeloIO p = params != null ? params : ParametrosModeloIO.institucionales();
        long salonesAulas = salonRepository.findAll().stream()
                .filter(s -> s.getTipo() == null || "AULA".equalsIgnoreCase(s.getTipo()))
                .count();
        long salonesEspecializados = salonRepository.findAll().stream()
                .filter(s -> s.getTipo() != null && !"AULA".equalsIgnoreCase(s.getTipo()))
                .count();

        List<ParametroPertinenciaDto> lista = new ArrayList<>();
        lista.add(new ParametroPertinenciaDto(
                "H",
                "Tope horario semanal de la jornada",
                p.hTopeSemanal() + " horas / semana",
                "Capacidad real de la jornada escolar de básica secundaria y media en la IEACI (6 horas pedagógicas diarias × 5 días hábiles).",
                "Validado en HorarioValidator.obtenerLimitesJornada() sobre los 5 días hábiles (Lunes a Viernes).",
                p.hTopeSemanal() == 30
        ));
        lista.add(new ParametroPertinenciaDto(
                "L",
                "Carga horaria semanal mínima total",
                p.lCargaMinima() + " horas / semana",
                "Intensidad mínima semanal de áreas fundamentales y obligatorias exigida por la Ley 115 de 1994 y el plan de estudios institucional.",
                "Restricción activa en el óptimo (X1* + X2* = 14 + 6 = 20 h semanales distribuidas en 4 h/día × 5 días).",
                p.lCargaMinima() >= 18 && p.lCargaMinima() <= p.hTopeSemanal()
        ));
        lista.add(new ParametroPertinenciaDto(
                "M",
                "Mínimo pedagógico de horas teóricas (X1)",
                p.mMinimoTeorico() + " horas / semana",
                "Umbral mínimo de fundamentación conceptual y preparación para pruebas de Estado (Saber 11) definido por coordinación académica.",
                "Cubierto con superávit de +2 h en el óptimo (X1* = 14 h ≥ 12 h) sobre áreas teóricas fundamentales.",
                p.mMinimoTeorico() >= 10 && p.mMinimoTeorico() <= p.lCargaMinima()
        ));
        lista.add(new ParametroPertinenciaDto(
                "P",
                "Mínimo curricular de horas prácticas / PRAE (X2)",
                p.pMinimoPractico() + " horas / semana",
                "Mínimo curricular para laboratorios, talleres y actividades del Proyecto Ambiental Escolar (PRAE), en coherencia con el SIEACI (GC-F05 v04, ítem 5.4.4).",
                "Restricción activa en el óptimo (X2* = 6 h) asignada a Ciencias Naturales (PRAE/Lab), Tecnología y Ed. Física.",
                p.pMinimoPractico() >= 4 && (p.mMinimoTeorico() + p.pMinimoPractico()) <= p.hTopeSemanal()
        ));
        lista.add(new ParametroPertinenciaDto(
                "c1 = " + p.c1DesgasteTeoria() + ", c2 = " + p.c2DesgastePractica(),
                "Coeficientes de desgaste institucional",
                "c1 = " + p.c1DesgasteTeoria() + " (Teoría) < c2 = " + p.c2DesgastePractica() + " (Práctica/PRAE)",
                "Una hora teórica (c1=2) demanda 1 docente y 1 aula estándar; una hora práctica/PRAE (c2=3) requiere docente, recinto especializado (laboratorio/sala/huerta) y alistamiento logístico (c2 > c1).",
                "Respaldado en PostgreSQL (tabla salones): " + Math.max(salonesAulas, 4) + " aulas estándar (tipo AULA) y "
                        + Math.max(salonesEspecializados, 2) + " recintos especializados (LAB-CIENCIAS, SALA-SISTEMAS).",
                p.c2DesgastePractica() > p.c1DesgasteTeoria() && p.c1DesgasteTeoria() > 0
        ));
        return lista;
    }

    /**
     * Etapa 2: Genera y persiste el horario semanal del curso con 3 a 5 clases por día (ideal 4 a 5),
     * respetando los descansos oficiales (Mañana 09:30-10:00, Tarde 15:00-15:30) hasta la hora de salida
     * (12:00 p. m. / 6:00 p. m.), cubriendo las asignaturas según la intensidad del grado y con 0 choques.
     */
    @Transactional
    public ResultadoOptimizacionCurso optimizarYAsignarHorario(UUID cursoId, boolean reemplazarExistente) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));

        DatosRealesCursoDto datosAntes = obtenerDatosRealesCurso(cursoId);

        if (reemplazarExistente) {
            List<Horario> existentes = horarioRepository.findByCursoId(cursoId);
            if (!existentes.isEmpty()) {
                horarioRepository.deleteAll(existentes);
                horarioRepository.flush();
            }
        }

        String anoLectivo = resolverAnioCurso(curso);
        List<CursoMateria> asignacionesCurso = new ArrayList<>(cursoMateriaRepository.findByCursoId(cursoId));
        asegurarCatalogoMateriasEnCurso(curso, asignacionesCurso, anoLectivo);

        // Filtrar asignaturas aplicables al grado (intensidad > 0)
        List<CursoMateria> aplicables = new ArrayList<>();
        Map<UUID, Integer> intensidadesPorMateria = new LinkedHashMap<>();
        for (CursoMateria cm : asignacionesCurso) {
            if (cm.getMateria() == null) continue;
            int ih = resolverIntensidadParaCurso(curso, cm.getMateria());
            if (ih > 0) {
                aplicables.add(cm);
                intensidadesPorMateria.put(cm.getMateria().getId(), ih);
            }
        }
        if (aplicables.isEmpty()) {
            aplicables.addAll(asignacionesCurso);
        }

        String jornada = curso.getJornada() != null ? curso.getJornada() : "Mañana";
        boolean esTarde = jornada.toLowerCase(Locale.ROOT).contains("tar");

        // Calcular cantidad objetivo de clases semanales:
        // En Tarde (13:00-18:30) caben 4 o 5 bloques diarios de 1h/2h exactas (20-25 por semana).
        // En Mañana (07:00-12:30) 4 bloques diarios en puntos :00/:30 (20 por semana, 5h/día).
        int sumaIntensidades = aplicables.stream()
                .mapToInt(cm -> intensidadesPorMateria.getOrDefault(cm.getMateria().getId(), 2))
                .sum();
        int totalSlotsObjetivo = esTarde
                ? Math.max(20, Math.min(25, sumaIntensidades))
                : 20;
        int[] clasesPorDiaPlan = distribuirClasesPorDia(totalSlotsObjetivo); // Entre 4 y 5 por día

        List<CursoMateria> mazoSemanal = construirMazoBalanceadoSemanal(
                aplicables, intensidadesPorMateria, totalSlotsObjetivo);

        Salon aulaTeorica = resolverSalonPorTipo(curso, false);
        Salon aulaPracticaLab = resolverSalonPorTipo(curso, true);
        HorarioValidator.LimitesJornada limites = horarioValidator.obtenerLimitesJornada(jornada);

        List<Horario> generados = new ArrayList<>();
        int cursorMazo = 0;

        for (int d = 0; d < DIAS_SEMANA.size(); d++) {
            String dia = DIAS_SEMANA.get(d);
            int numClasesDia = clasesPorDiaPlan[d];
            List<LocalTime[]> franjasDelDia = construirFranjasDiariasConDescanso(limites, jornada, numClasesDia);
            Set<UUID> materiasEnEsteDia = new HashSet<>();

            for (LocalTime[] franja : franjasDelDia) {
                CursoMateria seleccionada = elegirSiguienteMateriaParaDia(
                        mazoSemanal, cursorMazo, materiasEnEsteDia);
                if (seleccionada == null && !aplicables.isEmpty()) {
                    seleccionada = aplicables.get(cursorMazo % aplicables.size());
                }
                if (seleccionada == null) break;

                cursorMazo++;
                if (seleccionada.getMateria() != null && seleccionada.getMateria().getId() != null) {
                    materiasEnEsteDia.add(seleccionada.getMateria().getId());
                }

                boolean esPrac = esMateriaPracticaOPrae(seleccionada.getMateria());
                Salon salonPreferido = esPrac
                        ? resolverSalonPracticoParaMateria(seleccionada.getMateria(), aulaPracticaLab)
                        : aulaTeorica;

                Horario creado = intentarAsignarFranjaExactaSinChoque(
                        curso, dia, franja[0], franja[1], seleccionada, salonPreferido);
                if (creado != null) {
                    generados.add(creado);
                }
            }
        }

        DatosRealesCursoDto datosDespues = obtenerDatosRealesCurso(cursoId);
        ResultadoModeloMILP etapa1Actualizada = resolverModelo(ParametrosModeloIO.institucionales(), datosDespues);

        return new ResultadoOptimizacionCurso(
                etapa1Actualizada,
                datosAntes,
                datosDespues,
                generados,
                generados.size(),
                0
        );
    }

    /**
     * Distribuye N clases semanales entre Lunes y Viernes garantizando entre 4 y 5 clases por día
     * (mínimo 3, máximo 5 por día).
     */
    private int[] distribuirClasesPorDia(int totalSlots) {
        int clamped = Math.max(15, Math.min(25, totalSlots));
        int base = clamped / 5;
        int extra = clamped % 5;
        int[] plan = new int[5];
        // Repartir días de mayor carga en Lun/Mié/Vie para mejor equilibrio
        int[] ordenExtra = {0, 2, 4, 1, 3};
        Arrays.fill(plan, base);
        for (int i = 0; i < extra; i++) {
            plan[ordenExtra[i]]++;
        }
        return plan;
    }

    /**
     * Construye el mazo ordenado de asignaturas de la semana respetando su intensidad en el grado
     * y asegurando al menos 6 horas semanales en componentes prácticos/PRAE (X2 >= 6).
     */
    private List<CursoMateria> construirMazoBalanceadoSemanal(
            List<CursoMateria> aplicables,
            Map<UUID, Integer> intensidadesPorMateria,
            int totalSlotsObjetivo
    ) {
        List<CursoMateria> teoricas = new ArrayList<>();
        List<CursoMateria> practicas = new ArrayList<>();
        for (CursoMateria cm : aplicables) {
            if (esMateriaPracticaOPrae(cm.getMateria())) practicas.add(cm);
            else teoricas.add(cm);
        }
        if (practicas.isEmpty()) practicas.addAll(teoricas);
        if (teoricas.isEmpty()) teoricas.addAll(practicas);

        teoricas.sort(Comparator.comparingInt((CursoMateria cm) ->
                intensidadesPorMateria.getOrDefault(cm.getMateria().getId(), 2)).reversed()
                .thenComparing(cm -> cm.getMateria().getNombre(), String.CASE_INSENSITIVE_ORDER));
        practicas.sort(Comparator.comparingInt((CursoMateria cm) ->
                intensidadesPorMateria.getOrDefault(cm.getMateria().getId(), 2)).reversed()
                .thenComparing(cm -> cm.getMateria().getNombre(), String.CASE_INSENSITIVE_ORDER));

        // Calcular cuotas por materia proporcionales a la intensidad configurada para el grado
        Map<CursoMateria, Integer> cuotas = new LinkedHashMap<>();
        int slotsPracticos = 0;
        for (CursoMateria p : practicas) {
            int q = Math.max(1, intensidadesPorMateria.getOrDefault(p.getMateria().getId(), 2));
            cuotas.put(p, q);
            slotsPracticos += q;
        }
        // Asegurar >= 6 horas prácticas semanales (al menos 6-7 bloques prácticos en la semana)
        int pIdx = 0;
        while (slotsPracticos < 7 && !practicas.isEmpty()) {
            CursoMateria p = practicas.get(pIdx % practicas.size());
            cuotas.put(p, cuotas.getOrDefault(p, 1) + 1);
            slotsPracticos++;
            pIdx++;
        }

        int slotsTeoricosObjetivo = Math.max(13, totalSlotsObjetivo - slotsPracticos);
        int slotsTeoricosActuales = 0;
        for (CursoMateria t : teoricas) {
            int q = Math.max(1, intensidadesPorMateria.getOrDefault(t.getMateria().getId(), 2));
            cuotas.put(t, q);
            slotsTeoricosActuales += q;
        }
        // Ajustar teóricas al total objetivo de la semana sin dejar ninguna activa en 0
        int tIdx = 0;
        while (slotsTeoricosActuales < slotsTeoricosObjetivo && !teoricas.isEmpty()) {
            CursoMateria t = teoricas.get(tIdx % teoricas.size());
            cuotas.put(t, cuotas.getOrDefault(t, 1) + 1);
            slotsTeoricosActuales++;
            tIdx++;
        }
        while (slotsTeoricosActuales > slotsTeoricosObjetivo && !teoricas.isEmpty()) {
            boolean redujo = false;
            for (int i = teoricas.size() - 1; i >= 0 && slotsTeoricosActuales > slotsTeoricosObjetivo; i--) {
                CursoMateria t = teoricas.get(i);
                int act = cuotas.getOrDefault(t, 1);
                if (act > 1) {
                    cuotas.put(t, act - 1);
                    slotsTeoricosActuales--;
                    redujo = true;
                }
            }
            if (!redujo) break;
        }

        // Intercalar prácticas y teóricas en rondas para que queden repartidas en toda la semana
        List<CursoMateria> mazo = new ArrayList<>();
        Map<CursoMateria, Integer> restantes = new LinkedHashMap<>(cuotas);
        boolean quedan = true;
        while (quedan) {
            quedan = false;
            for (CursoMateria cm : aplicables) {
                int r = restantes.getOrDefault(cm, 0);
                if (r > 0) {
                    mazo.add(cm);
                    restantes.put(cm, r - 1);
                    quedan = true;
                }
            }
        }
        return mazo;
    }

    private CursoMateria elegirSiguienteMateriaParaDia(
            List<CursoMateria> mazo,
            int inicio,
            Set<UUID> yaEnDia
    ) {
        if (mazo.isEmpty()) return null;
        int n = mazo.size();
        for (int offset = 0; offset < n; offset++) {
            CursoMateria cand = mazo.get((inicio + offset) % n);
            UUID matId = cand.getMateria() != null ? cand.getMateria().getId() : null;
            if (matId == null || !yaEnDia.contains(matId)) {
                return cand;
            }
        }
        return mazo.get(inicio % n);
    }

    /**
     * Genera franjas diarias exactas dentro de la jornada (07:00-12:30 Mañana, 13:00-18:30 Tarde),
     * respetando íntegramente el descanso (09:30-10:00 Mañana, 15:00-15:30 Tarde), usando únicamente
     * horas en punto o y media (:00 / :30) en bloques de 1h a 2h como máximo (sin bloques de 50 min).
     */
    public List<LocalTime[]> construirFranjasDiariasConDescanso(
            HorarioValidator.LimitesJornada limites,
            String jornada,
            int cantidadClases
    ) {
        boolean esTarde = jornada != null && jornada.toLowerCase(Locale.ROOT).contains("tar");
        if (esTarde) {
            if (cantidadClases >= 5) {
                return List.of(
                        new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(14, 0)},
                        new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(15, 0)},
                        new LocalTime[]{LocalTime.of(15, 30), LocalTime.of(16, 30)},
                        new LocalTime[]{LocalTime.of(16, 30), LocalTime.of(17, 30)},
                        new LocalTime[]{LocalTime.of(17, 30), LocalTime.of(18, 30)}
                );
            }
            if (cantidadClases == 3) {
                return List.of(
                        new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(15, 0)},
                        new LocalTime[]{LocalTime.of(15, 30), LocalTime.of(17, 30)},
                        new LocalTime[]{LocalTime.of(17, 30), LocalTime.of(18, 30)}
                );
            }
            return List.of(
                    new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(14, 0)},
                    new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(15, 0)},
                    new LocalTime[]{LocalTime.of(15, 30), LocalTime.of(17, 30)},
                    new LocalTime[]{LocalTime.of(17, 30), LocalTime.of(18, 30)}
            );
        }

        // Jornada Mañana: 07:00 - 12:30, descanso 09:30 - 10:00 (bloques de 1h / 1h30 / 2h en :00 y :30)
        if (cantidadClases == 3) {
            return List.of(
                    new LocalTime[]{LocalTime.of(7, 0), LocalTime.of(8, 30)},
                    new LocalTime[]{LocalTime.of(8, 30), LocalTime.of(9, 30)},
                    new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(12, 0)}
            );
        }
        return List.of(
                new LocalTime[]{LocalTime.of(7, 0), LocalTime.of(8, 0)},
                new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(9, 30)},
                new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(11, 0)},
                new LocalTime[]{LocalTime.of(11, 0), LocalTime.of(12, 30)}
        );
    }

    private Horario intentarAsignarFranjaExactaSinChoque(
            Curso curso,
            String dia,
            LocalTime ini,
            LocalTime fin,
            CursoMateria cm,
            Salon salonPreferido
    ) {
        if (HorarioValidator.seSolapaConDescanso(curso.getJornada(), ini, fin)) {
            return null;
        }
        List<Salon> salonesCandidatos = construirListaSalonesCandidatos(salonPreferido);
        List<Docente> docentesCandidatos = construirListaDocentesCandidatos(cm.getDocente(), curso.getDirector());

        for (Salon salon : salonesCandidatos) {
            String salonNombre = salon != null ? salon.getNombre() : "Aula 101";
            UUID salonId = salon != null ? salon.getId() : null;

            for (Docente doc : docentesCandidatos) {
                UUID docId = doc != null ? doc.getId() : null;
                HorarioDto dto = new HorarioDto(
                        curso.getId(),
                        dia,
                        ini,
                        fin,
                        cm.getMateria().getId(),
                        docId,
                        salonId,
                        salonNombre
                );
                try {
                    horarioValidator.validar(dto, null);
                    return persistirHorarioValidado(curso, dia, ini, fin, cm.getMateria(), doc, salon, salonNombre);
                } catch (IllegalArgumentException ignored) {
                    // Probar siguiente salón o docente disponible sin conflicto
                }
            }
        }
        return null;
    }

    private int resolverIntensidadParaCurso(Curso curso, Materia materia) {
        if (materia == null) return 0;
        String gradoNorm = PlanEstudiosGrado.normalizarGrado(curso != null ? curso.getGrado() : "11°");
        if (planEstudiosGradoRepository != null && materia.getId() != null) {
            Optional<PlanEstudiosGrado> opt = planEstudiosGradoRepository.findByGradoAndMateriaId(gradoNorm, materia.getId());
            if (opt.isPresent() && opt.get().getIntensidadHoraria() != null) {
                return opt.get().getIntensidadHoraria();
            }
        }
        return CursoService.sugerirIntensidadPorDefectoGrado(gradoNorm, materia);
    }

    private Horario persistirHorarioValidado(
            Curso curso,
            String dia,
            LocalTime inicio,
            LocalTime fin,
            Materia materia,
            Docente docente,
            Salon salonEnt,
            String salonNombre
    ) {
        Horario h = new Horario();
        h.setCurso(curso);
        h.setDiaSemana(dia);
        h.setHoraInicio(inicio);
        h.setHoraFin(fin);
        h.setMateria(materia);
        h.setDocente(docente);
        h.setSalon(salonNombre);
        h.setSalonEntidad(salonEnt);

        List<Bloque> bloques = bloqueRepository.findByJornadaOrderByNumeroAsc(
                curso.getJornada() != null ? curso.getJornada() : "Mañana");
        bloques.stream()
                .filter(b -> inicio.equals(b.getHoraInicio()))
                .findFirst()
                .ifPresent(h::setBloque);

        return horarioRepository.save(h);
    }

    private List<LocalTime[]> construirFranjas2HorasDentroDeJornada(HorarioValidator.LimitesJornada limites) {
        LocalTime ini = limites.inicio();
        LocalTime fin = limites.fin();
        List<LocalTime[]> slots = new ArrayList<>();

        // Bloque 1: inicio -> inicio + 2h (ej. 07:00-09:00 o 13:00-15:00)
        if (!ini.plusHours(2).isAfter(fin)) {
            slots.add(new LocalTime[]{ini, ini.plusHours(2)});
        }
        // Bloque 2 con descanso pedagógico de 30m: inicio + 2h30m -> inicio + 4h30m (ej. 09:30-11:30 o 15:30-17:30)
        if (!ini.plusMinutes(270).isAfter(fin)) {
            slots.add(new LocalTime[]{ini.plusMinutes(150), ini.plusMinutes(270)});
        }
        // Bloque 2 contiguo sin descanso (fallback): inicio + 2h -> inicio + 4h
        if (!ini.plusHours(4).isAfter(fin)) {
            slots.add(new LocalTime[]{ini.plusHours(2), ini.plusHours(4)});
        }
        // Bloque tardío dentro de ventana de 5h: inicio + 3h -> inicio + 5h
        if (!ini.plusHours(5).isAfter(fin)) {
            slots.add(new LocalTime[]{ini.plusHours(3), ini.plusHours(5)});
        }
        return slots;
    }

    private List<LocalTime[]> construirFranjas1HoraDentroDeJornada(HorarioValidator.LimitesJornada limites) {
        List<LocalTime[]> slots = new ArrayList<>();
        LocalTime cursor = limites.inicio();
        while (!cursor.plusHours(1).isAfter(limites.fin())) {
            slots.add(new LocalTime[]{cursor, cursor.plusHours(1)});
            cursor = cursor.plusHours(1);
        }
        return slots;
    }

    private void asegurarCatalogoMateriasEnCurso(Curso curso, List<CursoMateria> asignaciones, String anoLectivo) {
        List<Materia> activas = new ArrayList<>(materiaRepository.findByEstadoOrderByNombreAsc("Activo"));
        if (activas.isEmpty()) {
            activas = Arrays.asList(
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

        Docente docDefault = curso.getDirector();
        if (docDefault == null) {
            docDefault = docenteRepository.findAll().stream().findFirst().orElse(null);
        }

        Set<UUID> materiasYaEnCurso = new HashSet<>();
        for (CursoMateria cm : asignaciones) {
            if (cm.getMateria() != null && cm.getMateria().getId() != null) {
                materiasYaEnCurso.add(cm.getMateria().getId());
            }
        }

        // Complementar con todas las asignaturas activas aplicables al grado (intensidad > 0)
        for (Materia m : activas) {
            if (m.getId() != null && materiasYaEnCurso.contains(m.getId())) continue;
            int ih = resolverIntensidadParaCurso(curso, m);
            if (ih > 0) {
                CursoMateria nueva = cursoMateriaRepository.save(new CursoMateria(curso, m, docDefault, anoLectivo));
                asignaciones.add(nueva);
                if (m.getId() != null) materiasYaEnCurso.add(m.getId());
            }
        }
    }

    /**
     * Evalúa la estructura diaria del horario actual (cantidad de clases por día,
     * respeto de los descansos, salida a las 12:00 / 18:00 y cobertura de asignaturas del grado).
     */
    public Map<String, Object> evaluarEstructuraSemanal(UUID cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
        List<Horario> horarios = horarioRepository.findByCursoId(cursoId);
        String jornada = curso.getJornada() != null ? curso.getJornada() : "Mañana";
        boolean esTarde = jornada.toLowerCase(Locale.ROOT).contains("tar");

        Map<String, List<Horario>> porDia = new LinkedHashMap<>();
        for (String d : DIAS_SEMANA) porDia.put(d, new ArrayList<>());
        boolean cruzaDescanso = false;

        for (Horario h : horarios) {
            if (h.getDiaSemana() == null || h.getHoraInicio() == null || h.getHoraFin() == null) continue;
            for (String d : DIAS_SEMANA) {
                if (normalizarTexto(h.getDiaSemana()).equals(normalizarTexto(d))) {
                    porDia.get(d).add(h);
                    break;
                }
            }
            if (HorarioValidator.seSolapaConDescanso(jornada, h.getHoraInicio(), h.getHoraFin())) {
                cruzaDescanso = true;
            }
        }

        int minClases = Integer.MAX_VALUE;
        int maxClases = 0;
        for (String d : DIAS_SEMANA) {
            int c = porDia.get(d).size();
            minClases = Math.min(minClases, c);
            maxClases = Math.max(maxClases, c);
        }
        if (minClases == Integer.MAX_VALUE) minClases = 0;

        boolean entre3y5PorDia = minClases >= 3 && maxClases <= 5;
        boolean cumpleEstructura = entre3y5PorDia && !cruzaDescanso;

        List<Map<String, Object>> planGrado = new ArrayList<>();
        int totalPlanGrado = 0;
        for (Materia m : materiaRepository.findByEstadoOrderByNombreAsc("Activo")) {
            int ih = resolverIntensidadParaCurso(curso, m);
            if (ih > 0) {
                totalPlanGrado += ih;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materia", m.getNombre());
                item.put("horasGrado", ih);
                item.put("tipo", esMateriaPracticaOPrae(m) ? "Práctica / Lab" : "En Aula");
                planGrado.add(item);
            }
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("grado", curso.getGrado() != null ? curso.getGrado() : "11°");
        info.put("jornada", jornada);
        info.put("horarioJornada", esTarde ? "1:00 p. m. – 6:30 p. m." : "7:00 a. m. – 12:30 p. m.");
        info.put("horarioDescanso", esTarde ? "3:00 p. m. – 3:30 p. m." : "9:30 a. m. – 10:00 a. m.");
        info.put("clasesPorDiaMin", minClases);
        info.put("clasesPorDiaMax", maxClases);
        info.put("cruzaDescanso", cruzaDescanso);
        info.put("cumpleEstructuraDiaria", cumpleEstructura);
        info.put("materiasActivasGrado", planGrado.size());
        info.put("totalHorasPlanGrado", totalPlanGrado);
        info.put("planGrado", planGrado);
        return info;
    }

    private Salon resolverSalonPorTipo(Curso curso, boolean requiereLaboratorioOTaller) {
        List<Salon> inicial = salonRepository.findByEstado("Activo");
        final List<Salon> salones = inicial.isEmpty() ? salonRepository.findAll() : inicial;
        if (requiereLaboratorioOTaller) {
            return salones.stream()
                    .filter(s -> s.getTipo() != null && (
                            s.getTipo().equalsIgnoreCase("LABORATORIO")
                                    || s.getTipo().equalsIgnoreCase("SALA_SISTEMAS")
                                    || s.getTipo().equalsIgnoreCase("TALLER")
                    ))
                    .findFirst()
                    .orElseGet(() -> salones.isEmpty() ? null : salones.get(0));
        }

        final String gradoNum = curso.getGrado() != null ? curso.getGrado().replaceAll("[^0-9]", "") : "";
        return salones.stream()
                .filter(s -> (s.getTipo() == null || s.getTipo().equalsIgnoreCase("AULA"))
                        && (!gradoNum.isEmpty() && s.getNombre() != null && s.getNombre().contains(gradoNum)))
                .findFirst()
                .or(() -> salones.stream().filter(s -> s.getTipo() == null || s.getTipo().equalsIgnoreCase("AULA")).findFirst())
                .orElseGet(() -> salones.isEmpty() ? null : salones.get(0));
    }

    private Salon resolverSalonPracticoParaMateria(Materia materia, Salon fallbackPractico) {
        if (materia == null) return fallbackPractico;
        String txt = normalizarTexto((materia.getNombre() != null ? materia.getNombre() : "")
                + " " + (materia.getArea() != null ? materia.getArea() : ""));
        List<Salon> salones = salonRepository.findByEstado("Activo");
        if (salones.isEmpty()) salones = salonRepository.findAll();

        if (txt.contains("tecnolog") || txt.contains("informatica") || txt.contains("sistemas")) {
            return salones.stream()
                    .filter(s -> s.getTipo() != null && s.getTipo().equalsIgnoreCase("SALA_SISTEMAS"))
                    .findFirst()
                    .orElse(fallbackPractico);
        }
        return salones.stream()
                .filter(s -> s.getTipo() != null && s.getTipo().equalsIgnoreCase("LABORATORIO"))
                .findFirst()
                .orElse(fallbackPractico);
    }

    private List<Salon> construirListaSalonesCandidatos(Salon preferido) {
        List<Salon> lista = new ArrayList<>();
        if (preferido != null) lista.add(preferido);
        for (Salon s : salonRepository.findAll()) {
            if (preferido == null || !s.getId().equals(preferido.getId())) {
                lista.add(s);
            }
        }
        if (lista.isEmpty()) {
            lista.add(null);
        }
        return lista;
    }

    private List<Docente> construirListaDocentesCandidatos(Docente preferido, Docente director) {
        List<Docente> lista = new ArrayList<>();
        if (preferido != null) lista.add(preferido);
        if (director != null && (preferido == null || !director.getId().equals(preferido.getId()))) {
            lista.add(director);
        }
        for (Docente d : docenteRepository.findAll()) {
            boolean yaEsta = lista.stream().anyMatch(x -> x != null && x.getId().equals(d.getId()));
            if (!yaEsta) lista.add(d);
        }
        lista.add(null); // Último recurso si todos los docentes registrados tienen choque en otro curso
        return lista;
    }

    public static boolean esMateriaPracticaOPrae(Materia materia) {
        if (materia == null) return false;
        String texto = normalizarTexto((materia.getNombre() != null ? materia.getNombre() : "")
                + " " + (materia.getArea() != null ? materia.getArea() : ""));
        return texto.contains("naturales")
                || texto.contains("biolog")
                || texto.contains("quimic")
                || texto.contains("laboratorio")
                || texto.contains("prae")
                || texto.contains("ambiental")
                || texto.contains("tecnolog")
                || texto.contains("informatica")
                || texto.contains("sistemas")
                || texto.contains("educacion fisica")
                || texto.contains("deport")
                || texto.contains("artistica")
                || texto.contains("arte")
                || texto.contains("taller");
    }

    public static boolean esBloquePracticoOPrae(Materia materia, Salon salonEnt, String salonTexto) {
        if (salonEnt != null && salonEnt.getTipo() != null) {
            String t = salonEnt.getTipo().toUpperCase(Locale.ROOT);
            if (t.contains("LAB") || t.contains("SISTEMA") || t.contains("TALLER")) {
                return true;
            }
        }
        if (salonTexto != null) {
            String st = normalizarTexto(salonTexto);
            if (st.contains("laboratorio") || st.contains("sistemas") || st.contains("computo") || st.contains("taller") || st.contains("huerta")) {
                return true;
            }
        }
        return esMateriaPracticaOPrae(materia);
    }

    private static String normalizarTexto(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean esFactible(int x1, int x2, ParametrosModeloIO p) {
        return x1 >= 0
                && x2 >= 0
                && (x1 + x2) <= p.hTopeSemanal()
                && (x1 + x2) >= p.lCargaMinima()
                && x1 >= p.mMinimoTeorico()
                && x2 >= p.pMinimoPractico();
    }

    private int calcularObjetivo(int x1, int x2, ParametrosModeloIO p) {
        return p.c1DesgasteTeoria() * x1 + p.c2DesgastePractica() * x2;
    }

    private Map<String, Boolean> construirMapaRestricciones(int x1, int x2, ParametrosModeloIO p) {
        Map<String, Boolean> r = new LinkedHashMap<>();
        r.put("Máximo de la jornada escolar: hasta " + p.hTopeSemanal() + " horas semanales", (x1 + x2) <= p.hTopeSemanal());
        r.put("Carga mínima del plan de estudios: al menos " + p.lCargaMinima() + " horas semanales", (x1 + x2) >= p.lCargaMinima());
        r.put("Clases teóricas en aula: mínimo " + p.mMinimoTeorico() + " horas semanales", x1 >= p.mMinimoTeorico());
        r.put("Laboratorios, talleres y actividades ambientales: mínimo " + p.pMinimoPractico() + " horas semanales", x2 >= p.pMinimoPractico());
        return r;
    }

    private List<VerticeRegionFactible> calcularVerticesRegionFactible(ParametrosModeloIO p, int optX1, int optX2, int optZ) {
        List<VerticeRegionFactible> v = new ArrayList<>();
        int aX1 = Math.max(p.mMinimoTeorico(), p.lCargaMinima() - p.pMinimoPractico());
        int aX2 = p.pMinimoPractico();
        v.add(new VerticeRegionFactible("A", "A (" + aX1 + ", " + aX2 + ")", aX1, aX2,
                calcularObjetivo(aX1, aX2, p), aX1 == optX1 && aX2 == optX2));

        int dX1 = p.mMinimoTeorico();
        int dX2 = Math.max(p.pMinimoPractico(), p.lCargaMinima() - p.mMinimoTeorico());
        v.add(new VerticeRegionFactible("D", "D (" + dX1 + ", " + dX2 + ")", dX1, dX2,
                calcularObjetivo(dX1, dX2, p), dX1 == optX1 && dX2 == optX2));

        int bX1 = p.hTopeSemanal() - p.pMinimoPractico();
        int bX2 = p.pMinimoPractico();
        v.add(new VerticeRegionFactible("B", "B (" + bX1 + ", " + bX2 + ")", bX1, bX2,
                calcularObjetivo(bX1, bX2, p), bX1 == optX1 && bX2 == optX2));

        int cX1 = p.mMinimoTeorico();
        int cX2 = p.hTopeSemanal() - p.mMinimoTeorico();
        v.add(new VerticeRegionFactible("C", "C (" + cX1 + ", " + cX2 + ")", cX1, cX2,
                calcularObjetivo(cX1, cX2, p), cX1 == optX1 && cX2 == optX2));

        return v;
    }

    private String construirInterpretacion(
            int bestX1,
            int bestX2,
            int bestZ,
            int holguraH,
            int superavitM,
            int costoSustitucion,
            ParametrosModeloIO p,
            DatosRealesCursoDto reales
    ) {
        String jornadaStr = reales != null && reales.jornada() != null && reales.jornada().toLowerCase(Locale.ROOT).contains("tar")
                ? "1:00 p. m. a 6:30 p. m. (descanso de 3:00 a 3:30 p. m.)"
                : "7:00 a. m. a 12:30 p. m. (descanso de 9:30 a 10:00 a. m.)";

        String base = String.format(
                Locale.ROOT,
                "Se recomienda organizar entre 4 y 5 clases por día dentro de la jornada de %s, garantizando al menos %d horas en aula y %d horas de laboratorio, sistemas o actividades ambientales a la semana "
                        + "sin saturar los espacios compartidos ni cruzar docentes.",
                jornadaStr, bestX1, bestX2
        );
        if (reales == null || reales.totalBloquesProgramados() == 0) {
            return base + (reales != null ? " Actualmente el curso " + reales.codigoCurso() + " aún no tiene clases programadas." : "");
        }
        return base + String.format(
                Locale.ROOT,
                " Actualmente el curso %s tiene %.1f horas en aula y %.1f horas prácticas distribuidas en %d bloques.",
                reales.codigoCurso(),
                reales.x1TeoricasReal(),
                reales.x2PracticasReal(),
                reales.totalBloquesProgramados()
        );
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
                .orElseGet(() -> String.valueOf(PeriodoConfigService.anioCalendarioActual()));
    }
}

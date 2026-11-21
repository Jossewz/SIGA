package com.siga.siga_iea.clases;

import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.entity.CursoMateria;
import com.siga.siga_iea.clases.entity.Horario;
import com.siga.siga_iea.clases.entity.Materia;
import com.siga.siga_iea.clases.entity.Salon;
import com.siga.siga_iea.clases.repository.CursoMateriaRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.clases.repository.HorarioRepository;
import com.siga.siga_iea.clases.repository.MateriaRepository;
import com.siga.siga_iea.clases.repository.SalonRepository;
import com.siga.siga_iea.clases.service.OptimizadorHorarioService;
import com.siga.siga_iea.clases.validation.HorarioValidator;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Usuario;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Transactional
class OptimizadorHorarioServiceTest {

    @Autowired private OptimizadorHorarioService optimizadorHorarioService;
    @Autowired private com.siga.siga_iea.clases.service.CursoService cursoService;
    @Autowired private HorarioValidator horarioValidator;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private CursoMateriaRepository cursoMateriaRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private SalonRepository salonRepository;
    @Autowired private DocenteRepository docenteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MockMvc mockMvc;

    private Curso curso;
    private Docente docente;

    @BeforeEach
    void setUp() {
        usuarioRepository.save(new Usuario("admin.io@ieaci.edu.co", "h", "ADMIN", "900001"));

        docente = new Docente();
        docente.setNombres("Carlos");
        docente.setApellidos("Acevedo");
        docente.setNumeroDocumento("900002");
        docente.setEstado("Activo");
        docente = docenteRepository.save(docente);

        salonRepository.save(new Salon("AULA-101-IO", "Aula 101 - Grado 11", 40, "Bloque A", "AULA", "Activo"));
        salonRepository.save(new Salon("LAB-CIENCIAS-IO", "Laboratorio de Ciencias Integradas", 30, "Bloque B", "LABORATORIO", "Activo"));
        salonRepository.save(new Salon("SALA-SISTEMAS-IO", "Sala de Cómputo y Robótica", 35, "Bloque B", "SALA_SISTEMAS", "Activo"));

        curso = new Curso();
        curso.setGrado("11°");
        curso.setGrupo("01");
        curso.setJornada("Tarde");
        curso.setCuposMaximos(35);
        curso.setAnoLectivo("2026");
        curso.setDirector(docente);
        curso = cursoRepository.save(curso);

        Materia matTeo = materiaRepository.save(new Materia("Matemáticas", "Ciencias Exactas"));
        Materia matPrac = materiaRepository.save(new Materia("Ciencias Naturales", "Laboratorio PRAE"));
        cursoMateriaRepository.save(new CursoMateria(curso, matTeo, docente, "2026"));
        cursoMateriaRepository.save(new CursoMateria(curso, matPrac, docente, "2026"));
    }

    @Test
    @DisplayName("Etapa 1 (Modelo MILP IO): Calcula la solución óptima X1*=14, X2*=6 con desgaste mínimo Z*=46 y vértices A, D, B, C")
    void testEtapa1SolucionOptimaVerticesYPertinencia() {
        OptimizadorHorarioService.ResultadoModeloMILP res = optimizadorHorarioService.resolverModelo();

        assertTrue(res.factible());
        assertEquals(14, res.x1OptimoTeoricas(), "X1* óptimo debe ser 14 horas teóricas");
        assertEquals(6, res.x2OptimoPracticas(), "X2* óptimo debe ser 6 horas prácticas/PRAE");
        assertEquals(46, res.zMinimoDesgaste(), "Z* mínimo debe ser 46");
        assertEquals(20, res.totalHorasOptimo(), "Total semanal X1*+X2* debe ser 20 horas");
        assertEquals(10, res.holguraJornadaH(), "Holgura respecto a H=30 debe ser 10 horas");
        assertEquals(2, res.superavitTeoriaM(), "Superávit respecto a M=12 debe ser 2 horas");
        assertEquals(0, res.superavitPracticaP(), "Mínimo práctico P=6 debe estar activo con holgura 0");
        assertEquals(1, res.costoMarginalSustitucion(), "Costo marginal c2 - c1 debe ser 1");
        assertEquals(2, res.precioSombraCargaMinima(), "Precio sombra de carga mínima L debe ser 2");

        // Verificar los 4 vértices analíticos de la región factible: A(14,6)=46, D(12,8)=48, B(24,6)=66, C(12,18)=78
        assertEquals(4, res.verticesRegionFactible().size());
        assertEquals(46, res.verticesRegionFactible().get(0).z());
        assertEquals(48, res.verticesRegionFactible().get(1).z());
        assertEquals(66, res.verticesRegionFactible().get(2).z());
        assertEquals(78, res.verticesRegionFactible().get(3).z());

        // Verificar tabla de validación de pertinencia institucional (H, L, M, P, c1/c2)
        assertEquals(5, res.tablaPertinencia().size());
        assertTrue(res.tablaPertinencia().stream().allMatch(OptimizadorHorarioService.ParametroPertinenciaDto::pertinente));

        // Evaluador de puntos factibles e infactibles
        var evalOpt = optimizadorHorarioService.evaluarCombinacion(14, 6, null);
        assertTrue(evalOpt.factible());
        assertEquals(46, evalOpt.z());
        assertEquals(0, evalOpt.diferenciaContraOptimo());

        var evalInfactible = optimizadorHorarioService.evaluarCombinacion(10, 5, null);
        assertFalse(evalInfactible.factible());

        List<Map<String, Object>> top5 = optimizadorHorarioService.topCombinaciones(5, null);
        assertEquals(5, top5.size());
        assertEquals(46, top5.get(0).get("desgasteZ"));
    }

    @Test
    @DisplayName("Etapa 2 (Operativa): Genera 4 a 5 clases por día (Mín 3, Máx 5) respetando descansos hasta salida y 0 choques")
    void testEtapa2GeneraHorarioSinChoquesYCumpleOptimo() {
        OptimizadorHorarioService.ResultadoOptimizacionCurso resultado =
                optimizadorHorarioService.optimizarYAsignarHorario(curso.getId(), true);

        assertEquals(0, resultado.choquesDetectados());
        assertTrue(resultado.bloquesCreados() >= 20 && resultado.bloquesCreados() <= 25,
                "Debe programar entre 20 y 25 clases semanales (4 a 5 por día)");
        assertTrue(resultado.datosDespues().x1TeoricasReal() >= 12.0, "Debe cubrir al menos 12h teóricas");
        assertTrue(resultado.datosDespues().x2PracticasReal() >= 6.0, "Debe cubrir al menos 6h prácticas/PRAE");
        assertTrue(resultado.datosDespues().totalHorasReal() >= 20.0 && resultado.datosDespues().totalHorasReal() <= 30.0);
        assertTrue(resultado.datosDespues().cumpleRegionFactible());

        List<Horario> guardados = horarioRepository.findByCursoId(curso.getId());
        Map<String, List<Horario>> porDia = new java.util.LinkedHashMap<>();
        for (Horario h : guardados) {
            porDia.computeIfAbsent(h.getDiaSemana(), k -> new java.util.ArrayList<>()).add(h);
            assertFalse(HorarioValidator.seSolapaConDescanso(curso.getJornada(), h.getHoraInicio(), h.getHoraFin()),
                    "Ninguna clase debe cruzar el descanso: " + h.getDiaSemana() + " " + h.getHoraInicio() + "-" + h.getHoraFin());
            long mins = java.time.Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes();
            assertTrue(mins >= 60 && mins <= 120 && (h.getHoraFin().getMinute() == 0 || h.getHoraFin().getMinute() == 30),
                    "Cada bloque debe durar entre 1h y 2h y terminar en :00 o :30, fue " + h.getHoraInicio() + "-" + h.getHoraFin());
        }

        // Todos los 5 días deben tener entre 3 y 5 clases y terminar a las 18:30 (jornada Tarde)
        assertEquals(5, porDia.size(), "Debe cubrir de Lunes a Viernes");
        for (Map.Entry<String, List<Horario>> entry : porDia.entrySet()) {
            int countDia = entry.getValue().size();
            assertTrue(countDia >= 3 && countDia <= 5,
                    "El día " + entry.getKey() + " debe tener entre 3 y 5 clases, tuvo: " + countDia);
            java.time.LocalTime maxFin = entry.getValue().stream()
                    .map(Horario::getHoraFin)
                    .max(java.time.LocalTime::compareTo)
                    .orElseThrow();
            assertEquals(java.time.LocalTime.of(18, 30), maxFin,
                    "La jornada Tarde en " + entry.getKey() + " debe finalizar a las 18:30");
        }

        // Verificar cero solapamientos entre todos los bloques generados en el mismo día
        for (int i = 0; i < guardados.size(); i++) {
            for (int j = i + 1; j < guardados.size(); j++) {
                Horario a = guardados.get(i);
                Horario b = guardados.get(j);
                if (a.getDiaSemana().equalsIgnoreCase(b.getDiaSemana())) {
                    assertFalse(
                            HorarioValidator.seSolapan(a.getHoraInicio(), a.getHoraFin(), b.getHoraInicio(), b.getHoraFin()),
                            "No debe existir solapamiento en " + a.getDiaSemana()
                    );
                }
            }
        }
    }

    @Test
    @DisplayName("Endpoints HTTP de Modelo IO y Optimización responden 200 OK con JSON estructurado")
    void testEndpointsHttpModeloIOYOptimizacion() throws Exception {
        mockMvc.perform(post("/clases/horarios/optimizar")
                        .param("cursoId", curso.getId().toString())
                        .with(user("admin.io@ieaci.edu.co").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.x1OptimoTeoricas").value(14))
                .andExpect(jsonPath("$.x2OptimoPracticas").value(6))
                .andExpect(jsonPath("$.zMinimoDesgaste").value(46))
                .andExpect(jsonPath("$.datosDespues.cumpleRegionFactible").value(true));

        mockMvc.perform(get("/clases/horarios/modelo-io")
                        .param("cursoId", curso.getId().toString())
                        .with(user("admin.io@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("optimo"))
                .andExpect(jsonPath("$.optimo.zMinimoDesgaste").value(46))
                .andExpect(jsonPath("$.pertinencia.length()").value(5))
                .andExpect(jsonPath("$.vertices.length()").value(4))
                .andExpect(jsonPath("$.estructura.grado").value("11°"));

        mockMvc.perform(get("/clases/horarios/modelo-io/evaluar")
                        .param("x1", "12")
                        .param("x2", "8")
                        .with(user("admin.io@ieaci.edu.co").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factible").value(true))
                .andExpect(jsonPath("$.desgasteZ").value(48))
                .andExpect(jsonPath("$.diferenciaContraOptimo").value(2));
    }

    @Test
    @DisplayName("Jornada Mañana respeta descanso 09:30-10:00, finaliza 12:00 e intensidad dinámica por grado valida límites")
    void testJornadaMananaDescansoYPlanDinamicoPorGrado() {
        Curso cursoManana = new Curso();
        cursoManana.setGrado("6°");
        cursoManana.setGrupo("01");
        cursoManana.setJornada("Mañana");
        cursoManana.setCuposMaximos(35);
        cursoManana.setAnoLectivo("2026");
        cursoManana.setDirector(docente);
        cursoManana = cursoRepository.save(cursoManana);

        OptimizadorHorarioService.ResultadoOptimizacionCurso res =
                optimizadorHorarioService.optimizarYAsignarHorario(cursoManana.getId(), true);

        assertTrue(res.bloquesCreados() >= 20 && res.bloquesCreados() <= 25);
        List<Horario> bloques = horarioRepository.findByCursoId(cursoManana.getId());
        Map<String, List<Horario>> porDia = new java.util.LinkedHashMap<>();
        for (Horario h : bloques) {
            porDia.computeIfAbsent(h.getDiaSemana(), k -> new java.util.ArrayList<>()).add(h);
            assertFalse(HorarioValidator.seSolapaConDescanso("Mañana", h.getHoraInicio(), h.getHoraFin()),
                    "No debe invadir el descanso de la mañana 09:30-10:00");
        }
        assertEquals(5, porDia.size());
        for (var entry : porDia.entrySet()) {
            int n = entry.getValue().size();
            assertTrue(n >= 3 && n <= 5, "Mañana " + entry.getKey() + " debe tener 3..5 clases, tuvo " + n);
            java.time.LocalTime maxFin = entry.getValue().stream().map(Horario::getHoraFin).max(java.time.LocalTime::compareTo).orElseThrow();
            assertEquals(java.time.LocalTime.of(12, 30), maxFin);
        }

        // Validar intensidad dinámica por grado (ej. Matemáticas 5h en 3° y 3h en 11°)
        Materia mat = materiaRepository.findAll().stream()
                .filter(m -> m.getNombre().toLowerCase().contains("matem"))
                .findFirst().orElseThrow();
        cursoService.guardarIntensidadesGrado("3°", Map.of(mat.getId(), 5));
        cursoService.guardarIntensidadesGrado("11°", Map.of(mat.getId(), 3));

        assertEquals(5, cursoService.resolverIntensidadPorGrado("3°", mat));
        assertEquals(3, cursoService.resolverIntensidadPorGrado("11°", mat));
        assertEquals(3, horarioValidator.resolverIntensidadPlanificada(curso, mat));

        // Validar rechazo cuando se intenta superar el tope de 30 horas semanales
        Materia matExtra1 = materiaRepository.save(new Materia("Español", "Humanidades"));
        Materia matExtra2 = materiaRepository.save(new Materia("Sociales", "Sociales"));
        assertThrows(IllegalArgumentException.class, () ->
                cursoService.guardarIntensidadesGrado("11°", Map.of(
                        mat.getId(), 12,
                        matExtra1.getId(), 12,
                        matExtra2.getId(), 10
                )));
    }
}

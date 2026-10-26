package com.siga.siga_iea.dashboard.service;

import com.siga.siga_iea.asistencias.repository.AsistenciaRepository;
import com.siga.siga_iea.calificaciones.repository.EvaluacionRepository;
import com.siga.siga_iea.certificados.entity.SolicitudCertificado;
import com.siga.siga_iea.certificados.repository.SolicitudCertificadoRepository;
import com.siga.siga_iea.clases.entity.Curso;
import com.siga.siga_iea.clases.repository.CursoEstudianteRepository;
import com.siga.siga_iea.clases.repository.CursoRepository;
import com.siga.siga_iea.dashboard.dto.DashboardStatsDTO;
import com.siga.siga_iea.dashboard.dto.SemaforoItemDTO;
import com.siga.siga_iea.matricula.entity.Matricula;
import com.siga.siga_iea.matricula.repository.MatriculaRepository;
import com.siga.siga_iea.reportes.entity.Reporte;
import com.siga.siga_iea.reportes.repository.ReporteRepository;
import com.siga.siga_iea.usuarios.entity.Docente;
import com.siga.siga_iea.usuarios.entity.Estudiante;
import com.siga.siga_iea.usuarios.repository.DocenteRepository;
import com.siga.siga_iea.usuarios.repository.EstudianteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;
    @Mock
    private DocenteRepository docenteRepository;
    @Mock
    private ReporteRepository reporteRepository;
    @Mock
    private SolicitudCertificadoRepository certificadoRepository;
    @Mock
    private MatriculaRepository matriculaRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private AsistenciaRepository asistenciaRepository;
    @Mock
    private EvaluacionRepository evaluacionRepository;
    @Mock
    private CursoEstudianteRepository cursoEstudianteRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                estudianteRepository,
                docenteRepository,
                reporteRepository,
                certificadoRepository,
                matriculaRepository,
                cursoRepository,
                asistenciaRepository,
                evaluacionRepository,
                cursoEstudianteRepository
        );
    }

    @Test
    @DisplayName("Dashboard retorna serie por defecto y 5 semáforos cuando no hay registros de asistencia")
    void testEstadisticasConBaseVacia() {
        when(estudianteRepository.count()).thenReturn(0L);
        when(docenteRepository.count()).thenReturn(0L);
        when(reporteRepository.count()).thenReturn(0L);
        when(matriculaRepository.count()).thenReturn(0L);
        when(cursoRepository.count()).thenReturn(0L);
        when(reporteRepository.findByEstado("Pendiente")).thenReturn(Collections.emptyList());
        when(certificadoRepository.findByEstado("Pendiente")).thenReturn(Collections.emptyList());
        when(asistenciaRepository.findResumenAsistenciaPorDia(any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(asistenciaRepository.findResumenAsistenciaPorFecha(any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(matriculaRepository.findAll()).thenReturn(Collections.emptyList());
        when(cursoEstudianteRepository.findAll()).thenReturn(Collections.emptyList());
        when(evaluacionRepository.count()).thenReturn(0L);
        when(cursoRepository.findAll()).thenReturn(Collections.emptyList());

        DashboardStatsDTO stats = dashboardService.obtenerEstadisticas();

        assertNotNull(stats);
        assertEquals("0%", stats.getAsistenciaHoy());
        assertEquals(93.7, stats.getPromedioAsistencia());
        assertEquals(15, stats.getAsistenciaLabels().size());
        assertEquals(15, stats.getAsistenciaValores().size());
        assertFalse(stats.getAsistenciaLabelsCsv().isBlank());
        assertFalse(stats.getAsistenciaValoresCsv().isBlank());

        // Distribución por defecto
        assertEquals(45, stats.getPorcentajePrimaria());
        assertEquals(40, stats.getPorcentajeSecundaria());
        assertEquals(15, stats.getPorcentajeMedia());

        // 5 Semáforos institucionales
        assertNotNull(stats.getSemaforos());
        assertEquals(5, stats.getSemaforos().size());
        assertEquals("Asistencia General", stats.getSemaforos().get(0).getTitulo());
        assertEquals("Proceso de Matrícula", stats.getSemaforos().get(1).getTitulo());
        assertEquals("Certificados Pendientes", stats.getSemaforos().get(2).getTitulo());
        assertEquals("Carga de Calificaciones", stats.getSemaforos().get(3).getTitulo());
        assertEquals("Alertas Disciplinarias", stats.getSemaforos().get(4).getTitulo());
    }

    @Test
    @DisplayName("Dashboard calcula correctamente asistencia real y distribución de alumnos")
    void testEstadisticasConDatosReales() {
        when(estudianteRepository.count()).thenReturn(100L);
        when(docenteRepository.count()).thenReturn(10L);
        when(reporteRepository.count()).thenReturn(2L);
        when(matriculaRepository.count()).thenReturn(100L);
        when(cursoRepository.count()).thenReturn(6L);

        Reporte r1 = new Reporte();
        Estudiante e1 = new Estudiante();
        e1.setNombres("Carlos");
        e1.setApellidos("Pérez");
        r1.setEstudiante(e1);
        r1.setRazon("Llegada tardía reiterada");
        when(reporteRepository.findByEstado("Pendiente")).thenReturn(List.of(r1));

        SolicitudCertificado sc1 = new SolicitudCertificado();
        sc1.setTipo("Certificado de Estudio");
        when(certificadoRepository.findByEstado("Pendiente")).thenReturn(List.of(sc1));

        // Asistencia hoy: 50 alumnos, 45 presentes = 90%
        Object[] rowHoy = new Object[]{50L, 45L};
        List<Object[]> listHoy = Collections.singletonList(rowHoy);
        when(asistenciaRepository.findResumenAsistenciaPorDia(any(LocalDate.class))).thenReturn(listHoy);

        // Asistencia últimos 30 días: 2 días registrados (95% y 85%)
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 2);
        Object[] hist1 = new Object[]{d1, 100L, 95L};
        Object[] hist2 = new Object[]{d2, 100L, 85L};
        List<Object[]> listHist = List.of(hist1, hist2);
        when(asistenciaRepository.findResumenAsistenciaPorFecha(any(LocalDate.class))).thenReturn(listHist);

        // Matrículas: 5 en Primaria (1°), 3 en Secundaria (9°), 2 en Media (11°) -> 50%, 30%, 20%
        Matricula m1 = new Matricula(); m1.setGrado("1°");
        Matricula m2 = new Matricula(); m2.setGrado("2°");
        Matricula m3 = new Matricula(); m3.setGrado("3°");
        Matricula m4 = new Matricula(); m4.setGrado("4°");
        Matricula m5 = new Matricula(); m5.setGrado("5°");
        Matricula m6 = new Matricula(); m6.setGrado("7°");
        Matricula m7 = new Matricula(); m7.setGrado("8°");
        Matricula m8 = new Matricula(); m8.setGrado("9°");
        Matricula m9 = new Matricula(); m9.setGrado("10°");
        Matricula m10 = new Matricula(); m10.setGrado("11°");
        when(matriculaRepository.findAll()).thenReturn(List.of(m1, m2, m3, m4, m5, m6, m7, m8, m9, m10));

        when(evaluacionRepository.count()).thenReturn(15L);

        Curso c1 = new Curso();
        Docente doc1 = new Docente();
        c1.setDirector(doc1);
        when(cursoRepository.findAll()).thenReturn(List.of(c1));

        DashboardStatsDTO stats = dashboardService.obtenerEstadisticas();

        assertNotNull(stats);
        assertEquals("90.0%", stats.getAsistenciaHoy());
        assertEquals(90.0, stats.getPromedioAsistencia());
        assertEquals(2, stats.getAsistenciaLabels().size());
        assertEquals("01/10,02/10", stats.getAsistenciaLabelsCsv());
        assertEquals("95.0,85.0", stats.getAsistenciaValoresCsv());

        // Distribución: 5 primaria, 3 secundaria, 2 media -> 50%, 30%, 20%
        assertEquals(5, stats.getTotalEstudiantesPrimaria());
        assertEquals(3, stats.getTotalEstudiantesSecundaria());
        assertEquals(2, stats.getTotalEstudiantesMedia());
        assertEquals(50, stats.getPorcentajePrimaria());
        assertEquals(30, stats.getPorcentajeSecundaria());
        assertEquals(20, stats.getPorcentajeMedia());

        // Semáforos con datos:
        // Asistencia 90% -> Óptimo
        SemaforoItemDTO semAsistencia = stats.getSemaforos().get(0);
        assertEquals("Óptimo", semAsistencia.getEstado());
        assertEquals("#1b6327", semAsistencia.getDotColor());

        // Matrícula 100/100 -> Al día
        SemaforoItemDTO semMatricula = stats.getSemaforos().get(1);
        assertEquals("Al día", semMatricula.getEstado());

        // Certificados 1 pendiente -> Pendiente (advertencia)
        SemaforoItemDTO semCerts = stats.getSemaforos().get(2);
        assertEquals("Pendiente", semCerts.getEstado());
        assertEquals("#8f5b12", semCerts.getDotColor());

        // Calificaciones 15 evaluaciones -> Al día
        SemaforoItemDTO semCalif = stats.getSemaforos().get(3);
        assertEquals("Al día", semCalif.getEstado());

        // Alertas disciplinarias 1 pendiente -> Atención
        SemaforoItemDTO semReportes = stats.getSemaforos().get(4);
        assertEquals("Atención", semReportes.getEstado());
        assertEquals("#a82a2a", semReportes.getDotColor());
    }
}

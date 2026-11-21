package com.siga.siga_iea.configuracion.service;

import com.siga.siga_iea.configuracion.entity.AnioLectivo;
import com.siga.siga_iea.configuracion.entity.ConfiguracionInstitucional;
import com.siga.siga_iea.configuracion.entity.PeriodoAcademico;
import com.siga.siga_iea.configuracion.repository.AnioLectivoRepository;
import com.siga.siga_iea.configuracion.repository.ConfiguracionInstitucionalRepository;
import com.siga.siga_iea.configuracion.repository.PeriodoAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PeriodoConfigService {

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final ConfiguracionInstitucionalRepository configuracionInstitucionalRepository;
    private final AnioLectivoRepository anioLectivoRepository;

    private volatile Integer cachedAnioActual;
    private volatile long cachedAnioTimestampMs;
    private static final long ANIO_CACHE_TTL_MS = 10_000L;

    public PeriodoConfigService(PeriodoAcademicoRepository periodoAcademicoRepository,
                                ConfiguracionInstitucionalRepository configuracionInstitucionalRepository,
                                AnioLectivoRepository anioLectivoRepository) {
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.configuracionInstitucionalRepository = configuracionInstitucionalRepository;
        this.anioLectivoRepository = anioLectivoRepository;
    }

    public static int anioCalendarioActual() {
        return Year.now(ZONA_COLOMBIA).getValue();
    }

    public void invalidarCacheAnio() {
        this.cachedAnioActual = null;
        this.cachedAnioTimestampMs = 0L;
    }

    public String obtenerAnioLectivoCodigo() {
        long now = System.currentTimeMillis();
        Integer cached = this.cachedAnioActual;
        if (cached != null && (now - this.cachedAnioTimestampMs) < ANIO_CACHE_TTL_MS) {
            return String.valueOf(cached);
        }
        AnioLectivo actual = obtenerAnioLectivoActual();
        int anio = actual != null && actual.getAnio() != null ? actual.getAnio() : anioCalendarioActual();
        this.cachedAnioActual = anio;
        this.cachedAnioTimestampMs = now;
        return String.valueOf(anio);
    }

    public AnioLectivo obtenerAnioLectivoActual() {
        int anioSistema = anioCalendarioActual();
        return anioLectivoRepository.findByEsActualTrue()
                .orElseGet(() -> anioLectivoRepository.findByAnio(anioSistema)
                        .orElseGet(() -> anioLectivoRepository.save(new AnioLectivo(
                                anioSistema, "ACTIVO",
                                LocalDate.of(anioSistema, 1, 15),
                                LocalDate.of(anioSistema, 11, 30),
                                true
                        ))));
    }

    public List<AnioLectivo> listarAniosLectivos() {
        return anioLectivoRepository.findAllByOrderByAnioDesc();
    }

    public List<PeriodoAcademico> listarPeriodos() {
        AnioLectivo anio = obtenerAnioLectivoActual();
        int a = anio.getAnio() != null ? anio.getAnio() : anioCalendarioActual();
        List<PeriodoAcademico> list = periodoAcademicoRepository.findByAnioLectivoOrderByNumeroPeriodoAsc(anio);
        if (list.isEmpty()) {
            list = List.of(
                    periodoAcademicoRepository.save(new PeriodoAcademico(anio, 1, "Primer Período", new BigDecimal("33.00"), LocalDate.of(a, 2, 1), LocalDate.of(a, 6, 15), "Activo")),
                    periodoAcademicoRepository.save(new PeriodoAcademico(anio, 2, "Segundo Período", new BigDecimal("33.00"), LocalDate.of(a, 7, 15), LocalDate.of(a, 9, 15), "Activo")),
                    periodoAcademicoRepository.save(new PeriodoAcademico(anio, 3, "Tercer Período", new BigDecimal("34.00"), LocalDate.of(a, 9, 16), LocalDate.of(a, 11, 30), "Activo"))
            );
        }
        return list;
    }

    public Map<Integer, BigDecimal> getPonderaciones() {
        Map<Integer, BigDecimal> map = new HashMap<>();
        List<PeriodoAcademico> periodos = listarPeriodos();
        for (PeriodoAcademico p : periodos) {
            map.put(p.getNumeroPeriodo(), p.getPesoPorcentaje());
        }
        map.putIfAbsent(1, new BigDecimal("33.00"));
        map.putIfAbsent(2, new BigDecimal("33.00"));
        map.putIfAbsent(3, new BigDecimal("34.00"));
        return map;
    }

    @Transactional
    public synchronized void actualizarPonderaciones(BigDecimal p1, BigDecimal p2, BigDecimal p3,
                                                     String fIni1, String fFin1,
                                                     String fIni2, String fFin2,
                                                     String fIni3, String fFin3) {
        actualizarPeriodoIndividual(1, "Primer Período", p1, fIni1, fFin1);
        actualizarPeriodoIndividual(2, "Segundo Período", p2, fIni2, fFin2);
        actualizarPeriodoIndividual(3, "Tercer Período", p3, fIni3, fFin3);
    }

    private void actualizarPeriodoIndividual(int numero, String nombre, BigDecimal peso, String fIni, String fFin) {
        AnioLectivo anio = obtenerAnioLectivoActual();
        PeriodoAcademico periodo = periodoAcademicoRepository.findByAnioLectivoIdAndNumeroPeriodo(anio.getId(), numero)
                .orElse(new PeriodoAcademico(anio, numero, nombre, peso != null ? peso : BigDecimal.valueOf(33.33), LocalDate.now(), LocalDate.now(), "Activo"));

        if (periodo.getAnioLectivo() == null) {
            periodo.setAnioLectivo(anio);
        }
        if (peso != null) periodo.setPesoPorcentaje(peso);
        if (fIni != null && !fIni.isBlank()) {
            try { periodo.setFechaInicio(LocalDate.parse(fIni.trim())); } catch (DateTimeParseException ignored) {}
        }
        if (fFin != null && !fFin.isBlank()) {
            try { periodo.setFechaFin(LocalDate.parse(fFin.trim())); } catch (DateTimeParseException ignored) {}
        }
        periodoAcademicoRepository.save(periodo);
    }

    public ConfiguracionInstitucional obtenerConfiguracionInstitucional() {
        String anioDefault = String.valueOf(anioCalendarioActual());
        return configuracionInstitucionalRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> configuracionInstitucionalRepository.save(new ConfiguracionInstitucional(
                        "800.124.567-2",
                        "Institución Educativa Ambientalista de Cartagena de Indias (IEACI)",
                        "San José de los Campanos, Mz 32 Lote 9-11",
                        "300 987 6543",
                        "contacto@ieaci.edu.co",
                        anioDefault
                )));
    }

    @Transactional
    public void guardarConfiguracionInstitucional(String nit, String nombre, String direccion, String telefono, String correo, String anoLectivo) {
        ConfiguracionInstitucional cfg = obtenerConfiguracionInstitucional();
        if (nit != null && !nit.isBlank()) cfg.setNit(nit.trim());
        if (nombre != null && !nombre.isBlank()) cfg.setNombreInst(nombre.trim());
        if (direccion != null) cfg.setDireccionInst(direccion.trim());
        if (telefono != null) cfg.setTelefonoInst(telefono.trim());
        if (correo != null) cfg.setCorreoInst(correo.trim());
        if (anoLectivo != null && !anoLectivo.isBlank()) {
            cfg.setAnoLectivo(anoLectivo.trim());
            try {
                int anioInt = Integer.parseInt(anoLectivo.trim());
                anioLectivoRepository.findByEsActualTrue().ifPresent(prev -> {
                    if (prev.getAnio() == null || !prev.getAnio().equals(anioInt)) {
                        prev.setEsActual(false);
                        anioLectivoRepository.save(prev);
                    }
                });
                Optional<AnioLectivo> anioOpt = anioLectivoRepository.findByAnio(anioInt);
                if (anioOpt.isPresent()) {
                    AnioLectivo existente = anioOpt.get();
                    existente.setEsActual(true);
                    existente.setEstado("ACTIVO");
                    anioLectivoRepository.save(existente);
                } else {
                    anioLectivoRepository.save(new AnioLectivo(anioInt, "ACTIVO", LocalDate.of(anioInt, 1, 15), LocalDate.of(anioInt, 11, 30), true));
                }
                invalidarCacheAnio();
            } catch (NumberFormatException ignored) {}
        }
        configuracionInstitucionalRepository.save(cfg);
    }

    public BigDecimal getPesoPeriodo1() {
        return periodoAcademicoRepository.findByNumeroPeriodo(1).map(PeriodoAcademico::getPesoPorcentaje).orElse(new BigDecimal("30.00"));
    }
    public BigDecimal getPesoPeriodo2() {
        return periodoAcademicoRepository.findByNumeroPeriodo(2).map(PeriodoAcademico::getPesoPorcentaje).orElse(new BigDecimal("35.00"));
    }
    public BigDecimal getPesoPeriodo3() {
        return periodoAcademicoRepository.findByNumeroPeriodo(3).map(PeriodoAcademico::getPesoPorcentaje).orElse(new BigDecimal("35.00"));
    }

    public String getFechaInicioP1() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(1).map(p -> p.getFechaInicio().toString()).orElse(a + "-02-01");
    }
    public String getFechaFinP1() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(1).map(p -> p.getFechaFin().toString()).orElse(a + "-06-15");
    }
    public String getFechaInicioP2() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(2).map(p -> p.getFechaInicio().toString()).orElse(a + "-07-15");
    }
    public String getFechaFinP2() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(2).map(p -> p.getFechaFin().toString()).orElse(a + "-09-15");
    }
    public String getFechaInicioP3() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(3).map(p -> p.getFechaInicio().toString()).orElse(a + "-09-16");
    }
    public String getFechaFinP3() {
        String a = obtenerAnioLectivoCodigo();
        return periodoAcademicoRepository.findByNumeroPeriodo(3).map(p -> p.getFechaFin().toString()).orElse(a + "-11-30");
    }
}

package com.siga.siga_iea.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siga.siga_iea.auth.service.CurrentUserContextService;
import com.siga.siga_iea.chat.dto.ChatMessageRequest;
import com.siga.siga_iea.chat.dto.ChatMessageResponse;
import com.siga.siga_iea.chat.dto.ToolExecutionResult;
import com.siga.siga_iea.chat.manual.ManualConvivenciaService;
import com.siga.siga_iea.chat.manual.ManualConvivenciaService.ArticuloNormativo;
import com.siga.siga_iea.chat.tools.ChatToolsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquestador principal del Chatbot Escolar SIGA - IEACI.
 * Gestiona el ciclo de vida de la consulta:
 * 1. Detección de rol y autorización de herramientas mediante Spring Security.
 * 2. Consulta de la base de conocimiento normativa (Manual de Convivencia / SIEE).
 * 3. Ejecución de herramientas autorizadas de solo lectura.
 * 4. Invocación a LLM local (Ollama) con mecanismo Zero-Failure Fallback determinista.
 */
@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);

    private final CurrentUserContextService userContextService;
    private final ChatToolsService toolsService;
    private final ManualConvivenciaService manualConvivenciaService;
    private final ChatPromptSafety promptSafety;
    private RestClient restClient;
    private final ObjectMapper objectMapper;

    // Patrones de detección de posibles datos personales en texto libre (Ley 1581 de 2012)
    private static final java.util.regex.Pattern PATRON_DOCUMENTO = java.util.regex.Pattern.compile("\\b\\d{7,12}\\b");
    private static final java.util.regex.Pattern PATRON_EMAIL = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final java.util.regex.Pattern PATRON_TELEFONO = java.util.regex.Pattern.compile("(?:\\+?57)?\\s*3\\d{2}[\\s.-]?\\d{7}");
    // Exclusión de términos institucionales, órganos colegiados y documentos normativos
    private static final String EXCLUSIONES_INSTITUCIONALES =
            "(?:Colegio|Instituci[oó]n|Manual|Sistema|Decreto|Ley|Escala|Periodo|Comisi[oó]n|Consejo|Comit[eé]|Gobierno|Evaluaci[oó]n|Promoci[oó]n|Convivencia|Acad[eé]mico|Directivo|Personero|Contralor|Rector|Rector[ií]a|Coordinaci[oó]n|Coordinador|Cartagena|Ambientalista)";

    private static final java.util.regex.Pattern PATRON_NOMBRE_PROPIO = java.util.regex.Pattern.compile(
            "\\b(?!" + EXCLUSIONES_INSTITUCIONALES + "\\b)[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,15}\\s+(?!" + EXCLUSIONES_INSTITUCIONALES + "\\b)[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,15}\\b"
            + "|\\b(?:[Ee]studiante|[Aa]lumn[oa]|[Dd]ocente|[Pp]rofesor(?:a)?)\\s+(?!" + EXCLUSIONES_INSTITUCIONALES + "\\b)[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,15}\\b"
    );

    @Value("${siga.chat.groq.api-key:}")
    private String groqApiKey;

    @Value("${siga.chat.groq.url:https://api.groq.com/openai/v1}")
    private String groqUrl;

    @Value("${siga.chat.groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    @Value("${siga.chat.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${siga.chat.ollama.model:llama3.2:1b}")
    private String ollamaModel;

    public ChatOrchestratorService(CurrentUserContextService userContextService,
                                   ChatToolsService toolsService,
                                   ManualConvivenciaService manualConvivenciaService,
                                   ChatPromptSafety promptSafety,
                                   ObjectMapper objectMapper) {
        this.userContextService = userContextService;
        this.toolsService = toolsService;
        this.manualConvivenciaService = manualConvivenciaService;
        this.promptSafety = promptSafety;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(3000));
        requestFactory.setReadTimeout(Duration.ofMillis(8000));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel;
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Detección heurística de datos personales en el texto libre del usuario (Ley 1581 de 2012).
     * Identifica números de documento, correos electrónicos, teléfonos y patrones de nombres de personas.
     */
    public boolean contienePosibleDatoPersonal(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return false;
        }
        return PATRON_DOCUMENTO.matcher(mensaje).find()
                || PATRON_EMAIL.matcher(mensaje).find()
                || PATRON_TELEFONO.matcher(mensaje).find()
                || PATRON_NOMBRE_PROPIO.matcher(mensaje).find();
    }

    /**
     * Frontera de Confianza: Criterio estricto de lista blanca (Local por defecto).
     * Solo permite salida a Groq Cloud si:
     * 1. No se invocó ninguna herramienta con datos transaccionales.
     * 2. Se clasificó positivamente normativa pública que citar.
     * 3. No contiene números de documento, correos, teléfonos ni nombres de personas en el mensaje.
     */
    public boolean puedeSalirANube(String mensaje, List<String> herramientasInvocadas, List<ArticuloNormativo> normativas) {
        return herramientasInvocadas.isEmpty()
                && normativas != null && !normativas.isEmpty()
                && !contienePosibleDatoPersonal(mensaje);
    }

    public ChatMessageResponse procesarMensaje(ChatMessageRequest request) {
        long t0 = System.currentTimeMillis();
        String mensaje = promptSafety.sanitizarEntradaUsuario(request.mensaje());
        if (mensaje != null && mensaje.length() > 250) {
            mensaje = mensaje.substring(0, 250);
        }
        String rol = userContextService.getRolAutenticado().name();

        List<String> herramientasInvocadas = new ArrayList<>();
        List<ToolExecutionResult> resultadosHerramientas = new ArrayList<>();
        List<String> referenciasNormativas = new ArrayList<>();
        StringBuilder contextoDatos = new StringBuilder();

        // 1. Detección y ejecución de herramientas operacionales según rol (prioridad directa)
        ejecutarHerramientasPorIntencion(mensaje, rol, herramientasInvocadas, resultadosHerramientas, contextoDatos);

        // 2. Búsqueda normativa solo cuando la consulta no es puramente operacional (o si pide norma explícita)
        boolean tieneRestriccionPrivacidad = contextoDatos.toString().contains("AVISO DE PRIVACIDAD:");
        boolean esConsultaNormativa = herramientasInvocadas.isEmpty() || esPreguntaNormativaExplicita(mensaje);
        List<ArticuloNormativo> normativas = (!tieneRestriccionPrivacidad && esConsultaNormativa)
                ? manualConvivenciaService.buscar(mensaje)
                : Collections.emptyList();
        if (!normativas.isEmpty()) {
            contextoDatos.append("NORMATIVA APLICABLE (Manual de Convivencia / SIEACI IEACI):\n");
            for (ArticuloNormativo art : normativas) {
                referenciasNormativas.add(art.codigo() + " - " + art.titulo());
                contextoDatos.append("- [").append(art.codigo()).append("] ").append(art.titulo())
                        .append(": ").append(art.contenido()).append("\n");
            }
            contextoDatos.append("\n");
        }

        // 3. Cascada de Inferencia con Frontera de Confianza (Lista Blanca / Local por Defecto)
        boolean puedeSalir = puedeSalirANube(mensaje, herramientasInvocadas, normativas);
        Optional<String> respuestaLlm = Optional.empty();
        String proveedor = "Motor Institucional Seguro (On-Premise)";

        // Nivel 1: Groq Cloud (Únicamente si cumple TODAS las condiciones de la lista de permitidos)
        if (puedeSalir && groqApiKey != null && !groqApiKey.isBlank()) {
            respuestaLlm = intentarLlamadaGroq(rol, contextoDatos.toString(), mensaje);
            if (respuestaLlm.isPresent() && !respuestaLlm.get().isBlank()) {
                proveedor = "Groq Cloud (" + (groqModel != null && !groqModel.isBlank() ? groqModel : "openai/gpt-oss-120b") + ")";
            }
        }

        // Nivel 2: Ollama Local (Inferencia on-premise en localhost:11434 sin salida de datos a internet)
        if (respuestaLlm.isEmpty()) {
            String promptCompleto = promptSafety.construirPromptConContexto(rol, contextoDatos.toString(), mensaje);
            respuestaLlm = intentarLlamadaOllama(promptCompleto);
            if (respuestaLlm.isPresent() && !respuestaLlm.get().isBlank()) {
                proveedor = "Ollama Local On-Premise (" + ollamaModel + ")";
            }
        }

        String respuestaFinal;
        boolean procesadoLocalmente;

        if (respuestaLlm.isPresent() && !respuestaLlm.get().isBlank()) {
            respuestaFinal = respuestaLlm.get();
            procesadoLocalmente = true;
        } else {
            // Nivel 3: Fallback determinista seguro estructurado en Java (Garantía Zero-Failure)
            respuestaFinal = generarRespuestaFallback(mensaje, rol, normativas, resultadosHerramientas, contextoDatos.toString());
            procesadoLocalmente = false;
            proveedor = !puedeSalir
                    ? "Motor Institucional Seguro (On-Premise)"
                    : "Motor Normativo Interno (Offline)";
        }

        long latencia = System.currentTimeMillis() - t0;
        return new ChatMessageResponse(
                respuestaFinal,
                rol,
                herramientasInvocadas,
                referenciasNormativas,
                latencia,
                procesadoLocalmente,
                proveedor
        );
    }

    private void ejecutarHerramientasPorIntencion(String mensaje, String rol,
                                                  List<String> herramientasInvocadas,
                                                  List<ToolExecutionResult> resultadosHerramientas,
                                                  StringBuilder contexto) {
        String query = mensaje.toLowerCase();

        if ("ESTUDIANTE".equalsIgnoreCase(rol)) {
            if (query.contains("nota") || query.contains("calificacion") || query.contains("boletin") || query.contains("promedio")) {
                boolean esConsultaPropia = query.contains("mis notas") || query.contains("mi nota")
                        || query.contains("mis calificaciones") || query.contains("mi calificacion")
                        || query.contains("mi promedio") || query.contains("mi boletin")
                        || query.contains("de mi") || query.contains("de mis");
                boolean consultaTercero = (query.contains("nota de ") || query.contains("notas de ")
                        || query.contains("calificacion de ") || query.contains("calificaciones de "))
                        && !esConsultaPropia;
                if (consultaTercero) {
                    contexto.append("AVISO DE PRIVACIDAD:\n")
                            .append("Por motivos de confidencialidad institucional y protección de datos (Ley 1581 de 2012), únicamente puedes acceder a tus propias calificaciones. No está autorizado consultar las notas de otros estudiantes.\n\n");
                } else {
                    try {
                        ToolExecutionResult res = toolsService.consultarMisNotas();
                        herramientasInvocadas.add(res.nombreHerramienta());
                        resultadosHerramientas.add(res);
                        contexto.append("DATOS DE TUS CALIFICACIONES:\n").append(res.datos()).append("\n\n");
                    } catch (Exception e) {
                        log.warn("Error ejecutando consultarMisNotas: {}", e.getMessage());
                    }
                }
            }
            if (query.contains("horario") || query.contains("clase hoy") || query.contains("clases hoy")
                    || query.contains("tengo hoy") || query.contains("clases tengo")) {
                try {
                    ToolExecutionResult res = toolsService.consultarMiHorarioHoy();
                    herramientasInvocadas.add(res.nombreHerramienta());
                    resultadosHerramientas.add(res);
                    contexto.append("DATOS DE TU HORARIO DE HOY:\n").append(res.datos()).append("\n\n");
                } catch (Exception e) {
                    log.warn("Error ejecutando consultarMiHorarioHoy: {}", e.getMessage());
                }
            }
            boolean esPreguntaDisciplinaria = query.contains("tipo i") || query.contains("tipo 1")
                    || query.contains("tipo ii") || query.contains("tipo 2")
                    || query.contains("tipo iii") || query.contains("tipo 3")
                    || query.contains("falta leve") || query.contains("falta grave") || query.contains("falta gravisima");
            boolean pideMiAsistencia = !esPreguntaDisciplinaria && (query.contains("asistencia")
                    || query.contains("inasistencia")
                    || query.contains("retraso")
                    || query.contains("llegada tarde")
                    || query.contains("mis faltas")
                    || query.contains("mis fallas")
                    || query.contains("cuantas faltas")
                    || query.contains("cuantas fallas"));
            if (pideMiAsistencia) {
                try {
                    ToolExecutionResult res = toolsService.consultarMiResumenAsistencia();
                    herramientasInvocadas.add(res.nombreHerramienta());
                    resultadosHerramientas.add(res);
                    contexto.append("DATOS DE TU ASISTENCIA:\n").append(res.datos()).append("\n\n");
                } catch (Exception e) {
                    log.warn("Error ejecutando consultarMiResumenAsistencia: {}", e.getMessage());
                }
            }
        } else if ("DOCENTE".equalsIgnoreCase(rol)) {
            if (query.contains("horario") || query.contains("clase hoy") || query.contains("clases hoy")
                    || query.contains("tengo hoy") || query.contains("clases tengo") || query.contains("agenda")) {
                try {
                    ToolExecutionResult res = toolsService.consultarMiHorarioDocenteHoy();
                    herramientasInvocadas.add(res.nombreHerramienta());
                    resultadosHerramientas.add(res);
                    contexto.append("DATOS DE TU HORARIO DOCENTE DE HOY:\n").append(res.datos()).append("\n\n");
                } catch (Exception e) {
                    log.warn("Error ejecutando consultarMiHorarioDocenteHoy: {}", e.getMessage());
                }
            }
            if (query.contains("curso") || query.contains("grupo")) {
                try {
                    ToolExecutionResult res = toolsService.consultarMisCursos();
                    herramientasInvocadas.add(res.nombreHerramienta());
                    resultadosHerramientas.add(res);
                    contexto.append("DATOS DE TUS CURSOS:\n").append(res.datos()).append("\n\n");
                } catch (Exception e) {
                    log.warn("Error ejecutando consultarMisCursos: {}", e.getMessage());
                }
            }
        } else if ("ADMIN".equalsIgnoreCase(rol) || "PERSONAL_ADMINISTRATIVO".equalsIgnoreCase(rol)) {
            if (query.contains("estadistica") || query.contains("matricula") || query.contains("total") || query.contains("colegio")) {
                try {
                    ToolExecutionResult res = toolsService.consultarEstadisticasGenerales();
                    herramientasInvocadas.add(res.nombreHerramienta());
                    resultadosHerramientas.add(res);
                    contexto.append("ESTADÍSTICAS INSTITUCIONALES:\n").append(res.datos()).append("\n\n");
                } catch (Exception e) {
                    log.warn("Error ejecutando consultarEstadisticasGenerales: {}", e.getMessage());
                }
            }
            if (query.contains("buscar") || (query.contains("estudiante") && !query.contains("estadistica"))) {
                try {
                    String criterio = query.replaceAll("(?i)(buscar|estudiante|alumno|informacion|datos|de|el)", "").trim();
                    if (!criterio.isBlank()) {
                        ToolExecutionResult res = toolsService.buscarEstudiante(criterio);
                        herramientasInvocadas.add(res.nombreHerramienta());
                        resultadosHerramientas.add(res);
                        contexto.append("RESULTADOS DE BÚSQUEDA DE ESTUDIANTE:\n").append(res.datos()).append("\n\n");
                    }
                } catch (Exception e) {
                    log.warn("Error ejecutando buscarEstudiante: {}", e.getMessage());
                }
            }
        }
    }

    private Optional<String> intentarLlamadaGroq(String rol, String contexto, String mensaje) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String systemContent = ChatPromptSafety.PROMPT_SISTEMA_BASE + "\n"
                    + "ROL DE LA SESIÓN AUTENTICADA: [" + rol + "]\n\n"
                    + "<datos_contexto>\n"
                    + (contexto != null && !contexto.isBlank() ? contexto : "No se requirieron datos operacionales adicionales para esta consulta.")
                    + "\n</datos_contexto>";

            Map<String, Object> systemMsg = Map.of("role", "system", "content", systemContent);
            Map<String, Object> userMsg = Map.of("role", "user", "content", mensaje);

            String modelToUse = (groqModel != null && !groqModel.isBlank()) ? groqModel.trim() : "openai/gpt-oss-120b";
            Map<String, Object> payload = Map.of(
                    "model", modelToUse,
                    "messages", List.of(systemMsg, userMsg),
                    "temperature", 0.2,
                    "max_tokens", 1024
            );

            String targetUrl = (groqUrl != null && !groqUrl.isBlank() ? groqUrl.replaceAll("/+$", "") : "https://api.groq.com/openai/v1") + "/chat/completions";

            String responseBody = restClient.post()
                    .uri(targetUrl)
                    .header("Authorization", "Bearer " + groqApiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        String text = messageNode.get("content").asText().trim();
                        if (!text.isBlank()) {
                            return Optional.of(text);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("Llamada a Groq Cloud falló con código {} ({}): {}. Activando fallback de inferencia.",
                    e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Llamada a Groq Cloud falló ({}: {}). Activando fallback de inferencia.", e.getClass().getSimpleName(), e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> intentarLlamadaOllama(String prompt) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", ollamaModel,
                    "prompt", prompt,
                    "stream", false
            );

            String responseBody = restClient.post()
                    .uri(ollamaUrl + "/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("response")) {
                    return Optional.of(root.get("response").asText().trim());
                }
            }
        } catch (Exception e) {
            log.debug("Ollama local no disponible en {}: {}. Empleando fallback determinista.", ollamaUrl, e.getMessage());
        }
        return Optional.empty();
    }

    private boolean esPreguntaNormativaExplicita(String mensaje) {
        if (mensaje == null) return false;
        String q = mensaje.toLowerCase();
        return q.contains("manual") || q.contains("siee") || q.contains("sieaci")
                || q.contains("reglamento") || q.contains("articulo") || q.contains("artículo")
                || q.contains("perder el ano") || q.contains("perder el año")
                || q.contains("reprobar") || q.contains("reprobacion") || q.contains("reprobación")
                || q.contains("porcentaje") || q.contains("conducto regular");
    }

    private String obtenerPrimerNombreLocal(String rol) {
        try {
            if ("ESTUDIANTE".equalsIgnoreCase(rol)) {
                return userContextService.getEstudianteAutenticado()
                        .map(e -> e.getNombres())
                        .filter(n -> n != null && !n.isBlank())
                        .map(n -> n.trim().split("\\s+")[0])
                        .orElse("");
            }
            if ("DOCENTE".equalsIgnoreCase(rol)) {
                return userContextService.getDocenteAutenticado()
                        .map(d -> d.getNombres())
                        .filter(n -> n != null && !n.isBlank())
                        .map(n -> n.trim().split("\\s+")[0])
                        .orElse("");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String generarRespuestaFallback(String mensaje, String rol,
                                            List<ArticuloNormativo> normativas,
                                            List<ToolExecutionResult> resultadosHerramientas,
                                            String contexto) {
        StringBuilder sb = new StringBuilder();
        String nombre = obtenerPrimerNombreLocal(rol);
        String sufijoNombre = nombre.isBlank() ? "" : ", " + nombre;

        if (contexto.contains("AVISO DE PRIVACIDAD:")) {
            sb.append("🔒 **Restricción de Privacidad y Protección de Datos**\n");
            sb.append("Por motivos de confidencialidad institucional (Ley 1581 de 2012), únicamente puedes acceder a tus propias calificaciones. No está autorizado consultar las notas de otros estudiantes.\n\n");
            return sb.toString().trim();
        }

        if (resultadosHerramientas != null && !resultadosHerramientas.isEmpty()) {
            for (ToolExecutionResult res : resultadosHerramientas) {
                String tool = res.nombreHerramienta();
                Map<String, Object> d = res.datos();
                if ("consultarMisNotas".equals(tool)) {
                    sb.append("📊 **Tus calificaciones actuales").append(sufijoNombre).append("**\n");
                    List<Map<String, Object>> notas = (List<Map<String, Object>>) d.get("calificaciones");
                    if (notas == null || notas.isEmpty()) {
                        sb.append("Aún no tienes calificaciones registradas en este momento. Puedes revisar tu boletín acumulado en **Calificaciones**.\n\n");
                    } else {
                        Map<String, List<Double>> porMateria = new LinkedHashMap<>();
                        for (Map<String, Object> item : notas) {
                            String mat = String.valueOf(item.getOrDefault("materia", "Asignatura"));
                            Object notaObj = item.get("nota");
                            if (notaObj instanceof Number num) {
                                porMateria.computeIfAbsent(mat, k -> new ArrayList<>()).add(num.doubleValue());
                            } else {
                                porMateria.computeIfAbsent(mat, k -> new ArrayList<>());
                            }
                        }
                        porMateria.forEach((mat, vals) -> {
                            if (vals.isEmpty()) {
                                sb.append("• **").append(mat).append("**: Sin nota calificada aún\n");
                            } else {
                                double prom = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                                sb.append(String.format(Locale.US, "• **%s**: promedio **%.2f** (%d eval.)%n", mat, prom, vals.size()));
                            }
                        });
                        sb.append("\n");
                    }
                } else if ("consultarMiHorarioHoy".equals(tool)) {
                    String cursoNom = String.valueOf(d.getOrDefault("curso", "")).trim();
                    String infoCurso = cursoNom.isBlank() ? "" : " · Curso " + cursoNom;
                    sb.append("⏰ **Tu horario de clases hoy").append(sufijoNombre).append(" (")
                            .append(d.getOrDefault("dia", "Hoy")).append(infoCurso).append(")**\n");
                    List<Map<String, Object>> bloques = (List<Map<String, Object>>) d.get("bloques");
                    if (bloques == null || bloques.isEmpty()) {
                        sb.append("Hoy no tienes bloques programados en tu curso. Puedes revisar tu semana completa en **Cursos**.\n\n");
                    } else {
                        for (Map<String, Object> b : bloques) {
                            sb.append("• **").append(b.get("horaInicio")).append(" - ").append(b.get("horaFin")).append("**: ")
                                    .append(b.get("materia")).append(" (").append(b.get("salon")).append(")\n");
                        }
                        sb.append("\n");
                    }
                } else if ("consultarMiHorarioDocenteHoy".equals(tool)) {
                    String prefijo = nombre.isBlank() ? "" : ", Prof. " + nombre;
                    sb.append("⏰ **Tu horario docente de hoy").append(prefijo).append(" (")
                            .append(d.getOrDefault("dia", "Hoy")).append(")**\n");
                    List<Map<String, Object>> bloques = (List<Map<String, Object>>) d.get("bloques");
                    if (bloques == null || bloques.isEmpty()) {
                        sb.append("Hoy no tienes bloques de clase asignados. Puedes revisar tu agenda semanal en **Mi Jornada**.\n\n");
                    } else {
                        for (Map<String, Object> b : bloques) {
                            sb.append("• **").append(b.get("horaInicio")).append(" - ").append(b.get("horaFin")).append("**: ")
                                    .append(b.get("materia")).append(" — Grupo ").append(b.get("curso"))
                                    .append(" (").append(b.get("salon")).append(")\n");
                        }
                        sb.append("\n");
                    }
                } else if ("consultarMiResumenAsistencia".equals(tool)) {
                    sb.append("📅 **Tu resumen de asistencia").append(sufijoNombre).append("**\n");
                    sb.append("• Presentes: **").append(d.getOrDefault("asistenciasPresente", 0)).append("**\n");
                    sb.append("• Llegadas tarde: **").append(d.getOrDefault("retrasos", 0)).append("**\n");
                    sb.append("• Excusadas: **").append(d.getOrDefault("fallasJustificadas", 0)).append("**\n");
                    sb.append("• Injustificadas: **").append(d.getOrDefault("fallasInjustificadas", 0)).append("**\n\n");
                } else if ("consultarMisCursos".equals(tool)) {
                    String prefijo = nombre.isBlank() ? "" : ", Prof. " + nombre;
                    sb.append("👥 **Tus cursos como director de grupo").append(prefijo).append("**\n");
                    List<Map<String, Object>> cursos = (List<Map<String, Object>>) d.get("cursos");
                    if (cursos == null || cursos.isEmpty()) {
                        sb.append("No tienes grupos de dirección asignados actualmente. Revisa tus asignaturas en **Cursos y Planillas**.\n\n");
                    } else {
                        for (Map<String, Object> c : cursos) {
                            sb.append("• **").append(c.get("nombre")).append("** (Grado ").append(c.get("grado"))
                                    .append(", Jornada ").append(c.get("jornada")).append(")\n");
                        }
                        sb.append("\n");
                    }
                } else if ("consultarEstadisticasGenerales".equals(tool)) {
                    sb.append("🏛️ **Panel Directivo IEACI**\n");
                    sb.append("• Estudiantes matriculados: **").append(d.getOrDefault("totalEstudiantesMatriculados", 0)).append("**\n");
                    sb.append("• Docentes activos: **").append(d.getOrDefault("totalDocentesActivos", 0)).append("**\n");
                    sb.append("• Cursos activos: **").append(d.getOrDefault("totalCursosActivos", 0)).append("**\n\n");
                } else if ("buscarEstudiante".equals(tool)) {
                    sb.append("🔍 **Búsqueda Administrativa de Estudiantes**\n");
                    sb.append("Coincidencias encontradas: **").append(d.getOrDefault("totalEncontrados", 0))
                            .append("** (consulta el expediente completo en el módulo **Estudiantes**).\n\n");
                }
            }
        }

        if (!normativas.isEmpty()) {
            if (normativas.size() == 1) {
                ArticuloNormativo art = normativas.get(0);
                sb.append("📋 **").append(art.titulo()).append("** (`").append(art.codigo()).append("`)\n");
                sb.append(art.contenido()).append("\n\n");
            } else {
                sb.append("📋 **Orientación Institucional (IEACI)**\n\n");
                for (ArticuloNormativo art : normativas) {
                    sb.append("• **").append(art.titulo()).append("** (`").append(art.codigo()).append("`):\n");
                    sb.append("  ").append(art.contenido()).append("\n\n");
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("🌿 ¡Hola").append(sufijoNombre).append("! Soy **Mangle**, tu asistente de la IEACI.\n\n");
            if ("ESTUDIANTE".equalsIgnoreCase(rol)) {
                sb.append("Puedes preguntarme directamente por:\n");
                sb.append("• **Tus datos**: tus notas por asignatura, horario de clases hoy o asistencias.\n");
            } else if ("DOCENTE".equalsIgnoreCase(rol)) {
                sb.append("Puedes preguntarme directamente por:\n");
                sb.append("• **Tu gestión docente**: tus clases de hoy, grupos asignados o dirección de grupo.\n");
            } else {
                sb.append("Puedes preguntarme directamente por:\n");
                sb.append("• **Gestión directiva**: estadísticas institucionales o búsqueda rápida de estudiantes.\n");
            }
            sb.append("• **Normativa IEACI**: derechos, deberes, uniforme, excusas médicas, estímulos, Gobierno Escolar, evaluación (SIEACI) y PRAE.\n");
        }

        return sb.toString().trim();
    }
}

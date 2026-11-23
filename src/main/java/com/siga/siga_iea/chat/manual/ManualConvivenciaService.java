package com.siga.siga_iea.chat.manual;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base de conocimiento normativa e institucional para el asistente Mangle.
 * Integra:
 * 1. SIEACI oficial de la IEACI (Código GC-F05, Versión 04, Vigencia 10/11/25 - Decreto 1290 de 2009).
 * 2. Manual de Convivencia Escolar adaptado al contexto distrital de Cartagena de Indias
 *    (Ley 115 de 1994, Decreto 1860 de 1994, Ley 1098 de 2006, Ley 1620 de 2013 y Decreto 1965 de 2013).
 * 3. Lineamientos del Proyecto Ambiental Escolar (PRAE) y módulos SIGA-IEA.
 */
@Service
public class ManualConvivenciaService {

    public record ArticuloNormativo(
            String codigo,
            String capitulo,
            String titulo,
            String contenido,
            List<String> palabrasClave
    ) {}

    private static final Set<String> STOPWORDS = Set.of(
            "para", "como", "sobre", "cual", "cuales", "donde", "cuando", "quien", "quienes",
            "tengo", "puedo", "hacer", "algo", "esto", "esta", "este", "estos", "estas",
            "unos", "unas", "entre", "todo", "todos", "toda", "todas", "cada", "siempre",
            "mucho", "poco", "pero", "porque", "por", "que", "con", "sin", "del", "las",
            "los", "una", "uno", "son", "hay", "ser", "estan", "tiene", "tienen",
            "puede", "pueden", "debe", "deben", "hace", "dice", "segun", "ante", "bajo",
            "desde", "hacia", "hasta", "mas", "muy", "bien", "aqui", "alli", "ese", "esa"
    );

    private final List<ArticuloNormativo> articulos = new ArrayList<>();

    public ManualConvivenciaService() {
        inicializarBaseNormativaOficial();
    }

    private void inicializarBaseNormativaOficial() {
        // =====================================================================
        // BLOQUE A: SISTEMA INSTITUCIONAL DE EVALUACIÓN (SIEACI - DECRETO 1290)
        // =====================================================================

        articulos.add(new ArticuloNormativo(
                "SIEACI-5.4.11",
                "Capítulo 5: Criterios de Evaluación, Promoción y Reprobación",
                "Escala de Valoración Institucional (Decreto 1290 de 2009)",
                "La escala valorativa de la IEACI va de 1,0 a 5,0 en cuatro niveles de desempeño: " +
                        "• Desempeño Superior: 4,6 a 5,0. " +
                        "• Desempeño Alto: 4,0 a 4,5. " +
                        "• Desempeño Básico: 3,0 a 3,9 (nota mínima aprobatoria: 3,0). " +
                        "• Desempeño Bajo: 1,0 a 2,9.",
                List.of("escala", "notas", "nota minima", "sieaci", "siee", "calificacion", "desempeno", "superior", "alto", "basico", "bajo", "aprobar")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-5.4.4",
                "Capítulo 5: Criterios de Evaluación",
                "Valoración Porcentual por Dimensiones en Cada Periodo",
                "Cada periodo académico evalúa tres dimensiones formativas: " +
                        "1. Dimensión Cognitivo-Procedimental: 70% (actividades, talleres, evaluaciones y proyectos de aula). " +
                        "2. Dimensión Socio-Ambiental: 15% (trabajo colaborativo, respeto y compromiso ecológico/PRAE). " +
                        "3. Dimensión Personal: 15% (responsabilidad, puntualidad, actitud y autoevaluación).",
                List.of("dimensiones", "porcentaje", "personal", "socio-ambiental", "cognitivo", "procedimental", "evaluacion", "ponderacion")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-5.5.3",
                "Capítulo 5: Promoción",
                "Criterios de Promoción de Grado y Nivelación Final",
                "Se promueve al grado siguiente quien alcance desempeño básico, alto o superior (mínimo 3,00) en todas las áreas, " +
                        "previa revisión de la Comisión de Evaluación y Promoción (exceptuando educación religiosa según normativa). " +
                        "Quienes finalicen el año con 1 o 2 áreas en desempeño bajo presentan actividades pedagógicas complementarias de nivelación " +
                        "con compromiso firmado por su acudiente (ítem 5.5.4).",
                List.of("promocion", "pasar de ano", "pasar el grado", "aprobar grado", "nivelacion", "recuperacion", "habilitacion")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-5.6.1",
                "Capítulo 5: Reprobación",
                "Causales de Reprobación del Grado e Inasistencia",
                "Un estudiante reprueba el grado en la IEACI cuando: " +
                        "1. Obtiene valoración final en nivel bajo (menor a 3,0) en tres (3) o más asignaturas. " +
                        "2. Acumula inasistencias injustificadas iguales o superiores al 25% de las actividades académicas. " +
                        "3. Acumula inasistencias justificadas del 30% o más sin entregar los compromisos pedagógicos acordados.",
                List.of("reprobacion", "perder el ano", "perder por fallas", "inasistencia", "porcentaje fallas", "25%", "30%", "faltas de asistencia")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-7.1",
                "Capítulo 7: Comisión de Evaluación y Promoción",
                "División y Ponderación de los Tres Periodos Académicos",
                "El año escolar de la IEACI se organiza en tres (3) periodos académicos: " +
                        "• Primer periodo: 13 semanas (33% de la nota definitiva anual). " +
                        "• Segundo periodo: 12 semanas (33% de la nota definitiva anual). " +
                        "• Tercer periodo: 15 semanas (34% de la nota definitiva anual).",
                List.of("periodos", "cuantos periodos", "semanas", "valor periodo", "33%", "34%", "definitiva anual", "calendario academico")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-7.3",
                "Capítulo 7: Comisión de Evaluación y Promoción",
                "Promoción Anticipada de Grado",
                "Durante el primer periodo académico, la Comisión puede recomendar ante el Consejo Académico la promoción anticipada " +
                        "de estudiantes con desempeño superior excepcional en todas las áreas (o repitentes que superen ampliamente las metas iniciales), " +
                        "en los ciclos 1°–3°, 4°–5°, 6°–7° y 8°–9°, con consentimiento expreso del acudiente.",
                List.of("promocion anticipada", "saltar de grado", "avanzar de grado", "repitentes", "excepcional")
        ));

        articulos.add(new ArticuloNormativo(
                "SIEACI-CAP-11",
                "Capítulo 11: Reclamaciones y Apelaciones Evaluativas",
                "Conducto Regular y Plazos para Revisión de Notas",
                "Para solicitar revisión de evaluaciones o notas debe respetarse el conducto regular: " +
                        "1ª Instancia: Docente de la asignatura (3 días hábiles). " +
                        "2ª Instancia: Director de grupo (3 días hábiles). " +
                        "3ª Instancia: Coordinación Académica (5 días hábiles). " +
                        "4ª Instancia: Comisión de Evaluación y Promoción (5 días hábiles). " +
                        "5ª Instancia: Consejo Académico y 6ª Instancia: Consejo Directivo (5 días hábiles).",
                List.of("reclamo", "reclamacion", "apelacion", "segundo evaluador", "revision de nota", "conducto regular", "plazo reclamo")
        ));

        // =====================================================================
        // BLOQUE B: MANUAL DE CONVIVENCIA (CARTAGENA - LEY 115 / LEY 1620)
        // =====================================================================

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP1-IDENTIDAD",
                "Capítulo I: Identidad y Principios Institucionales",
                "Horizonte Institucional y Perfil del Estudiante Ambientalista",
                "La IEACI forma líderes académicos con conciencia ecológica, respeto por la dignidad humana, honestidad y responsabilidad ciudadana " +
                        "en el distrito de Cartagena de Indias. El Manual de Convivencia es un instrumento formativo y preventivo amparado en la Ley 115 de 1994, " +
                        "el Decreto 1860 de 1994, el Código de Infancia y Adolescencia (Ley 1098 de 2006) y la Ley 1620 de 2013.",
                List.of("mision", "vision", "principios", "valores", "perfil", "identidad", "manual de convivencia", "que es el manual", "colegio", "ieaci")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP2-DERECHOS",
                "Capítulo II: Derechos y Garantías Estudiantiles",
                "Derechos Formativos, Académicos y de Participación",
                "Todo estudiante tiene derecho a: 1. Recibir educación integral de calidad y conocer oportunamente los criterios e informes evaluativos. " +
                        "2. Ser tratado con respeto, sin discriminación y en un ambiente seguro. " +
                        "3. Expresar libremente sus opiniones por los canales institucionales. " +
                        "4. Utilizar adecuadamente aulas, biblioteca, laboratorios, salas de informática, zonas deportivas y áreas verdes. " +
                        "5. Ser escuchado en descargos bajo el debido proceso y recibir estímulos por sus logros.",
                List.of("derechos", "derecho", "garantias", "libre expresion", "trato digno", "respeto", "espacios", "biblioteca", "laboratorios")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP2-DEBERES",
                "Capítulo II: Deberes de los Estudiantes",
                "Deberes Académicos, de Convivencia y Cuidado Ambiental",
                "Son deberes esenciales de los estudiantes: 1. Asistir con puntualidad a clases y cumplir responsablemente con tareas, talleres y evaluaciones. " +
                        "2. Tratar con amabilidad y respeto a compañeros, docentes, directivos y personal de apoyo. " +
                        "3. Cuidar pupitres, equipos tecnológicos, laboratorios y mantener el aseo depositando residuos en los puntos ecológicos. " +
                        "4. Seguir el conducto regular ante cualquier dificultad y representar dignamente al colegio dentro y fuera del plantel.",
                List.of("deberes", "deber", "obligaciones", "responsabilidades", "aseo", "cuidado", "respeto", "tareas", "puntualidad")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP2-COMPORTAMIENTO",
                "Capítulo II: Normas de Comportamiento Escolar",
                "Normas en el Aula, Salidas Pedagógicas y Enfermería",
                "En el salón de clases se debe mantener una escucha activa, pedir la palabra ordenadamente y solicitar autorización al docente para desplazamientos o ir al baño. " +
                        "En laboratorios, sala de sistemas y salidas pedagógicas deben seguirse las instrucciones de seguridad del docente. " +
                        "Si un estudiante presenta malestar físico, el docente autoriza su paso a enfermería/bienestar para valoración y aviso oportuno al acudiente.",
                List.of("comportamiento", "salon", "aula", "enfermeria", "permiso", "bano", "salida pedagogica", "sala de sistemas", "laboratorio")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP2-UNIFORME",
                "Capítulo II: Presentación Personal y Uniforme",
                "Porte del Uniforme Institucional y Higiene Personal",
                "El uniforme institucional (diario y educación física) debe portarse limpio, completo y de acuerdo con el horario asignado a cada jornada, sin accesorios que pongan en riesgo la seguridad física en prácticas deportivas o de laboratorio. " +
                        "Ante dificultades económicas o eventos temporales debidamente justificados por el acudiente, Coordinación concertará plazos razonables garantizando siempre el derecho a la educación.",
                List.of("uniforme", "presentacion personal", "sudadera", "educacion fisica", "higiene", "ropa", "porte")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-ASISTENCIA-EXCUSAS",
                "Capítulo II y V: Asistencia, Puntualidad y Excusas",
                "Justificación de Inasistencias y Excusas Médicas",
                "Toda ausencia por enfermedad, calamidad doméstica o representación oficial debe ser justificada por el padre o acudiente dentro de los tres (3) días hábiles siguientes al regreso, " +
                        "adjuntando soporte médico o carta firmada ante el director de grupo o Coordinación Académica. " +
                        "Con excusa validada, el estudiante tiene derecho a reprogramar evaluaciones y actividades pendientes sin sanción académica.",
                List.of("excusa", "excusa medica", "incapacidad", "justificar", "justificacion", "ausencia", "permiso", "llegada tarde", "no pude ir", "falte")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CONVIVENCIA-DIGITAL",
                "Capítulo II y III: Ciudadanía y Convivencia Digital",
                "Uso Responsable de Celulares, Redes y Entornos Digitales",
                "El teléfono celular y dispositivos personales solo se usan en clase cuando el docente autoriza su empleo pedagógico. " +
                        "Se prohíbe grabar o difundir imágenes, audios o burlas de compañeros o docentes sin autorización. " +
                        "Cualquier agresión mediante redes sociales o mensajería (ciberacoso) se atiende dentro de la Ruta de Convivencia Escolar.",
                List.of("celular", "telefono", "redes sociales", "internet", "ciberacoso", "bullying digital", "fotos", "videos", "digital")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP3-RUTA-ATENCION",
                "Capítulo III: Ruta de Atención Integral (Ley 1620 / Decreto 1965)",
                "Componentes de Promoción, Prevención, Atención y Seguimiento",
                "La Ruta de Atención Integral de Convivencia Escolar se desarrolla en cuatro componentes: " +
                        "1. Promoción: fomento del buen trato, respeto y proyectos transversales. " +
                        "2. Prevención: identificación temprana de factores de riesgo en aula y entorno. " +
                        "3. Atención: aplicación oportuna de los protocolos diferenciados para situaciones Tipo I, II y III. " +
                        "4. Seguimiento: monitoreo de acuerdos pedagógicos y bienestar del estudiante.",
                List.of("ruta de atencion", "ley 1620", "decreto 1965", "promocion", "prevencion", "atencion", "seguimiento", "protocolo", "convivencia escolar")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-FALTAS-TIPO-1",
                "Capítulo III y V: Régimen de Convivencia Escolar",
                "Faltas Tipo I (Situaciones Leves)",
                "Situaciones esporádicas que inciden negativamente en el clima escolar sin generar daños físicos ni afectar la dignidad. " +
                        "Incluyen: impuntualidad reiterada sin excusa, uso no autorizado del celular en clase, porte incorrecto del uniforme o interrupciones leves en el aula. " +
                        "Protocolo: mediación pedagógica inmediata, escucha de las partes, diálogo reflexivo y registro formativo en el observador.",
                List.of("falta leve", "faltas leves", "tipo 1", "tipo i", "situacion tipo 1", "celular", "uso el celular", "en clase", "impuntualidad", "desorden", "amonestacion verbal")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-FALTAS-TIPO-2",
                "Capítulo III y V: Régimen de Convivencia Escolar",
                "Faltas Tipo II (Situaciones Graves)",
                "Situaciones de agresión escolar, acoso escolar (bullying), ciberacoso o incumplimientos graves que no constituyen delito: " +
                        "incluye agresiones verbales sistemáticas, fraude o plagio comprobado, daño intencional a bienes institucionales, evasión de clases o reincidencia grave. " +
                        "Protocolo: atención inmediata si hay afectación, descargos con debido proceso, citación a acudientes, acta de compromiso, acción reparatoria y remisión al Comité de Convivencia.",
                List.of("falta grave", "faltas graves", "tipo 2", "tipo ii", "situacion tipo 2", "bullying", "acoso", "pelea", "fraude", "plagio", "evasion", "dano")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-FALTAS-TIPO-3",
                "Capítulo III y V: Régimen de Convivencia Escolar",
                "Faltas Tipo III (Situaciones Gravísimas - Ley 1620 de 2013)",
                "Situaciones que constituyen presuntos delitos contra la libertad, integridad física o sexual, porte de armas o tráfico/distribución de sustancias psicoactivas. " +
                        "Protocolo obligatorio: atención en salud si aplica, protección de evidencias e integridad del menor, reporte inmediato a Policía de Infancia y Adolescencia / ICBF, " +
                        "notificación a acudientes e informe al Comité Escolar de Convivencia.",
                List.of("falta gravisima", "faltas gravisimas", "tipo 3", "tipo iii", "situacion tipo 3", "armas", "drogas", "sustancias", "delito", "policia", "icbf")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP4-ESTIMULOS",
                "Capítulo IV: Estímulos y Reconocimientos",
                "Tipos de Estímulos Académicos, Ambientales y Formativos",
                "La institución reconoce el mérito y la perseverancia mediante: anotaciones positivas en el observador, menciones de honor por periodo, " +
                        "reconocimiento en izadas de bandera, cuadro de excelencia académica, distinción al liderazgo ambiental (PRAE), mejor bachiller/ICFES y mérito deportivo o cultural.",
                List.of("estimulos", "reconocimientos", "premios", "mencion de honor", "cuadro de honor", "izada de bandera", "diploma", "medalla", "excelencia")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP5-DEBIDO-PROCESO",
                "Capítulo V: Medidas Pedagógicas y Debido Proceso",
                "Garantías de Debido Proceso y Medidas Correctivo-Formativas",
                "Toda medida formativa respeta el debido proceso (Art. 29 Constitucional): presunción de buena fe, derecho a ser escuchado en descargos, " +
                        "acompañamiento del acudiente en faltas graves, proporcionalidad y derecho de reposición/apelación en el conducto regular. " +
                        "Las medidas son progresivas: diálogo reflexivo, registro disciplinario, amonestación escrita con plan de mejora y, en casos graves motivados, suspensión temporal concertada por instancia directiva.",
                List.of("debido proceso", "descargos", "sancion", "suspension", "castigo", "medidas", "observador", "amonestacion", "citacion")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP8-ACUDIENTES",
                "Capítulo VIII: Padres de Familia y Acudientes",
                "Derechos y Deberes de las Familias en el Proceso Formativo",
                "Los padres o acudientes tienen derecho a recibir boletines oportunos, participar en el Consejo de Padres y presentar inquietudes por el conducto regular. " +
                        "Sus deberes incluyen acompañar el proceso académico diario, atender las citaciones escolares, justificar oportunamente las ausencias y fomentar hábitos de estudio y cuidado ambiental.",
                List.of("padres", "acudiente", "acudientes", "familia", "citaciones", "reunion de padres", "escuela de padres", "boletines")
        ));

        articulos.add(new ArticuloNormativo(
                "MANUAL-CAP9-GOBIERNO-ESCOLAR",
                "Capítulo IX y X: Gobierno Escolar (Decreto 1860 de 1994)",
                "Órganos Directivos y Participación Estudiantil (Personero y Consejo)",
                "El Gobierno Escolar está integrado por: Rectoría, Consejo Directivo (máxima instancia administrativa y normativa), Consejo Académico (orientación curricular) y " +
                        "Comité Escolar de Convivencia (Ley 1620). Los estudiantes participan mediante el Consejo de Estudiantes (un representante por curso) y el Personero Estudiantil de grado 11°, " +
                        "elegido democráticamente para promover los derechos y deberes del estudiantado.",
                List.of("gobierno escolar", "personero", "personera", "consejo directivo", "consejo academico", "consejo de estudiantes", "comite de convivencia", "representante", "rector", "rectoria")
        ));

        // =====================================================================
        // BLOQUE C: PRAE AMBIENTAL Y USO DE LA PLATAFORMA SIGA-IEA
        // =====================================================================

        articulos.add(new ArticuloNormativo(
                "IEACI-PRAE-AMBIENTAL",
                "Componente Institucional Ambiental (PRAE)",
                "Proyecto Ambiental Escolar y Cultura Ecológica IEACI",
                "El PRAE de la IEACI articula el currículo con la conservación de los ecosistemas costeros y de manglar de Cartagena de Indias. " +
                        "Promueve la separación en la fuente (puntos ecológicos), ahorro de agua y energía, huerta escolar, jornadas de reciclaje y brigadas ambientales estudiantiles.",
                List.of("prae", "ambiental", "ecologico", "ecologia", "manglar", "reciclaje", "huerta", "residuos", "basura", "modulo ambiental")
        ));

        articulos.add(new ArticuloNormativo(
                "SIGA-GUIA-MODULOS",
                "Guía Rápida de Uso · Plataforma SIGA-IEA",
                "Módulos Disponibles para Estudiantes, Docentes y Directivos",
                "En SIGA-IEA puedes acceder según tu rol a: • Inicio y Boletín: resumen de notas por periodo y exportación de boletín PDF. " +
                        "• Cursos y Horarios: bloques diarios/semanales y salones asignados. " +
                        "• Asistencias: historial de presencia, llegadas tarde y excusas. " +
                        "• Certificados y Reportes: descarga de constancias de estudio y seguimiento de citaciones o novedades.",
                List.of("siga", "plataforma", "boletin", "pdf", "certificado", "certificados", "constancia", "descargar", "como funciona", "ayuda", "modulos")
        ));
    }

    public List<ArticuloNormativo> buscar(String consulta) {
        if (consulta == null || consulta.isBlank()) {
            return Collections.emptyList();
        }
        String normalizada = normalizarTexto(consulta);
        Set<String> tokens = Arrays.stream(normalizada.split("\\s+"))
                .filter(p -> p.length() >= 3 && !STOPWORDS.contains(p))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<AbstractMap.SimpleEntry<ArticuloNormativo, Integer>> ranked = articulos.stream()
                .map(art -> new AbstractMap.SimpleEntry<>(art, calcularRelevancia(art, normalizada, tokens)))
                .filter(entry -> entry.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }

        int topScore = ranked.get(0).getValue();
        int minThreshold = Math.max(4, (int) Math.ceil(topScore * 0.70));

        return ranked.stream()
                .filter(entry -> entry.getValue() >= minThreshold)
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int calcularRelevancia(ArticuloNormativo art, String consultaNormalizada, Set<String> tokensConsulta) {
        int score = 0;
        String tituloNorm = normalizarTexto(art.titulo());
        String cuerpoNorm = normalizarTexto(art.contenido());
        Set<String> tokensTitulo = new HashSet<>(Arrays.asList(tituloNorm.split("\\s+")));
        Set<String> tokensCuerpo = new HashSet<>(Arrays.asList(cuerpoNorm.split("\\s+")));

        for (String kw : art.palabrasClave()) {
            String kwNorm = normalizarTexto(kw);
            if (kwNorm.contains(" ") && consultaNormalizada.contains(kwNorm)) {
                score += 10;
            } else if (tokensConsulta.contains(kwNorm)) {
                score += 8;
            }
        }

        for (String p : tokensConsulta) {
            if (tokensTitulo.contains(p)) {
                score += 4;
            } else if (tokensCuerpo.contains(p)) {
                score += 2;
            } else {
                for (String kw : art.palabrasClave()) {
                    String kwNorm = normalizarTexto(kw);
                    if (p.length() >= 4 && (kwNorm.equals(p) || (kwNorm.startsWith(p) && Math.abs(kwNorm.length() - p.length()) <= 3))) {
                        score += 5;
                        break;
                    }
                }
            }
        }
        return score;
    }

    private String normalizarTexto(String str) {
        return str.toLowerCase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ü", "u")
                .replace("ñ", "n")
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();
    }
}

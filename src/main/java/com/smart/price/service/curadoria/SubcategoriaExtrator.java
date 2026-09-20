package com.smart.price.service.curadoria;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Identifica a subcategoria/tipo de produto com base no título e termo de busca,
 * e calcula a similaridade textual para impedir que produtos funcionalmente idênticos
 * (mesmo que de marcas ou vendedores diferentes) sejam publicados no mesmo lote.
 */
@Component
public class SubcategoriaExtrator {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "de", "da", "do", "das", "dos", "para", "com", "sem", "em", "por",
            "e", "a", "o", "as", "os", "um", "uma", "uns", "umas",
            "pro", "pra", "original", "novo", "promocao", "oferta", "frete",
            "gratis", "bivolt", "110v", "220v", "cor", "preto", "branco"
    ));

    // Mapeamento ordenado: subcategorias mais específicas (ex: SUPORTE_MONITOR) DEVEM vir antes de genéricas (ex: MONITOR)
    private final Map<String, Set<String>> subcategoriasPorKeywords = new LinkedHashMap<>();

    public SubcategoriaExtrator() {
        // 1. Periféricos específicos e Suportes
        subcategoriasPorKeywords.put("SUPORTE_MONITOR", Set.of("suporte articulado", "pistao a gas", "suporte de monitor", "suporte para monitor", "suporte mesa"));
        subcategoriasPorKeywords.put("CADEIRA_GAMER", Set.of("cadeira gamer", "cadeira ergonomica"));
        subcategoriasPorKeywords.put("VOLANTE", Set.of("volante logitech", "volante gamer", "g29", "g923"));
        subcategoriasPorKeywords.put("MICROFONE", Set.of("microfone gamer", "quadcast", "microfone condensador", "microfone"));
        subcategoriasPorKeywords.put("MOUSEPAD", Set.of("mousepad", "mouse pad"));
        subcategoriasPorKeywords.put("MOUSE", Set.of("mouse gamer", "mouse sem fio", "mouse"));
        subcategoriasPorKeywords.put("TECLADO", Set.of("teclado mecanico", "teclado gamer", "teclado sem fio", "teclado"));
        subcategoriasPorKeywords.put("HEADSET", Set.of("headset gamer", "headset", "headphone"));

        // 2. Hardware & Informática
        subcategoriasPorKeywords.put("PLACA_VIDEO", Set.of("rtx", "geforce", "radeon", "placa de video"));
        subcategoriasPorKeywords.put("PROCESSADOR", Set.of("ryzen", "core i3", "core i5", "core i7", "core i9", "processador"));
        subcategoriasPorKeywords.put("MEMORIA_RAM", Set.of("memoria ram", "ddr4", "ddr5"));
        subcategoriasPorKeywords.put("SSD_STORAGE", Set.of("ssd nvme", "ssd m.2", "ssd", "disco solido"));
        subcategoriasPorKeywords.put("PLACA_MAE", Set.of("placa mae", "motherboard", "b550", "b650", "h610", "b760", "x670", "z790"));
        subcategoriasPorKeywords.put("FONTE_ENERGIA", Set.of("fonte 80 plus", "fonte gamer", "fonte modular", "fonte 650w", "fonte 750w", "fonte 500w", "fonte"));
        subcategoriasPorKeywords.put("COOLER", Set.of("water cooler", "air cooler", "dissipador"));
        subcategoriasPorKeywords.put("GABINETE", Set.of("gabinete gamer", "gabinete aquario", "mid tower", "gabinete"));
        subcategoriasPorKeywords.put("MONITOR", Set.of("monitor gamer", "monitor ultrawide", "monitor 144hz", "monitor 240hz", "monitor"));

        // 3. Games & Consoles
        subcategoriasPorKeywords.put("CONTROLE", Set.of("dualsense", "controle sem fio", "gamepad", "joystick", "8bitdo", "controle xbox", "controle ps5"));
        subcategoriasPorKeywords.put("CONSOLE", Set.of("playstation 5", "ps5", "xbox series", "nintendo switch", "console"));
        subcategoriasPorKeywords.put("JOGO", Set.of("jogo", "game", "midia fisica", "zelda", "spider-man", "fc 24", "fifa"));

        // 4. Smartphones & Wearables
        subcategoriasPorKeywords.put("SMARTWATCH", Set.of("smartwatch", "apple watch", "galaxy watch", "amazfit", "smartband", "mi band", "relogio inteligente"));
        subcategoriasPorKeywords.put("FONE_OUVIDO", Set.of("airpods", "galaxy buds", "earbuds", "fone bluetooth", "fone de ouvido"));
        subcategoriasPorKeywords.put("CARREGADOR", Set.of("carregador inducao", "carregador rapido", "carregador sem fio", "carregador", "magsafe"));
        subcategoriasPorKeywords.put("POWERBANK", Set.of("power bank", "bateria externa", "carregador portatil"));
        subcategoriasPorKeywords.put("SMARTPHONE", Set.of("iphone", "galaxy s", "galaxy a", "redmi", "xiaomi", "poco", "motorola", "smartphone", "celular"));

        // 5. Casa Inteligente & Eletro
        subcategoriasPorKeywords.put("ROBO_ASPIRADOR", Set.of("robo aspirador", "aspirador robo", "mop"));
        subcategoriasPorKeywords.put("AIR_FRYER", Set.of("air fryer", "fritadeira sem oleo", "fritadeira eletrica", "fritadeira"));
        subcategoriasPorKeywords.put("SMART_SPEAKER", Set.of("alexa", "echo dot", "echo show", "echo pop"));
        subcategoriasPorKeywords.put("FECHADURA_DIGITAL", Set.of("fechadura digital", "fechadura biometrica", "fechadura inteligente"));
        subcategoriasPorKeywords.put("CAFETEIRA", Set.of("cafeteira nespresso", "cafeteira dolce gusto", "cafeteira espresso", "cafeteira"));
        subcategoriasPorKeywords.put("VENTILADOR", Set.of("ventilador de coluna", "ventilador de mesa", "ventilador", "climatizador"));
        subcategoriasPorKeywords.put("LAMPADA_SMART", Set.of("lampada inteligente", "fita led", "interruptor inteligente"));
        subcategoriasPorKeywords.put("CAMERA_SEGURANCA", Set.of("camera de seguranca", "camera ip", "camera wifi"));
        subcategoriasPorKeywords.put("SMART_TV", Set.of("smart tv", "tv 4k", "oled", "qled"));
    }

    /**
     * Extrai a subcategoria normalizada de um produto com base no título e termo de busca.
     * Utiliza limites de palavra (\b) para evitar falsos positivos (ex: "HyperX" conter "rx").
     */
    public String extrairSubcategoria(String titulo, String termoBusca) {
        String textoTitulo = normalizar(titulo != null ? titulo : "");

        // 1. Prioridade absoluta: identifica pelo TÍTULO do produto (específico do item)
        if (!textoTitulo.isBlank()) {
            for (Map.Entry<String, Set<String>> entry : subcategoriasPorKeywords.entrySet()) {
                for (String kw : entry.getValue()) {
                    String kwNorm = normalizar(kw);
                    Pattern pattern = Pattern.compile("\\b" + Pattern.quote(kwNorm) + "\\b", Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(textoTitulo).find()) {
                        return entry.getKey();
                    }
                }
            }
        }

        // 2. Fallback: se o título não tiver palavra-chave conhecida, utiliza o termo de busca
        String textoTermo = normalizar(termoBusca != null ? termoBusca : "");
        if (!textoTermo.isBlank()) {
            for (Map.Entry<String, Set<String>> entry : subcategoriasPorKeywords.entrySet()) {
                for (String kw : entry.getValue()) {
                    String kwNorm = normalizar(kw);
                    Pattern pattern = Pattern.compile("\\b" + Pattern.quote(kwNorm) + "\\b", Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(textoTermo).find()) {
                        return entry.getKey();
                    }
                }
            }
        }

        // 3. Fallback final: usa as 2 primeiras palavras-chave significativas do termo de busca
        if (termoBusca != null && !termoBusca.isBlank()) {
            String canonico = extrairTokensSignificativos(termoBusca).stream()
                    .limit(2)
                    .collect(Collectors.joining("_"))
                    .toUpperCase();
            if (!canonico.isBlank()) {
                return canonico;
            }
        }

        return "OUTROS";
    }

    /**
     * Calcula o índice de similaridade de Jaccard entre as palavras-chave de dois títulos.
     * Retorna um valor entre 0.0 (totalmente distintos) e 1.0 (idênticos).
     */
    public double calcularSimilaridadeJaccard(String titulo1, String titulo2) {
        Set<String> tokens1 = extrairTokensSignificativos(titulo1);
        Set<String> tokens2 = extrairTokensSignificativos(titulo2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersecao = new HashSet<>(tokens1);
        intersecao.retainAll(tokens2);

        Set<String> uniao = new HashSet<>(tokens1);
        uniao.addAll(tokens2);

        if (uniao.isEmpty()) {
            return 0.0;
        }

        return (double) intersecao.size() / uniao.size();
    }

    /**
     * Extrai tokens normalizados ignorando acentos, pontuações e stopwords.
     */
    public Set<String> extrairTokensSignificativos(String texto) {
        if (texto == null || texto.isBlank()) {
            return Set.of();
        }

        String limpo = normalizar(texto).replaceAll("[^a-z0-9\\s]", " ");
        return Arrays.stream(limpo.split("\\s+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOPWORDS.contains(t))
                .collect(Collectors.toSet());
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(decomposto).replaceAll("").toLowerCase().trim();
    }
}

package com.smart.price.service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.Nicho;
import com.smart.price.repository.NichoRepository;

@Service
public class CupomCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(CupomCrawlerService.class);

    private final RestTemplate restTemplate;
    private final CupomService cupomService;
    private final NichoRepository nichoRepository;
    private final CupomValidadorService cupomValidadorService;

    @Value("${cupons.crawler.enabled:true}")
    private boolean enabled;

    @Value("#{'${cupons.crawler.fontes:https://t.me/s/MercadoCuponsBR,https://t.me/s/promosdorafa,https://t.me/s/PromoCupons_Brasil}'.split(',')}")
    private List<String> fontesUrls;

    // Circuit Breaker: Guarda timestamps de cooldown para evitar reincidência se uma fonte estiver instável
    private final Map<String, LocalDateTime> cooldownPorFonte = new ConcurrentHashMap<>();

    // Regex para identificar itens em feeds RSS/Atom
    private static final Pattern PATTERN_RSS_ITEM = Pattern.compile("(?is)<item>(.*?)</item>|<entry>(.*?)</entry>");
    private static final Pattern PATTERN_TITLE = Pattern.compile("(?is)<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>");
    private static final Pattern PATTERN_DESC = Pattern.compile("(?is)<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</description>|<content.*?>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</content>");
    private static final Pattern PATTERN_LINK = Pattern.compile("(?is)<link>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</link>|<link[^>]*href=[\"']([^\"']+)[\"']");

    // Regex para canais públicos do Telegram (t.me/s/...)
    private static final Pattern PATTERN_TG_MESSAGE = Pattern.compile("(?is)<div class=\"tgme_widget_message_text[^>]*>(.*?)</div>");
    private static final Pattern PATTERN_CODE_TAG = Pattern.compile("(?i)<code>(.*?)</code>");
    private static final Pattern PATTERN_HREF = Pattern.compile("(?i)href=[\"']([^\"']+)[\"']");

    // Conjunto de palavras reservadas / comuns que não são códigos de cupom
    private static final java.util.Set<String> PALAVRAS_IGNORADAS = java.util.Set.of(
            "MERCADO", "LIVRE", "MERCADOLIVRE", "OFERTA", "DESCONTO", "DESCONTOS",
            "PROMOCAO", "PROMOÇÃO", "PROMO", "CUPOM", "CUPONS", "CODIGO", "CÓDIGO",
            "FRETE", "GRATIS", "GRÁTIS", "COMPRA", "COMPRAS", "PARA", "TODO", "TODA",
            "SITE", "BRASIL", "SELECIONADOS", "PRODUTOS", "REAIS", "VALIDO", "VÁLIDO",
            "CHECKOUT", "ANTES", "PAGAR", "APROVEITE", "CONFIRA", "VEJA", "CLIQUE", "AQUI", "LINK",
            "AVISE-ME", "AVISE", "APP", "HTTP", "HTTPS", "ESCRITO", "RESGATE", "AVISO", "REGRAS", "CANAL", "GRUPO", "GRUPOS", "TERMOS"
    );

    // Regex para identificar códigos de cupom
    private static final Pattern PATTERN_CUPOM_EXPLICITO = Pattern.compile(
            "(?i)(?:use o cupom|com o c[oó]digo|cupom\\s*[:=\\-]|c[oó]digo\\s*[:=\\-]|cupom|c[oó]digo)\\s*[`\"']?\\s*([A-Z0-9_\\-]{3,20})\\b");
    private static final Pattern PATTERN_CUPOM_MELI = Pattern.compile(
            "\\b(MELI[A-Z0-9_\\-]{1,16}|VALE[A-Z0-9_\\-]{1,16}|APP[A-Z0-9_\\-]{1,16}|ACHEI[A-Z0-9_\\-]{1,16}|TEM[A-Z0-9_\\-]{1,16}|TECH[A-Z0-9_\\-]{1,16}|ELETRO[A-Z0-9_\\-]{1,16}|MODA[A-Z0-9_\\-]{1,16}|CASA[A-Z0-9_\\-]{1,16}|NOVO[A-Z0-9_\\-]{1,16}|GANHE[A-Z0-9_\\-]{1,16})\\b");

    // Regex para identificar valores de desconto
    private static final Pattern PATTERN_PERCENTUAL = Pattern.compile("(\\d{1,2})\\s*%\\s*(?:OFF|de desconto)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_VALOR_FIXO = Pattern.compile("(?i)(?:R\\$\\s*|desconto de\\s*R\\$\\s*)(\\d+(?:[.,]\\d{2})?)\\s*(?:OFF|de desconto|reais)");
    private static final Pattern PATTERN_VALOR_REAIS = Pattern.compile("(\\d+)\\s*reais\\s*OFF", Pattern.CASE_INSENSITIVE);

    // Regex para valor mínimo de compra
    private static final Pattern PATTERN_VALOR_MINIMO = Pattern.compile("(?i)(?:acima de|a partir de|m[ií]nimo de|compras de|compras a partir de|em compras de|em)\\s*R\\$\\s*(\\d+(?:[.,]\\d{2})?)");

    public CupomCrawlerService(
            RestTemplate restTemplate,
            CupomService cupomService,
            NichoRepository nichoRepository,
            CupomValidadorService cupomValidadorService) {
        this.restTemplate = restTemplate;
        this.cupomService = cupomService;
        this.nichoRepository = nichoRepository;
        this.cupomValidadorService = cupomValidadorService;
    }

    /**
     * Agendamento autônomo: varre os feeds a cada 5 minutos (configurável).
     * Roda 100% silencioso se não houver cupons novos.
     */
    @Scheduled(cron = "${cupons.crawler.cron:0 0/5 * * * ?}")
    public void executarCicloCrawler() {
        if (!enabled) {
            logger.debug("CupomCrawlerService: Crawler desabilitado nas configurações.");
            return;
        }

        logger.debug("CupomCrawlerService: Iniciando ciclo autônomo de busca de cupons do Mercado Livre...");
        int novosCadastrados = executarVarreduraManual();
        if (novosCadastrados > 0) {
            logger.info("CupomCrawlerService: Ciclo finalizado com sucesso! {} novos cupons descobertos e anunciados.", novosCadastrados);
        } else {
            logger.debug("CupomCrawlerService: Nenhum cupom novo descoberto nesta rodada. Silêncio mantido.");
        }
    }

    /**
     * Executa a varredura em todas as fontes configuradas e processa novos cupons.
     * Retorna a quantidade de cupons inéditos cadastrados e anunciados.
     */
    public int executarVarreduraManual() {
        if (fontesUrls == null || fontesUrls.isEmpty()) {
            return 0;
        }

        int novos = 0;
        List<Nicho> nichosDisponiveis = nichoRepository.findAll();

        for (String urlFonte : fontesUrls) {
            String url = urlFonte.trim();
            if (url.isBlank()) continue;

            // Proteção contra rate limit: se estiver em cooldown, pula
            LocalDateTime cooldownAte = cooldownPorFonte.get(url);
            if (cooldownAte != null && LocalDateTime.now().isBefore(cooldownAte)) {
                logger.debug("CupomCrawlerService: Fonte [{}] em cooldown até {}. Pulando...", url, cooldownAte);
                continue;
            }

            try {
                String conteudoFeed = buscarFeedComHeadersSeguros(url);
                if (conteudoFeed != null && !conteudoFeed.isBlank()) {
                    List<Cupom> cuponsExtraidos = extrairCuponsDoFeed(conteudoFeed, nichosDisponiveis);
                    for (Cupom c : cuponsExtraidos) {
                        Optional<Cupom> processado = cupomService.processarCupomDetectado(c);
                        if (processado.isPresent()) {
                            novos++;
                        }
                    }
                }
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                    logger.warn("CupomCrawlerService: Fonte [{}] retornou HTTP {}. Ativando Circuit Breaker (pausa de 15 min).",
                            url, ex.getStatusCode());
                    cooldownPorFonte.put(url, LocalDateTime.now().plusMinutes(15));
                } else {
                    logger.warn("CupomCrawlerService: Erro HTTP [{}] ao ler fonte [{}]: {}",
                            ex.getStatusCode(), url, ex.getMessage());
                }
            } catch (Exception ex) {
                logger.warn("CupomCrawlerService: Falha ao consultar feed [{}]: {}", url, ex.getMessage());
            }
        }

        return novos;
    }

    /**
     * Realiza a requisição HTTP com cabeçalhos de navegador comuns e seguros, evitando bloqueios anti-bot.
     */
    private String buscarFeedComHeadersSeguros(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.set("Accept", "application/rss+xml, application/xml, text/xml, text/html, application/xhtml+xml, */*");
        headers.set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.set("Cache-Control", "no-cache");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(URI.create(url), HttpMethod.GET, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        return null;
    }

    /**
     * Faz o parse de feeds RSS/Atom XML ou de canais públicos do Telegram (t.me/s/...) e extrai cupons do Mercado Livre.
     */
    public List<Cupom> extrairCuponsDoFeed(String conteudo, List<Nicho> nichosDisponiveis) {
        List<Cupom> encontrados = new ArrayList<>();
        if (conteudo == null || conteudo.isBlank()) {
            return encontrados;
        }

        // 1. Tenta formato RSS / Atom XML
        Matcher itemMatcher = PATTERN_RSS_ITEM.matcher(conteudo);
        boolean isRss = false;
        while (itemMatcher.find()) {
            isRss = true;
            String itemXml = itemMatcher.group(1) != null ? itemMatcher.group(1) : itemMatcher.group(2);
            if (itemXml == null) continue;

            String titulo = extrairTag(itemXml, PATTERN_TITLE);
            String descricao = extrairTag(itemXml, PATTERN_DESC);
            String link = extrairTag(itemXml, PATTERN_LINK);

            String textoCompleto = (titulo + " " + descricao).trim();

            if (!isRelevanteMercadoLivre(textoCompleto) || !temMencaoCupom(textoCompleto)) {
                continue;
            }

            Optional<Cupom> cupomOpt = interpretarDadosCupom(titulo, descricao, link, nichosDisponiveis);
            cupomOpt.ifPresent(encontrados::add);
        }

        if (isRss) {
            return encontrados;
        }

        // 2. Tenta formato Web Telegram Channel (ex: t.me/s/...)
        Matcher tgMatcher = PATTERN_TG_MESSAGE.matcher(conteudo);
        while (tgMatcher.find()) {
            String postHtml = tgMatcher.group(1);
            if (postHtml == null || postHtml.isBlank()) continue;

            String postDecodificado = decodificarHtml(postHtml);

            if (!isRelevanteMercadoLivre(postDecodificado) || !temMencaoCupom(postDecodificado)) {
                continue;
            }

            List<Cupom> cuponsPost = extrairCuponsDePostTelegram(postDecodificado, nichosDisponiveis);
            encontrados.addAll(cuponsPost);
        }

        return encontrados;
    }

    /**
     * Extrai cupons de uma mensagem de canal do Telegram, tratando códigos múltiplos e formatação <code>.
     */
    public List<Cupom> extrairCuponsDePostTelegram(String postHtml, List<Nicho> nichosDisponiveis) {
        List<Cupom> cupons = new ArrayList<>();
        if (postHtml == null || postHtml.isBlank()) {
            return cupons;
        }

        String linkHotsite = extrairLinkMercadoLivre(postHtml);

        Matcher mCode = PATTERN_CODE_TAG.matcher(postHtml);
        List<String> codigos = new ArrayList<>();
        while (mCode.find()) {
            String cand = mCode.group(1).replaceAll("<[^>]*>", "").trim().toUpperCase();
            if (isCodigoValido(cand) && !codigos.contains(cand)) {
                codigos.add(cand);
            }
        }

        if (!codigos.isEmpty()) {
            String[] linhas = postHtml.split("(?i)<br\\s*/?>|\\r?\\n");
            for (String cod : codigos) {
                String linhaContexto = "";
                for (String l : linhas) {
                    if (l.toUpperCase().contains(cod)) {
                        linhaContexto = l;
                        break;
                    }
                }

                String contextoUso = (!linhaContexto.isBlank() && (linhaContexto.contains("%") || linhaContexto.contains("OFF") || linhaContexto.contains("$")))
                        ? linhaContexto
                        : postHtml;

                Cupom c = montarCupom(cod, contextoUso, postHtml, linkHotsite, nichosDisponiveis);
                cupons.add(c);
            }
            return cupons;
        }

        Optional<Cupom> cupomUnico = interpretarDadosCupom("Cupom Mercado Livre", postHtml, linkHotsite, nichosDisponiveis);
        cupomUnico.ifPresent(cupons::add);
        return cupons;
    }

    private Cupom montarCupom(String codigo, String contextoDesconto, String postCompleto, String linkHotsite, List<Nicho> nichosDisponiveis) {
        String textoDescontoLimpo = contextoDesconto.replaceAll("<[^>]*>", " ");
        String textoCompletoLimpo = postCompleto.replaceAll("<[^>]*>", " ");

        String tipoDesconto = "VALOR_FIXO";
        BigDecimal valorDesconto = BigDecimal.ZERO;

        Matcher mPerc = PATTERN_PERCENTUAL.matcher(textoDescontoLimpo);
        if (mPerc.find()) {
            tipoDesconto = "PERCENTUAL";
            valorDesconto = new BigDecimal(mPerc.group(1));
        } else {
            Matcher mFixo = PATTERN_VALOR_FIXO.matcher(textoDescontoLimpo);
            if (mFixo.find()) {
                tipoDesconto = "VALOR_FIXO";
                String valStr = mFixo.group(1).replace(".", "").replace(",", ".");
                valorDesconto = new BigDecimal(valStr);
            } else {
                Matcher mReais = PATTERN_VALOR_REAIS.matcher(textoDescontoLimpo);
                if (mReais.find()) {
                    tipoDesconto = "VALOR_FIXO";
                    valorDesconto = new BigDecimal(mReais.group(1));
                }
            }
        }

        if (valorDesconto.compareTo(BigDecimal.ZERO) <= 0) {
            valorDesconto = new BigDecimal("10.00");
        }

        BigDecimal valorMinimo = BigDecimal.ZERO;
        Matcher mMin = PATTERN_VALOR_MINIMO.matcher(textoDescontoLimpo);
        if (mMin.find()) {
            String minStr = mMin.group(1).replace(".", "").replace(",", ".");
            valorMinimo = new BigDecimal(minStr);
        } else {
            Matcher mMinGeral = PATTERN_VALOR_MINIMO.matcher(textoCompletoLimpo);
            if (mMinGeral.find()) {
                String minStr = mMinGeral.group(1).replace(".", "").replace(",", ".");
                valorMinimo = new BigDecimal(minStr);
            }
        }

        Nicho nichoCompativel = identificarNicho(textoCompletoLimpo, nichosDisponiveis);

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        String desc = "Cupom " + codigo + " no Mercado Livre ("
                + (tipoDesconto.equals("PERCENTUAL") ? valorDesconto + "% OFF" : "R$ " + valorDesconto + " OFF")
                + (valorMinimo.compareTo(BigDecimal.ZERO) > 0 ? " acima de R$ " + valorMinimo : "") + ")";
        cupom.setDescricao(desc);
        cupom.setTipoDesconto(tipoDesconto);
        cupom.setValorDesconto(valorDesconto);
        cupom.setValorMinimoCompra(valorMinimo);
        cupom.setNicho(nichoCompativel);
        cupom.setLinkHotsite(linkHotsite);
        cupom.setDataInicio(LocalDateTime.now());
        // TTL reduzido para 6h: cupons relâmpago do ML costumam esgotar em poucas horas
        cupom.setDataExpiracao(LocalDateTime.now().plusHours(6));
        cupom.setAtivo(true);
        // Sonda automática: valida o cupom no ML via HTTP antes de definir testado
        boolean validado = cupomValidadorService.validarCupomAutomaticamente(cupom);
        cupom.setTestado(validado);
        cupom.setAnunciadoAvulso(false);
        cupom.setObservacao("Capturado automaticamente via crawler autônomo em " + LocalDateTime.now()
                + (validado ? " | Validado automaticamente ✅" : " | Em quarentena — validação falhou ⚠️"));

        return cupom;
    }

    private boolean isCodigoValido(String cand) {
        if (cand == null) return false;
        String clean = cand.trim().toUpperCase();
        if (clean.length() < 3 || clean.length() > 25) return false;
        if (PALAVRAS_IGNORADAS.contains(clean)) return false;
        return clean.matches("^[A-Z0-9_\\-]+$");
    }

    private String extrairLinkMercadoLivre(String postHtml) {
        if (postHtml == null) return "https://www.mercadolivre.com.br/cupons";
        Matcher mLink = PATTERN_HREF.matcher(postHtml);
        while (mLink.find()) {
            String href = mLink.group(1);
            if (href.contains("mercadolivre.com") || href.contains("meli.la")) {
                return href;
            }
        }
        return "https://www.mercadolivre.com.br/cupons";
    }

    private String decodificarHtml(String html) {
        if (html == null) return "";
        return html
                .replace("&#036;", "$")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    /**
     * Interpreta o texto do post/artigo e monta a entidade Cupom com dados estruturados.
     */
    public Optional<Cupom> interpretarDadosCupom(String titulo, String descricao, String link, List<Nicho> nichosDisponiveis) {
        String texto = (titulo + " " + descricao).replaceAll("<[^>]*>", " "); // Remove tags HTML

        // A. Extração do Código
        String codigo = null;

        // 1ª Prioridade: Códigos com prefixos típicos do Mercado Livre (MELI..., VALE..., TECH..., etc.)
        Matcher mMeli = PATTERN_CUPOM_MELI.matcher(texto);
        while (mMeli.find()) {
            String cand = mMeli.group(1).trim().toUpperCase();
            if (!PALAVRAS_IGNORADAS.contains(cand) && cand.length() >= 4) {
                codigo = cand;
                break;
            }
        }

        // 2ª Prioridade: Frases com cupom explícito ("com o código X", "use o cupom X", "cupom: X")
        if (codigo == null) {
            Matcher mExplicito = PATTERN_CUPOM_EXPLICITO.matcher(texto);
            while (mExplicito.find()) {
                String cand = mExplicito.group(1).trim().toUpperCase();
                if (!PALAVRAS_IGNORADAS.contains(cand) && cand.length() >= 4) {
                    codigo = cand;
                    break;
                }
            }
        }

        if (codigo == null || codigo.length() < 3 || codigo.length() > 25) {
            return Optional.empty();
        }

        // B. Tipo e Valor de Desconto
        String tipoDesconto = "VALOR_FIXO";
        BigDecimal valorDesconto = BigDecimal.ZERO;

        Matcher mPerc = PATTERN_PERCENTUAL.matcher(texto);
        if (mPerc.find()) {
            tipoDesconto = "PERCENTUAL";
            valorDesconto = new BigDecimal(mPerc.group(1));
        } else {
            Matcher mFixo = PATTERN_VALOR_FIXO.matcher(texto);
            if (mFixo.find()) {
                tipoDesconto = "VALOR_FIXO";
                String valStr = mFixo.group(1).replace(".", "").replace(",", ".");
                valorDesconto = new BigDecimal(valStr);
            } else {
                Matcher mReais = PATTERN_VALOR_REAIS.matcher(texto);
                if (mReais.find()) {
                    tipoDesconto = "VALOR_FIXO";
                    valorDesconto = new BigDecimal(mReais.group(1));
                }
            }
        }

        if (valorDesconto.compareTo(BigDecimal.ZERO) <= 0) {
            // Desconto não identificado claramente; define valor mínimo simbólico se for cupom
            valorDesconto = new BigDecimal("10.00");
        }

        // C. Valor Mínimo de Compra
        BigDecimal valorMinimo = BigDecimal.ZERO;
        Matcher mMin = PATTERN_VALOR_MINIMO.matcher(texto);
        if (mMin.find()) {
            String minStr = mMin.group(1).replace(".", "").replace(",", ".");
            valorMinimo = new BigDecimal(minStr);
        }

        // D. Nicho / Categoria
        Nicho nichoCompativel = identificarNicho(texto, nichosDisponiveis);

        // E. Link de Hotsite
        String linkHotsite = (link != null && !link.isBlank()) ? link : "https://www.mercadolivre.com.br/cupons";

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setDescricao(titulo != null && !titulo.isBlank() ? limparTitulo(titulo) : ("Cupom " + codigo + " no Mercado Livre"));
        cupom.setTipoDesconto(tipoDesconto);
        cupom.setValorDesconto(valorDesconto);
        cupom.setValorMinimoCompra(valorMinimo);
        cupom.setNicho(nichoCompativel);
        cupom.setLinkHotsite(linkHotsite);
        cupom.setDataInicio(LocalDateTime.now());
        // TTL reduzido para 6h: cupons relâmpago do ML costumam esgotar em poucas horas
        cupom.setDataExpiracao(LocalDateTime.now().plusHours(6));
        cupom.setAtivo(true);
        // Sonda automática: valida o cupom no ML via HTTP antes de definir testado
        boolean validado = cupomValidadorService.validarCupomAutomaticamente(cupom);
        cupom.setTestado(validado);
        cupom.setAnunciadoAvulso(false);
        cupom.setObservacao("Capturado automaticamente via crawler autônomo em " + LocalDateTime.now()
                + (validado ? " | Validado automaticamente ✅" : " | Em quarentena — validação falhou ⚠️"));

        return Optional.of(cupom);
    }

    private boolean isRelevanteMercadoLivre(String texto) {
        if (texto == null) return false;
        String lower = texto.toLowerCase();
        return lower.contains("mercado livre") || lower.contains("mercadolivre")
                || lower.contains("meli") || lower.contains("mercadolivre.com")
                || lower.contains("meli.la");
    }

    private boolean temMencaoCupom(String texto) {
        if (texto == null) return false;
        String lower = texto.toLowerCase();
        return lower.contains("cupom") || lower.contains("código") || lower.contains("codigo")
                || lower.contains("vale") || lower.contains("off") || lower.contains("<code>");
    }

    /**
     * Identifica dinamicamente o nicho correspondente baseado no nome de cada nicho
     * ativo no banco e vocabulário semântico associado, suportando qualquer quantidade de nichos.
     */
    private Nicho identificarNicho(String texto, List<Nicho> nichos) {
        if (nichos == null || nichos.isEmpty() || texto == null || texto.isBlank()) {
            return null;
        }
        String lower = texto.toLowerCase();

        Nicho melhorNicho = null;
        int maxCorrespondencias = 0;

        for (Nicho n : nichos) {
            if (n.getNome() == null) continue;
            String nomeNicho = n.getNome().toLowerCase();

            // Divide as palavras do nome do nicho (ex: "FERRAMENTAS & CONSTRUÇÃO" -> ["ferramentas", "construção"])
            String[] partes = nomeNicho.split("[&/,\\s]+");
            int correspondencias = 0;

            for (String parte : partes) {
                String p = parte.trim();
                if (p.length() >= 4 && lower.contains(p)) {
                    correspondencias += 2;
                }
            }

            // Dicionário dinâmico de afinidade por categoria
            if (nomeNicho.contains("hardware") || nomeNicho.contains("informática") || nomeNicho.contains("informatica")) {
                if (lower.contains("computador") || lower.contains("notebook") || lower.contains("teclado") || lower.contains("ssd") || lower.contains("ram") || lower.contains("mouse")) correspondencias++;
            } else if (nomeNicho.contains("celular") || nomeNicho.contains("smartphone")) {
                if (lower.contains("iphone") || lower.contains("galaxy") || lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("motorola") || lower.contains("power bank")) correspondencias++;
            } else if (nomeNicho.contains("gamer") || nomeNicho.contains("game") || nomeNicho.contains("console")) {
                if (lower.contains("playstation") || lower.contains("xbox") || lower.contains("nintendo") || lower.contains("jogos") || lower.contains("ps5") || lower.contains("gamer")) correspondencias++;
            } else if (nomeNicho.contains("casa") || nomeNicho.contains("eletro")) {
                if (lower.contains("fritadeira") || lower.contains("geladeira") || lower.contains("aspirador") || lower.contains("ventilador") || lower.contains("cafeteira") || lower.contains("airfryer")) correspondencias++;
            } else if (nomeNicho.contains("ferramenta") || nomeNicho.contains("construção") || nomeNicho.contains("construcao")) {
                if (lower.contains("ferramenta") || lower.contains("furadeira") || lower.contains("parafusadeira") || lower.contains("solda") || lower.contains("chave")) correspondencias += 2;
            } else if (nomeNicho.contains("veículo") || nomeNicho.contains("veiculo") || nomeNicho.contains("auto")) {
                if (lower.contains("auto") || lower.contains("carro") || lower.contains("moto") || lower.contains("pneu") || lower.contains("automotivo") || lower.contains("óleo")) correspondencias += 2;
            } else if (nomeNicho.contains("áudio") || nomeNicho.contains("audio") || nomeNicho.contains("tv") || nomeNicho.contains("vídeo") || nomeNicho.contains("video")) {
            } else if (nomeNicho.contains("perfum") || nomeNicho.contains("cosmétic") || nomeNicho.contains("cosmetic") || nomeNicho.contains("beleza")) {
                if (lower.contains("perfume") || lower.contains("colonia") || lower.contains("colônia") || lower.contains("hidratante") || lower.contains("fragrancia") || lower.contains("fragrância") || lower.contains("maquiagem") || lower.contains("boticario") || lower.contains("natura")) correspondencias += 2;
            } else if (nomeNicho.contains("moda") || nomeNicho.contains("calçado") || nomeNicho.contains("calcado")) {
                if (lower.contains("roupa") || lower.contains("tênis") || lower.contains("tenis") || lower.contains("vestuário") || lower.contains("camisa") || lower.contains("calça")) correspondencias++;
            } else if (nomeNicho.contains("esporte") || nomeNicho.contains("fitness")) {
                if (lower.contains("suplemento") || lower.contains("academia") || lower.contains("bicicleta") || lower.contains("whey") || lower.contains("creatina")) correspondencias++;
            }

            if (correspondencias > maxCorrespondencias) {
                maxCorrespondencias = correspondencias;
                melhorNicho = n;
            }
        }

        return maxCorrespondencias > 0 ? melhorNicho : null;
    }

    private String extrairTag(String xml, Pattern pattern) {
        Matcher m = pattern.matcher(xml);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            return val != null ? val.trim() : "";
        }
        return "";
    }

    private String limparTitulo(String titulo) {
        return titulo.replaceAll("<[^>]*>", "").replaceAll("&amp;", "&").replaceAll("&quot;", "\"").trim();
    }
}

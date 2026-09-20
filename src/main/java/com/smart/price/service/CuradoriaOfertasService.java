package com.smart.price.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.entity.TermoBusca;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.repository.TermoBuscaRepository;
import com.smart.price.service.curadoria.CalculadorScoreOferta;
import com.smart.price.service.curadoria.SubcategoriaExtrator;
import com.smart.price.service.provedor.ProvedorLojaHub;
import com.smart.price.service.provedor.ProvedorLojaService;

@Service
public class CuradoriaOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(CuradoriaOfertasService.class);

    private final TermoBuscaRepository termoBuscaRepository;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;
    private final ProvedorLojaHub provedorLojaHub;
    private final CopywriterIaService copywriterIaService;
    private final CupomService cupomService;
    private final SubcategoriaExtrator subcategoriaExtrator;
    private final CalculadorScoreOferta calculadorScoreOferta;

    @Value("${monitoramento.curadoria.horas-anti-duplicacao:24}")
    private int horasAntiDuplicacao;

    @Value("${monitoramento.curadoria.min-ciclos-anti-duplicacao:3}")
    private int minCiclosAntiDuplicacao;

    @Value("${monitoramento.curadoria.termos-por-ciclo:5}")
    private int termosPorCiclo;

    @Value("${monitoramento.curadoria.max-ofertas-por-nicho:2}")
    private int maxOfertasPorNicho;

    @Value("${monitoramento.curadoria.score-minimo:60}")
    private int scoreMinimo;

    @Value("${monitoramento.curadoria.delay-ms:600}")
    private long delayMs;

    public CuradoriaOfertasService(
            TermoBuscaRepository termoBuscaRepository,
            OfertaDescobertaRepository ofertaDescobertaRepository,
            ProvedorLojaHub provedorLojaHub,
            CopywriterIaService copywriterIaService,
            CupomService cupomService,
            SubcategoriaExtrator subcategoriaExtrator,
            CalculadorScoreOferta calculadorScoreOferta) {
        this.termoBuscaRepository = termoBuscaRepository;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.provedorLojaHub = provedorLojaHub;
        this.copywriterIaService = copywriterIaService;
        this.cupomService = cupomService;
        this.subcategoriaExtrator = subcategoriaExtrator;
        this.calculadorScoreOferta = calculadorScoreOferta;
    }

    /**
     * Realiza a curadoria de ofertas do nicho aplicando regras de negócio:
     * controle de repetição por ciclo, diversificação por subcategoria e pontuação mínima de relevância.
     */
    @Transactional
    public List<OfertaDescoberta> curarMelhoresOfertasDoNicho(Nicho nicho) {
        if (nicho == null) {
            return List.of();
        }

        int cicloAtual = (nicho.getTotalCiclos() != null && nicho.getTotalCiclos() > 0) ? nicho.getTotalCiclos() : 1;
        List<ProvedorLojaService> provedoresAtivos = provedorLojaHub.getProvedoresAtivos();
        List<OfertaDescoberta> candidatosBrutos = new ArrayList<>();

        // 1. Coleta inicial de ofertas em destaque por categoria oficial
        for (ProvedorLojaService provedor : provedoresAtivos) {
            try {
                List<OfertaDescoberta> emAlta = provedor.buscarOfertasEmAlta(nicho);
                if (emAlta != null && !emAlta.isEmpty()) {
                    candidatosBrutos.addAll(emAlta);
                    logger.info("CuradoriaOfertasService: Provedor [{}] retornou {} ofertas em alta para nicho '{}'.",
                            provedor.getLoja(), emAlta.size(), nicho.getNome());
                }
            } catch (Exception e) {
                logger.error("CuradoriaOfertasService: Erro ao buscar ofertas em alta no provedor [{}]: {}",
                        provedor.getLoja(), e.getMessage());
            }
        }

        // 2. Mineração complementar por termos de busca cadastrados (apenas se os destaques em alta trouxerem menos de 6 itens)
        if (candidatosBrutos.size() < 6) {
            List<TermoBusca> termos = termoBuscaRepository.findProximosTermosParaBusca(
                    nicho,
                    PageRequest.of(0, termosPorCiclo)
            );
            if (termos.isEmpty()) {
                termos = termoBuscaRepository.findByNichoAndAtivoTrue(nicho);
            }

            if (!termos.isEmpty()) {
                String termosNomes = termos.stream().map(TermoBusca::getTermo).collect(Collectors.joining(", "));
                logger.info("CuradoriaOfertasService: Volume em alta baixo ({} itens). Pesquisando também {} termos rotativos para nicho '{}': [{}]...",
                        candidatosBrutos.size(), termos.size(), nicho.getNome(), termosNomes);

                for (TermoBusca termoBusca : termos) {
                    try {
                        if (delayMs > 0) {
                            Thread.sleep(delayMs);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("CuradoriaOfertasService: Intervalo de delay interrompido.");
                        break;
                    }

                    for (ProvedorLojaService provedor : provedoresAtivos) {
                        try {
                            List<OfertaDescoberta> ofertasDoTermo = provedor.buscarOfertasPorTermo(termoBusca.getTermo(), nicho);
                            if (ofertasDoTermo != null && !ofertasDoTermo.isEmpty()) {
                                candidatosBrutos.addAll(ofertasDoTermo);
                            }
                        } catch (Exception e) {
                            logger.error("CuradoriaOfertasService: Erro ao buscar ofertas por termo [{}] no provedor [{}]: {}",
                                    termoBusca.getTermo(), provedor.getLoja(), e.getMessage());
                        }
                    }

                    termoBusca.setDataUltimaBusca(LocalDateTime.now());
                    termoBusca.setTotalBuscas((termoBusca.getTotalBuscas() != null ? termoBusca.getTotalBuscas() : 0) + 1);
                    termoBuscaRepository.save(termoBusca);
                }
            }
        }

        if (candidatosBrutos.isEmpty()) {
            logger.info("CuradoriaOfertasService: Nenhuma oferta encontrada para o nicho '{}' (em alta ou por termos).", nicho.getNome());
            return List.of();
        }

        logger.info("CuradoriaOfertasService: Ciclo #{}: Analisando {} ofertas candidatas para o nicho '{}'...",
                cicloAtual, candidatosBrutos.size(), nicho.getNome());

        LocalDateTime limiteAntiDuplicacao = LocalDateTime.now().minusHours(horasAntiDuplicacao);
        List<OfertaDescoberta> candidatas = new ArrayList<>();
        Set<String> idsColetadosNaRodada = new HashSet<>();
        Set<String> urlsColetadasNaRodada = new HashSet<>();
        Set<String> titulosColetadosNaRodada = new HashSet<>();

        for (OfertaDescoberta oferta : candidatosBrutos) {
            if (oferta.getPreco() == null || oferta.getPreco().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Ignora ofertas indisponíveis ou sem URL
            if (Boolean.FALSE.equals(oferta.getDisponivel()) || oferta.getUrl() == null || oferta.getUrl().isBlank()) {
                continue;
            }

            String mlbId = oferta.getMlbId();
            String urlNormalizada = normalizarUrl(oferta.getUrl());
            String tituloNormalizado = normalizarTitulo(oferta.getTitulo());

            // 1. Anti-duplicação na mesma rodada (ID único, URL ou título idêntico)
            if (mlbId != null && idsColetadosNaRodada.contains(mlbId)) {
                continue;
            }
            if (!urlNormalizada.isBlank() && urlsColetadasNaRodada.contains(urlNormalizada)) {
                continue;
            }
            if (!tituloNormalizado.isBlank() && titulosColetadosNaRodada.contains(tituloNormalizado)) {
                continue;
            }

            // 2. Filtro de corte de miudezas/acessórios e sanidade mínima de preço
            if (isMiudezaOuAcessorioIrrelevante(oferta)) {
                logger.info("CuradoriaOfertasService: Oferta [{}] descartada por filtro de miudeza/acessório irrelevante.", oferta.getTitulo());
                continue;
            }

            // 3. Extração semântica da subcategoria do produto
            String termoOrigem = oferta.getTermoOrigem() != null ? oferta.getTermoOrigem() : "";
            String subcategoria = subcategoriaExtrator.extrairSubcategoria(oferta.getTitulo(), termoOrigem);
            oferta.setSubcategoria(subcategoria);

            // 3. Checagem de Menor Preço Histórico e Baixa de Preço
            java.util.Optional<OfertaDescoberta> menorAnterior = (mlbId != null)
                    ? ofertaDescobertaRepository.findFirstByMlbIdOrderByPrecoAsc(mlbId)
                    : java.util.Optional.empty();
            boolean precoCaiu = false;
            if (menorAnterior.isPresent()) {
                if (oferta.getPreco().compareTo(menorAnterior.get().getPreco()) < 0) {
                    oferta.setMenorPrecoHistorico(true);
                    precoCaiu = true;
                    logger.info("CuradoriaOfertasService: Menor preço histórico! [{}] caiu de R$ {} para R$ {}!",
                            mlbId, menorAnterior.get().getPreco(), oferta.getPreco());
                }
            } else {
                if (temDesconto(oferta)) {
                    oferta.setMenorPrecoHistorico(true);
                }
            }

            // 4. Anti-duplicação por ciclo: Se o preço não caiu, impede republicação recente (3 ciclos do nicho)
            if (!precoCaiu) {
                java.util.Optional<OfertaDescoberta> ultimaOcorrencia = ofertaDescobertaRepository
                        .findUltimaPublicacaoNoNicho(mlbId, oferta.getUrl(), nicho);

                if (ultimaOcorrencia.isPresent()) {
                    OfertaDescoberta anterior = ultimaOcorrencia.get();
                    Integer cicloAnterior = anterior.getCicloNicho();

                    if (cicloAnterior != null) {
                        int ciclosPassados = cicloAtual - cicloAnterior;
                        if (ciclosPassados < minCiclosAntiDuplicacao) {
                            continue;
                        }
                    } else {
                        if (anterior.getDataDescoberta() != null && anterior.getDataDescoberta().isAfter(limiteAntiDuplicacao)) {
                            continue;
                        }
                    }
                }
            }

            if (mlbId != null) {
                idsColetadosNaRodada.add(mlbId);
            }
            if (!urlNormalizada.isBlank()) {
                urlsColetadasNaRodada.add(urlNormalizada);
            }
            if (!tituloNormalizado.isBlank()) {
                titulosColetadosNaRodada.add(tituloNormalizado);
            }

            // 5. Pareamento com cupons de desconto ativos
            java.util.Optional<com.smart.price.entity.Cupom> cupomOpt = cupomService.encontrarMelhorCupomParaOferta(oferta);
            if (cupomOpt.isPresent()) {
                com.smart.price.entity.Cupom cupom = cupomOpt.get();
                BigDecimal descCupom = cupom.calcularDesconto(oferta.getPreco());
                if (descCupom.compareTo(BigDecimal.ZERO) > 0) {
                    oferta.setCupomAplicado(cupom.getCodigo());
                    oferta.setDescontoCupom(descCupom);
                    oferta.setPrecoComCupom(oferta.getPreco().subtract(descCupom));
                }
            }

            // 6. Cálculo da pontuação de relevância
            int score = calculadorScoreOferta.calcularScore(oferta);
            oferta.setScoreQualidade(score);

            // Filtro de corte: só avança se a oferta atingir o score mínimo
            if (score < scoreMinimo) {
                logger.debug("CuradoriaOfertasService: Oferta [{}] descartada por Score insuficiente ({} < {})",
                        oferta.getTitulo(), score, scoreMinimo);
                continue;
            }

            oferta.setCicloNicho(cicloAtual);
            candidatas.add(oferta);
        }

        if (candidatas.isEmpty()) {
            logger.info("CuradoriaOfertasService: Nenhuma oferta atingiu o Score Mínimo (>= {}) para o nicho '{}' no Ciclo #{}. Canal preservado de spam.",
                    scoreMinimo, nicho.getNome(), cicloAtual);
            return List.of();
        }

        // Ordenação por relevância: Score de Qualidade primeiro, menor preço histórico, maior desconto, menor preço
        Comparator<OfertaDescoberta> comparadorRelevancia = Comparator
                .comparing((OfertaDescoberta o) -> o.getScoreQualidade() != null ? -o.getScoreQualidade() : 0)
                .thenComparing((OfertaDescoberta o) -> Boolean.TRUE.equals(o.getMenorPrecoHistorico()) ? 0 : 1)
                .thenComparing((OfertaDescoberta o) -> o.getDescontoPercentual() != null ? -o.getDescontoPercentual() : 0)
                .thenComparing(OfertaDescoberta::getPreco);

        List<OfertaDescoberta> ordenadas = candidatas.stream()
                .sorted(comparadorRelevancia)
                .toList();

        // 7. DIVERSIFICAÇÃO DE LOTE E ANTI-DUPLICAÇÃO SEMÂNTICA:
        // Garante no máximo 1 produto por subcategoria e sem títulos com similaridade alta (> 0.45)
        List<OfertaDescoberta> selecionadas = new ArrayList<>();
        Set<String> subcategoriasNoLote = new HashSet<>();

        for (OfertaDescoberta cand : ordenadas) {
            String sub = cand.getSubcategoria();

            // Bloqueia se a mesma subcategoria já foi contemplada no lote atual (ex: 2 mouses, 2 suportes, 2 fritadeiras)
            if (sub != null && !"OUTROS".equalsIgnoreCase(sub) && subcategoriasNoLote.contains(sub)) {
                logger.info("CuradoriaOfertasService: Oferta [{}] ignorada por subcategoria [{}] já presente na mesma leva.",
                        cand.getTitulo(), sub);
                continue;
            }

            // Bloqueia se o título possui similaridade textual excessiva com algum item já selecionado
            boolean similar = false;
            for (OfertaDescoberta sel : selecionadas) {
                double sim = subcategoriaExtrator.calcularSimilaridadeJaccard(cand.getTitulo(), sel.getTitulo());
                if (sim >= 0.45) {
                    logger.info("CuradoriaOfertasService: Oferta [{}] descartada por similaridade alta ({}) com [{}] na mesma leva.",
                            cand.getTitulo(), String.format("%.2f", sim), sel.getTitulo());
                    similar = true;
                    break;
                }
            }
            if (similar) {
                continue;
            }

            if (sub != null && !"OUTROS".equalsIgnoreCase(sub)) {
                subcategoriasNoLote.add(sub);
            }
            selecionadas.add(cand);

            if (selecionadas.size() >= maxOfertasPorNicho) {
                break;
            }
        }

        if (selecionadas.isEmpty()) {
            logger.info("CuradoriaOfertasService: Nenhuma oferta restante após filtro de diversificação para o nicho '{}'.", nicho.getNome());
            return List.of();
        }

        // Gera copy personalizada com IA para cada oferta selecionada
        for (OfertaDescoberta oferta : selecionadas) {
            oferta.setCicloNicho(cicloAtual);
            if (provedorLojaHub != null && oferta.getLoja() != null && oferta.getUrl() != null) {
                String linkAfiliado = provedorLojaHub.formatarLinkAfiliado(oferta.getLoja(), oferta.getUrl());
                if (linkAfiliado != null && !linkAfiliado.isBlank()) {
                    oferta.setUrl(linkAfiliado);
                }
            }
            String copy = copywriterIaService.gerarCopyOferta(
                    oferta,
                    oferta.getCupomAplicado(),
                    oferta.getPrecoComCupom()
            );
            oferta.setCopyIa(copy);
        }

        List<OfertaDescoberta> salvas = ofertaDescobertaRepository.saveAll(selecionadas);

        logger.info("CuradoriaOfertasService: Curadoria finalizada com sucesso! {} ofertas de ouro selecionadas para o nicho '{}' (Ciclo #{}).",
                salvas.size(), nicho.getNome(), cicloAtual);

        return salvas;
    }

    private boolean temDesconto(OfertaDescoberta o) {
        return o.getPrecoOriginal() != null && o.getPrecoOriginal().compareTo(o.getPreco()) > 0;
    }

    private String normalizarUrl(String url) {
        if (url == null || url.isBlank()) return "";
        int queryIndex = url.indexOf('?');
        String base = (queryIndex >= 0) ? url.substring(0, queryIndex) : url;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.trim().toLowerCase();
    }

    private String normalizarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) return "";
        return java.text.Normalizer.normalize(titulo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Filtra anúncios de acessórios baratos (capinhas, películas, adesivos, suportes plásticos)
     * quando o termo de busca for de produtos nobres (ex: iphone, console, notebook, smart tv, placa de video).
     */
    private boolean isMiudezaOuAcessorioIrrelevante(OfertaDescoberta oferta) {
        if (oferta == null || oferta.getTitulo() == null) {
            return false;
        }

        String tituloLower = oferta.getTitulo().toLowerCase(Locale.ROOT);
        String termoLower = (oferta.getTermoOrigem() != null) ? oferta.getTermoOrigem().toLowerCase(Locale.ROOT) : "";
        BigDecimal preco = oferta.getPreco();

        // 1. Sanidade mínima de preço para categorias nobres
        // Se a busca é por iPhone, console, notebook, TV ou placa de vídeo, não pode custar menos de R$ 150
        boolean termoNobre = termoLower.contains("iphone")
                || termoLower.contains("playstation")
                || termoLower.contains("xbox")
                || termoLower.contains("switch")
                || termoLower.contains("notebook")
                || termoLower.contains("smart tv")
                || termoLower.contains("placa de video")
                || termoLower.contains("rtx")
                || termoLower.contains("apple watch");

        if (termoNobre && preco != null && preco.compareTo(BigDecimal.valueOf(150.0)) < 0) {
            return true;
        }

        // 2. Se o termo não é expressamente de acessórios/capas, rejeita títulos com palavras típicas de miudezas
        boolean buscaEspecificaAcessorio = termoLower.contains("capa")
                || termoLower.contains("pelicula")
                || termoLower.contains("suporte")
                || termoLower.contains("adesivo")
                || termoLower.contains("case");

        if (!buscaEspecificaAcessorio) {
            List<String> palavrasMiudeza = List.of(
                    "capa para", "capinha para", "capinha de", "case para",
                    "pelicula para", "película para", "película de", "pelicula 3d",
                    "adesivo para", "skin para", "skin adesivo", "suporte de tomada"
            );
            for (String m : palavrasMiudeza) {
                if (tituloLower.contains(m)) {
                    return true;
                }
            }
        }

        return false;
    }
}

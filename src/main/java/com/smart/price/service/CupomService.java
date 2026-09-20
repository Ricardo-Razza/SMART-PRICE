package com.smart.price.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smart.price.dto.request.CupomRequest;
import com.smart.price.entity.Cupom;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.repository.CupomRepository;
import com.smart.price.repository.NichoRepository;
import com.smart.price.repository.OfertaDescobertaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class CupomService {

    private static final Logger logger = LoggerFactory.getLogger(CupomService.class);

    private final CupomRepository cupomRepository;
    private final NichoRepository nichoRepository;
    private final CopywriterIaService copywriterIaService;
    private final TelegramNotificadorService telegramNotificadorService;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;
    private final ModoNoturnoService modoNoturnoService;

    @Value("${cupons.anuncio-avulso.enabled:true}")
    private boolean anuncioAvulsoEnabled = true;

    @Value("${cupons.anuncio-avulso.intervalo-minutos:45}")
    private long intervaloMinutosEntreAvulsos = 45;

    @Value("${cupons.anuncio-avulso.min-desconto-reais:30.0}")
    private double minDescontoReais = 30.0;

    @Value("${cupons.anuncio-avulso.min-desconto-percentual:10.0}")
    private double minDescontoPercentual = 10.0;

    private LocalDateTime ultimoEnvioAvulso = null;

    @Autowired
    public CupomService(
            CupomRepository cupomRepository,
            NichoRepository nichoRepository,
            CopywriterIaService copywriterIaService,
            TelegramNotificadorService telegramNotificadorService,
            OfertaDescobertaRepository ofertaDescobertaRepository,
            @Autowired(required = false) ModoNoturnoService modoNoturnoService) {
        this.cupomRepository = cupomRepository;
        this.nichoRepository = nichoRepository;
        this.copywriterIaService = copywriterIaService;
        this.telegramNotificadorService = telegramNotificadorService;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.modoNoturnoService = modoNoturnoService;
    }

    public CupomService(
            CupomRepository cupomRepository,
            NichoRepository nichoRepository,
            CopywriterIaService copywriterIaService,
            TelegramNotificadorService telegramNotificadorService,
            OfertaDescobertaRepository ofertaDescobertaRepository) {
        this(cupomRepository, nichoRepository, copywriterIaService, telegramNotificadorService, ofertaDescobertaRepository, null);
    }

    @Transactional
    public Cupom cadastrarCupom(CupomRequest req) {
        Cupom cupom = new Cupom();
        atualizarDadosCupom(cupom, req);

        Cupom salvo = cupomRepository.save(cupom);
        logger.info("CupomService: Cupom [{}] cadastrado com sucesso.", salvo.getCodigo());

        if (Boolean.TRUE.equals(req.getAnunciarImediatamente())) {
            anunciarCupomAvulso(salvo);
        }

        return salvo;
    }

    @Transactional
    public Cupom atualizarCupom(Long id, CupomRequest req) {
        Cupom cupom = cupomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado com id: " + id));

        atualizarDadosCupom(cupom, req);
        return cupomRepository.save(cupom);
    }

    @Transactional(readOnly = true)
    public List<Cupom> listarTodos() {
        return cupomRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Cupom> listarValidosAgora() {
        return cupomRepository.findAllValidosAgora(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<Cupom> obterPorId(Long id) {
        return cupomRepository.findById(id);
    }

    @Transactional
    public void alternarStatus(Long id) {
        Cupom cupom = cupomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado com id: " + id));
        cupom.setAtivo(!Boolean.TRUE.equals(cupom.getAtivo()));
        cupomRepository.save(cupom);
    }

    @Transactional
    public void deletarCupom(Long id) {
        cupomRepository.deleteById(id);
    }

    /**
     * Encontra o melhor cupom ativo e GARANTIDO como elegível para uma oferta específica.
     *
     * Regras de blindagem (4 camadas):
     * 1. Cupom deve estar ativo e dentro da validade temporal.
     * 2. Cupom deve gerar desconto real (≥ R$ 1,00) para o preço do produto.
     * 3. Cupom de nicho específico NUNCA é aplicado em produto de nicho diferente.
     *    (ex: cupom "MODA20" não vai aparecer em oferta de hardware/games)
     * 4. Cupom com valor mínimo de compra é checado contra o preço real do produto.
     */
    @Transactional(readOnly = true)
    public Optional<Cupom> encontrarMelhorCupomParaOferta(OfertaDescoberta oferta) {
        if (oferta == null || oferta.getPreco() == null || oferta.getPreco().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        List<Cupom> elegiveis = cupomRepository.findCuponsElegiveis(
                oferta.getNicho(),
                oferta.getPreco(),
                LocalDateTime.now()
        );

        if (elegiveis.isEmpty()) {
            return Optional.empty();
        }

        return elegiveis.stream()
                // Camada 1: Validade temporal confirmada no momento do pareamento
                .filter(Cupom::isValidoAgora)
                // Camada 2: Cupom deve ter sido confirmado como funcional (não apenas capturado)
                .filter(c -> !Boolean.FALSE.equals(c.getTestado()))
                // Camada 3: Validação de nicho rigorosa — cupom de nicho específico NÃO casa com produto de outro nicho
                .filter(c -> isCupomCompativelComOferta(c, oferta))
                // Camada 4: Desconto real calculado deve ser de no mínimo R$ 1,00 para justificar mencionar no anúncio
                .filter(c -> {
                    BigDecimal desconto = c.calcularDesconto(oferta.getPreco());
                    if (desconto.compareTo(BigDecimal.ONE) < 0) {
                        logger.debug("CupomService: Cupom [{}] rejeitado — desconto calculado ({}) insuficiente para o produto (R$ {}).",
                                c.getCodigo(), desconto, oferta.getPreco());
                        return false;
                    }
                    return true;
                })
                // Seleciona o que gera maior economia real em reais para o comprador
                .max(Comparator.comparing(c -> c.calcularDesconto(oferta.getPreco())));
    }

    @Transactional
    public boolean desativarPorCodigo(String codigo) {
        return desativarCupomEAlertar(codigo, "Desativado manualmente por relato de indisponibilidade em " + LocalDateTime.now());
    }

    /**
     * Marca um cupom capturado automaticamente como testado e funcional.
     * Após esse passo, o cupom passa a ser elegível para pareamento automático com produtos.
     * Use somente após confirmar que o cupom aplica desconto no checkout do Mercado Livre.
     */
    @Transactional
    public boolean confirmarCupomTestado(Long id) {
        Cupom cupom = cupomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado com id: " + id));
        if (Boolean.TRUE.equals(cupom.getTestado())) {
            logger.debug("CupomService: Cupom [{}] já estava marcado como testado.", cupom.getCodigo());
            return false;
        }
        cupom.setTestado(true);
        String obs = cupom.getObservacao() != null ? cupom.getObservacao() : "";
        cupom.setObservacao(obs + " | Confirmado como funcional em " + LocalDateTime.now());
        cupomRepository.save(cupom);
        logger.info("CupomService: Cupom [{}] confirmado como testado. Agora elegível para pareamento automático.", cupom.getCodigo());
        return true;
    }

    @Transactional
    public boolean desativarCupomEAlertar(String codigo, String motivo) {
        if (codigo == null || codigo.isBlank()) return false;
        Optional<Cupom> cupomOpt = cupomRepository.findByCodigoIgnoreCase(codigo.trim());
        if (cupomOpt.isPresent()) {
            Cupom cupom = cupomOpt.get();
            if (!Boolean.TRUE.equals(cupom.getAtivo())) {
                logger.debug("CupomService: Cupom [{}] já está inativo.", codigo);
                return false;
            }
            cupom.setAtivo(false);
            cupom.setObservacao(motivo != null ? motivo : ("Desativado em " + LocalDateTime.now()));
            cupomRepository.save(cupom);
            logger.info("CupomService: Cupom [{}] desativado. Motivo: {}. Disparando alerta no Telegram...", codigo, motivo);
            telegramNotificadorService.notificarCupomEsgotado(cupom);

            // Edição dinâmica de postagens anteriores: busca ofertas enviadas com este cupom e atualiza no Telegram
            try {
                List<OfertaDescoberta> ofertasAfetadas = ofertaDescobertaRepository
                        .findByCupomAplicadoIgnoreCaseAndStatusEnvio(codigo.trim(), "ENVIADO");

                for (OfertaDescoberta o : ofertasAfetadas) {
                    if (Boolean.TRUE.equals(o.getDisponivel()) && o.getTelegramMessageId() != null) {
                        telegramNotificadorService.editarMensagemOfertaEsgotada(o, "Cupom " + codigo.toUpperCase() + " Esgotado");
                    }
                }
            } catch (Exception ex) {
                logger.warn("CupomService: Erro ao editar mensagens anteriores das ofertas afetadas: {}", ex.getMessage());
            }

            return true;
        }
        return false;
    }

    /**
     * Processa um cupom recém-descoberto pelo crawler autônomo.
     * Cupons Ouro entram na fila para anúncio avulso respeitando cadência e sarrafo.
     * Cupons menores são salvos ativos no banco para aplicação automática em ofertas de produtos.
     */
    @Transactional
    public Optional<Cupom> processarCupomDetectado(Cupom cupom) {
        if (cupom == null || cupom.getCodigo() == null || cupom.getCodigo().isBlank()) {
            return Optional.empty();
        }

        String codigoLimpo = cupom.getCodigo().trim().toUpperCase();
        cupom.setCodigo(codigoLimpo);

        Optional<Cupom> existente = cupomRepository.findByCodigoIgnoreCase(codigoLimpo);
        if (existente.isPresent()) {
            logger.debug("CupomService: Cupom [{}] já cadastrado anteriormente. Ignorando duplicação.", codigoLimpo);
            return Optional.empty();
        }

        cupom.setAtivo(true);
        if (cupom.getDataCriacao() == null) {
            cupom.setDataCriacao(LocalDateTime.now());
        }

        // Se o cupom atende ao sarrafo de Cupom Ouro, ele fica pendente na fila (anunciadoAvulso = false)
        // Se NÃO atende, é marcado como anunciadoAvulso = true para NÃO poluir o grupo avulso,
        // mas permanece 100% ativo no banco de dados para parear com produtos daquele nicho!
        boolean ouro = isCupomOuro(cupom);
        cupom.setAnunciadoAvulso(!ouro);

        Cupom salvo = cupomRepository.save(cupom);
        if (ouro) {
            logger.info("CupomService: Novo Cupom OURO [{}] (desconto: {} {}) salvo e enfileirado para anúncio avulso.",
                    salvo.getCodigo(), salvo.getValorDesconto(), salvo.getTipoDesconto());
        } else {
            logger.info("CupomService: Novo cupom [{}] salvo no banco (mantido ativo para vincular em produtos; abaixo do sarrafo para post avulso).",
                    salvo.getCodigo());
        }

        return Optional.of(salvo);
    }

    /**
     * Avalia se o cupom possui qualidade/relevância suficiente para justificar um post avulso.
     */
    public boolean isCupomOuro(Cupom c) {
        if (c == null || c.getValorDesconto() == null) {
            return false;
        }

        BigDecimal desconto = c.getValorDesconto();
        String tipo = c.getTipoDesconto() != null ? c.getTipoDesconto().toUpperCase() : "VALOR_FIXO";

        if ("PERCENTUAL".equals(tipo)) {
            if (desconto.compareTo(BigDecimal.valueOf(minDescontoPercentual)) < 0) {
                return false;
            }
        } else {
            if (desconto.compareTo(BigDecimal.valueOf(minDescontoReais)) < 0) {
                return false;
            }
        }

        // Regra de sanidade: se exigir compra mínima, a proporção do desconto deve ser relevante
        if (c.getValorMinimoCompra() != null && c.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0) {
            if (!"PERCENTUAL".equals(tipo)) {
                BigDecimal minCompra = c.getValorMinimoCompra();
                // O valor mínimo não pode exceder 35x o desconto (ex: R$ 30 OFF para R$ 1.500 é fraco: ~2%)
                if (minCompra.compareTo(desconto.multiply(BigDecimal.valueOf(35))) > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Calcula um valor estimado em Reais para ranquear cupons na fila de prioridade.
     */
    public BigDecimal calcularScoreOuValorDesconto(Cupom c) {
        if (c == null || c.getValorDesconto() == null) {
            return BigDecimal.ZERO;
        }
        if ("PERCENTUAL".equalsIgnoreCase(c.getTipoDesconto())) {
            BigDecimal base = (c.getValorMinimoCompra() != null && c.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0)
                    ? c.getValorMinimoCompra()
                    : BigDecimal.valueOf(250);
            return base.multiply(c.getValorDesconto()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        }
        return c.getValorDesconto();
    }

    /**
     * Processa a fila de cupons avulsos com anti-flood inteligente:
     * - Dispara NO MÁXIMO 1 cupom por ciclo.
     * - Respeita o intervalo mínimo entre postagens de cupons (ex: 45 min).
     * - Respeita o Modo Noturno (pausa de madrugada).
     * - Seleciona estritamente o MELHOR cupom disponível na fila.
     */
    @Scheduled(cron = "${cupons.anuncio-avulso.cron:0 0/15 * * * ?}")
    public synchronized void processarFilaAnuncioAvulso() {
        if (!anuncioAvulsoEnabled) {
            logger.debug("CupomService: Anúncios avulsos de cupons desabilitados nas configurações.");
            return;
        }

        if (modoNoturnoService != null && modoNoturnoService.devePausarEnvios()) {
            logger.debug("CupomService: Modo Noturno ativo. Postagens avulsas de cupons pausadas durante o horário de descanso.");
            return;
        }

        // Anti-Flood: Intervalo mínimo entre postagens de cupons avulsos no grupo/canal
        if (ultimoEnvioAvulso != null) {
            long minutosDesdeUltimo = java.time.Duration.between(ultimoEnvioAvulso, LocalDateTime.now()).toMinutes();
            if (minutosDesdeUltimo < intervaloMinutosEntreAvulsos) {
                logger.debug("CupomService: Cadência de cupons mantida. Último envio há {} min (mínimo exigido: {} min).",
                        minutosDesdeUltimo, intervaloMinutosEntreAvulsos);
                return;
            }
        }

        List<Cupom> pendentes = cupomRepository.findByAtivoTrueAndAnunciadoAvulsoFalse();
        if (pendentes == null || pendentes.isEmpty()) {
            return;
        }

        // Filtra cupons válidos que atendem ao sarrafo de Cupom Ouro
        List<Cupom> elegiveis = pendentes.stream()
                .filter(Cupom::isValidoAgora)
                .filter(this::isCupomOuro)
                .sorted(Comparator.comparing(this::calcularScoreOuValorDesconto).reversed())
                .toList();

        // Cupons pendentes que NÃO são ouro ou expiraram são marcados como anunciados para não bloquear a fila
        for (Cupom c : pendentes) {
            if (!c.isValidoAgora() || !isCupomOuro(c)) {
                c.setAnunciadoAvulso(true);
                cupomRepository.save(c);
            }
        }

        if (elegiveis.isEmpty()) {
            logger.debug("CupomService: Nenhum cupom pendente cumpre os requisitos de 'Cupom Ouro' para anúncio avulso.");
            return;
        }

        // Elege estritamente 1 único cupom (o de maior valor real) para envio neste ciclo
        Cupom melhor = elegiveis.get(0);
        logger.info("CupomService: Disparando anúncio do melhor cupom da fila [{}] (Desconto: {} {})...",
                melhor.getCodigo(), melhor.getValorDesconto(), melhor.getTipoDesconto());

        boolean enviou = anunciarCupomAvulso(melhor);
        if (enviou) {
            this.ultimoEnvioAvulso = LocalDateTime.now();
            logger.info("CupomService: Cupom avulso [{}] publicado com sucesso! Próximo envio permitido em {} minutos.",
                    melhor.getCodigo(), intervaloMinutosEntreAvulsos);
        }
    }

    /**
     * Dispara o anúncio avulso de um cupom específico para o Telegram.
     */
    @Transactional
    public boolean anunciarCupomAvulso(Long cupomId) {
        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado com id: " + cupomId));
        return anunciarCupomAvulso(cupom);
    }

    @Transactional
    public boolean anunciarCupomAvulso(Cupom cupom) {
        if (cupom == null || !cupom.isValidoAgora()) {
            logger.warn("CupomService: Tentativa de anunciar cupom inválido ou expirado.");
            return false;
        }

        String linkBruto = (cupom.getLinkHotsite() != null && !cupom.getLinkHotsite().isBlank())
                ? cupom.getLinkHotsite()
                : "https://www.mercadolivre.com.br/cupons";

        String linkFinal = telegramNotificadorService.aplicarTagAfiliado(linkBruto);
        String copy = copywriterIaService.gerarCopyCupomAvulso(cupom, linkFinal);

        boolean enviou = telegramNotificadorService.notificarCupomAvulso(cupom, copy, linkFinal);
        if (enviou) {
            cupom.setAnunciadoAvulso(true);
            cupomRepository.save(cupom);
            this.ultimoEnvioAvulso = LocalDateTime.now();
            logger.info("CupomService: Anúncio avulso do cupom [{}] publicado no Telegram com sucesso.", cupom.getCodigo());
        }

        return enviou;
    }

    public boolean isAnuncioAvulsoEnabled() {
        return anuncioAvulsoEnabled;
    }

    public void setAnuncioAvulsoEnabled(boolean anuncioAvulsoEnabled) {
        this.anuncioAvulsoEnabled = anuncioAvulsoEnabled;
    }

    public long getIntervaloMinutosEntreAvulsos() {
        return intervaloMinutosEntreAvulsos;
    }

    public void setIntervaloMinutosEntreAvulsos(long intervaloMinutosEntreAvulsos) {
        this.intervaloMinutosEntreAvulsos = intervaloMinutosEntreAvulsos;
    }

    public double getMinDescontoReais() {
        return minDescontoReais;
    }

    public void setMinDescontoReais(double minDescontoReais) {
        this.minDescontoReais = minDescontoReais;
    }

    public double getMinDescontoPercentual() {
        return minDescontoPercentual;
    }

    public void setMinDescontoPercentual(double minDescontoPercentual) {
        this.minDescontoPercentual = minDescontoPercentual;
    }

    public LocalDateTime getUltimoEnvioAvulso() {
        return ultimoEnvioAvulso;
    }

    public void setUltimoEnvioAvulso(LocalDateTime ultimoEnvioAvulso) {
        this.ultimoEnvioAvulso = ultimoEnvioAvulso;
    }

    private void atualizarDadosCupom(Cupom cupom, CupomRequest req) {
        cupom.setCodigo(req.getCodigo().trim().toUpperCase());
        cupom.setDescricao(req.getDescricao());
        cupom.setTipoDesconto(req.getTipoDesconto() != null ? req.getTipoDesconto().toUpperCase() : "VALOR_FIXO");
        cupom.setValorDesconto(req.getValorDesconto());
        cupom.setValorMinimoCompra(req.getValorMinimoCompra() != null ? req.getValorMinimoCompra() : BigDecimal.ZERO);
        cupom.setLinkHotsite(req.getLinkHotsite());
        cupom.setDataInicio(req.getDataInicio());
        cupom.setDataExpiracao(req.getDataExpiracao());
        cupom.setAtivo(req.getAtivo() != null ? req.getAtivo() : true);
        cupom.setTestado(req.getTestado() != null ? req.getTestado() : true);
        cupom.setObservacao(req.getObservacao());

        if (req.getNichoId() != null) {
            Nicho nicho = nichoRepository.findById(req.getNichoId())
                    .orElseThrow(() -> new IllegalArgumentException("Nicho não encontrado com id: " + req.getNichoId()));
            cupom.setNicho(nicho);
        } else {
            cupom.setNicho(null);
        }
    }

    /**
     * Validação rigorosa de compatibilidade entre um Cupom e uma Oferta.
     * Suporta qualquer quantidade de nichos dinâmicos e impede que cupons de
     * categorias alheias (ex: AUTOEFERRAMENTAS) sejam colados em ofertas não relacionadas.
     */
    private boolean isCupomCompativelComOferta(Cupom c, OfertaDescoberta oferta) {
        if (c == null || oferta == null) {
            return false;
        }

        Nicho nichoOferta = oferta.getNicho();

        // 1. Se o cupom tiver nicho associado, exige correspondência exata de ID
        if (c.getNicho() != null) {
            if (nichoOferta == null) {
                logger.debug("CupomService: Cupom [{}] rejeitado — oferta sem nicho, mas cupom é do nicho [{}].",
                        c.getCodigo(), c.getNicho().getNome());
                return false;
            }
            boolean match = c.getNicho().getId().equals(nichoOferta.getId());
            if (!match) {
                logger.debug("CupomService: Cupom [{}] rejeitado — nicho do cupom [{}] difere do nicho da oferta [{}].",
                        c.getCodigo(), c.getNicho().getNome(), nichoOferta.getNome());
            }
            return match;
        }

        // 2. Se o cupom NÃO tiver nicho associado, NUNCA assumir cegamente que é "para todo o site"!
        // Verifica se o código ou descrição contêm palavras-chave de categorias específicas
        String codigoUpper = c.getCodigo() != null ? c.getCodigo().toUpperCase() : "";
        String descLower = c.getDescricao() != null ? c.getDescricao().toLowerCase() : "";
        String nichoNomeLower = (nichoOferta != null && nichoOferta.getNome() != null)
                ? nichoOferta.getNome().toLowerCase()
                : "";

        // Palavras de categorias específicas que NUNCA devem vazar para outros nichos
        Map<String, String> prefixosCategorias = Map.ofEntries(
                Map.entry("AUTO", "veículo|veiculo|auto|carro|moto"),
                Map.entry("FERRAMENTA", "ferramenta|construção|construcao"),
                Map.entry("MODA", "moda|roupa|calcado|calçado|beleza"),
                Map.entry("BELEZA", "beleza|perfume|maquiagem|moda"),
                Map.entry("PET", "pet|animal|veterinario"),
                Map.entry("BEBE", "bebe|bebê|brinquedo|infantil"),
                Map.entry("LIVRO", "livro|leitura|papelaria"),
                Map.entry("SUPER", "supermercado|alimento|bebida|mercado"),
                Map.entry("MERCADO", "supermercado|alimento|bebida|mercado"),
                Map.entry("CASA", "casa|eletro|cozinha"),
                Map.entry("ELETRO", "casa|eletro"),
                Map.entry("GAME", "game|gamer|console|jogo"),
                Map.entry("TECH", "hardware|informática|informatica|smartphone|celular")
        );

        for (Map.Entry<String, String> entry : prefixosCategorias.entrySet()) {
            String categoriaChave = entry.getKey();
            String regexAceitavel = entry.getValue();

            // Se o cupom remete a essa categoria específica
            if (codigoUpper.contains(categoriaChave) || descLower.contains(categoriaChave.toLowerCase())) {
                // O nicho do produto DEVE conter pelo menos um dos termos aceitáveis
                boolean nichoCompativel = false;
                for (String termo : regexAceitavel.split("\\|")) {
                    if (nichoNomeLower.contains(termo)) {
                        nichoCompativel = true;
                        break;
                    }
                }

                if (!nichoCompativel) {
                    logger.info("CupomService: Cupom [{}] com semântica [{}] REJEITADO para oferta de nicho [{}] ('{}').",
                            c.getCodigo(), categoriaChave, nichoOferta != null ? nichoOferta.getNome() : "GERAL", oferta.getTitulo());
                    return false;
                }
            }
        }

        // 3. Se for cupom sem nicho e sem prefixo categórico, só aceita se comprovadamente universal
        // (menção a "todo o site" / "no app todo" / códigos conhecidos de novos usuários ou institucionais)
        boolean isUniversalComprovado = descLower.contains("todo o site")
                || descLower.contains("todo site")
                || descLower.contains("no app todo")
                || descLower.contains("em todo")
                || codigoUpper.startsWith("MELI")
                || codigoUpper.startsWith("APP")
                || codigoUpper.startsWith("NOVO")
                || codigoUpper.startsWith("BEMVINDO")
                || codigoUpper.startsWith("PRIMEIRA")
                || codigoUpper.startsWith("VALE");

        if (!isUniversalComprovado) {
            logger.info("CupomService: Cupom sem nicho [{}] rejeitado por falta de comprovação de universalidade para a oferta '{}'.",
                    c.getCodigo(), oferta.getTitulo());
            return false;
        }

        return true;
    }
}

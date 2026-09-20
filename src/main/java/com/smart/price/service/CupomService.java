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

@Service
public class CupomService {

    private static final Logger logger = LoggerFactory.getLogger(CupomService.class);

    private final CupomRepository cupomRepository;
    private final NichoRepository nichoRepository;
    private final CopywriterIaService copywriterIaService;
    private final TelegramNotificadorService telegramNotificadorService;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;

    public CupomService(
            CupomRepository cupomRepository,
            NichoRepository nichoRepository,
            CopywriterIaService copywriterIaService,
            TelegramNotificadorService telegramNotificadorService,
            OfertaDescobertaRepository ofertaDescobertaRepository) {
        this.cupomRepository = cupomRepository;
        this.nichoRepository = nichoRepository;
        this.copywriterIaService = copywriterIaService;
        this.telegramNotificadorService = telegramNotificadorService;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
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
     * Se for inédito, cadastra como ativo e dispara o anúncio no Telegram imediatamente!
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
        // Preserva o campo testado definido pela origem (crawler define false; cadastro manual define true).
        // Não sobrescrever aqui garante que a quarentena do crawler funcione corretamente.
        cupom.setAnunciadoAvulso(false);
        if (cupom.getDataCriacao() == null) {
            cupom.setDataCriacao(LocalDateTime.now());
        }

        Cupom salvo = cupomRepository.save(cupom);
        logger.info("CupomService: Novo cupom [{}] detectado e salvo com sucesso! Disparando anúncio urgente...", salvo.getCodigo());

        // Dispara o anúncio exclusivo no Telegram
        anunciarCupomAvulso(salvo);

        return Optional.of(salvo);
    }

    /**
     * Dispara o anúncio avulso e urgente do cupom para o Telegram.
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
            logger.info("CupomService: Anúncio avulso do cupom [{}] publicado no Telegram com sucesso.", cupom.getCodigo());
        }

        return enviou;
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

package com.smart.price.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smart.price.entity.HistoricoPreco;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.LojaEnum;
import com.smart.price.repository.HistoricoPrecoRepository;

/**
 * Coleta e consulta o histórico real de preços. É a base do selo "menor preço em 30 dias"
 * e do desconto calculado contra a mediana, em vez do preço "De" informado pela loja.
 */
@Service
public class HistoricoPrecoService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricoPrecoService.class);

    private final HistoricoPrecoRepository historicoPrecoRepository;

    public HistoricoPrecoService(HistoricoPrecoRepository historicoPrecoRepository) {
        this.historicoPrecoRepository = historicoPrecoRepository;
    }

    /**
     * Grava uma coleta por produto e loja para cada candidato do ciclo. Se o mesmo produto
     * apareceu em mais de uma busca, fica o menor preço visto. Roda em transação própria:
     * falha aqui nunca derruba a curadoria.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registrarColetas(List<OfertaDescoberta> candidatos) {
        return registrarColetas(candidatos, Map.of());
    }

    /**
     * Igual a {@link #registrarColetas(List)}, gravando junto o score de cada produto avaliado
     * (chave: produtoId). Produto sem score no mapa fica com score nulo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int registrarColetas(List<OfertaDescoberta> candidatos, Map<String, Integer> scorePorProduto) {
        if (candidatos == null || candidatos.isEmpty()) {
            return 0;
        }

        LocalDateTime agora = LocalDateTime.now();
        Map<String, HistoricoPreco> porProduto = new LinkedHashMap<>();

        for (OfertaDescoberta oferta : candidatos) {
            if (oferta.getMlbId() == null || oferta.getMlbId().isBlank()
                    || oferta.getPreco() == null || oferta.getPreco().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LojaEnum loja = oferta.getLoja() != null ? oferta.getLoja() : LojaEnum.MERCADO_LIVRE;
            String chave = loja + "|" + oferta.getMlbId();
            HistoricoPreco existente = porProduto.get(chave);
            if (existente != null && existente.getPreco().compareTo(oferta.getPreco()) <= 0) {
                continue;
            }

            HistoricoPreco coleta = new HistoricoPreco();
            coleta.setProdutoId(oferta.getMlbId());
            coleta.setLoja(loja);
            coleta.setPreco(oferta.getPreco());
            coleta.setPrecoOriginal(oferta.getPrecoOriginal());
            coleta.setVendedorId(oferta.getVendedorId());
            coleta.setDataColeta(agora);
            coleta.setScore(scorePorProduto != null ? scorePorProduto.get(oferta.getMlbId()) : null);
            porProduto.put(chave, coleta);
        }

        historicoPrecoRepository.saveAll(porProduto.values());
        logger.info("HistoricoPrecoService: {} coletas de preço gravadas no histórico.", porProduto.size());
        return porProduto.size();
    }

    /**
     * Mínimo, mediana e máximo do período, com o número de coletas e de dias distintos
     * para decidir se há dados suficientes antes de anunciar qualquer selo.
     */
    @Transactional(readOnly = true)
    public Optional<EstatisticaPreco> estatisticas(String produtoId, LojaEnum loja, int dias) {
        if (produtoId == null || produtoId.isBlank() || loja == null) {
            return Optional.empty();
        }

        List<HistoricoPreco> coletas = historicoPrecoRepository.findByProdutoIdAndLojaAndDataColetaAfter(
                produtoId, loja, LocalDateTime.now().minusDays(dias));
        if (coletas.isEmpty()) {
            return Optional.empty();
        }

        List<BigDecimal> precos = new ArrayList<>(coletas.stream().map(HistoricoPreco::getPreco).sorted().toList());
        int n = precos.size();
        BigDecimal mediana = (n % 2 == 1)
                ? precos.get(n / 2)
                : precos.get(n / 2 - 1).add(precos.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        long diasDistintos = coletas.stream().map(c -> c.getDataColeta().toLocalDate()).distinct().count();

        return Optional.of(new EstatisticaPreco(precos.get(0), mediana, precos.get(n - 1), n, (int) diasDistintos));
    }

    /** Data da coleta mais antiga do histórico, para saber há quanto tempo o sistema coleta preços. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> inicioDoHistorico() {
        return historicoPrecoRepository.findFirstByOrderByDataColetaAsc().map(HistoricoPreco::getDataColeta);
    }

    public record EstatisticaPreco(BigDecimal minimo, BigDecimal mediana, BigDecimal maximo,
                                   int totalColetas, int diasDistintos) {

        /** Mínimo para anunciar selo ou desconto real: 10 coletas em pelo menos 7 dias diferentes. */
        public static final int MIN_COLETAS = 10;
        public static final int MIN_DIAS_DISTINTOS = 7;

        public boolean isSuficiente() {
            return totalColetas >= MIN_COLETAS && diasDistintos >= MIN_DIAS_DISTINTOS;
        }
    }
}

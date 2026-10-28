package com.smart.price.service.curadoria;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.service.VendedorService;

/**
 * Calcula a pontuação de relevância de uma oferta (0 a 100 pontos). Pesos configuráveis em
 * monitoramento.score.*:
 * - Desconto real contra a mediana de 30 dias: até 35 (proporcional, cheio a partir de 50%)
 * - Menor preço em 30 dias: 25
 * - Cupom confirmado: 15
 * - Loja oficial ou vendedor de reputação alta: 10
 * - Frete grátis: 5
 * - Desempenho da categoria: até 10 (entra na Fase 5, com dados de clique; hoje soma 0)
 */
@Component
public class CalculadorScoreOferta {

    @Value("${monitoramento.score.peso-desconto:35}")
    private int pesoDesconto = 35;

    /** Desconto, em %, que já vale a pontuação cheia de desconto. */
    @Value("${monitoramento.score.desconto-teto:50}")
    private int descontoTeto = 50;

    @Value("${monitoramento.score.peso-menor-preco:25}")
    private int pesoMenorPreco = 25;

    @Value("${monitoramento.score.peso-cupom:15}")
    private int pesoCupom = 15;

    @Value("${monitoramento.score.peso-vendedor:10}")
    private int pesoVendedor = 10;

    @Value("${monitoramento.score.peso-frete:5}")
    private int pesoFrete = 5;

    @Value("${monitoramento.vendedor.nivel-alto:5_green}")
    private String nivelVendedorAlto = "5_green";

    public int calcularScore(OfertaDescoberta oferta) {
        if (oferta == null) {
            return 0;
        }

        int score = 0;

        // 1. Desconto real: contra a mediana quando há histórico; senão, o "De" da loja que passou no filtro
        int desc = descontoConsiderado(oferta);
        if (desc > 0 && descontoTeto > 0) {
            score += Math.round(pesoDesconto * (float) Math.min(desc, descontoTeto) / descontoTeto);
        }

        // 2. Menor preço em 30 dias (só marcado com histórico suficiente)
        if (Boolean.TRUE.equals(oferta.getMenorPrecoHistorico())) {
            score += pesoMenorPreco;
        }

        // 3. Cupom confirmado: o desconto do cupom só é preenchido quando o cupom está CONFIRMADO
        if (oferta.getCupomAplicado() != null && !oferta.getCupomAplicado().isBlank()
                && oferta.getDescontoCupom() != null && oferta.getDescontoCupom().compareTo(BigDecimal.ZERO) > 0) {
            score += pesoCupom;
        }

        // 4. Loja oficial ou vendedor de reputação alta
        if (Boolean.TRUE.equals(oferta.getLojaOficial())
                || VendedorService.nivel(oferta.getVendedorNivel()) >= VendedorService.nivel(nivelVendedorAlto)) {
            score += pesoVendedor;
        }

        // 5. Frete grátis
        if (Boolean.TRUE.equals(oferta.getFreteGratis())) {
            score += pesoFrete;
        }

        return Math.min(score, 100);
    }

    /**
     * Determina se a oferta atinge o critério mínimo de "Oferta Ouro".
     */
    public boolean isOfertaOuro(OfertaDescoberta oferta, int scoreMinimo) {
        if (oferta == null) {
            return false;
        }
        return calcularScore(oferta) >= scoreMinimo;
    }

    private int descontoConsiderado(OfertaDescoberta oferta) {
        if (oferta.getDescontoReal() != null) {
            return oferta.getDescontoReal();
        }
        return oferta.getDescontoPercentual() != null ? oferta.getDescontoPercentual() : 0;
    }
}

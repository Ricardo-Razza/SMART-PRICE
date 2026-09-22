package com.smart.price.service.curadoria;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.smart.price.entity.OfertaDescoberta;

/**
 * Calcula a pontuação de relevância de uma oferta (0 a 100 pontos)
 * com base em desconto percentual, histórico de preço, cupom e frete.
 */
@Component
public class CalculadorScoreOferta {

    private static final BigDecimal VALOR_PRODUTO_RELEVANTE = BigDecimal.valueOf(300.0);

    /**
     * Calcula a pontuação total da oferta com base nos fatores objetivos de atratividade:
     * - Percentual de desconto apurado: até 35 pontos
     * - Menor preço histórico registrado: 30 pontos
     * - Cupom ativo aplicável: 25 pontos
     * - Bônus produto de alto giro / valor agregado (>= R$ 300): 15 pontos
     * - Frete grátis: 10 pontos
     */
    public int calcularScore(OfertaDescoberta oferta) {
        if (oferta == null) {
            return 0;
        }

        int score = 0;

        // 1. Pontuação por desconto percentual real (máx 35 pts)
        int desc = oferta.getDescontoPercentual() != null ? oferta.getDescontoPercentual() : 0;
        if (desc >= 50) {
            score += 35;
        } else if (desc >= 35) {
            score += 30;
        } else if (desc >= 20) {
            score += 20;
        } else if (desc >= 10) {
            score += 10;
        } else if (desc >= 5) {
            score += 5;
        }

        // 2. Pontuação por menor preço histórico registrado (30 pts)
        if (Boolean.TRUE.equals(oferta.getMenorPrecoHistorico())) {
            score += 30;
        }

        // 3. Pontuação por cupom de desconto aplicável ativo (25 pts)
        if (oferta.getCupomAplicado() != null && !oferta.getCupomAplicado().isBlank()
                && oferta.getDescontoCupom() != null && oferta.getDescontoCupom().compareTo(BigDecimal.ZERO) > 0) {
            score += 25;
        }

        // 4. Bônus para produtos de maior valor agregado / alto giro (15 pts)
        // Evita que apenas miudezas/acessórios baratos com descontos inflados monopolizem o canal
        if (oferta.getPreco() != null && oferta.getPreco().compareTo(VALOR_PRODUTO_RELEVANTE) >= 0) {
            score += 15;
        }

        // 5. Pontuação por frete grátis (10 pts)
        if (Boolean.TRUE.equals(oferta.getFreteGratis())) {
            score += 10;
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
}

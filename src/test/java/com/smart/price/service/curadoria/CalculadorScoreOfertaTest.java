package com.smart.price.service.curadoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smart.price.entity.OfertaDescoberta;

class CalculadorScoreOfertaTest {

    private CalculadorScoreOferta calculador;

    @BeforeEach
    void setUp() {
        calculador = new CalculadorScoreOferta();
    }

    @Test
    @DisplayName("Oferta com 40% OFF, menor preço histórico, cupom e frete grátis deve atingir score alto (Oferta Ouro)")
    void deveAtingirScoreOuro() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("120.00"));
        oferta.setPrecoOriginal(new BigDecimal("200.00"));
        oferta.setDescontoPercentual(40); // 30 pts
        oferta.setMenorPrecoHistorico(true); // 30 pts
        oferta.setCupomAplicado("DESC20"); // 25 pts
        oferta.setDescontoCupom(new BigDecimal("20.00"));
        oferta.setFreteGratis(true); // 10 pts

        int score = calculador.calcularScore(oferta);
        assertEquals(95, score);
        assertTrue(calculador.isOfertaOuro(oferta, 60));
    }

    @Test
    @DisplayName("Oferta morna com apenas 5% de desconto e sem cupom nem menor preço deve ter score baixo")
    void deveTerScoreBaixoParaOfertaMorna() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("95.00"));
        oferta.setPrecoOriginal(new BigDecimal("100.00"));
        oferta.setDescontoPercentual(5); // 0 pts
        oferta.setMenorPrecoHistorico(false); // 0 pts
        oferta.setFreteGratis(false); // 0 pts

        int score = calculador.calcularScore(oferta);
        assertEquals(0, score);
        assertFalse(calculador.isOfertaOuro(oferta, 60));
    }
}

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
    @DisplayName("Desconto real de 50%, menor preço em 30 dias, cupom confirmado, loja oficial e frete grátis somam 90")
    void deveAtingirScoreMaximoSemCategoria() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("100.00"));
        oferta.setDescontoReal(50); // 35 pts
        oferta.setMenorPrecoHistorico(true); // 25 pts
        oferta.setCupomAplicado("DESC20"); // 15 pts
        oferta.setDescontoCupom(new BigDecimal("20.00"));
        oferta.setLojaOficial(true); // 10 pts
        oferta.setFreteGratis(true); // 5 pts

        assertEquals(90, calculador.calcularScore(oferta));
        assertTrue(calculador.isOfertaOuro(oferta, 50));
    }

    @Test
    @DisplayName("Desconto real tem prioridade sobre o \"De\" da loja")
    void deveUsarDescontoRealNoLugarDoPrecoDe() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("90.00"));
        oferta.setDescontoPercentual(60); // "De" inflado da loja
        oferta.setDescontoReal(10); // contra a mediana: 7 pts

        assertEquals(7, calculador.calcularScore(oferta));
    }

    @Test
    @DisplayName("Sem histórico, o desconto do \"De\" conta de forma proporcional")
    void deveUsarDescontoDaLojaSemHistorico() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("95.00"));
        oferta.setDescontoPercentual(5); // 35 * 5/50 = 3.5 → 4 pts
        oferta.setMenorPrecoHistorico(false);
        oferta.setFreteGratis(false);

        assertEquals(4, calculador.calcularScore(oferta));
        assertFalse(calculador.isOfertaOuro(oferta, 20));
    }

    @Test
    @DisplayName("Cupom não confirmado (sem desconto preenchido) não pontua")
    void naoDevePontuarCupomNaoConfirmado() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setPreco(new BigDecimal("200.00"));
        oferta.setCupomAplicado("TALVEZ10");

        assertEquals(0, calculador.calcularScore(oferta));
    }

    @Test
    @DisplayName("Vendedor 5_green pontua; 4_light_green não")
    void devePontuarSoReputacaoAlta() {
        OfertaDescoberta verde = new OfertaDescoberta();
        verde.setPreco(new BigDecimal("200.00"));
        verde.setVendedorNivel("5_green");

        OfertaDescoberta verdeClaro = new OfertaDescoberta();
        verdeClaro.setPreco(new BigDecimal("200.00"));
        verdeClaro.setVendedorNivel("4_light_green");

        assertEquals(10, calculador.calcularScore(verde));
        assertEquals(0, calculador.calcularScore(verdeClaro));
    }
}

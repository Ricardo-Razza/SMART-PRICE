package com.smart.price.service.curadoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubcategoriaExtratorTest {

    private SubcategoriaExtrator extrator;

    @BeforeEach
    void setUp() {
        extrator = new SubcategoriaExtrator();
    }

    @Test
    @DisplayName("Deve extrair a mesma subcategoria para produtos funcionalmente iguais mesmo com marcas diferentes")
    void deveExtrairMesmaSubcategoriaParaProdutosIguais() {
        String sub1 = extrator.extrairSubcategoria("Suporte Articulado A Gás De Mesa Para Monitor 17 A 35", "Suporte Articulado");
        String sub2 = extrator.extrairSubcategoria("Suporte De Monitor Pistão A Gás F80N Elg", "Suporte De Mesa");

        assertEquals("SUPORTE_MONITOR", sub1);
        assertEquals("SUPORTE_MONITOR", sub2);
    }

    @Test
    @DisplayName("Deve extrair subcategoria para Air Fryers de marcas diferentes")
    void deveExtrairSubcategoriaParaAirFryers() {
        String subMondial = extrator.extrairSubcategoria("Fritadeira Air Fryer Mondial 4L Inox", "Air Fryer");
        String subOster = extrator.extrairSubcategoria("Fritadeira Sem Óleo Oster Digital 4.6L", "Fritadeira");

        assertEquals("AIR_FRYER", subMondial);
        assertEquals("AIR_FRYER", subOster);
    }

    @Test
    @DisplayName("Deve extrair subcategoria para periféricos diferentes")
    void deveExtrairSubcategoriasDistintasParaPerifericos() {
        String mouse = extrator.extrairSubcategoria("Mouse Gamer Logitech G502 HERO", "Mouse Gamer");
        String teclado = extrator.extrairSubcategoria("Teclado Mecânico Redragon Kumara Switch Blue", "Teclado Mecânico");
        String headset = extrator.extrairSubcategoria("Headset Gamer HyperX Cloud Stinger 2", "Headset Gamer");

        assertEquals("MOUSE", mouse);
        assertEquals("TECLADO", teclado);
        assertEquals("HEADSET", headset);
    }

    @Test
    @DisplayName("Deve detectar alta similaridade textual entre títulos de produtos concorrentes equivalentes")
    void deveDetectarAltaSimilaridadeEmTitulosEquivalentes() {
        String t1 = "Suporte Articulado A Gás De Mesa Para Monitor 17 A 35";
        String t2 = "Suporte De Mesa Articulado A Gás Para Monitor 17 A 35 Preto";

        double similaridade = extrator.calcularSimilaridadeJaccard(t1, t2);
        assertTrue(similaridade >= 0.50, "A similaridade esperada era >= 0.50, mas foi: " + similaridade);
    }

    @Test
    @DisplayName("Deve acusar baixa similaridade entre produtos de categorias diferentes")
    void deveAcusarBaixaSimilaridadeEntreProdutosDiferentes() {
        String t1 = "Mouse Gamer Sem Fio Logitech G305 Lightspeed";
        String t2 = "Teclado Mecânico Gamer Redragon Kumara";

        double similaridade = extrator.calcularSimilaridadeJaccard(t1, t2);
        assertTrue(similaridade < 0.25, "A similaridade entre mouse e teclado deveria ser baixa: " + similaridade);
    }
}

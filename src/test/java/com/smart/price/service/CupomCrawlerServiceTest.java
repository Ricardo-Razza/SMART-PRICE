package com.smart.price.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.Nicho;
import com.smart.price.repository.NichoRepository;

@ExtendWith(MockitoExtension.class)
class CupomCrawlerServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CupomService cupomService;

    @Mock
    private NichoRepository nichoRepository;

    @Mock
    private CupomValidadorService cupomValidadorService;

    private CupomCrawlerService crawlerService;

    @BeforeEach
    void setUp() {
        // Validador retorna true por padrão nos testes — não faz chamadas HTTP reais
        lenient().when(cupomValidadorService.validarCupomAutomaticamente(any(Cupom.class))).thenReturn(true);
        crawlerService = new CupomCrawlerService(restTemplate, cupomService, nichoRepository, cupomValidadorService);
    }

    @Test
    void deveInterpretarCupomPercentualMercadoLivreCorretamente() {
        String titulo = "Cupom Mercado Livre: 20% OFF acima de R$ 199 com o código MELI20";
        String desc = "Aproveite 20% de desconto em todo o site do Mercado Livre para compras acima de R$ 199. Use o cupom MELI20 no checkout.";
        String link = "https://www.mercadolivre.com.br/cupons";

        Optional<Cupom> cupomOpt = crawlerService.interpretarDadosCupom(titulo, desc, link, List.of());

        assertTrue(cupomOpt.isPresent());
        Cupom c = cupomOpt.get();
        assertEquals("MELI20", c.getCodigo());
        assertEquals("PERCENTUAL", c.getTipoDesconto());
        assertEquals(new BigDecimal("20"), c.getValorDesconto());
        assertEquals(new BigDecimal("199"), c.getValorMinimoCompra());
        assertTrue(c.getAtivo());
        // Com validador automático aprovando (mock retorna true), testado deve ser true
        assertTrue(c.getTestado());
    }

    @Test
    void deveInterpretarCupomValorFixoMercadoLivreCorretamente() {
        String titulo = "Super Cupom Mercado Livre: R$ 50 OFF acima de R$ 299";
        String desc = "Economize R$ 50 nas suas compras no Mercado Livre acima de R$ 299. Insira o cupom VALE50 antes de pagar.";
        String link = "https://www.mercadolivre.com.br/promocao";

        Optional<Cupom> cupomOpt = crawlerService.interpretarDadosCupom(titulo, desc, link, List.of());

        assertTrue(cupomOpt.isPresent());
        Cupom c = cupomOpt.get();
        assertEquals("VALE50", c.getCodigo());
        assertEquals("VALOR_FIXO", c.getTipoDesconto());
        assertEquals(new BigDecimal("50"), c.getValorDesconto());
        assertEquals(new BigDecimal("299"), c.getValorMinimoCompra());
    }

    @Test
    void deveMapearNichoCompativelQuandoIdentificadoNoTexto() {
        Nicho nichoHardware = new Nicho();
        nichoHardware.setId(1L);
        nichoHardware.setNome("HARDWARE & INFORMÁTICA");

        String titulo = "Cupom Mercado Livre Informática: R$ 100 OFF com cupom TECH100";
        String desc = "Válido para notebooks, computadores e hardware no Mercado Livre para compras acima de R$ 800.";
        String link = "https://www.mercadolivre.com.br";

        Optional<Cupom> cupomOpt = crawlerService.interpretarDadosCupom(titulo, desc, link, List.of(nichoHardware));

        assertTrue(cupomOpt.isPresent());
        Cupom c = cupomOpt.get();
        assertEquals("TECH100", c.getCodigo());
        assertNotNull(c.getNicho());
        assertEquals("HARDWARE & INFORMÁTICA", c.getNicho().getNome());
    }

    @Test
    void deveIgnorarItemQueNaoSejaDoMercadoLivre() {
        String xml = """
            <rss version="2.0">
                <channel>
                    <item>
                        <title>Cupom Amazon: R$ 30 OFF com cupom AMZ30</title>
                        <description>Válido para compras na Amazon Brasil</description>
                        <link>https://www.amazon.com.br</link>
                    </item>
                </channel>
            </rss>
            """;

        List<Cupom> extraidos = crawlerService.extrairCuponsDoFeed(xml, List.of());
        assertTrue(extraidos.isEmpty(), "Não deve extrair cupons de outras lojas que não o Mercado Livre");
    }

    @Test
    void deveExtrairCupomValidoDeFeedRssReal() {
        String xml = """
            <rss version="2.0">
                <channel>
                    <item>
                        <title>Cupom Mercado Livre: R$ 40 OFF acima de R$ 200 com cupom MELI40</title>
                        <description><![CDATA[Aproveite R$ 40 de desconto no Mercado Livre em produtos selecionados. Cupom MELI40]]></description>
                        <link>https://www.mercadolivre.com.br/cupons</link>
                    </item>
                </channel>
            </rss>
            """;

        List<Cupom> extraidos = crawlerService.extrairCuponsDoFeed(xml, List.of());
        assertEquals(1, extraidos.size());
        assertEquals("MELI40", extraidos.get(0).getCodigo());
        assertEquals(new BigDecimal("40"), extraidos.get(0).getValorDesconto());
        assertEquals(new BigDecimal("200"), extraidos.get(0).getValorMinimoCompra());
    }

    @Test
    void deveExtrairCupomDePostCanalTelegram() {
        String html = """
            <div class="tgme_widget_message_text js-message_text" dir="auto">
                <b>CUPONS MERCADO LIVRE<br/>18% de desconto em R&#036;79(Limite de R&#036;60)</b><br/>
                <code>COMPRAFACIL</code><br/>
                Produtos: <a href="https://meli.la/1dyDkWa">https://meli.la/1dyDkWa</a>
            </div>
            """;

        List<Cupom> extraidos = crawlerService.extrairCuponsDoFeed(html, List.of());
        assertEquals(1, extraidos.size());
        Cupom c = extraidos.get(0);
        assertEquals("COMPRAFACIL", c.getCodigo());
        assertEquals("PERCENTUAL", c.getTipoDesconto());
        assertEquals(new BigDecimal("18"), c.getValorDesconto());
        assertEquals(new BigDecimal("79"), c.getValorMinimoCompra());
        assertEquals("https://meli.la/1dyDkWa", c.getLinkHotsite());
    }

    @Test
    void deveExtrairMultiplosCuponsDePostTelegram() {
        String html = """
            <div class="tgme_widget_message_text js-message_text" dir="auto">
                <b>🚨 *CUPONS DE DESCONTO MERCADO LIVRE* 🚨</b><br/><br/>
                🏷️ <b>R&#036;200 OFF</b> acima de <b>R&#036;1299</b>: <code>TODOSITE2001609</code><br/>
                🏷️ <b>R&#036;20 OFF</b> acima de <b>R&#036;199</b>: <code>TODOSITE1609</code><br/>
                🏷️ <b>R&#036;60 OFF</b> acima de <b>R&#036;599</b>: <code>ALLSITE1609</code><br/>
                🏷️ <b>10% OFF</b> acima de <b>R&#036;149</b>, <b>limite R&#036;200</b>: <code>OFERTAS</code><br/><br/>
                👉 <a href="https://mercadolivre.com/sec/18mB2SS">Ative aqui</a>
            </div>
            """;

        List<Cupom> extraidos = crawlerService.extrairCuponsDoFeed(html, List.of());
        assertEquals(4, extraidos.size());

        Cupom c1 = extraidos.get(0);
        assertEquals("TODOSITE2001609", c1.getCodigo());
        assertEquals("VALOR_FIXO", c1.getTipoDesconto());
        assertEquals(new BigDecimal("200"), c1.getValorDesconto());
        assertEquals(new BigDecimal("1299"), c1.getValorMinimoCompra());

        Cupom c2 = extraidos.get(1);
        assertEquals("TODOSITE1609", c2.getCodigo());
        assertEquals("VALOR_FIXO", c2.getTipoDesconto());
        assertEquals(new BigDecimal("20"), c2.getValorDesconto());
        assertEquals(new BigDecimal("199"), c2.getValorMinimoCompra());

        Cupom c4 = extraidos.get(3);
        assertEquals("OFERTAS", c4.getCodigo());
        assertEquals("PERCENTUAL", c4.getTipoDesconto());
        assertEquals(new BigDecimal("10"), c4.getValorDesconto());
        assertEquals(new BigDecimal("149"), c4.getValorMinimoCompra());
    }
}

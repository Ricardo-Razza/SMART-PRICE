package com.smart.price.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.smart.price.enums.LojaEnum;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

@ExtendWith(MockitoExtension.class)
class CopywriterIaServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private CopywriterIaService copywriterIaService;

    @BeforeEach
    void setUp() {
        copywriterIaService = new CopywriterIaService(restTemplate);
        ReflectionTestUtils.setField(copywriterIaService, "enabled", false);
    }

    @Test
    @DisplayName("formatarMensagemCompleta deve sempre incluir título, preços formatados em R$ e link oficial")
    void deveFormatarMensagemCompletaComPrecosELink() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("Óleo Extraordinário Elseve L'Oréal Paris 100ml");
        oferta.setPreco(BigDecimal.valueOf(29.90));
        oferta.setPrecoOriginal(BigDecimal.valueOf(49.90));
        oferta.setDescontoPercentual(40);
        oferta.setFreteGratis(true);
        oferta.setMenorPrecoHistorico(true);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-123456789");
        oferta.setLoja(LojaEnum.MERCADO_LIVRE);

        String gancho = "🚨 MENOR PREÇO DA HISTÓRIA! O ÓLEO DE MILIONÁRIA!";
        String linkAfiliado = "https://produto.mercadolivre.com.br/MLB-123456789?matttool=123&mattword=test";

        String mensagem = copywriterIaService.formatarMensagemCompleta(
                oferta,
                gancho,
                "BELEZA10",
                BigDecimal.valueOf(26.91),
                linkAfiliado
        );

        assertThat(mensagem).contains("🚨 MENOR PREÇO DA HISTÓRIA! O ÓLEO DE MILIONÁRIA!");
        assertThat(mensagem).contains("🔥 *Óleo Extraordinário Elseve L'Oréal Paris 100ml*");
        assertThat(mensagem).contains("💵 De: ~R$");
        assertThat(mensagem).contains("49,90");
        assertThat(mensagem).contains("💥 *Por apenas: R$");
        assertThat(mensagem).contains("29,90");
        assertThat(mensagem).contains("40% OFF");
        assertThat(mensagem).contains("🎟️ *COM CUPOM:* Aplique `BELEZA10`");
        assertThat(mensagem).contains("26,91");
        assertThat(mensagem).contains("🚚 *Frete Grátis incluso!*");
        assertThat(mensagem).contains("👉 [mercadolivre.com.br/MLB-123456789](" + linkAfiliado + ")");
    }

    @Test
    @DisplayName("gerarCopyOferta deve gerar anúncio completo contendo preços mesmo com IA desabilitada")
    void deveGerarCopyComPrecoMesmoComIaDesabilitada() {
        Nicho nicho = new Nicho();
        nicho.setNome("Perfumaria & Cosméticos");

        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("Perfume Masculino Malbec 100ml");
        oferta.setPreco(BigDecimal.valueOf(149.90));
        oferta.setPrecoOriginal(BigDecimal.valueOf(199.90));
        oferta.setDescontoPercentual(25);
        oferta.setNicho(nicho);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-987654321");

        String copy = copywriterIaService.gerarCopyOferta(oferta, null, null);

        assertThat(copy).isNotEmpty();
        assertThat(copy).contains("Perfume Masculino Malbec 100ml");
        assertThat(copy).contains("R$");
        assertThat(copy).contains("149,90");
        assertThat(copy).contains("199,90");
        assertThat(copy).contains("👉 [mercadolivre.com.br/MLB-987654321](https://produto.mercadolivre.com.br/MLB-987654321)");
    }

    @Test
    @DisplayName("gerarCopyOferta deve gerar anúncio limpo estilo Herói da Promo sem ganchos ou frases longas")
    void deveGerarAnuncioLimpoEstiloHeroiDaPromo() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("PlayStation 5 Slim Edição Digital 1TB");
        oferta.setPreco(BigDecimal.valueOf(3499.00));
        oferta.setPrecoOriginal(BigDecimal.valueOf(3999.00));
        oferta.setDescontoPercentual(12);
        oferta.setFreteGratis(true);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-112233");

        String copy = copywriterIaService.gerarCopyOferta(oferta, null, null);

        assertThat(copy).startsWith("🔥 *PlayStation 5 Slim Edição Digital 1TB*");
        assertThat(copy).contains("💵 De: ~R$");
        assertThat(copy).contains("💥 *Por apenas: R$");
        assertThat(copy).contains("🚚 *Frete Grátis incluso!*");
        assertThat(copy).contains("🛒 *Compre aqui:*\n👉 [mercadolivre.com.br/MLB-112233](https://produto.mercadolivre.com.br/MLB-112233)");
        assertThat(copy).doesNotContain("gafanhotos");
        assertThat(copy).doesNotContain("estagiário");
    }
}

package com.smart.price.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smart.price.enums.LojaEnum;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.service.formatacao.TelegramHtml;
import com.smart.price.service.formatacao.WhatsAppMarkdown;

class CopywriterIaServiceTest {

    private CopywriterIaService copywriterIaService;

    @BeforeEach
    void setUp() {
        copywriterIaService = new CopywriterIaService();
    }

    @Test
    @DisplayName("Telegram: mensagem em HTML com preço riscado, cupom em <code> e link <a>")
    void deveFormatarMensagemCompletaEmHtmlParaTelegram() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("Óleo Extraordinário Elseve L'Oréal Paris 100ml");
        oferta.setPreco(BigDecimal.valueOf(29.90));
        oferta.setPrecoOriginal(BigDecimal.valueOf(49.90));
        oferta.setDescontoPercentual(40);
        oferta.setFreteGratis(true);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-123456789");
        oferta.setLoja(LojaEnum.MERCADO_LIVRE);

        String linkAfiliado = "https://produto.mercadolivre.com.br/MLB-123456789?matt_tool=123&matt_word=test";

        String mensagem = copywriterIaService.formatarMensagemCompleta(
                oferta, "🚨 Gancho", "BELEZA10", BigDecimal.valueOf(26.91), linkAfiliado, TelegramHtml.INSTANCIA);

        assertThat(mensagem).startsWith("🚨 Gancho\n\n");
        assertThat(mensagem).contains("🔥 <b>Óleo Extraordinário Elseve L'Oréal Paris 100ml</b>");
        assertThat(mensagem).contains("💵 De: <s>R$");
        assertThat(mensagem).contains("49,90</s>");
        assertThat(mensagem).contains("💥 <b>Por apenas: R$");
        assertThat(mensagem).contains("<b>40% OFF</b>");
        assertThat(mensagem).contains("🎟️ <b>COM CUPOM:</b> Aplique <code>BELEZA10</code>");
        assertThat(mensagem).contains("26,91");
        assertThat(mensagem).contains("🚚 <b>Frete Grátis incluso!</b>");
        assertThat(mensagem).contains("👉 <a href=\"https://produto.mercadolivre.com.br/MLB-123456789?matt_tool=123&amp;matt_word=test\">"
                + "mercadolivre.com.br/MLB-123456789</a>");
    }

    @Test
    @DisplayName("Telegram: título com < > & _ * [ não quebra a formatação")
    void deveEscaparCaracteresEspeciaisDoTituloNoHtml() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("Cabo USB_C *Turbo* [2m] <Preto> & Branco");
        oferta.setPreco(BigDecimal.valueOf(19.90));
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-1");

        String mensagem = copywriterIaService.formatarMensagemCompleta(
                oferta, null, null, null, oferta.getUrl(), TelegramHtml.INSTANCIA);

        assertThat(mensagem).contains("<b>Cabo USB_C *Turbo* [2m] &lt;Preto&gt; &amp; Branco</b>");
    }

    @Test
    @DisplayName("WhatsApp: *negrito*, ~riscado~ e URL explícita para gerar o preview")
    void deveFormatarParaWhatsApp() {
        Nicho nicho = new Nicho();
        nicho.setNome("Perfumaria & Cosméticos");

        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("Perfume Masculino Malbec 100ml");
        oferta.setPreco(BigDecimal.valueOf(149.90));
        oferta.setPrecoOriginal(BigDecimal.valueOf(199.90));
        oferta.setDescontoPercentual(25);
        oferta.setNicho(nicho);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-987654321");

        String mensagem = copywriterIaService.formatarMensagemCompleta(
                oferta, null, null, null, oferta.getUrl(), WhatsAppMarkdown.INSTANCIA);

        assertThat(mensagem).startsWith("🔥 *Perfume Masculino Malbec 100ml*");
        assertThat(mensagem).contains("💵 De: ~R$");
        assertThat(mensagem).contains("💥 *Por apenas: R$");
        assertThat(mensagem).contains("149,90");
        assertThat(mensagem).contains("🛒 *Compre aqui:*\n👉 https://produto.mercadolivre.com.br/MLB-987654321");
        assertThat(mensagem).doesNotContain("](");
    }

    @Test
    @DisplayName("gerarCopyOferta gera o texto de referência mesmo sem IA")
    void deveGerarCopyComPrecoMesmoComIaDesabilitada() {
        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setTitulo("PlayStation 5 Slim Edição Digital 1TB");
        oferta.setPreco(BigDecimal.valueOf(3499.00));
        oferta.setPrecoOriginal(BigDecimal.valueOf(3999.00));
        oferta.setDescontoPercentual(12);
        oferta.setFreteGratis(true);
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB-112233");

        String copy = copywriterIaService.gerarCopyOferta(oferta, null, null);

        assertThat(copy).startsWith("🔥 *PlayStation 5 Slim Edição Digital 1TB*");
        assertThat(copy).contains("3.499,00");
        assertThat(copy).contains("🚚 *Frete Grátis incluso!*");
    }
}

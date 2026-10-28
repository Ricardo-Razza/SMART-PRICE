package com.smart.price.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.service.formatacao.FormatadorMensagem;
import com.smart.price.service.formatacao.WhatsAppMarkdown;

/**
 * Serviço responsável pela formatação padronizada das mensagens de ofertas e cupons para o Telegram.
 */
@Service
public class CopywriterIaService {

    private static final Logger logger = LoggerFactory.getLogger(CopywriterIaService.class);
    private static final Locale LOCALE_BR = Locale.of("pt", "BR");

    public CopywriterIaService() {
    }

    /**
     * Sobrecarga para formatação de mensagem com código de cupom.
     */
    public String gerarCopyOferta(OfertaDescoberta oferta, String cupomCodigo) {
        return gerarCopyOferta(oferta, cupomCodigo, null);
    }

    /**
     * Gera o texto de referência da oferta (guardado em copy_ia). Cada canal formata o seu na hora do envio.
     */
    public String gerarCopyOferta(OfertaDescoberta oferta, String cupomCodigo, BigDecimal precoComCupom) {
        if (oferta == null) {
            return "";
        }
        return formatarMensagemCompleta(oferta, null, cupomCodigo, precoComCupom, oferta.getUrl(), WhatsAppMarkdown.INSTANCIA);
    }

    /**
     * Formata o anúncio avulso de um cupom de desconto.
     */
    public String gerarCopyCupomAvulso(Cupom cupom, String linkFinal, FormatadorMensagem f) {
        if (cupom == null) {
            return "";
        }
        return formatarMensagemCupom(cupom, linkFinal, f);
    }

    /**
     * Monta o corpo da mensagem da oferta com detalhes de preço, desconto, cupom e link de afiliado
     * na marcação do canal informado.
     */
    public String formatarMensagemCompleta(OfertaDescoberta oferta, String gancho, String cupomCodigo,
                                           BigDecimal precoComCupom, String link, FormatadorMensagem f) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        if (gancho != null && !gancho.isBlank()) {
            sb.append(f.texto(gancho.trim())).append("\n\n");
        }

        // Título do Produto
        sb.append("🔥 ").append(f.negrito(oferta.getTitulo().trim())).append("\n\n");

        // Preço original se houver desconto
        boolean temPrecoOriginal = oferta.getPrecoOriginal() != null && oferta.getPrecoOriginal().compareTo(oferta.getPreco()) > 0;
        if (temPrecoOriginal) {
            sb.append("💵 De: ").append(f.riscado(moeda.format(oferta.getPrecoOriginal()))).append("\n");
        }

        // Preço promocional e percentual de desconto
        int desc = (oferta.getDescontoPercentual() != null && oferta.getDescontoPercentual() > 0) ? oferta.getDescontoPercentual() : 0;
        sb.append("💥 ").append(f.negrito("Por apenas: " + moeda.format(oferta.getPreco())));
        if (desc > 0) {
            sb.append(" (📉 ").append(f.negrito(desc + "% OFF")).append(")");
        }
        sb.append("\n");

        // Cupom aplicável: preço com cupom só para cupom confirmado (precoComCupom preenchido)
        if (cupomCodigo != null && !cupomCodigo.isBlank()) {
            if (precoComCupom != null) {
                sb.append("🎟️ ").append(f.negrito("COM CUPOM:")).append(" Aplique ").append(f.codigo(cupomCodigo.trim()))
                        .append(" no checkout ➡️ ").append(f.negrito(moeda.format(precoComCupom) + "!")).append("\n");
            } else {
                sb.append("🎟️ ").append(f.negrito("TEM CUPOM:")).append(" ").append(f.codigo(cupomCodigo.trim()))
                        .append(" ").append(f.texto("— confira o desconto no checkout")).append("\n");
            }
        }

        // Frete grátis
        if (Boolean.TRUE.equals(oferta.getFreteGratis())) {
            sb.append("🚚 ").append(f.negrito("Frete Grátis incluso!")).append("\n");
        }

        // Link de compra formatado
        String linkFinal = (link != null && !link.isBlank()) ? link : (oferta.getUrl() != null ? oferta.getUrl() : "");
        if (!linkFinal.isBlank()) {
            String textoCurto = formatarTextoLinkCurto(linkFinal, oferta.getMlbId());
            sb.append("\n🛒 ").append(f.negrito("Compre aqui:")).append("\n👉 ").append(f.link(textoCurto, linkFinal));
        }

        return sb.toString();
    }

    /**
     * Formata o texto exibido no hyperlink encurtado para melhor legibilidade.
     */
    public String formatarTextoLinkCurto(String url, String mlbId) {
        if (mlbId != null && !mlbId.isBlank()) {
            return "mercadolivre.com.br/" + mlbId.trim();
        }
        if (url != null && url.contains("MLB-")) {
            try {
                int idx = url.indexOf("MLB-");
                int endIdx = url.indexOf("?", idx);
                if (endIdx < 0) {
                    endIdx = url.indexOf("/", idx);
                }
                if (endIdx < 0) {
                    endIdx = url.length();
                }
                String code = url.substring(idx, endIdx);
                return "mercadolivre.com.br/" + code;
            } catch (Exception ignored) {
            }
        }
        return "mercadolivre.com.br/oferta";
    }

    /**
     * Formata anúncio avulso de cupom na marcação do canal informado.
     */
    public String formatarMensagemCupom(Cupom cupom, String linkFinal, FormatadorMensagem f) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        sb.append("🎟️ ").append(f.negrito("CUPOM DESTAQUE MERCADO LIVRE")).append(" 🎟️\n\n");

        String valorDesc = "PERCENTUAL".equalsIgnoreCase(cupom.getTipoDesconto())
                ? cupom.getValorDesconto().intValue() + "% OFF"
                : moeda.format(cupom.getValorDesconto()) + " OFF";

        sb.append("🏷️ Código: ").append(f.codigo(cupom.getCodigo())).append(" ").append(f.italico("(toque para copiar)")).append("\n");
        sb.append("💥 ").append(f.negrito("Desconto:")).append(" ").append(f.texto(valorDesc)).append("\n");

        if (cupom.getValorMinimoCompra() != null && cupom.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("📦 ").append(f.negrito("Válido para compras a partir de:")).append(" ")
                    .append(f.texto(moeda.format(cupom.getValorMinimoCompra()))).append("\n");
        }

        String categoria = cupom.getNicho() != null ? cupom.getNicho().getNome() : "Válido em produtos selecionados";
        sb.append("🎯 ").append(f.negrito("Categoria:")).append(" ").append(f.texto(categoria)).append("\n");

        if (cupom.getDescricao() != null && !cupom.getDescricao().isBlank()) {
            sb.append("ℹ️ ").append(f.italico(cupom.getDescricao().trim())).append("\n");
        }

        sb.append("\n⚠️ ").append(f.italico("Disponibilidade limitada por volume de ativações no checkout.")).append("\n\n");
        sb.append("👉 ").append(f.link("mercadolivre.com.br/cupons", linkFinal));

        return sb.toString();
    }
}

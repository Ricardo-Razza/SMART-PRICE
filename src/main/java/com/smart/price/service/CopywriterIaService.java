package com.smart.price.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.OfertaDescoberta;

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
     * Formata os dados de uma oferta descoberta em mensagem estruturada para o Telegram.
     */
    public String gerarCopyOferta(OfertaDescoberta oferta, String cupomCodigo, BigDecimal precoComCupom) {
        if (oferta == null) {
            return "";
        }
        return formatarMensagemCompleta(oferta, null, cupomCodigo, precoComCupom, oferta.getUrl());
    }

    /**
     * Formata o anúncio avulso de um cupom de desconto.
     */
    public String gerarCopyCupomAvulso(Cupom cupom, String linkFinal) {
        if (cupom == null) {
            return "";
        }
        return formatarMensagemCupom(cupom, linkFinal);
    }

    /**
     * Monta o corpo da mensagem da oferta com detalhes de preço, desconto, cupom e link de afiliado.
     */
    public String formatarMensagemCompleta(OfertaDescoberta oferta, String gancho, String cupomCodigo, BigDecimal precoComCupom, String link) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        if (gancho != null && !gancho.isBlank()) {
            sb.append(gancho.trim()).append("\n\n");
        }

        // Título do Produto
        sb.append("🔥 *").append(oferta.getTitulo().trim()).append("*\n\n");

        // Preço original se houver desconto
        boolean temPrecoOriginal = oferta.getPrecoOriginal() != null && oferta.getPrecoOriginal().compareTo(oferta.getPreco()) > 0;
        if (temPrecoOriginal) {
            sb.append("💵 De: ~").append(moeda.format(oferta.getPrecoOriginal())).append("~\n");
        }

        // Preço promocional e percentual de desconto
        int desc = (oferta.getDescontoPercentual() != null && oferta.getDescontoPercentual() > 0) ? oferta.getDescontoPercentual() : 0;
        sb.append("💥 *Por apenas: ").append(moeda.format(oferta.getPreco())).append("*");
        if (desc > 0) {
            sb.append(" (📉 *").append(desc).append("% OFF*)");
        }
        sb.append("\n");

        // Cupom aplicável
        if (cupomCodigo != null && !cupomCodigo.isBlank()) {
            String precoFinal = (precoComCupom != null) ? moeda.format(precoComCupom) : moeda.format(oferta.getPreco());
            sb.append("🎟️ *COM CUPOM:* Aplique `").append(cupomCodigo.trim()).append("` no checkout ➡️ *").append(precoFinal).append("!*\n");
        }

        // Frete grátis
        if (Boolean.TRUE.equals(oferta.getFreteGratis())) {
            sb.append("🚚 *Frete Grátis incluso!*\n");
        }

        // Link de compra formatado
        String linkFinal = (link != null && !link.isBlank()) ? link : (oferta.getUrl() != null ? oferta.getUrl() : "");
        if (!linkFinal.isBlank()) {
            String textoCurto = formatarTextoLinkCurto(linkFinal, oferta.getMlbId());
            sb.append("\n🛒 *Compre aqui:*\n👉 [").append(textoCurto).append("](").append(linkFinal).append(")");
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
     * Formata anúncio avulso de cupom para o Telegram.
     */
    public String formatarMensagemCupom(Cupom cupom, String linkFinal) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        StringBuilder sb = new StringBuilder();

        sb.append("🎟️ *CUPOM DESTAQUE MERCADO LIVRE* 🎟️\n\n");

        String valorDesc = "PERCENTUAL".equalsIgnoreCase(cupom.getTipoDesconto())
                ? cupom.getValorDesconto().intValue() + "% OFF"
                : moeda.format(cupom.getValorDesconto()) + " OFF";

        sb.append("🏷️ Código: `").append(cupom.getCodigo()).append("` _(toque para copiar)_\n");
        sb.append("💥 *Desconto:* ").append(valorDesc).append("\n");

        if (cupom.getValorMinimoCompra() != null && cupom.getValorMinimoCompra().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("📦 *Válido para compras a partir de:* ").append(moeda.format(cupom.getValorMinimoCompra())).append("\n");
        }

        if (cupom.getNicho() != null) {
            sb.append("🎯 *Categoria:* ").append(cupom.getNicho().getNome()).append("\n");
        } else {
            sb.append("🎯 *Categoria:* Válido em produtos selecionados\n");
        }

        if (cupom.getDescricao() != null && !cupom.getDescricao().isBlank()) {
            sb.append("ℹ️ _").append(cupom.getDescricao().trim()).append("_\n");
        }

        sb.append("\n⚠️ _Disponibilidade limitada por volume de ativações no checkout._\n\n");
        sb.append("👉 [mercadolivre.com.br/cupons](").append(linkFinal).append(")");

        return sb.toString();
    }

    /**
     * Fallback mantido para compatibilidade com chamadas legadas.
     */
    public String gerarCopyFallback(OfertaDescoberta oferta, String cupomCodigo, BigDecimal precoComCupom) {
        return formatarMensagemCompleta(oferta, null, cupomCodigo, precoComCupom, oferta.getUrl());
    }

    /**
     * Fallback mantido para compatibilidade com chamadas legadas.
     */
    public String gerarCopyCupomAvulsoFallback(Cupom cupom, String linkFinal) {
        return formatarMensagemCupom(cupom, linkFinal);
    }
}

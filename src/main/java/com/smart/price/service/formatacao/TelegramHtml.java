package com.smart.price.service.formatacao;

import java.util.regex.Pattern;

/**
 * Formatação para o Telegram com parse_mode HTML. Só &lt; &gt; &amp; e aspas (em atributos)
 * precisam de escape, o que torna o HTML bem mais seguro que o Markdown legado para títulos de produto.
 */
public final class TelegramHtml implements FormatadorMensagem {

    public static final TelegramHtml INSTANCIA = new TelegramHtml();

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private TelegramHtml() {
    }

    public static String escapar(String puro) {
        if (puro == null) {
            return "";
        }
        return puro.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Remove as tags e desfaz o escape; usado quando o Telegram recusa o HTML e reenviamos sem parse_mode. */
    public static String paraTextoPuro(String html) {
        if (html == null) {
            return "";
        }
        return TAG.matcher(html).replaceAll("")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    @Override
    public String texto(String puro) {
        return escapar(puro);
    }

    @Override
    public String negrito(String puro) {
        return "<b>" + escapar(puro) + "</b>";
    }

    @Override
    public String italico(String puro) {
        return "<i>" + escapar(puro) + "</i>";
    }

    @Override
    public String riscado(String puro) {
        return "<s>" + escapar(puro) + "</s>";
    }

    @Override
    public String codigo(String puro) {
        return "<code>" + escapar(puro) + "</code>";
    }

    @Override
    public String link(String textoVisivel, String url) {
        return "<a href=\"" + escapar(url).replace("\"", "&quot;") + "\">" + escapar(textoVisivel) + "</a>";
    }
}

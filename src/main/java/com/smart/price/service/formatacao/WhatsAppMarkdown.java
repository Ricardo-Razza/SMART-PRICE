package com.smart.price.service.formatacao;

/**
 * Formatação nativa do WhatsApp: *negrito*, _itálico_, ~riscado~ e `código`.
 * O WhatsApp não tem hiperlink com texto, então o link sai como URL explícita para gerar o preview.
 */
public final class WhatsAppMarkdown implements FormatadorMensagem {

    public static final WhatsAppMarkdown INSTANCIA = new WhatsAppMarkdown();

    private WhatsAppMarkdown() {
    }

    @Override
    public String texto(String puro) {
        return puro == null ? "" : puro;
    }

    @Override
    public String negrito(String puro) {
        return "*" + texto(puro) + "*";
    }

    @Override
    public String italico(String puro) {
        return "_" + texto(puro) + "_";
    }

    @Override
    public String riscado(String puro) {
        return "~" + texto(puro) + "~";
    }

    @Override
    public String codigo(String puro) {
        return "`" + texto(puro) + "`";
    }

    @Override
    public String link(String textoVisivel, String url) {
        return texto(url);
    }
}

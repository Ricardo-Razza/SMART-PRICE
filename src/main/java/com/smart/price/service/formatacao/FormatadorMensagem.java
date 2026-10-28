package com.smart.price.service.formatacao;

/**
 * Marcação de texto de um canal. Cada método recebe texto puro e cuida do escape,
 * então títulos com caracteres especiais nunca quebram a formatação.
 */
public interface FormatadorMensagem {

    String texto(String puro);

    String negrito(String puro);

    String italico(String puro);

    String riscado(String puro);

    String codigo(String puro);

    String link(String textoVisivel, String url);
}

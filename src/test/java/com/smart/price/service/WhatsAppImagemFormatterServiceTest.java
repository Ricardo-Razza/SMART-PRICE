package com.smart.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhatsAppImagemFormatterServiceTest {

    private WhatsAppImagemFormatterService formatterService;

    @BeforeEach
    void setUp() {
        formatterService = new WhatsAppImagemFormatterService();
    }

    @Test
    @DisplayName("formatarImagemParaWhatsApp deve retornar null com segurança para URL nula ou vazia")
    void deveRetornarNullParaUrlVazia() {
        assertThat(formatterService.formatarImagemParaWhatsApp(null)).isNull();
        assertThat(formatterService.formatarImagemParaWhatsApp("")).isNull();
        assertThat(formatterService.formatarImagemParaWhatsApp("   ")).isNull();
    }

    @Test
    @DisplayName("formatarImagemParaWhatsApp deve retornar null com segurança para URL inexistente ou inacessível sem quebrar")
    void deveRetornarNullParaUrlInvalida() {
        String resultado = formatterService.formatarImagemParaWhatsApp("http://localhost:9999/imagem-que-nao-existe.jpg");
        assertThat(resultado).isNull();
    }
}

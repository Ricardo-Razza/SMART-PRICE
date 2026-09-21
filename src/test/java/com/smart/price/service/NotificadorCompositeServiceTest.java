package com.smart.price.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

@ExtendWith(MockitoExtension.class)
class NotificadorCompositeServiceTest {

    @Mock
    private TelegramNotificadorService telegramService;

    @Mock
    private WhatsAppNotificadorService whatsAppService;

    private NotificadorCompositeService compositeService;

    @BeforeEach
    void setUp() {
        compositeService = new NotificadorCompositeService(telegramService, whatsAppService);
    }

    @Test
    @DisplayName("Deve despachar notificações tanto para Telegram quanto para WhatsApp")
    void deveDespacharParaAmbosOsCanais() {
        Nicho nicho = new Nicho();
        nicho.setNome("Smartphones");
        OfertaDescoberta oferta = new OfertaDescoberta();
        List<OfertaDescoberta> ofertas = List.of(oferta);

        compositeService.notificar(nicho, ofertas);

        verify(telegramService).notificar(nicho, ofertas);
        verify(whatsAppService).notificar(nicho, ofertas);
    }

    @Test
    @DisplayName("Se Telegram falhar com erro, o envio para o WhatsApp NÃO deve ser interrompido")
    void seTelegramFalharWhatsAppContinua() {
        Nicho nicho = new Nicho();
        OfertaDescoberta oferta = new OfertaDescoberta();
        List<OfertaDescoberta> ofertas = List.of(oferta);

        doThrow(new RuntimeException("Telegram API timeout")).when(telegramService).notificar(any(), any());

        compositeService.notificar(nicho, ofertas);

        verify(telegramService).notificar(nicho, ofertas);
        verify(whatsAppService).notificar(nicho, ofertas);
    }

    @Test
    @DisplayName("Se WhatsApp falhar com erro, não deve propagar exceção")
    void seWhatsAppFalharNaoPropagaErro() {
        Nicho nicho = new Nicho();
        OfertaDescoberta oferta = new OfertaDescoberta();
        List<OfertaDescoberta> ofertas = List.of(oferta);

        doThrow(new RuntimeException("WhatsApp API timeout")).when(whatsAppService).notificar(any(), any());

        compositeService.notificar(nicho, ofertas);

        verify(telegramService).notificar(nicho, ofertas);
        verify(whatsAppService).notificar(nicho, ofertas);
    }

    @Test
    @DisplayName("Não deve fazer nada se a lista de ofertas estiver vazia")
    void naoDeveFazerNadaSeListaVazia() {
        Nicho nicho = new Nicho();

        compositeService.notificar(nicho, List.of());
        compositeService.notificar(nicho, null);

        verify(telegramService, never()).notificar(any(), any());
        verify(whatsAppService, never()).notificar(any(), any());
    }
}

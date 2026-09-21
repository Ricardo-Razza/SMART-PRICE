package com.smart.price.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

/**
 * Hub despachador de notificações que distribui as ofertas selecionadas
 * simultaneamente em paralelo para todos os canais habilitados (Telegram, WhatsApp, etc.).
 */
@Service
@Primary
public class NotificadorCompositeService implements NotificadorOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(NotificadorCompositeService.class);

    private final TelegramNotificadorService telegramService;
    private final WhatsAppNotificadorService whatsAppService;

    public NotificadorCompositeService(
            TelegramNotificadorService telegramService,
            WhatsAppNotificadorService whatsAppService) {
        this.telegramService = telegramService;
        this.whatsAppService = whatsAppService;
    }

    @Override
    public void notificar(Nicho nicho, List<OfertaDescoberta> ofertas) {
        if (ofertas == null || ofertas.isEmpty()) {
            return;
        }

        // Despacha paralelamente para Telegram e WhatsApp, reduzindo a latência total pela metade
        CompletableFuture<Void> telegramFuture = CompletableFuture.runAsync(() -> {
            try {
                telegramService.notificar(nicho, ofertas);
            } catch (Exception ex) {
                logger.error("NotificadorComposite: Falha ao notificar canal Telegram: {}", ex.getMessage());
            }
        });

        CompletableFuture<Void> whatsAppFuture = CompletableFuture.runAsync(() -> {
            try {
                whatsAppService.notificar(nicho, ofertas);
            } catch (Exception ex) {
                logger.error("NotificadorComposite: Falha ao notificar grupo WhatsApp: {}", ex.getMessage());
            }
        });

        CompletableFuture.allOf(telegramFuture, whatsAppFuture).join();
    }
}

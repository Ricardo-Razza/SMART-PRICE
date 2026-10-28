package com.smart.price.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

/**
 * Hub despachador de notificações que distribui as ofertas selecionadas
 * em paralelo para todos os canais habilitados (Telegram, WhatsApp, etc.).
 * Cada canal grava só a sua linha na tabela envio, então o paralelismo não disputa a mesma entidade.
 */
@Service
@Primary
public class NotificadorCompositeService implements NotificadorOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(NotificadorCompositeService.class);

    private final TelegramNotificadorService telegramService;
    private final WhatsAppNotificadorService whatsAppService;
    private final Executor executor;

    public NotificadorCompositeService(
            TelegramNotificadorService telegramService,
            WhatsAppNotificadorService whatsAppService,
            @Qualifier("notificacaoExecutor") Executor executor) {
        this.telegramService = telegramService;
        this.whatsAppService = whatsAppService;
        this.executor = executor;
    }

    @Override
    public void notificar(Nicho nicho, List<OfertaDescoberta> ofertas) {
        if (ofertas == null || ofertas.isEmpty()) {
            return;
        }

        CompletableFuture<Void> telegramFuture = CompletableFuture.runAsync(() -> {
            try {
                telegramService.notificar(nicho, ofertas);
            } catch (Exception ex) {
                logger.error("NotificadorComposite: Falha ao notificar canal Telegram: {}", ex.getMessage());
            }
        }, executor);

        CompletableFuture<Void> whatsAppFuture = CompletableFuture.runAsync(() -> {
            try {
                whatsAppService.notificar(nicho, ofertas);
            } catch (Exception ex) {
                logger.error("NotificadorComposite: Falha ao notificar grupo WhatsApp: {}", ex.getMessage());
            }
        }, executor);

        CompletableFuture.allOf(telegramFuture, whatsAppFuture).join();
    }
}

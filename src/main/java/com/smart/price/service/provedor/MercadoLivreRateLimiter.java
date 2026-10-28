package com.smart.price.service.provedor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Controla o 429 do Mercado Livre sem dormir dentro do pool: ao receber 429, bloqueia novas chamadas
 * até o Retry-After (ou uma espera exponencial, se o header não vier) e as chamadas nesse intervalo
 * são puladas. Um sucesso zera a sequência.
 */
@Component
public class MercadoLivreRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(MercadoLivreRateLimiter.class);

    static final Duration ESPERA_BASE = Duration.ofSeconds(5);
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(5);

    private final Clock clock;
    private Instant bloqueadoAte = Instant.MIN;
    private int sequencia429 = 0;

    public MercadoLivreRateLimiter() {
        this(Clock.systemUTC());
    }

    MercadoLivreRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean podeChamar() {
        return !clock.instant().isBefore(bloqueadoAte);
    }

    public synchronized void registrarSucesso() {
        sequencia429 = 0;
    }

    public synchronized Duration registrar429(HttpHeaders headers) {
        sequencia429++;
        Duration espera = lerRetryAfter(headers);
        if (espera == null) {
            // 5s, 10s, 20s, 40s... até 5 min
            long fator = 1L << Math.min(sequencia429 - 1, 10);
            espera = ESPERA_BASE.multipliedBy(fator);
        }
        if (espera.compareTo(ESPERA_MAXIMA) > 0) {
            espera = ESPERA_MAXIMA;
        }

        Instant fim = clock.instant().plus(espera);
        if (fim.isAfter(bloqueadoAte)) {
            bloqueadoAte = fim;
        }
        logger.warn("MercadoLivre: 429 (#{} seguido). Chamadas suspensas por {}s.", sequencia429, espera.toSeconds());
        return espera;
    }

    private Duration lerRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String valor = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(valor.trim())));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

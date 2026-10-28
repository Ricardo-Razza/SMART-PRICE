package com.smart.price.service.provedor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class MercadoLivreRateLimiterTest {

    private static class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-11-01T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("Respeita o Retry-After e libera depois do prazo")
    void deveRespeitarRetryAfter() {
        RelogioAjustavel relogio = new RelogioAjustavel();
        MercadoLivreRateLimiter limiter = new MercadoLivreRateLimiter(relogio);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");

        assertThat(limiter.registrar429(headers)).isEqualTo(Duration.ofSeconds(30));
        assertThat(limiter.podeChamar()).isFalse();

        relogio.avancar(Duration.ofSeconds(29));
        assertThat(limiter.podeChamar()).isFalse();

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(limiter.podeChamar()).isTrue();
    }

    @Test
    @DisplayName("Sem Retry-After, a espera dobra a cada 429 seguido e zera com sucesso")
    void deveUsarEsperaExponencial() {
        MercadoLivreRateLimiter limiter = new MercadoLivreRateLimiter(new RelogioAjustavel());

        assertThat(limiter.registrar429(null)).isEqualTo(Duration.ofSeconds(5));
        assertThat(limiter.registrar429(null)).isEqualTo(Duration.ofSeconds(10));
        assertThat(limiter.registrar429(null)).isEqualTo(Duration.ofSeconds(20));

        limiter.registrarSucesso();
        assertThat(limiter.registrar429(null)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("A espera nunca passa do teto de 5 minutos")
    void deveLimitarEsperaMaxima() {
        MercadoLivreRateLimiter limiter = new MercadoLivreRateLimiter(new RelogioAjustavel());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "3600");

        assertThat(limiter.registrar429(headers)).isEqualTo(MercadoLivreRateLimiter.ESPERA_MAXIMA);
    }
}

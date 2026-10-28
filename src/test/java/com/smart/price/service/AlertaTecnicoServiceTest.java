package com.smart.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.smart.price.enums.CanalEnvio;

@ExtendWith(MockitoExtension.class)
class AlertaTecnicoServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private AlertaTecnicoService service;

    @BeforeEach
    void setUp() {
        service = new AlertaTecnicoService(restTemplate);
        ReflectionTestUtils.setField(service, "botToken", "token");
        ReflectionTestUtils.setField(service, "chatIdTech", "-100123");
    }

    @Test
    @DisplayName("Avisa uma única vez na 3ª falha seguida e de novo quando o canal volta")
    void deveAvisarNaTerceiraFalhaEAoRecuperar() {
        service.registrarResultado(CanalEnvio.WHATSAPP, false, "timeout");
        service.registrarResultado(CanalEnvio.WHATSAPP, false, "timeout");
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));

        service.registrarResultado(CanalEnvio.WHATSAPP, false, "timeout");
        service.registrarResultado(CanalEnvio.WHATSAPP, false, "timeout");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));

        service.registrarResultado(CanalEnvio.WHATSAPP, true, null);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
        assertThat(service.getFalhasSeguidas(CanalEnvio.WHATSAPP)).isZero();
    }

    @Test
    @DisplayName("Um sucesso no meio zera a contagem")
    void sucessoZeraContagem() {
        service.registrarResultado(CanalEnvio.TELEGRAM, false, "x");
        service.registrarResultado(CanalEnvio.TELEGRAM, false, "x");
        service.registrarResultado(CanalEnvio.TELEGRAM, true, null);
        service.registrarResultado(CanalEnvio.TELEGRAM, false, "x");

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
        assertThat(service.getFalhasSeguidas(CanalEnvio.TELEGRAM)).isEqualTo(1);
    }
}

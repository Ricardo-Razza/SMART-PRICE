package com.smart.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;
import com.smart.price.enums.StatusCupom;

@ExtendWith(MockitoExtension.class)
class CupomValidadorServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private CupomValidadorService validador;
    private Cupom cupom;

    @BeforeEach
    void setUp() {
        validador = new CupomValidadorService(restTemplate);
        ReflectionTestUtils.setField(validador, "enabled", true);
        cupom = new Cupom();
        cupom.setCodigo("MELI20");
    }

    private void responder(ResponseEntity<String> resposta) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resposta);
    }

    @Test
    @DisplayName("Página com \"error\", \"404\" e \"not found\" no HTML não reprova o cupom")
    void naoDeveReprovarPorTermosGenericos() {
        responder(ResponseEntity.ok("<script>onerror=handleError; var e404='not found';</script> Cupom MELI20 ativo"));

        assertThat(validador.validarCupomAutomaticamente(cupom)).isEqualTo(StatusCupom.NAO_VERIFICADO);
    }

    @Test
    @DisplayName("Texto claro de cupom expirado marca como INVALIDO")
    void deveMarcarInvalidoComSinalClaro() {
        responder(ResponseEntity.ok("<h1>Cupom expirado</h1>"));

        assertThat(validador.validarCupomAutomaticamente(cupom)).isEqualTo(StatusCupom.INVALIDO);
    }

    @Test
    @DisplayName("404 marca como INVALIDO")
    void deveMarcarInvalidoEm404() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThat(validador.validarCupomAutomaticamente(cupom)).isEqualTo(StatusCupom.INVALIDO);
    }

    @Test
    @DisplayName("Bloqueio (403) e timeout ficam NAO_VERIFICADO, nunca aprovados")
    void deveFicarNaoVerificadoEmBloqueioOuTimeout() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .thenThrow(new ResourceAccessException("Read timed out"));

        assertThat(validador.validarCupomAutomaticamente(cupom)).isEqualTo(StatusCupom.NAO_VERIFICADO);
        assertThat(validador.validarCupomAutomaticamente(cupom)).isEqualTo(StatusCupom.NAO_VERIFICADO);
    }
}

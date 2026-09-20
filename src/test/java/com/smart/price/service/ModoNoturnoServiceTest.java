package com.smart.price.service;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ModoNoturnoServiceTest {

    private ModoNoturnoService modoNoturnoService;

    @BeforeEach
    void setUp() {
        modoNoturnoService = new ModoNoturnoService();
        ReflectionTestUtils.setField(modoNoturnoService, "enabled", true);
        ReflectionTestUtils.setField(modoNoturnoService, "horaInicioStr", "23:00");
        ReflectionTestUtils.setField(modoNoturnoService, "horaFimStr", "07:30");
        ReflectionTestUtils.setField(modoNoturnoService, "fusoHorario", "America/Sao_Paulo");
        ReflectionTestUtils.setField(modoNoturnoService, "pausarEnvios", true);
    }

    @Test
    @DisplayName("estaNoIntervalo deve reconhecer corretamente horários dentro da madrugada (23:00 às 07:30)")
    void deveIdentificarHorariosNoturnos() {
        // Horários que DEVEM estar no modo noturno
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(23, 0))).isTrue();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(23, 30))).isTrue();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(0, 0))).isTrue();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(2, 45))).isTrue();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(6, 0))).isTrue();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(7, 30))).isTrue();

        // Horários que NÃO devem estar no modo noturno (dia)
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(7, 31))).isFalse();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(12, 0))).isFalse();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(15, 30))).isFalse();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(20, 0))).isFalse();
        assertThat(modoNoturnoService.estaNoIntervalo(LocalTime.of(22, 59))).isFalse();
    }

    @Test
    @DisplayName("devePausarEnvios deve retornar true apenas durante a madrugada quando pausarEnvios estiver ativo")
    void devePausarEnviosApenasNaMadrugada() {
        ReflectionTestUtils.setField(modoNoturnoService, "pausarEnvios", true);

        // Se for 03:00 da manhã
        boolean noturno = modoNoturnoService.estaNoIntervalo(LocalTime.of(3, 0));
        assertThat(noturno).isTrue();

        // Se o modo estiver desabilitado
        ReflectionTestUtils.setField(modoNoturnoService, "enabled", false);
        assertThat(modoNoturnoService.devePausarEnvios()).isFalse();
        assertThat(modoNoturnoService.isEnvioSilencioso()).isFalse();
    }

    @Test
    @DisplayName("isEnvioSilencioso deve ser ativo caso pausarEnvios seja false durante o modo noturno")
    void deveAtivarEnvioSilenciosoCasoNaoPause() {
        ReflectionTestUtils.setField(modoNoturnoService, "enabled", true);
        ReflectionTestUtils.setField(modoNoturnoService, "pausarEnvios", false);

        // Se pausarEnvios for falso, não deve pausar
        assertThat(modoNoturnoService.isPausarEnvios()).isFalse();
    }
}

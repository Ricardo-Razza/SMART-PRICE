package com.smart.price.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smart.price.entity.Cupom;
import com.smart.price.repository.CupomRepository;

@ExtendWith(MockitoExtension.class)
class CupomMonitorStatusServiceTest {

    @Mock
    private CupomRepository cupomRepository;

    @Mock
    private CupomService cupomService;

    private CupomMonitorStatusService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new CupomMonitorStatusService(cupomRepository, cupomService);
        ReflectionTestUtils.setField(monitorService, "enabled", true);
        ReflectionTestUtils.setField(monitorService, "maxHorasTtl", 12);
    }

    @Test
    void deveDesativarCupomQuandoDataExpiracaoForUltrapassada() {
        Cupom c = new Cupom();
        c.setId(1L);
        c.setCodigo("EXPIROU10");
        c.setAtivo(true);
        c.setDataExpiracao(LocalDateTime.now().minusMinutes(5)); // Expirou há 5 minutos

        when(cupomRepository.findByAtivoTrue()).thenReturn(List.of(c));
        when(cupomService.desativarCupomEAlertar(eq("EXPIROU10"), anyString())).thenReturn(true);

        int desativados = monitorService.executarMonitoramento();

        assertEquals(1, desativados);
        verify(cupomService).desativarCupomEAlertar(eq("EXPIROU10"), anyString());
    }

    @Test
    void deveDesativarCupomQuandoTtlMaximoDeSegurancaForUltrapassado() {
        Cupom c = new Cupom();
        c.setId(2L);
        c.setCodigo("TTL20");
        c.setAtivo(true);
        c.setDataCriacao(LocalDateTime.now().minusHours(13)); // Criado há 13h (limite é 12h)
        c.setDataExpiracao(null);

        when(cupomRepository.findByAtivoTrue()).thenReturn(List.of(c));
        when(cupomService.desativarCupomEAlertar(eq("TTL20"), anyString())).thenReturn(true);

        int desativados = monitorService.executarMonitoramento();

        assertEquals(1, desativados);
        verify(cupomService).desativarCupomEAlertar(eq("TTL20"), anyString());
    }

    @Test
    void deveManterCupomAtivoQuandoAindaForValido() {
        Cupom c = new Cupom();
        c.setId(3L);
        c.setCodigo("ATIVO50");
        c.setAtivo(true);
        c.setDataCriacao(LocalDateTime.now().minusHours(2)); // Criado há 2h
        c.setDataExpiracao(LocalDateTime.now().plusHours(5)); // Expira em 5h

        when(cupomRepository.findByAtivoTrue()).thenReturn(List.of(c));

        int desativados = monitorService.executarMonitoramento();

        assertEquals(0, desativados);
        verify(cupomService, never()).desativarCupomEAlertar(anyString(), anyString());
    }
}

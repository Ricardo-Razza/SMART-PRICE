package com.smart.price.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smart.price.entity.Cupom;
import com.smart.price.repository.CupomRepository;
import com.smart.price.repository.NichoRepository;

@ExtendWith(MockitoExtension.class)
class CupomServiceTest {

    @Mock
    private CupomRepository cupomRepository;

    @Mock
    private NichoRepository nichoRepository;

    @Mock
    private CopywriterIaService copywriterIaService;

    @Mock
    private TelegramNotificadorService telegramNotificadorService;

    @Mock
    private com.smart.price.repository.OfertaDescobertaRepository ofertaDescobertaRepository;

    private CupomService cupomService;

    @BeforeEach
    void setUp() {
        cupomService = new CupomService(
                cupomRepository,
                nichoRepository,
                copywriterIaService,
                telegramNotificadorService,
                ofertaDescobertaRepository
        );
    }

    @Test
    void deveDesativarCupomEAlertarNoTelegramComSucesso() {
        Cupom cupom = new Cupom();
        cupom.setId(10L);
        cupom.setCodigo("MELI30");
        cupom.setAtivo(true);

        when(cupomRepository.findByCodigoIgnoreCase("MELI30")).thenReturn(Optional.of(cupom));
        when(cupomRepository.save(any(Cupom.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean desativado = cupomService.desativarCupomEAlertar("MELI30", "Cupom esgotado no Mercado Livre");

        assertTrue(desativado);
        assertFalse(cupom.getAtivo());
        assertEquals("Cupom esgotado no Mercado Livre", cupom.getObservacao());

        verify(cupomRepository).save(cupom);
        verify(telegramNotificadorService).notificarCupomEsgotado(cupom);
    }

    @Test
    void deveIgnorarDesativacaoSeCupomJaEstiverInativo() {
        Cupom cupom = new Cupom();
        cupom.setId(11L);
        cupom.setCodigo("INATIVO10");
        cupom.setAtivo(false);

        when(cupomRepository.findByCodigoIgnoreCase("INATIVO10")).thenReturn(Optional.of(cupom));

        boolean desativado = cupomService.desativarCupomEAlertar("INATIVO10", "Tentativa de desativar novamente");

        assertFalse(desativado);
        verify(cupomRepository, never()).save(any());
        verify(telegramNotificadorService, never()).notificarCupomEsgotado(any());
    }

    @Test
    void deveProcessarNovoCupomIneditoESalvarEAnunciar() {
        Cupom novo = new Cupom();
        novo.setCodigo("NOVO20");
        novo.setValorDesconto(new BigDecimal("20"));
        novo.setValorMinimoCompra(new BigDecimal("150"));
        novo.setAtivo(true);
        // Simula cupom capturado pelo crawler: testado=false (quarentena)
        novo.setTestado(false);

        when(cupomRepository.findByCodigoIgnoreCase("NOVO20")).thenReturn(Optional.empty());
        when(cupomRepository.save(any(Cupom.class))).thenAnswer(inv -> {
            Cupom c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(telegramNotificadorService.aplicarTagAfiliado(any())).thenReturn("https://www.mercadolivre.com.br/cupons?tag=123");
        when(copywriterIaService.gerarCopyCupomAvulso(any(), any())).thenReturn("🔥 NOVO CUPOM LIBERADO!");
        when(telegramNotificadorService.notificarCupomAvulso(any(), any(), any())).thenReturn(true);

        Optional<Cupom> processado = cupomService.processarCupomDetectado(novo);

        assertTrue(processado.isPresent());
        assertEquals("NOVO20", processado.get().getCodigo());
        assertTrue(processado.get().getAtivo());
        // processarCupomDetectado preserva o testado da origem (false para crawler, true para cadastro manual)
        assertFalse(processado.get().getTestado());

        verify(cupomRepository, org.mockito.Mockito.atLeastOnce()).save(any(Cupom.class));
        verify(telegramNotificadorService).notificarCupomAvulso(any(), any(), any());
    }

    @Test
    void deveIgnorarCupomDetectadoJaExistenteNoBanco() {
        Cupom duplicado = new Cupom();
        duplicado.setCodigo("EXISTENTE10");

        Cupom jaExistente = new Cupom();
        jaExistente.setId(1L);
        jaExistente.setCodigo("EXISTENTE10");

        when(cupomRepository.findByCodigoIgnoreCase("EXISTENTE10")).thenReturn(Optional.of(jaExistente));

        Optional<Cupom> processado = cupomService.processarCupomDetectado(duplicado);

        assertFalse(processado.isPresent());
        verify(cupomRepository, never()).save(duplicado);
        verify(telegramNotificadorService, never()).notificarCupomAvulso(any(), any(), any());
    }
}

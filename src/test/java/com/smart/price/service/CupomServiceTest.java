package com.smart.price.service;

import java.math.BigDecimal;
import java.util.List;
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
import com.smart.price.repository.OfertaDescobertaRepository;

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
    private OfertaDescobertaRepository ofertaDescobertaRepository;

    @Mock
    private ModoNoturnoService modoNoturnoService;

    private CupomService cupomService;

    @BeforeEach
    void setUp() {
        cupomService = new CupomService(
                cupomRepository,
                nichoRepository,
                copywriterIaService,
                telegramNotificadorService,
                ofertaDescobertaRepository,
                modoNoturnoService
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
    void deveProcessarNovoCupomIneditoESalvarSemAnuncioImediato() {
        Cupom novo = new Cupom();
        novo.setCodigo("OURO50");
        novo.setTipoDesconto("VALOR_FIXO");
        novo.setValorDesconto(new BigDecimal("50"));
        novo.setValorMinimoCompra(new BigDecimal("200"));
        novo.setAtivo(true);
        novo.setTestado(false);

        when(cupomRepository.findByCodigoIgnoreCase("OURO50")).thenReturn(Optional.empty());
        when(cupomRepository.save(any(Cupom.class))).thenAnswer(inv -> {
            Cupom c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        Optional<Cupom> processado = cupomService.processarCupomDetectado(novo);

        assertTrue(processado.isPresent());
        assertEquals("OURO50", processado.get().getCodigo());
        assertTrue(processado.get().getAtivo());
        assertFalse(processado.get().getTestado());
        // Cupom ouro fica com anunciadoAvulso=false para aguardar a fila cadenciada
        assertFalse(processado.get().getAnunciadoAvulso());

        verify(cupomRepository).save(any(Cupom.class));
        // NÃO deve disparar anúncio avulso imediatamente
        verify(telegramNotificadorService, never()).notificarCupomAvulso(any(), any(), any());
    }

    @Test
    void deveMarcarCupomFracoComoJaAnunciadoParaNaoPoluirCanal() {
        Cupom fraco = new Cupom();
        fraco.setCodigo("FRACO5");
        fraco.setTipoDesconto("VALOR_FIXO");
        fraco.setValorDesconto(new BigDecimal("5"));
        fraco.setValorMinimoCompra(new BigDecimal("100"));
        fraco.setAtivo(true);

        when(cupomRepository.findByCodigoIgnoreCase("FRACO5")).thenReturn(Optional.empty());
        when(cupomRepository.save(any(Cupom.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Cupom> processado = cupomService.processarCupomDetectado(fraco);

        assertTrue(processado.isPresent());
        // Cupom fraco é marcado como anunciadoAvulso=true para nunca ser enviado sozinho
        assertTrue(processado.get().getAnunciadoAvulso());
        verify(telegramNotificadorService, never()).notificarCupomAvulso(any(), any(), any());
    }

    @Test
    void deveProcessarFilaAnuncioAvulsoComSucessoParaMelhorCupomOuro() {
        // Cupom Fraco (deve ser descartado da fila de avulsos)
        Cupom fraco = new Cupom();
        fraco.setId(1L);
        fraco.setCodigo("FRACO10");
        fraco.setTipoDesconto("VALOR_FIXO");
        fraco.setValorDesconto(new BigDecimal("10"));
        fraco.setValorMinimoCompra(new BigDecimal("100"));
        fraco.setAtivo(true);
        fraco.setAnunciadoAvulso(false);

        // Cupom Ouro 1 (R$ 50 OFF)
        Cupom ouro1 = new Cupom();
        ouro1.setId(2L);
        ouro1.setCodigo("OURO50");
        ouro1.setTipoDesconto("VALOR_FIXO");
        ouro1.setValorDesconto(new BigDecimal("50"));
        ouro1.setValorMinimoCompra(new BigDecimal("250"));
        ouro1.setAtivo(true);
        ouro1.setAnunciadoAvulso(false);

        // Cupom Ouro 2 (R$ 100 OFF - Campeão)
        Cupom ouro2 = new Cupom();
        ouro2.setId(3L);
        ouro2.setCodigo("OURO100");
        ouro2.setTipoDesconto("VALOR_FIXO");
        ouro2.setValorDesconto(new BigDecimal("100"));
        ouro2.setValorMinimoCompra(new BigDecimal("600"));
        ouro2.setAtivo(true);
        ouro2.setAnunciadoAvulso(false);

        when(modoNoturnoService.devePausarEnvios()).thenReturn(false);
        when(cupomRepository.findByAtivoTrueAndAnunciadoAvulsoFalse()).thenReturn(List.of(fraco, ouro1, ouro2));
        when(telegramNotificadorService.aplicarTagAfiliado(any())).thenReturn("https://mercadolivre.com.br/cupons?tag=1");
        when(copywriterIaService.gerarCopyCupomAvulso(any(), any())).thenReturn("🎟️ CUPOM DESTAQUE");
        when(telegramNotificadorService.notificarCupomAvulso(any(), any(), any())).thenReturn(true);
        when(cupomRepository.save(any(Cupom.class))).thenAnswer(inv -> inv.getArgument(0));

        cupomService.processarFilaAnuncioAvulso();

        // Fraco deve ter sido marcado como anunciadoAvulso=true para sair da fila
        assertTrue(fraco.getAnunciadoAvulso());
        // Ouro 2 (melhor) deve ter sido anunciado e marcado como anunciadoAvulso=true
        assertTrue(ouro2.getAnunciadoAvulso());
        // Ouro 1 continua não anunciado para o próximo ciclo
        assertFalse(ouro1.getAnunciadoAvulso());

        // Apenas 1 cupom avulso foi enviado ao Telegram
        verify(telegramNotificadorService).notificarCupomAvulso(eq(ouro2), any(), any());
    }

    @Test
    void deveRespeitarModoNoturnoNoProcessamentoDeFila() {
        when(modoNoturnoService.devePausarEnvios()).thenReturn(true);

        cupomService.processarFilaAnuncioAvulso();

        verify(cupomRepository, never()).findByAtivoTrueAndAnunciadoAvulsoFalse();
        verify(telegramNotificadorService, never()).notificarCupomAvulso(any(), any(), any());
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

package com.smart.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.entity.TermoBusca;
import com.smart.price.enums.StatusCupom;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.repository.TermoBuscaRepository;
import com.smart.price.service.HistoricoPrecoService.EstatisticaPreco;
import com.smart.price.service.curadoria.CalculadorScoreOferta;
import com.smart.price.service.curadoria.SubcategoriaExtrator;
import com.smart.price.service.provedor.ProvedorLojaHub;
import com.smart.price.service.provedor.ProvedorLojaService;

@ExtendWith(MockitoExtension.class)
class CuradoriaOfertasServiceTest {

    @Mock
    private TermoBuscaRepository termoBuscaRepository;

    @Mock
    private OfertaDescobertaRepository ofertaDescobertaRepository;

    @Mock
    private ProvedorLojaHub provedorLojaHub;

    @Mock
    private ProvedorLojaService provedorLojaService;

    @Mock
    private CopywriterIaService copywriterIaService;

    @Mock
    private CupomService cupomService;

    @Mock
    private HistoricoPrecoService historicoPrecoService;

    private SubcategoriaExtrator subcategoriaExtrator;
    private CalculadorScoreOferta calculadorScoreOferta;
    private CuradoriaOfertasService curadoriaOfertasService;

    @BeforeEach
    void setUp() {
        subcategoriaExtrator = new SubcategoriaExtrator();
        calculadorScoreOferta = new CalculadorScoreOferta();

        curadoriaOfertasService = new CuradoriaOfertasService(
                termoBuscaRepository,
                ofertaDescobertaRepository,
                provedorLojaHub,
                copywriterIaService,
                cupomService,
                subcategoriaExtrator,
                calculadorScoreOferta,
                historicoPrecoService
        );

        ReflectionTestUtils.setField(curadoriaOfertasService, "horasAntiDuplicacao", 24);
        ReflectionTestUtils.setField(curadoriaOfertasService, "minCiclosAntiDuplicacao", 3);
        ReflectionTestUtils.setField(curadoriaOfertasService, "termosPorCiclo", 4);
        ReflectionTestUtils.setField(curadoriaOfertasService, "maxOfertasPorNicho", 5);
        ReflectionTestUtils.setField(curadoriaOfertasService, "scoreMinimo", 50);
        ReflectionTestUtils.setField(curadoriaOfertasService, "scoreMinimoMaduro", 50);
        ReflectionTestUtils.setField(curadoriaOfertasService, "diasParaScoreMaduro", 30);
        ReflectionTestUtils.setField(curadoriaOfertasService, "delayMs", 0L);

        // Sem histórico por padrão: nenhum selo nem desconto real
        lenient().when(historicoPrecoService.estatisticas(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Deve impedir publicação de dois produtos da mesma subcategoria (ex: dois mouses) na mesma leva")
    void deveImpedirDuplicidadeDeSubcategoriaNaMesmaLeva() {
        historicoFavoravel();
        Nicho nicho = new Nicho("HARDWARE & INFORMÁTICA");
        nicho.setId(1L);

        TermoBusca termo1 = new TermoBusca(nicho, "Mouse Gamer");
        termo1.setId(10L);

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any()))
                .thenReturn(List.of(termo1));
        when(provedorLojaHub.getProvedoresAtivos())
                .thenReturn(List.of(provedorLojaService));

        // Dois mouses gamers com preços ótimos e alto desconto
        OfertaDescoberta mouseLogitech = new OfertaDescoberta();
        mouseLogitech.setMlbId("MLB111");
        mouseLogitech.setTitulo("Mouse Gamer Logitech G502 HERO 25K DPI");
        mouseLogitech.setPreco(new BigDecimal("200.00"));
        mouseLogitech.setPrecoOriginal(new BigDecimal("400.00"));
        mouseLogitech.setDescontoPercentual(50); // 35 pts
        mouseLogitech.setUrl("https://produto.mercadolivre.com.br/MLB-111");
        mouseLogitech.setDisponivel(true);

        OfertaDescoberta mouseRazer = new OfertaDescoberta();
        mouseRazer.setMlbId("MLB222");
        mouseRazer.setTitulo("Mouse Gamer Razer Deathadder Essential");
        mouseRazer.setPreco(new BigDecimal("150.00"));
        mouseRazer.setPrecoOriginal(new BigDecimal("300.00"));
        mouseRazer.setDescontoPercentual(50); // 35 pts
        mouseRazer.setUrl("https://produto.mercadolivre.com.br/MLB-222");
        mouseRazer.setDisponivel(true);

        // Um teclado mecânico para comparar (Score Ouro)
        OfertaDescoberta teclado = new OfertaDescoberta();
        teclado.setMlbId("MLB333");
        teclado.setTitulo("Teclado Mecânico Redragon Kumara RGB");
        teclado.setPreco(new BigDecimal("150.00"));
        teclado.setPrecoOriginal(new BigDecimal("300.00"));
        teclado.setDescontoPercentual(50); // 35 pts
        teclado.setUrl("https://produto.mercadolivre.com.br/MLB-333");
        teclado.setDisponivel(true);

        when(provedorLojaService.buscarOfertasPorTermo(anyString(), any()))
                .thenReturn(List.of(mouseLogitech, mouseRazer, teclado));
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());
        when(ofertaDescobertaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        // Apenas 2 ofertas devem ser selecionadas: 1 Mouse e 1 Teclado! O segundo mouse deve ser barrado!
        assertEquals(2, selecionadas.size());
        long mousesNoLote = selecionadas.stream()
                .filter(o -> "MOUSE".equals(o.getSubcategoria()))
                .count();
        assertEquals(1, mousesNoLote, "Deveria haver no máximo 1 mouse na mesma leva!");
    }

    @Test
    @DisplayName("Não deve selecionar ofertas com score abaixo da nota de corte")
    void naoDeveSelecionarOfertasComScoreBaixo() {
        Nicho nicho = new Nicho("GAMES");
        nicho.setId(2L);
        TermoBusca termo = new TermoBusca(nicho, "Jogo PS5");

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any())).thenReturn(List.of(termo));
        when(provedorLojaHub.getProvedoresAtivos()).thenReturn(List.of(provedorLojaService));

        // Oferta fraca (apenas 5% de desconto, sem cupom, sem menor preço histórico)
        OfertaDescoberta fraca = new OfertaDescoberta();
        fraca.setMlbId("MLB999");
        fraca.setTitulo("Jogo Qualquer PS5");
        fraca.setPreco(new BigDecimal("330.00"));
        fraca.setPrecoOriginal(new BigDecimal("350.00"));
        fraca.setDescontoPercentual(5);
        fraca.setUrl("https://produto.mercadolivre.com.br/MLB-999");
        fraca.setDisponivel(true);

        when(provedorLojaService.buscarOfertasPorTermo(anyString(), any())).thenReturn(List.of(fraca));
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        // Canal preservado: nenhuma oferta qualificada
        assertTrue(selecionadas.isEmpty());
    }

    @Test
    @DisplayName("Deve curar ofertas em alta de forma 100% autônoma mesmo quando não há termos cadastrados no nicho")
    void deveCurarOfertasEmAltaAutonomamenteMesmoSemTermosCadastrados() {
        historicoFavoravel();
        Nicho nicho = new Nicho("GAMES & CONSOLES", "MLB1144");
        nicho.setId(10L);

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any())).thenReturn(List.of());
        when(termoBuscaRepository.findByNichoAndAtivoTrue(any())).thenReturn(List.of());
        when(provedorLojaHub.getProvedoresAtivos()).thenReturn(List.of(provedorLojaService));

        OfertaDescoberta ps5 = new OfertaDescoberta();
        ps5.setMlbId("MLB555");
        ps5.setTitulo("Console PlayStation 5 Slim Edição Digital");
        ps5.setPreco(new BigDecimal("2500.00"));
        ps5.setPrecoOriginal(new BigDecimal("5000.00"));
        ps5.setDescontoPercentual(50); // selo vem do histórico simulado
        ps5.setUrl("https://produto.mercadolivre.com.br/MLB-555");
        ps5.setDisponivel(true);

        OfertaDescoberta headset = new OfertaDescoberta();
        headset.setMlbId("MLB666");
        headset.setTitulo("Headset Gamer Sem Fio Sony Pulse 3D");
        headset.setPreco(new BigDecimal("300.00"));
        headset.setPrecoOriginal(new BigDecimal("600.00"));
        headset.setDescontoPercentual(50); // selo vem do histórico simulado
        headset.setUrl("https://produto.mercadolivre.com.br/MLB-666");
        headset.setDisponivel(true);

        when(provedorLojaService.buscarOfertasEmAlta(nicho)).thenReturn(List.of(ps5, headset));
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());
        when(ofertaDescobertaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        assertEquals(2, selecionadas.size(), "Deveria curar as 2 ofertas em alta autônomas do nicho!");
        java.util.Set<String> subcategorias = selecionadas.stream()
                .map(OfertaDescoberta::getSubcategoria)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(subcategorias.contains("CONSOLE"), "Deveria conter a subcategoria CONSOLE");
        assertTrue(subcategorias.contains("HEADSET"), "Deveria conter a subcategoria HEADSET");
    }

    @Test
    @DisplayName("Deve descartar capinhas e miudezas quando o termo de busca for de produto nobre (ex: iphone)")
    void deveDescartarCapinhasEMiudezasEmBuscaDeProdutosNobres() {
        historicoFavoravel();
        Nicho nicho = new Nicho("SMARTPHONES & WEARABLES");
        nicho.setId(2L);

        TermoBusca termoIphone = new TermoBusca(nicho, "iphone");
        termoIphone.setId(20L);

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any()))
                .thenReturn(List.of(termoIphone));
        when(provedorLojaHub.getProvedoresAtivos())
                .thenReturn(List.of(provedorLojaService));

        // 1. Capinha barata anunciada com termo "iphone"
        OfertaDescoberta capinha = new OfertaDescoberta();
        capinha.setMlbId("MLB-CAPA");
        capinha.setTitulo("Capinha de Silicone Aveludada para iPhone 15 Pro");
        capinha.setPreco(new BigDecimal("29.90"));
        capinha.setPrecoOriginal(new BigDecimal("59.90"));
        capinha.setDescontoPercentual(50);
        capinha.setUrl("https://produto.mercadolivre.com.br/MLB-CAPA");
        capinha.setTermoOrigem("iphone");
        capinha.setDisponivel(true);

        // 2. Aparelho real iPhone 13 com preço e desconto legítimos
        OfertaDescoberta iphoneReal = new OfertaDescoberta();
        iphoneReal.setMlbId("MLB-IPHONE13");
        iphoneReal.setTitulo("Apple iPhone 13 (128 GB) - Meia-noite");
        iphoneReal.setPreco(new BigDecimal("3499.00"));
        iphoneReal.setPrecoOriginal(new BigDecimal("4999.00"));
        iphoneReal.setDescontoPercentual(50);
        iphoneReal.setFreteGratis(true);
        iphoneReal.setUrl("https://produto.mercadolivre.com.br/MLB-IPHONE13");
        iphoneReal.setTermoOrigem("iphone");
        iphoneReal.setDisponivel(true);

        when(provedorLojaService.buscarOfertasEmAlta(nicho)).thenReturn(List.of());
        when(provedorLojaService.buscarOfertasPorTermo("iphone", nicho)).thenReturn(List.of(capinha, iphoneReal));
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());
        when(ofertaDescobertaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        assertEquals(1, selecionadas.size(), "Deveria selecionar apenas o iPhone real e descartar a capinha!");
        assertEquals("Apple iPhone 13 (128 GB) - Meia-noite", selecionadas.get(0).getTitulo());
    }

    @Test
    @DisplayName("Deve barrar oferta já publicada nas últimas 24 horas caso não haja queda de preço expressiva")
    void deveBarrarOfertaJaPublicadaNasUltimas24HorasSemQuedaDePreco() {
        Nicho nicho = new Nicho("HARDWARE & INFORMÁTICA");
        nicho.setId(1L);

        TermoBusca termo = new TermoBusca(nicho, "Monitor Gamer");
        termo.setId(30L);

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any())).thenReturn(List.of(termo));
        when(provedorLojaHub.getProvedoresAtivos()).thenReturn(List.of(provedorLojaService));

        OfertaDescoberta monitor = new OfertaDescoberta();
        monitor.setMlbId("MLB-MONITOR");
        monitor.setTitulo("Monitor Gamer LG UltraGear 24 144Hz");
        monitor.setPreco(new BigDecimal("800.00"));
        monitor.setPrecoOriginal(new BigDecimal("1200.00"));
        monitor.setDescontoPercentual(33);
        monitor.setUrl("https://produto.mercadolivre.com.br/MLB-MONITOR");
        monitor.setDisponivel(true);

        when(provedorLojaService.buscarOfertasEmAlta(nicho)).thenReturn(List.of());
        when(provedorLojaService.buscarOfertasPorTermo("Monitor Gamer", nicho)).thenReturn(List.of(monitor));

        // Simula que o produto já foi publicado nas últimas 24h pelo mesmo preço
        OfertaDescoberta publicadaAnteriormente = new OfertaDescoberta();
        publicadaAnteriormente.setMlbId("MLB-MONITOR");
        publicadaAnteriormente.setPreco(new BigDecimal("800.00"));
        publicadaAnteriormente.setDataDescoberta(java.time.LocalDateTime.now().minusHours(2));

        when(ofertaDescobertaRepository.findUltimaPublicacaoGlobal(any(), any(), any()))
                .thenReturn(Optional.of(publicadaAnteriormente));

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        assertTrue(selecionadas.isEmpty(), "Oferta deveria ser bloqueada pelo anti-duplicação global de 24 horas!");
    }

    @Test
    @DisplayName("Deve permitir republicação de produto recente se houver queda de preço de pelo menos 5%")
    void devePermitirRepublicacaoQuandoPrecoCaiMaisDe5Porcento() {
        historicoFavoravel();
        Nicho nicho = new Nicho("HARDWARE & INFORMÁTICA");
        nicho.setId(1L);

        TermoBusca termo = new TermoBusca(nicho, "Monitor Gamer");
        termo.setId(30L);

        when(termoBuscaRepository.findProximosTermosParaBusca(any(), any())).thenReturn(List.of(termo));
        when(provedorLojaHub.getProvedoresAtivos()).thenReturn(List.of(provedorLojaService));

        OfertaDescoberta monitorComPrecoMenor = new OfertaDescoberta();
        monitorComPrecoMenor.setMlbId("MLB-MONITOR");
        monitorComPrecoMenor.setTitulo("Monitor Gamer LG UltraGear 24 144Hz");
        monitorComPrecoMenor.setPreco(new BigDecimal("720.00")); // Queda de 10% (de 800 para 720)
        monitorComPrecoMenor.setPrecoOriginal(new BigDecimal("1200.00"));
        monitorComPrecoMenor.setDescontoPercentual(40);
        monitorComPrecoMenor.setUrl("https://produto.mercadolivre.com.br/MLB-MONITOR");
        monitorComPrecoMenor.setDisponivel(true);

        when(provedorLojaService.buscarOfertasEmAlta(nicho)).thenReturn(List.of());
        when(provedorLojaService.buscarOfertasPorTermo("Monitor Gamer", nicho)).thenReturn(List.of(monitorComPrecoMenor));

        OfertaDescoberta publicadaAnteriormente = new OfertaDescoberta();
        publicadaAnteriormente.setMlbId("MLB-MONITOR");
        publicadaAnteriormente.setPreco(new BigDecimal("800.00"));
        publicadaAnteriormente.setDataDescoberta(java.time.LocalDateTime.now().minusHours(3));

        when(ofertaDescobertaRepository.findUltimaPublicacaoGlobal(any(), any(), any()))
                .thenReturn(Optional.of(publicadaAnteriormente));
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());
        when(ofertaDescobertaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(nicho);

        assertEquals(1, selecionadas.size(), "Deveria permitir republicação porque houve queda de preço >= 5%!");
        assertTrue(selecionadas.get(0).getMenorPrecoHistorico());
    }

    @Test
    @DisplayName("Com menos de 10 coletas em 7 dias não há selo nem desconto real, mesmo no menor preço visto")
    void naoDeveDarSeloComHistoricoInsuficiente() {
        when(historicoPrecoService.estatisticas(anyString(), any(), anyInt())).thenReturn(Optional.of(
                new EstatisticaPreco(new BigDecimal("900"), new BigDecimal("1000"), new BigDecimal("1100"), 9, 6)));
        OfertaDescoberta oferta = ofertaUnicaPorTermo("MLB-POUCO", "Monitor Gamer AOC 27", "500.00", "1000.00", 50);

        curadoriaOfertasService.curarMelhoresOfertasDoNicho(oferta.getNicho());

        assertEquals(false, oferta.getMenorPrecoHistorico());
        assertEquals(null, oferta.getDescontoReal());
    }

    @Test
    @DisplayName("Com histórico suficiente: selo, desconto contra a mediana e \"De\" acima do máximo ignorado")
    void deveCalcularSeloEDescontoRealEIgnorarPrecoDeInflado() {
        when(historicoPrecoService.estatisticas(anyString(), any(), anyInt())).thenReturn(Optional.of(
                new EstatisticaPreco(new BigDecimal("800"), new BigDecimal("1000"), new BigDecimal("1100"), 12, 8)));
        OfertaDescoberta oferta = ofertaUnicaPorTermo("MLB-REAL", "Monitor Gamer AOC 27", "700.00", "2000.00", 65);

        curadoriaOfertasService.curarMelhoresOfertasDoNicho(oferta.getNicho());

        assertTrue(oferta.getMenorPrecoHistorico());
        assertEquals(30, oferta.getDescontoReal()); // (1000 - 700) / 1000
        assertEquals(null, oferta.getPrecoOriginal()); // "De" 2000 > máximo 1100
        assertEquals(0, oferta.getDescontoPercentual());
    }

    @Test
    @DisplayName("Cupom não verificado aparece sem preço com cupom; confirmado leva o preço final")
    void deveMostrarPrecoComCupomSoQuandoConfirmado() {
        OfertaDescoberta oferta = ofertaUnicaPorTermo("MLB-CUPOM1", "Monitor Gamer AOC 27", "500.00", "1000.00", 50);
        Cupom naoVerificado = cupom("TALVEZ50", StatusCupom.NAO_VERIFICADO);
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.of(naoVerificado));

        curadoriaOfertasService.curarMelhoresOfertasDoNicho(oferta.getNicho());

        assertEquals("TALVEZ50", oferta.getCupomAplicado());
        assertEquals(null, oferta.getPrecoComCupom());
        assertEquals(null, oferta.getDescontoCupom());

        OfertaDescoberta outra = ofertaUnicaPorTermo("MLB-CUPOM2", "Monitor Gamer LG 24", "500.00", "1000.00", 50);
        Cupom confirmado = cupom("CERTO50", StatusCupom.CONFIRMADO);
        when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.of(confirmado));

        curadoriaOfertasService.curarMelhoresOfertasDoNicho(outra.getNicho());

        assertEquals("CERTO50", outra.getCupomAplicado());
        assertEquals(0, new BigDecimal("450.00").compareTo(outra.getPrecoComCupom()));
    }

    @Test
    @DisplayName("Score de todo candidato avaliado vai para o histórico, inclusive o reprovado")
    void deveGravarScoreDeCandidatoReprovado() {
        OfertaDescoberta fraca = ofertaUnicaPorTermo("MLB-FRACA", "Jogo Qualquer PS5", "330.00", "350.00", 5);

        List<OfertaDescoberta> selecionadas = curadoriaOfertasService.curarMelhoresOfertasDoNicho(fraca.getNicho());

        assertTrue(selecionadas.isEmpty());
        verify(historicoPrecoService).registrarColetas(anyList(),
                argThat(scores -> Integer.valueOf(4).equals(scores.get("MLB-FRACA"))));
    }

    @Test
    @DisplayName("Score mínimo sobe para o valor maduro depois de 30 dias de histórico")
    void deveUsarScoreMinimoMaduroApos30Dias() {
        ReflectionTestUtils.setField(curadoriaOfertasService, "scoreMinimo", 20);
        when(historicoPrecoService.inicioDoHistorico()).thenReturn(Optional.of(LocalDateTime.now().minusDays(10)));
        assertEquals(20, curadoriaOfertasService.scoreMinimoVigente());

        when(historicoPrecoService.inicioDoHistorico()).thenReturn(Optional.of(LocalDateTime.now().minusDays(31)));
        assertEquals(50, curadoriaOfertasService.scoreMinimoVigente());
    }

    /** Histórico suficiente com mínimo acima de qualquer preço dos testes: dá o selo e desconto real cheio. */
    private void historicoFavoravel() {
        lenient().when(historicoPrecoService.estatisticas(anyString(), any(), anyInt())).thenReturn(Optional.of(
                new EstatisticaPreco(new BigDecimal("10000"), new BigDecimal("12000"), new BigDecimal("15000"), 12, 8)));
    }

    private OfertaDescoberta ofertaUnicaPorTermo(String mlbId, String titulo, String preco, String precoDe, int desconto) {
        Nicho nicho = new Nicho("HARDWARE & INFORMÁTICA");
        nicho.setId(1L);
        TermoBusca termo = new TermoBusca(nicho, "Monitor");
        lenient().when(termoBuscaRepository.findProximosTermosParaBusca(any(), any())).thenReturn(List.of(termo));
        lenient().when(provedorLojaHub.getProvedoresAtivos()).thenReturn(List.of(provedorLojaService));
        lenient().when(ofertaDescobertaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cupomService.encontrarMelhorCupomParaOferta(any())).thenReturn(Optional.empty());

        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setMlbId(mlbId);
        oferta.setTitulo(titulo);
        oferta.setPreco(new BigDecimal(preco));
        oferta.setPrecoOriginal(new BigDecimal(precoDe));
        oferta.setDescontoPercentual(desconto);
        oferta.setUrl("https://produto.mercadolivre.com.br/" + mlbId);
        oferta.setDisponivel(true);
        oferta.setNicho(nicho);
        lenient().when(provedorLojaService.buscarOfertasPorTermo(anyString(), eq(nicho))).thenReturn(List.of(oferta));
        return oferta;
    }

    private Cupom cupom(String codigo, StatusCupom status) {
        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setTipoDesconto("VALOR_FIXO");
        cupom.setValorDesconto(new BigDecimal("50"));
        cupom.setStatusValidacao(status);
        return cupom;
    }
}

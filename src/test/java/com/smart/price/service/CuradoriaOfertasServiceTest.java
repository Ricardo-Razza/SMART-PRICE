package com.smart.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.entity.TermoBusca;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.repository.TermoBuscaRepository;
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
                calculadorScoreOferta
        );

        ReflectionTestUtils.setField(curadoriaOfertasService, "horasAntiDuplicacao", 24);
        ReflectionTestUtils.setField(curadoriaOfertasService, "minCiclosAntiDuplicacao", 3);
        ReflectionTestUtils.setField(curadoriaOfertasService, "termosPorCiclo", 4);
        ReflectionTestUtils.setField(curadoriaOfertasService, "maxOfertasPorNicho", 5);
        ReflectionTestUtils.setField(curadoriaOfertasService, "scoreMinimo", 60);
        ReflectionTestUtils.setField(curadoriaOfertasService, "delayMs", 0L);
    }

    @Test
    @DisplayName("Deve impedir publicação de dois produtos da mesma subcategoria (ex: dois mouses) na mesma leva")
    void deveImpedirDuplicidadeDeSubcategoriaNaMesmaLeva() {
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
        mouseLogitech.setMenorPrecoHistorico(true); // 30 pts -> Total 65 pts (Score Ouro)
        mouseLogitech.setUrl("https://produto.mercadolivre.com.br/MLB-111");
        mouseLogitech.setDisponivel(true);

        OfertaDescoberta mouseRazer = new OfertaDescoberta();
        mouseRazer.setMlbId("MLB222");
        mouseRazer.setTitulo("Mouse Gamer Razer Deathadder Essential");
        mouseRazer.setPreco(new BigDecimal("150.00"));
        mouseRazer.setPrecoOriginal(new BigDecimal("300.00"));
        mouseRazer.setDescontoPercentual(50); // 35 pts
        mouseRazer.setMenorPrecoHistorico(true); // 30 pts -> Total 65 pts (Score Ouro)
        mouseRazer.setUrl("https://produto.mercadolivre.com.br/MLB-222");
        mouseRazer.setDisponivel(true);

        // Um teclado mecânico para comparar (Score Ouro)
        OfertaDescoberta teclado = new OfertaDescoberta();
        teclado.setMlbId("MLB333");
        teclado.setTitulo("Teclado Mecânico Redragon Kumara RGB");
        teclado.setPreco(new BigDecimal("150.00"));
        teclado.setPrecoOriginal(new BigDecimal("300.00"));
        teclado.setDescontoPercentual(50); // 35 pts
        teclado.setMenorPrecoHistorico(true); // 30 pts -> Total 65 pts (Score Ouro)
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
        ps5.setDescontoPercentual(50); // 35 pts + 30 pts histórico = 65 pts
        ps5.setMenorPrecoHistorico(true);
        ps5.setUrl("https://produto.mercadolivre.com.br/MLB-555");
        ps5.setDisponivel(true);

        OfertaDescoberta headset = new OfertaDescoberta();
        headset.setMlbId("MLB666");
        headset.setTitulo("Headset Gamer Sem Fio Sony Pulse 3D");
        headset.setPreco(new BigDecimal("300.00"));
        headset.setPrecoOriginal(new BigDecimal("600.00"));
        headset.setDescontoPercentual(50); // 35 pts + 30 pts histórico = 65 pts
        headset.setMenorPrecoHistorico(true);
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
        capinha.setMenorPrecoHistorico(true);
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
        iphoneReal.setMenorPrecoHistorico(true);
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
}

package com.smart.price.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.service.provedor.ProvedorLojaHub;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificadorServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OfertaDescobertaRepository ofertaDescobertaRepository;

    @Mock
    private ProvedorLojaHub provedorLojaHub;

    @Mock
    private CopywriterIaService copywriterIaService;

    @Mock
    private ModoNoturnoService modoNoturnoService;

    @Mock
    private WhatsAppImagemFormatterService imagemFormatterService;

    private WhatsAppNotificadorService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppNotificadorService(
                restTemplate,
                ofertaDescobertaRepository,
                provedorLojaHub,
                copywriterIaService,
                modoNoturnoService,
                imagemFormatterService
        );

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiUrl", "http://evolution-api:8080");
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "instanceName", "smartprice");
        ReflectionTestUtils.setField(service, "defaultGroupId", "120363028471928374@g.us");
    }

    @Test
    @DisplayName("Não deve enviar quando enabled for false")
    void naoDeveEnviarQuandoDesabilitado() {
        ReflectionTestUtils.setField(service, "enabled", false);

        Nicho nicho = new Nicho();
        nicho.setNome("Hardware");
        OfertaDescoberta oferta = new OfertaDescoberta();

        service.notificar(nicho, List.of(oferta));

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Não deve enviar durante o Modo Noturno")
    void naoDeveEnviarDuranteModoNoturno() {
        when(modoNoturnoService.devePausarEnvios()).thenReturn(true);

        Nicho nicho = new Nicho();
        nicho.setNome("Hardware");
        OfertaDescoberta oferta = new OfertaDescoberta();

        service.notificar(nicho, List.of(oferta));

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Não deve enviar se não houver groupId configurado no nicho nem no default")
    void naoDeveEnviarSemGroupId() {
        ReflectionTestUtils.setField(service, "defaultGroupId", "");
        Nicho nicho = new Nicho();
        nicho.setWhatsappGroupId(null);
        OfertaDescoberta oferta = new OfertaDescoberta();

        service.notificar(nicho, List.of(oferta));

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Deve extrair múltiplos grupos separados por vírgula ou espaço")
    void deveExtrairMultiplosGrupos() {
        ReflectionTestUtils.setField(service, "defaultGroupId", "120363000000000001@g.us, 120363000000000002@g.us; 120363000000000003@g.us");

        List<String> grupos = service.extrairGruposDestino(null);

        assertThat(grupos).containsExactly(
                "120363000000000001@g.us",
                "120363000000000002@g.us",
                "120363000000000003@g.us"
        );
    }

    @Test
    @DisplayName("Deve enviar oferta com foto enquadrada e atualizar dataEnvioWhatsApp")
    void deveEnviarOfertaComFoto() {
        when(modoNoturnoService.devePausarEnvios()).thenReturn(false);

        Nicho nicho = new Nicho();
        nicho.setNome("Smartphones");
        nicho.setWhatsappGroupId("120363000000000000@g.us");

        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setId(1L);
        oferta.setTitulo("Galaxy S24");
        oferta.setPreco(BigDecimal.valueOf(3999.00));
        oferta.setFotoUrl("https://exemplo.com/s24.jpg");
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB123");
        oferta.setDisponivel(true);

        when(imagemFormatterService.formatarImagemParaWhatsApp("https://exemplo.com/s24.jpg"))
                .thenReturn("base64_enquadrado_perfeito");

        when(copywriterIaService.formatarMensagemCompleta(any(), any(), any(), any(), any()))
                .thenReturn("🔥 *Galaxy S24* por R$ 3.999 [Ver Oferta](https://link.com)");

        when(restTemplate.postForEntity(contains("/message/sendMedia/smartprice"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("key", "val"), HttpStatus.OK));

        service.notificar(nicho, List.of(oferta));

        verify(restTemplate).postForEntity(contains("/message/sendMedia/smartprice"), any(HttpEntity.class), eq(Map.class));
        verify(ofertaDescobertaRepository).save(oferta);
        assertThat(oferta.getDataEnvioWhatsApp()).isNotNull();
        assertThat(oferta.getStatusEnvio()).isEqualTo("ENVIADO");
    }

    @Test
    @DisplayName("Deve despachar para múltiplos grupos quando configurados")
    void deveDespacharParaMultiplosGrupos() {
        when(modoNoturnoService.devePausarEnvios()).thenReturn(false);

        Nicho nicho = new Nicho();
        nicho.setNome("Geral");
        nicho.setWhatsappGroupId("120363000000000001@g.us, 120363000000000002@g.us");

        OfertaDescoberta oferta = new OfertaDescoberta();
        oferta.setId(1L);
        oferta.setTitulo("Fritadeira Airfryer");
        oferta.setUrl("https://produto.mercadolivre.com.br/MLB456");
        oferta.setDisponivel(true);

        when(copywriterIaService.formatarMensagemCompleta(any(), any(), any(), any(), any()))
                .thenReturn("🔥 Airfryer em promoção!");

        when(restTemplate.postForEntity(contains("/message/sendText/smartprice"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("key", "val"), HttpStatus.OK));

        service.notificar(nicho, List.of(oferta));

        // Deve ter chamado 2 vezes (uma para cada grupo)
        verify(restTemplate, times(2)).postForEntity(contains("/message/sendText/smartprice"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("consultarStatusConexao deve retornar CONNECTED quando state for open")
    void deveRetornarStatusConectado() {
        Map<String, Object> mockResponse = Map.of(
                "instance", Map.of("instanceName", "smartprice", "state", "open")
        );

        when(restTemplate.exchange(
                contains("/instance/connectionState/smartprice"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        Map<String, Object> status = service.consultarStatusConexao();

        assertThat(status.get("status")).isEqualTo("CONNECTED");
        assertThat(status.get("conectado")).isEqualTo(true);
    }

    @Test
    @DisplayName("consultarStatusConexao deve retornar DISCONNECTED quando falhar comunicação")
    void deveRetornarStatusDesconectadoEmCasoDeErro() {
        when(restTemplate.exchange(
                contains("/instance/connectionState/smartprice"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> status = service.consultarStatusConexao();

        assertThat(status.get("status")).isEqualTo("DISCONNECTED");
        assertThat(status.get("conectado")).isEqualTo(false);
    }

    @Test
    @DisplayName("listarGrupos deve formatar a lista com id e nome")
    void deveListarGruposCorretamente() {
        List<Map<String, Object>> mockList = List.of(
                Map.of("id", "12036301@g.us", "subject", "Grupo de Promos 1"),
                Map.of("id", "12036302@g.us", "name", "Grupo de Hardware 2")
        );

        when(restTemplate.exchange(
                contains("/group/fetchAllGroups/smartprice"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(List.class)
        )).thenReturn(new ResponseEntity<>(mockList, HttpStatus.OK));

        List<Map<String, Object>> grupos = service.listarGrupos();

        assertThat(grupos).hasSize(2);
        assertThat(grupos.get(0).get("id")).isEqualTo("12036301@g.us");
        assertThat(grupos.get(0).get("nome")).isEqualTo("Grupo de Promos 1");
        assertThat(grupos.get(1).get("nome")).isEqualTo("Grupo de Hardware 2");
    }

    @Test
    @DisplayName("dispararSendText deve retornar true em caso de sucesso HTTP 200")
    void deveEnviarTextoComSucesso() {
        when(restTemplate.postForEntity(contains("/message/sendText/smartprice"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

        boolean resultado = service.dispararSendText("120363028471928374@g.us", "Mensagem de teste");

        assertThat(resultado).isTrue();
    }
}

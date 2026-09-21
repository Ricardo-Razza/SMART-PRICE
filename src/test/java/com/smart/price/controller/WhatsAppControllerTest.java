package com.smart.price.controller;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.service.WhatsAppNotificadorService;

@ExtendWith(MockitoExtension.class)
class WhatsAppControllerTest {

    @Mock
    private WhatsAppNotificadorService whatsAppService;

    @Mock
    private OfertaDescobertaRepository ofertaRepository;

    private WhatsAppController controller;

    @BeforeEach
    void setUp() {
        controller = new WhatsAppController(whatsAppService, ofertaRepository);
    }

    @Test
    @DisplayName("GET /status deve retornar o mapa de status da instância")
    void deveRetornarStatus() {
        when(whatsAppService.consultarStatusConexao())
                .thenReturn(Map.of("status", "CONNECTED", "conectado", true));

        ResponseEntity<Map<String, Object>> response = controller.obterStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("GET /grupos deve retornar a lista de grupos disponíveis")
    void deveRetornarGrupos() {
        when(whatsAppService.listarGrupos())
                .thenReturn(List.of(Map.of("id", "123@g.us", "nome", "Ofertas VIP")));

        ResponseEntity<List<Map<String, Object>>> response = controller.listarGrupos();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("nome")).isEqualTo("Ofertas VIP");
    }

    @Test
    @DisplayName("POST /testar-envio deve enviar mensagem com sucesso para o groupId default")
    void deveTestarEnvioComSucessoUsandoDefault() {
        when(whatsAppService.getDefaultGroupId()).thenReturn("120363028471928374@g.us");
        when(whatsAppService.dispararSendText(eq("120363028471928374@g.us"), anyString())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.testarEnvio(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sucesso")).isEqualTo(true);
        verify(whatsAppService).dispararSendText(eq("120363028471928374@g.us"), anyString());
    }

    @Test
    @DisplayName("POST /testar-envio deve retornar badRequest se nenhum groupId for informado nem existir default")
    void deveRetornarBadRequestSemGroupId() {
        when(whatsAppService.getDefaultGroupId()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.testarEnvio(Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("sucesso")).isEqualTo(false);
    }

    @Test
    @DisplayName("POST /testar-envio deve retornar 500 se o disparo falhar")
    void deveRetornar500SeDisparoFalhar() {
        when(whatsAppService.dispararSendText(eq("123@g.us"), anyString())).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.testarEnvio(Map.of("groupId", "123@g.us"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("sucesso")).isEqualTo(false);
    }

    @Test
    @DisplayName("GET /qrcode deve retornar dados de conexão ou QR Code")
    void deveRetornarQrCode() {
        when(whatsAppService.obterQrCodeOuConexao())
                .thenReturn(Map.of("code", "qrcode_base64_data"));

        ResponseEntity<Map<String, Object>> response = controller.obterQrCode();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("code")).isEqualTo("qrcode_base64_data");
    }

    @Test
    @DisplayName("POST /testar-oferta deve disparar oferta com sucesso")
    void deveTestarOfertaComSucesso() {
        when(whatsAppService.getDefaultGroupId()).thenReturn("120363028471928374@g.us");
        when(whatsAppService.enviarOferta(any(), eq("120363028471928374@g.us"))).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.testarOferta(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sucesso")).isEqualTo(true);
    }
}

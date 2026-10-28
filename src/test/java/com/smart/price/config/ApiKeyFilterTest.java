package com.smart.price.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private MockHttpServletResponse executar(ApiKeyFilter filtro, String uri, String chave) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api" + uri);
        request.setContextPath("/api");
        if (chave != null) {
            request.addHeader(ApiKeyFilter.HEADER, chave);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("Chave correta passa")
    void devePermitirComChaveCorreta() throws Exception {
        assertThat(executar(new ApiKeyFilter("segredo"), "/whatsapp/testar-envio", "segredo").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sem header ou com chave errada responde 401")
    void deveBloquearSemChaveOuComChaveErrada() throws Exception {
        ApiKeyFilter filtro = new ApiKeyFilter("segredo");
        assertThat(executar(filtro, "/whatsapp/testar-envio", null).getStatus()).isEqualTo(401);
        assertThat(executar(filtro, "/whatsapp/testar-envio", "errada").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Sem chave configurada, tudo é recusado (falha fechada)")
    void deveRecusarTudoSemChaveConfigurada() throws Exception {
        assertThat(executar(new ApiKeyFilter(""), "/nichos", "").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Swagger fica fora do filtro (desligado no perfil prod)")
    void deveIgnorarSwagger() throws Exception {
        assertThat(executar(new ApiKeyFilter("segredo"), "/swagger-ui/index.html", null).getStatus()).isEqualTo(200);
    }
}

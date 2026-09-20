package com.smart.price.service.provedor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smart.price.service.MercadoLivrePesquisaPorNomeService;

@ExtendWith(MockitoExtension.class)
class MercadoLivreProvedorServiceTest {

    @Mock
    private MercadoLivrePesquisaPorNomeService mlPesquisaService;

    private MercadoLivreProvedorService provedorService;

    @BeforeEach
    void setUp() {
        provedorService = new MercadoLivreProvedorService(mlPesquisaService);
        ReflectionTestUtils.setField(provedorService, "mattTool", "32843307");
        ReflectionTestUtils.setField(provedorService, "mattWord", "smartpricetele");
        ReflectionTestUtils.setField(provedorService, "forceInApp", true);
    }

    @Test
    @DisplayName("Deve formatar link direto do produto adicionando matt_tool, matt_word e forceInApp")
    void deveFormatarLinkDiretoDoProduto() {
        String urlOriginal = "https://produto.mercadolivre.com.br/MLB-123456789";
        String linkAfiliado = provedorService.formatarLinkAfiliado(urlOriginal);

        assertEquals("https://produto.mercadolivre.com.br/MLB-123456789?matt_tool=32843307&matt_word=smartpricetele&forceInApp=true",
                linkAfiliado);
    }

    @Test
    @DisplayName("Deve formatar link que já possui query string usando separador &")
    void deveFormatarLinkComQueryExistente() {
        String urlOriginal = "https://produto.mercadolivre.com.br/MLB-123456789?pdp_filters=category:MLB1144";
        String linkAfiliado = provedorService.formatarLinkAfiliado(urlOriginal);

        assertTrue(linkAfiliado.startsWith("https://produto.mercadolivre.com.br/MLB-123456789?pdp_filters=category:MLB1144&"));
        assertTrue(linkAfiliado.contains("matt_tool=32843307"));
        assertTrue(linkAfiliado.contains("matt_word=smartpricetele"));
        assertTrue(linkAfiliado.contains("forceInApp=true"));
    }

    @Test
    @DisplayName("Deve substituir parâmetros de afiliados de terceiros pelos parâmetros oficiais do usuário")
    void deveSubstituirAfiliadoDeTerceiros() {
        String urlComOutroAfiliado = "https://produto.mercadolivre.com.br/MLB-123456789?matt_tool=99999999&matt_word=outro_canal";
        String linkAfiliado = provedorService.formatarLinkAfiliado(urlComOutroAfiliado);

        assertTrue(linkAfiliado.contains("matt_tool=32843307"));
        assertTrue(linkAfiliado.contains("matt_word=smartpricetele"));
        assertTrue(!linkAfiliado.contains("99999999"));
        assertTrue(!linkAfiliado.contains("outro_canal"));
    }

    @Test
    @DisplayName("Não deve duplicar parâmetros se o link já estiver com a tag exata")
    void naoDeveDuplicarSeJaFormatado() {
        String urlJaFormatada = "https://produto.mercadolivre.com.br/MLB-123456789?matt_tool=32843307&matt_word=smartpricetele&forceInApp=true";
        String resultado = provedorService.formatarLinkAfiliado(urlJaFormatada);

        assertEquals(urlJaFormatada, resultado);
    }
}

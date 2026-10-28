package com.smart.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smart.price.entity.HistoricoPreco;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.LojaEnum;
import com.smart.price.repository.HistoricoPrecoRepository;
import com.smart.price.service.HistoricoPrecoService.EstatisticaPreco;

@ExtendWith(MockitoExtension.class)
class HistoricoPrecoServiceTest {

    @Mock
    private HistoricoPrecoRepository repository;

    private HistoricoPrecoService service;

    @BeforeEach
    void setUp() {
        service = new HistoricoPrecoService(repository);
    }

    @Test
    @DisplayName("Grava uma coleta por produto, ficando com o menor preço visto no ciclo")
    @SuppressWarnings("unchecked")
    void deveGravarUmaColetaPorProdutoComMenorPreco() {
        List<OfertaDescoberta> candidatos = List.of(
                oferta("MLB1", "500.00", "SELLER-A"),
                oferta("MLB1", "450.00", "SELLER-B"),
                oferta("MLB2", "99.90", null),
                oferta(null, "10.00", null),
                oferta("MLB3", "0", null)
        );

        int gravadas = service.registrarColetas(candidatos);

        ArgumentCaptor<Iterable<HistoricoPreco>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<HistoricoPreco> salvas = new ArrayList<>();
        captor.getValue().forEach(salvas::add);

        assertThat(gravadas).isEqualTo(2);
        assertThat(salvas).extracting(HistoricoPreco::getProdutoId).containsExactly("MLB1", "MLB2");
        assertThat(salvas.get(0).getPreco()).isEqualByComparingTo("450.00");
        assertThat(salvas.get(0).getVendedorId()).isEqualTo("SELLER-B");
        assertThat(salvas.get(0).getLoja()).isEqualTo(LojaEnum.MERCADO_LIVRE);
    }

    @Test
    @DisplayName("Calcula mínimo, mediana, máximo, coletas e dias distintos")
    void deveCalcularEstatisticas() {
        LocalDateTime hoje = LocalDateTime.now();
        when(repository.findByProdutoIdAndLojaAndDataColetaAfter(eq("MLB1"), eq(LojaEnum.MERCADO_LIVRE), any()))
                .thenReturn(List.of(
                        coleta("300.00", hoje),
                        coleta("100.00", hoje.minusDays(1)),
                        coleta("200.00", hoje.minusDays(1)),
                        coleta("400.00", hoje.minusDays(2))
                ));

        EstatisticaPreco estatistica = service.estatisticas("MLB1", LojaEnum.MERCADO_LIVRE, 30).orElseThrow();

        assertThat(estatistica.minimo()).isEqualByComparingTo("100.00");
        assertThat(estatistica.mediana()).isEqualByComparingTo("250.00");
        assertThat(estatistica.maximo()).isEqualByComparingTo("400.00");
        assertThat(estatistica.totalColetas()).isEqualTo(4);
        assertThat(estatistica.diasDistintos()).isEqualTo(3);
    }

    @Test
    @DisplayName("Sem coletas no período, não há estatística")
    void deveRetornarVazioSemColetas() {
        when(repository.findByProdutoIdAndLojaAndDataColetaAfter(any(), any(), any())).thenReturn(List.of());

        assertThat(service.estatisticas("MLB1", LojaEnum.MERCADO_LIVRE, 30)).isEmpty();
    }

    private OfertaDescoberta oferta(String id, String preco, String vendedor) {
        OfertaDescoberta o = new OfertaDescoberta();
        o.setMlbId(id);
        o.setPreco(new BigDecimal(preco));
        o.setVendedorId(vendedor);
        return o;
    }

    private HistoricoPreco coleta(String preco, LocalDateTime data) {
        HistoricoPreco h = new HistoricoPreco();
        h.setPreco(new BigDecimal(preco));
        h.setDataColeta(data);
        return h;
    }
}

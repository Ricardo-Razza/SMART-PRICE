package com.smart.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smart.price.entity.Vendedor;
import com.smart.price.repository.VendedorRepository;

@ExtendWith(MockitoExtension.class)
class VendedorServiceTest {

    @Mock
    private VendedorRepository vendedorRepository;

    private VendedorService service;

    @BeforeEach
    void setUp() {
        service = new VendedorService(vendedorRepository);
    }

    @Test
    @DisplayName("Nível mínimo padrão 4_light_green: aceita 4 e 5, recusa 3 e vendedor sem reputação")
    void deveAplicarNivelMinimo() {
        assertThat(service.atendeNivelMinimo(vendedor("5_green", LocalDateTime.now()))).isTrue();
        assertThat(service.atendeNivelMinimo(vendedor("4_light_green", LocalDateTime.now()))).isTrue();
        assertThat(service.atendeNivelMinimo(vendedor("3_yellow", LocalDateTime.now()))).isFalse();
        assertThat(service.atendeNivelMinimo(vendedor(null, LocalDateTime.now()))).isFalse();
    }

    @Test
    @DisplayName("Cache vale por 24h; depois disso o vendedor é consultado de novo")
    void deveRespeitarValidadeDoCache() {
        when(vendedorRepository.findById("1")).thenReturn(Optional.of(vendedor("5_green", LocalDateTime.now().minusHours(23))));
        when(vendedorRepository.findById("2")).thenReturn(Optional.of(vendedor("5_green", LocalDateTime.now().minusHours(25))));

        assertThat(service.buscarEmCache("1")).isPresent();
        assertThat(service.buscarEmCache("2")).isEmpty();
    }

    private Vendedor vendedor(String nivel, LocalDateTime atualizadoEm) {
        return new Vendedor("id", nivel, null, atualizadoEm);
    }
}

package com.smart.price.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CupomRequest {

    @NotBlank(message = "O código do cupom é obrigatório")
    private String codigo;

    private String descricao;

    private String tipoDesconto = "VALOR_FIXO"; // VALOR_FIXO ou PERCENTUAL

    @NotNull(message = "O valor do desconto é obrigatório")
    @Positive(message = "O valor do desconto deve ser maior que zero")
    private BigDecimal valorDesconto;

    private BigDecimal valorMinimoCompra;

    private Long nichoId; // Opcional: ID do nicho vinculado, ou null para cupom geral

    private String linkHotsite;

    private LocalDateTime dataInicio;

    private LocalDateTime dataExpiracao;

    private Boolean ativo = true;

    private Boolean testado = true;

    private String observacao;

    private Boolean anunciarImediatamente = false;
}

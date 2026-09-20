package com.smart.price.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smart.price.entity.Cupom;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CupomResponseDTO {

    private Long id;
    private String codigo;
    private String descricao;
    private String tipoDesconto;
    private BigDecimal valorDesconto;
    private BigDecimal valorMinimoCompra;
    private Long nichoId;
    private String nichoNome;
    private String linkHotsite;
    private LocalDateTime dataInicio;
    private LocalDateTime dataExpiracao;
    private Boolean ativo;
    private Boolean anunciadoAvulso;
    private Boolean testado;
    private String observacao;
    private LocalDateTime dataCriacao;
    private Boolean validoAgora;

    public static CupomResponseDTO fromEntity(Cupom cupom) {
        if (cupom == null) return null;
        return new CupomResponseDTO(
                cupom.getId(),
                cupom.getCodigo(),
                cupom.getDescricao(),
                cupom.getTipoDesconto(),
                cupom.getValorDesconto(),
                cupom.getValorMinimoCompra(),
                cupom.getNicho() != null ? cupom.getNicho().getId() : null,
                cupom.getNicho() != null ? cupom.getNicho().getNome() : "GERAL (Todo o site)",
                cupom.getLinkHotsite(),
                cupom.getDataInicio(),
                cupom.getDataExpiracao(),
                cupom.getAtivo(),
                cupom.getAnunciadoAvulso(),
                cupom.getTestado(),
                cupom.getObservacao(),
                cupom.getDataCriacao(),
                cupom.isValidoAgora()
        );
    }
}

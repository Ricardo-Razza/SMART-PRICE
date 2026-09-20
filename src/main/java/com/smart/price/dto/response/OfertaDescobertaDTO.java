package com.smart.price.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smart.price.entity.OfertaDescoberta;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfertaDescobertaDTO {
    private Long id;
    private String mlbId;
    private String titulo;
    private BigDecimal preco;
    private BigDecimal precoOriginal;
    private Boolean freteGratis;
    private String fotoUrl;
    private Integer descontoPercentual;
    private Boolean menorPrecoHistorico;
    private String copyIa;
    private String url;
    private String nichoNome;
    private String termoOrigem;
    private LocalDateTime dataDescoberta;
    private Integer cicloNicho;
    private LocalDateTime dataEnvioWhatsApp;
    private String statusEnvio;
    private Boolean disponivel;
    private String cupomAplicado;
    private BigDecimal precoComCupom;
    private BigDecimal descontoCupom;

    public static OfertaDescobertaDTO fromEntity(OfertaDescoberta entity) {
        if (entity == null) return null;
        return new OfertaDescobertaDTO(
                entity.getId(),
                entity.getMlbId(),
                entity.getTitulo(),
                entity.getPreco(),
                entity.getPrecoOriginal(),
                entity.getFreteGratis(),
                entity.getFotoUrl(),
                entity.getDescontoPercentual(),
                entity.getMenorPrecoHistorico(),
                entity.getCopyIa(),
                entity.getUrl(),
                entity.getNicho() != null ? entity.getNicho().getNome() : null,
                entity.getTermoOrigem(),
                entity.getDataDescoberta(),
                entity.getCicloNicho(),
                entity.getDataEnvioWhatsApp(),
                entity.getStatusEnvio(),
                entity.getDisponivel(),
                entity.getCupomAplicado(),
                entity.getPrecoComCupom(),
                entity.getDescontoCupom()
        );
    }
}

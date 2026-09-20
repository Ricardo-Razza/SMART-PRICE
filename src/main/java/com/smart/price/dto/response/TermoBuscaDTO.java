package com.smart.price.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TermoBuscaDTO {
    private Long id;
    private String termo;
    private Boolean ativo;
    private LocalDateTime dataUltimaBusca;
    private Integer totalBuscas;

    public TermoBuscaDTO(Long id, String termo, Boolean ativo) {
        this.id = id;
        this.termo = termo;
        this.ativo = ativo;
        this.totalBuscas = 0;
    }
}

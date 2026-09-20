package com.smart.price.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NichoResponseDTO {
    private Long id;
    private String nome;
    private Boolean ativo;
    private LocalDateTime dataUltimaBusca;
    private Integer totalCiclos;
    private String categoriaMlb;
    private String telegramChatId;
    private List<TermoBuscaDTO> termos;

    public NichoResponseDTO(Long id, String nome, Boolean ativo, LocalDateTime dataUltimaBusca, List<TermoBuscaDTO> termos) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
        this.dataUltimaBusca = dataUltimaBusca;
        this.totalCiclos = 0;
        this.termos = termos;
    }
}

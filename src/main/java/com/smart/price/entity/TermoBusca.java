package com.smart.price.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "termos_busca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TermoBusca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nicho_id", nullable = false)
    private Nicho nicho;

    @Column(name = "termo", nullable = false, length = 200)
    private String termo;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "data_ultima_busca")
    private LocalDateTime dataUltimaBusca;

    @Column(name = "total_buscas")
    private Integer totalBuscas = 0;

    public Integer getTotalBuscas() {
        return totalBuscas != null ? totalBuscas : 0;
    }

    public TermoBusca(Nicho nicho, String termo) {
        this.nicho = nicho;
        this.termo = termo;
        this.ativo = true;
        this.totalBuscas = 0;
    }
}

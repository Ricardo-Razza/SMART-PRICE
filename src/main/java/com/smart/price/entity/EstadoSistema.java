package com.smart.price.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pares chave/valor de estado operacional que precisa sobreviver a reinícios.
 */
@Entity
@Table(name = "estado_sistema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstadoSistema {

    @Id
    @Column(name = "chave", length = 100)
    private String chave;

    @Column(name = "valor", length = 500)
    private String valor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}

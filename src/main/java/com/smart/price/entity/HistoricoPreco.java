package com.smart.price.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smart.price.enums.LojaEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma coleta de preço de um produto em uma loja. Grava todo candidato visto no ciclo,
 * publicado ou não, para provar desconto real e o selo de menor preço em 30 dias.
 */
@Entity
@Table(name = "historico_preco", indexes = {
        @Index(name = "idx_historico_produto_loja_data", columnList = "produto_id, loja, data_coleta")
})
@Getter
@Setter
@NoArgsConstructor
public class HistoricoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", length = 50, nullable = false)
    private String produtoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loja", length = 30, nullable = false)
    private LojaEnum loja;

    @Column(name = "preco", precision = 19, scale = 2, nullable = false)
    private BigDecimal preco;

    /** Preço "De" anunciado pela loja no momento da coleta. */
    @Column(name = "preco_original", precision = 19, scale = 2)
    private BigDecimal precoOriginal;

    @Column(name = "vendedor_id", length = 50)
    private String vendedorId;

    @Column(name = "data_coleta", nullable = false)
    private LocalDateTime dataColeta;
}

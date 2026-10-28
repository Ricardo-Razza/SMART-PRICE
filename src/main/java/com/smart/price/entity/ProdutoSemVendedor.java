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
 * Produto de catálogo que não tinha vendedor ativo na última consulta. Evita repetir a chamada
 * por algumas horas e sobrevive a deploys (antes vivia num mapa em memória).
 */
@Entity
@Table(name = "produto_sem_vendedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoSemVendedor {

    @Id
    @Column(name = "produto_id", length = 50)
    private String produtoId;

    @Column(name = "data_registro", nullable = false)
    private LocalDateTime dataRegistro;
}

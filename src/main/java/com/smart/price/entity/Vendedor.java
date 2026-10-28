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
 * Cache da reputação de um vendedor do Mercado Livre (GET /users/{id}), válido por 24h.
 */
@Entity
@Table(name = "vendedor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vendedor {

    @Id
    @Column(name = "vendedor_id", length = 50)
    private String vendedorId;

    /** level_id do ML, de "1_red" a "5_green". Nulo para vendedor novo sem reputação. */
    @Column(name = "nivel_reputacao", length = 20)
    private String nivelReputacao;

    /** power_seller_status do ML: silver, gold, platinum ou nulo. */
    @Column(name = "power_seller", length = 20)
    private String powerSeller;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}

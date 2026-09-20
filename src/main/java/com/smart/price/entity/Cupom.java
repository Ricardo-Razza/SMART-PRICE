package com.smart.price.entity;

import java.math.BigDecimal;
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
@Table(name = "cupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 50, nullable = false)
    private String codigo;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "tipo_desconto", length = 20, nullable = false)
    private String tipoDesconto = "VALOR_FIXO"; // VALOR_FIXO ou PERCENTUAL

    @Column(name = "valor_desconto", precision = 19, scale = 2, nullable = false)
    private BigDecimal valorDesconto;

    @Column(name = "valor_minimo_compra", precision = 19, scale = 2)
    private BigDecimal valorMinimoCompra = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nicho_id")
    private Nicho nicho; // Opcional: Se nulo, cupom geral para qualquer nicho / site todo

    @Column(name = "link_hotsite", length = 2048)
    private String linkHotsite;

    @Column(name = "data_inicio")
    private LocalDateTime dataInicio;

    @Column(name = "data_expiracao")
    private LocalDateTime dataExpiracao;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "anunciado_avulso")
    private Boolean anunciadoAvulso = false;

    @Column(name = "testado", columnDefinition = "boolean default true")
    private Boolean testado = true; // Se o cupom foi confirmado/testado como funcional

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    /**
     * Verifica se o cupom está válido no momento atual.
     */
    public boolean isValidoAgora() {
        if (!Boolean.TRUE.equals(ativo)) {
            return false;
        }
        LocalDateTime agora = LocalDateTime.now();
        if (dataInicio != null && agora.isBefore(dataInicio)) {
            return false;
        }
        if (dataExpiracao != null && agora.isAfter(dataExpiracao)) {
            return false;
        }
        return true;
    }

    /**
     * Calcula o valor de desconto gerado pelo cupom para um determinado preço.
     */
    public BigDecimal calcularDesconto(BigDecimal preco) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (valorMinimoCompra != null && preco.compareTo(valorMinimoCompra) < 0) {
            return BigDecimal.ZERO;
        }

        if ("PERCENTUAL".equalsIgnoreCase(tipoDesconto)) {
            return preco.multiply(valorDesconto)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            // VALOR_FIXO: o desconto não pode ser maior que o próprio preço
            return valorDesconto.min(preco);
        }
    }
}

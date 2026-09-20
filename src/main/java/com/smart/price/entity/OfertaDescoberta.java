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
@Table(name = "ofertas_descobertas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class  OfertaDescoberta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mlb_id", length = 50)
    private String mlbId;

    @Column(name = "titulo", length = 500, nullable = false)
    private String titulo;

    @Column(name = "preco", precision = 19, scale = 2, nullable = false)
    private BigDecimal preco;

    @Column(name = "preco_original", precision = 19, scale = 2)
    private BigDecimal precoOriginal;

    @Column(name = "frete_gratis")
    private Boolean freteGratis = false;

    @Column(name = "foto_url", length = 1024)
    private String fotoUrl;

    @Column(name = "desconto_percentual")
    private Integer descontoPercentual = 0;

    @Column(name = "menor_preco_historico")
    private Boolean menorPrecoHistorico = false;

    @Column(name = "copy_ia", columnDefinition = "TEXT")
    private String copyIa;

    @Column(name = "url", length = 2048, nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nicho_id")
    private Nicho nicho;

    @Column(name = "termo_origem", length = 200)
    private String termoOrigem;

    @Column(name = "data_descoberta", nullable = false)
    private LocalDateTime dataDescoberta;

    @Column(name = "ciclo_nicho")
    private Integer cicloNicho;

    @Column(name = "data_envio_whatsapp")
    private LocalDateTime dataEnvioWhatsApp;

    @Column(name = "status_envio", length = 20, nullable = false)
    private String statusEnvio; // PENDENTE, ENVIADO, IGNORADO

    @Column(name = "disponivel")
    private Boolean disponivel = true;

    @Column(name = "cupom_aplicado", length = 50)
    private String cupomAplicado;

    @Column(name = "preco_com_cupom", precision = 19, scale = 2)
    private BigDecimal precoComCupom;

    @Column(name = "desconto_cupom", precision = 19, scale = 2)
    private BigDecimal descontoCupom;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "loja", length = 30)
    private com.smart.price.enums.LojaEnum loja = com.smart.price.enums.LojaEnum.MERCADO_LIVRE;

    @Column(name = "subcategoria", length = 100)
    private String subcategoria;

    @Column(name = "score_qualidade")
    private Integer scoreQualidade = 0;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "telegram_chat_id", length = 100)
    private String telegramChatId;
}

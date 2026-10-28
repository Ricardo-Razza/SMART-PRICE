package com.smart.price.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.smart.price.enums.CanalEnvio;
import com.smart.price.enums.StatusEnvio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma tentativa de publicação de oferta em um canal e destino. Cada notificador grava só a sua linha.
 */
@Entity
@Table(name = "envio")
@Getter
@Setter
@NoArgsConstructor
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo em envios de teste com oferta de demonstração não persistida. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_id")
    private OfertaDescoberta oferta;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "canal", length = 20, nullable = false)
    private CanalEnvio canal;

    @Column(name = "destino", length = 100, nullable = false)
    private String destino;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private StatusEnvio status;

    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(name = "erro", length = 500)
    private String erro;

    @Column(name = "data_envio", nullable = false)
    private LocalDateTime dataEnvio;
}

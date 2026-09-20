package com.smart.price.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nichos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class
Nicho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "data_ultima_busca")
    private LocalDateTime dataUltimaBusca;

    @Column(name = "total_ciclos")
    private Integer totalCiclos = 0;

    public Integer getTotalCiclos() {
        return totalCiclos != null ? totalCiclos : 0;
    }

    @Column(name = "categoria_mlb", length = 255)
    private String categoriaMlb;

    @Column(name = "telegram_chat_id", length = 100)
    private String telegramChatId;

    @JsonIgnore
    @OneToMany(mappedBy = "nicho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TermoBusca> termos = new ArrayList<>();

    public Nicho(String nome) {
        this.nome = nome;
        this.ativo = true;
        this.totalCiclos = 0;
    }

    public Nicho(String nome, String categoriaMlb) {
        this.nome = nome;
        this.categoriaMlb = categoriaMlb;
        this.ativo = true;
        this.totalCiclos = 0;
    }

    public Nicho(String nome, String categoriaMlb, String telegramChatId) {
        this.nome = nome;
        this.categoriaMlb = categoriaMlb;
        this.telegramChatId = telegramChatId;
        this.ativo = true;
        this.totalCiclos = 0;
    }
}

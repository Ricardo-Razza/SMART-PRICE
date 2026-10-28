package com.smart.price.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.HistoricoPreco;
import com.smart.price.enums.LojaEnum;

@Repository
public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {

    List<HistoricoPreco> findByProdutoIdAndLojaAndDataColetaAfter(String produtoId, LojaEnum loja, LocalDateTime desde);

    Optional<HistoricoPreco> findFirstByOrderByDataColetaAsc();
}

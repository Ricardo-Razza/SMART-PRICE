package com.smart.price.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.ProdutoSemVendedor;

@Repository
public interface ProdutoSemVendedorRepository extends JpaRepository<ProdutoSemVendedor, String> {

    boolean existsByProdutoIdAndDataRegistroAfter(String produtoId, LocalDateTime limite);
}

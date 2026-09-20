package com.smart.price.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.Nicho;

@Repository
public interface CupomRepository extends JpaRepository<Cupom, Long> {

    Optional<Cupom> findByCodigoIgnoreCase(String codigo);

    List<Cupom> findByAtivoTrue();

    List<Cupom> findByAtivoTrueAndAnunciadoAvulsoFalse();

    /**
     * Busca cupons ativos e vigentes elegíveis para um determinado nicho (ou gerais)
     * e cujo valor mínimo de compra seja menor ou igual ao preço do produto.
     */
    @Query("SELECT c FROM Cupom c WHERE c.ativo = true " +
           "AND (c.dataInicio IS NULL OR c.dataInicio <= :agora) " +
           "AND (c.dataExpiracao IS NULL OR c.dataExpiracao >= :agora) " +
           "AND (c.nicho = :nicho OR c.nicho IS NULL) " +
           "AND (c.valorMinimoCompra IS NULL OR c.valorMinimoCompra <= :preco)")
    List<Cupom> findCuponsElegiveis(
            @Param("nicho") Nicho nicho,
            @Param("preco") BigDecimal preco,
            @Param("agora") LocalDateTime agora);

    /**
     * Busca todos os cupons atualmente vigentes e ativos no sistema.
     */
    @Query("SELECT c FROM Cupom c WHERE c.ativo = true " +
           "AND (c.dataInicio IS NULL OR c.dataInicio <= :agora) " +
           "AND (c.dataExpiracao IS NULL OR c.dataExpiracao >= :agora) " +
           "ORDER BY c.dataCriacao DESC")
    List<Cupom> findAllValidosAgora(@Param("agora") LocalDateTime agora);
}

package com.smart.price.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.TermoBusca;

@Repository
public interface TermoBuscaRepository extends JpaRepository<TermoBusca, Long> {

    List<TermoBusca> findByNichoAndAtivoTrue(Nicho nicho);

    List<TermoBusca> findByNichoIdAndAtivoTrue(Long nichoId);

    java.util.Optional<TermoBusca> findByNichoAndTermoIgnoreCase(Nicho nicho, String termo);

    /**
     * Retorna os próximos N termos ativos para busca no nicho, ordenando:
     * 1. Termos que nunca foram buscados (dataUltimaBusca IS NULL) primeiro.
     * 2. Termos cuja última busca foi há mais tempo.
     */
    @Query("SELECT t FROM TermoBusca t WHERE t.nicho = :nicho AND t.ativo = true ORDER BY CASE WHEN t.dataUltimaBusca IS NULL THEN 0 ELSE 1 END, t.dataUltimaBusca ASC, t.id ASC")
    List<TermoBusca> findProximosTermosParaBusca(@Param("nicho") Nicho nicho, Pageable pageable);
}

package com.smart.price.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

@Repository
public interface OfertaDescobertaRepository extends JpaRepository<OfertaDescoberta, Long> {

    /**
     * Verifica se o mesmo anúncio (MLB ID) já foi descoberto recentemente
     * para evitar repetições no mesmo dia / período.
     */
    boolean existsByMlbIdAndDataDescobertaAfter(String mlbId, LocalDateTime dataLimite);

    Optional<OfertaDescoberta> findFirstByMlbIdOrderByPrecoAsc(String mlbId);

    /**
     * Busca a última ocorrência do produto no nicho para controle de intervalo mínimo de publicação.
     */
    Optional<OfertaDescoberta> findFirstByMlbIdAndNichoOrderByDataDescobertaDesc(String mlbId, Nicho nicho);

    /**
     * Busca a última ocorrência do produto no nicho (por MLB ID ou URL) para controle de repetição.
     */
    @Query("SELECT o FROM OfertaDescoberta o WHERE (o.mlbId = :mlbId OR o.url = :url) AND o.nicho = :nicho ORDER BY o.dataDescoberta DESC LIMIT 1")
    Optional<OfertaDescoberta> findUltimaPublicacaoNoNicho(
            @Param("mlbId") String mlbId,
            @Param("url") String url,
            @Param("nicho") Nicho nicho);

    /**
     * Busca a última ocorrência do produto em QUALQUER nicho dentro da janela de tempo limite
     * para garantir anti-duplicação global de 24 horas em todos os canais.
     */
    @Query("SELECT o FROM OfertaDescoberta o WHERE ((:mlbId IS NOT NULL AND o.mlbId = :mlbId) OR (:url IS NOT NULL AND (o.url = :url OR o.url LIKE CONCAT(:url, '%')))) AND o.dataDescoberta >= :limite ORDER BY o.dataDescoberta DESC LIMIT 1")
    Optional<OfertaDescoberta> findUltimaPublicacaoGlobal(
            @Param("mlbId") String mlbId,
            @Param("url") String url,
            @Param("limite") LocalDateTime limite);

    List<OfertaDescoberta> findByStatusEnvio(String statusEnvio);

    List<OfertaDescoberta> findTop20ByOrderByDataDescobertaDesc();

    List<OfertaDescoberta> findByNichoIdOrderByDataDescobertaDesc(Long nichoId);

    List<OfertaDescoberta> findByCupomAplicadoIgnoreCaseAndStatusEnvio(String cupomAplicado, String statusEnvio);
}

package com.smart.price.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Envio;
import com.smart.price.enums.CanalEnvio;
import com.smart.price.enums.StatusEnvio;

@Repository
public interface EnvioRepository extends JpaRepository<Envio, Long> {

    /**
     * Envios bem-sucedidos de ofertas ainda disponíveis que usaram o cupom informado,
     * para editar os posts quando o cupom esgota.
     */
    @Query("SELECT e FROM Envio e JOIN FETCH e.oferta o "
            + "WHERE e.canal = :canal AND e.status = :status AND e.messageId IS NOT NULL "
            + "AND UPPER(o.cupomAplicado) = UPPER(:cupom) AND (o.disponivel IS NULL OR o.disponivel = true)")
    List<Envio> findEnviosDoCupom(@Param("cupom") String cupom,
                                  @Param("canal") CanalEnvio canal,
                                  @Param("status") StatusEnvio status);

    List<Envio> findByOfertaId(Long ofertaId);
}

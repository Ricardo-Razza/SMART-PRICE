package com.smart.price.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Nicho;

@Repository
public interface NichoRepository extends JpaRepository<Nicho, Long> {

    List<Nicho> findByAtivoTrue();

    @Query("SELECT n FROM Nicho n LEFT JOIN FETCH n.termos WHERE UPPER(n.nome) = UPPER(:nome)")
    Optional<Nicho> findByNomeIgnoreCase(String nome);

    /**
     * Consulta os nichos ativos ordenando por:
     * 1. Nichos que nunca foram buscados (data_ultima_busca IS NULL) primeiro.
     * 2. Nichos com data_ultima_busca mais antiga.
     */
    @Query("SELECT n FROM Nicho n WHERE n.ativo = true ORDER BY CASE WHEN n.dataUltimaBusca IS NULL THEN 0 ELSE 1 END, n.dataUltimaBusca ASC")
    List<Nicho> findNichosOrdenadosParaBusca(Pageable pageable);

    /**
     * Método helper para retornar o próximo nicho da fila Round-Robin.
     */
    default Optional<Nicho> findProximoNichoParaBusca() {
        List<Nicho> result = findNichosOrdenadosParaBusca(org.springframework.data.domain.PageRequest.of(0, 1));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}

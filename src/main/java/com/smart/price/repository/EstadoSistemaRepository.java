package com.smart.price.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.EstadoSistema;

@Repository
public interface EstadoSistemaRepository extends JpaRepository<EstadoSistema, String> {
}

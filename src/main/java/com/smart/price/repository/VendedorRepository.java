package com.smart.price.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smart.price.entity.Vendedor;

@Repository
public interface VendedorRepository extends JpaRepository<Vendedor, String> {
}

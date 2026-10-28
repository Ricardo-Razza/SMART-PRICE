package com.smart.price.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smart.price.entity.EstadoSistema;
import com.smart.price.repository.EstadoSistemaRepository;

/**
 * Lê e grava estado operacional que precisa sobreviver a deploys (ex.: horário do último cupom avulso).
 */
@Service
public class EstadoSistemaService {

    public static final String ULTIMO_ENVIO_CUPOM_AVULSO = "cupons.ultimo-envio-avulso";

    private static final Logger logger = LoggerFactory.getLogger(EstadoSistemaService.class);

    private final EstadoSistemaRepository repository;

    public EstadoSistemaService(EstadoSistemaRepository repository) {
        this.repository = repository;
    }

    public Optional<LocalDateTime> lerDataHora(String chave) {
        try {
            return repository.findById(chave)
                    .map(EstadoSistema::getValor)
                    .filter(v -> v != null && !v.isBlank())
                    .map(LocalDateTime::parse);
        } catch (Exception ex) {
            logger.warn("EstadoSistema: Não foi possível ler [{}]: {}", chave, ex.getMessage());
            return Optional.empty();
        }
    }

    public void gravarDataHora(String chave, LocalDateTime valor) {
        try {
            repository.save(new EstadoSistema(chave, valor != null ? valor.toString() : null, LocalDateTime.now()));
        } catch (Exception ex) {
            logger.warn("EstadoSistema: Não foi possível gravar [{}]: {}", chave, ex.getMessage());
        }
    }
}

package com.smart.price.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Envio;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.CanalEnvio;
import com.smart.price.enums.StatusEnvio;
import com.smart.price.repository.EnvioRepository;

/**
 * Grava uma linha de envio por canal e destino. É o único ponto que registra resultado de publicação,
 * para que os notificadores nunca salvem a mesma oferta em paralelo.
 */
@Service
public class EnvioService {

    private static final Logger logger = LoggerFactory.getLogger(EnvioService.class);
    private static final int MAX_ERRO = 500;

    private final EnvioRepository envioRepository;
    private final AlertaTecnicoService alertaTecnicoService;

    public EnvioService(EnvioRepository envioRepository, AlertaTecnicoService alertaTecnicoService) {
        this.envioRepository = envioRepository;
        this.alertaTecnicoService = alertaTecnicoService;
    }

    public void registrarSucesso(OfertaDescoberta oferta, CanalEnvio canal, String destino, String messageId) {
        registrar(oferta, canal, destino, StatusEnvio.ENVIADO, messageId, null);
    }

    public void registrarFalha(OfertaDescoberta oferta, CanalEnvio canal, String destino, String erro) {
        registrar(oferta, canal, destino, StatusEnvio.FALHA, null, erro);
    }

    private void registrar(OfertaDescoberta oferta, CanalEnvio canal, String destino,
                           StatusEnvio status, String messageId, String erro) {
        alertaTecnicoService.registrarResultado(canal, status == StatusEnvio.ENVIADO, erro);

        try {
            Envio envio = new Envio();
            // Ofertas de demonstração (endpoint de teste) não estão no banco
            envio.setOferta(oferta != null && oferta.getId() != null ? oferta : null);
            envio.setCanal(canal);
            envio.setDestino(destino != null ? destino : "");
            envio.setStatus(status);
            envio.setMessageId(messageId);
            envio.setErro(erro != null && erro.length() > MAX_ERRO ? erro.substring(0, MAX_ERRO) : erro);
            envio.setDataEnvio(LocalDateTime.now());
            envioRepository.save(envio);
        } catch (Exception ex) {
            logger.error("EnvioService: Falha ao registrar envio {} para [{}]: {}", canal, destino, ex.getMessage());
        }
    }
}

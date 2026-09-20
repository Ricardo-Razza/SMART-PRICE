package com.smart.price.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.smart.price.entity.Cupom;
import com.smart.price.repository.CupomRepository;

@Service
public class CupomMonitorStatusService {

    private static final Logger logger = LoggerFactory.getLogger(CupomMonitorStatusService.class);

    private final CupomRepository cupomRepository;
    private final CupomService cupomService;

    @Value("${cupons.monitor.enabled:true}")
    private boolean enabled;

    @Value("${cupons.monitor.max-horas-ttl:12}")
    private int maxHorasTtl;

    public CupomMonitorStatusService(
            CupomRepository cupomRepository,
            CupomService cupomService) {
        this.cupomRepository = cupomRepository;
        this.cupomService = cupomService;
    }

    /**
     * Agendamento autônomo: verifica a validade e status de esgotamento de cupons a cada 5 minutos.
     * Totalmente silencioso quando todos os cupons continuarem válidos.
     */
    @Scheduled(cron = "${cupons.monitor.cron:0 0/5 * * * ?}")
    public void executarCicloMonitoramento() {
        if (!enabled) {
            logger.debug("CupomMonitorStatusService: Monitoramento de cupons desabilitado.");
            return;
        }

        int desativados = executarMonitoramento();
        if (desativados > 0) {
            logger.info("CupomMonitorStatusService: {} cupom(ns) esgotado(s)/expirado(s) identificado(s) e alertado(s) no Telegram!", desativados);
        } else {
            logger.debug("CupomMonitorStatusService: Ciclo finalizado. Nenhum cupom precisou ser desativado.");
        }
    }

    /**
     * Executa a varredura nos cupons ativos e desativa os que estiverem esgotados/expirados,
     * disparando o alerta imediato no Telegram.
     * Retorna a quantidade de cupons desativados.
     */
    public int executarMonitoramento() {
        List<Cupom> cuponsAtivos = cupomRepository.findByAtivoTrue();
        if (cuponsAtivos.isEmpty()) {
            return 0;
        }

        int desativados = 0;
        LocalDateTime agora = LocalDateTime.now();

        for (Cupom cupom : cuponsAtivos) {
            boolean deveDesativar = false;
            String motivo = null;

            // 1. Verificação de data de expiração oficial
            if (cupom.getDataExpiracao() != null && agora.isAfter(cupom.getDataExpiracao())) {
                deveDesativar = true;
                motivo = "Cupom expirou em " + cupom.getDataExpiracao();
            }

            // 2. Verificação por TTL máximo de segurança (cupons relâmpago do Mercado Livre)
            if (!deveDesativar && cupom.getDataCriacao() != null && maxHorasTtl > 0) {
                LocalDateTime limiteTtl = cupom.getDataCriacao().plusHours(maxHorasTtl);
                if (agora.isAfter(limiteTtl)) {
                    deveDesativar = true;
                    motivo = String.format("Tempo máximo de validade atingido (TTL %dh) em %s", maxHorasTtl, agora);
                }
            }

            if (deveDesativar) {
                boolean desativou = cupomService.desativarCupomEAlertar(cupom.getCodigo(), motivo);
                if (desativou) {
                    desativados++;
                }
            }
        }

        return desativados;
    }
}

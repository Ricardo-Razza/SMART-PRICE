package com.smart.price.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável pelo gerenciamento do Modo Noturno (Silent Hours / Anti-Unsubscribe).
 * Garante que os membros dos grupos do Telegram não recebam notificações sonoras ou spam
 * de madrugada, respeitando o horário de descanso oficial no fuso horário de Brasília
 * (America/Sao_Paulo), mesmo quando a aplicação estiver rodando em VPS com horário UTC.
 */
@Service
public class ModoNoturnoService {

    private static final Logger logger = LoggerFactory.getLogger(ModoNoturnoService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${monitoramento.modo-noturno.enabled:true}")
    private boolean enabled = true;

    @Value("${monitoramento.modo-noturno.inicio:23:00}")
    private String horaInicioStr = "23:00";

    @Value("${monitoramento.modo-noturno.fim:07:30}")
    private String horaFimStr = "07:30";

    @Value("${monitoramento.modo-noturno.fuso-horario:America/Sao_Paulo}")
    private String fusoHorario = "America/Sao_Paulo";

    @Value("${monitoramento.modo-noturno.pausar-envios:true}")
    private boolean pausarEnvios = true;

    /**
     * Verifica se o momento atual está dentro do intervalo do Modo Noturno.
     */
    public boolean isModoNoturnoAtivo() {
        if (!enabled) {
            return false;
        }

        try {
            ZoneId zoneId = ZoneId.of(fusoHorario != null && !fusoHorario.isBlank() ? fusoHorario.trim() : "America/Sao_Paulo");
            LocalTime agora = LocalTime.now(zoneId);
            return estaNoIntervalo(agora);
        } catch (Exception ex) {
            logger.error("ModoNoturnoService: Erro ao calcular horário do modo noturno: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Avalia se um determinado horário local está dentro do intervalo noturno configurado.
     */
    public boolean estaNoIntervalo(LocalTime hora) {
        if (!enabled || hora == null) {
            return false;
        }

        try {
            LocalTime inicio = LocalTime.parse(horaInicioStr.trim(), TIME_FORMATTER);
            LocalTime fim = LocalTime.parse(horaFimStr.trim(), TIME_FORMATTER);

            // Intervalo que cruza a meia-noite (ex: 23:00 às 07:30)
            if (inicio.isAfter(fim)) {
                return !hora.isBefore(inicio) || !hora.isAfter(fim);
            } else {
                return !hora.isBefore(inicio) && !hora.isAfter(fim);
            }
        } catch (Exception ex) {
            logger.error("ModoNoturnoService: Erro ao verificar intervalo ({} às {}): {}",
                    horaInicioStr, horaFimStr, ex.getMessage());
            return false;
        }
    }

    /**
     * Indica se a rotina automática deve pausar os envios durante a madrugada.
     */
    public boolean devePausarEnvios() {
        return enabled && pausarEnvios && isModoNoturnoAtivo();
    }

    /**
     * Indica se as mensagens enviadas durante o modo noturno devem ser silenciosas (sem som / sem vibração).
     */
    public boolean isEnvioSilencioso() {
        return enabled && !pausarEnvios && isModoNoturnoAtivo();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getHoraInicioStr() {
        return horaInicioStr;
    }

    public String getHoraFimStr() {
        return horaFimStr;
    }

    public String getFusoHorario() {
        return fusoHorario;
    }

    public boolean isPausarEnvios() {
        return pausarEnvios;
    }
}

package com.smart.price.service;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.smart.price.enums.CanalEnvio;
import com.smart.price.service.formatacao.TelegramHtml;

/**
 * Conta falhas seguidas de envio por canal e avisa no canal técnico do Telegram
 * (TELEGRAM_CHAT_ID_TECH) quando um canal falha N vezes seguidas, e de novo quando ele volta.
 */
@Service
public class AlertaTecnicoService {

    private static final Logger logger = LoggerFactory.getLogger(AlertaTecnicoService.class);

    private final RestTemplate restTemplate;
    private final Map<CanalEnvio, Integer> falhasSeguidas = new EnumMap<>(CanalEnvio.class);
    private final Map<CanalEnvio, Boolean> alertaAberto = new EnumMap<>(CanalEnvio.class);

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.chat-id-tech:}")
    private String chatIdTech;

    @Value("${alertas.falhas-seguidas:3}")
    private int limiteFalhas = 3;

    public AlertaTecnicoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public synchronized void registrarResultado(CanalEnvio canal, boolean sucesso, String detalhe) {
        if (canal == null) {
            return;
        }

        if (sucesso) {
            falhasSeguidas.put(canal, 0);
            if (Boolean.TRUE.equals(alertaAberto.remove(canal))) {
                enviar("✅ <b>" + canal + " voltou a funcionar.</b>");
            }
            return;
        }

        int falhas = falhasSeguidas.merge(canal, 1, Integer::sum);
        if (falhas >= limiteFalhas && !Boolean.TRUE.equals(alertaAberto.get(canal))) {
            alertaAberto.put(canal, true);
            enviar("🚨 <b>" + canal + " falhou " + falhas + " vezes seguidas.</b>\n\nÚltimo erro: <code>"
                    + TelegramHtml.escapar(detalhe != null ? detalhe : "sem detalhe") + "</code>");
        }
    }

    public int getFalhasSeguidas(CanalEnvio canal) {
        return falhasSeguidas.getOrDefault(canal, 0);
    }

    private void enviar(String html) {
        logger.warn("AlertaTecnico: {}", TelegramHtml.paraTextoPuro(html));
        if (botToken == null || botToken.isBlank() || chatIdTech == null || chatIdTech.isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "chat_id", chatIdTech.trim(),
                    "text", html,
                    "parse_mode", "HTML");
            restTemplate.postForEntity(
                    String.format("https://api.telegram.org/bot%s/sendMessage", botToken.trim()),
                    new HttpEntity<>(body, headers), Map.class);
        } catch (Exception ex) {
            logger.error("AlertaTecnico: Não foi possível avisar o canal técnico: {}", ex.getMessage());
        }
    }
}

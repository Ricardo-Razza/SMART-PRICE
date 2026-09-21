package com.smart.price.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;

@Service
public class TelegramNotificadorService implements NotificadorOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificadorService.class);

    private final RestTemplate restTemplate;
    private final com.smart.price.repository.OfertaDescobertaRepository ofertaDescobertaRepository;
    private final com.smart.price.service.provedor.ProvedorLojaHub provedorLojaHub;
    private final CopywriterIaService copywriterIaService;
    private final ModoNoturnoService modoNoturnoService;

    @Value("${telegram.bot.enabled:false}")
    private boolean enabled;

    @Value("${telegram.bot.token:${TELEGRAM_BOT_TOKEN:}}")
    private String botToken;

    @Value("${telegram.bot.chat-id:${TELEGRAM_CHAT_ID:}}")
    private String chatId;

    @Value("${afiliado.mercadolivre.tag:${ML_AFILIADO_TAG:}}")
    private String tagAfiliado;

    public TelegramNotificadorService(
            RestTemplate restTemplate,
            com.smart.price.repository.OfertaDescobertaRepository ofertaDescobertaRepository,
            com.smart.price.service.provedor.ProvedorLojaHub provedorLojaHub,
            CopywriterIaService copywriterIaService,
            ModoNoturnoService modoNoturnoService) {
        this.restTemplate = restTemplate;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.provedorLojaHub = provedorLojaHub;
        this.copywriterIaService = copywriterIaService;
        this.modoNoturnoService = modoNoturnoService;
    }

    @Override
    public void notificar(Nicho nicho, List<OfertaDescoberta> ofertas) {
        if (!enabled || botToken == null || botToken.isBlank()) {
            logger.debug("TelegramNotificador: Bot desabilitado ou credenciais não preenchidas no application.yaml.");
            return;
        }

        String chatIdDestino = (nicho != null && nicho.getTelegramChatId() != null && !nicho.getTelegramChatId().isBlank())
                ? nicho.getTelegramChatId().trim()
                : (this.chatId != null ? this.chatId.trim() : null);

        if (chatIdDestino == null || chatIdDestino.isBlank()) {
            logger.warn("TelegramNotificador: Nenhum Chat ID de destino configurado (nem no nicho nem no application.yaml).");
            return;
        }

        if (ofertas == null || ofertas.isEmpty()) {
            return;
        }

        logger.info("TelegramNotificador: Enviando {} ofertas do nicho [{}] para o canal/grupo [{}]...",
                ofertas.size(), nicho != null ? nicho.getNome() : "GERAL", chatIdDestino);

        for (OfertaDescoberta oferta : ofertas) {
            try {
                if (Boolean.FALSE.equals(oferta.getDisponivel())) {
                    logger.warn("TelegramNotificador: Oferta '{}' ignorada pois está marcada como indisponível.", oferta.getTitulo());
                    continue;
                }
                enviarOferta(oferta, chatIdDestino);
                // Pequeno intervalo de 500ms entre envios para respeitar o rate limit do Telegram
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger.error("TelegramNotificador: Erro ao enviar oferta '{}': {}", oferta.getTitulo(), ex.getMessage());
            }
        }
    }

    private void enviarOferta(OfertaDescoberta oferta, String chatIdDestino) {
        if (oferta == null || Boolean.FALSE.equals(oferta.getDisponivel()) || oferta.getUrl() == null || oferta.getUrl().isBlank()) {
            logger.warn("TelegramNotificador: Oferta nula, sem URL ou indisponível ignorada.");
            return;
        }

        String linkFinal = (provedorLojaHub != null && oferta.getLoja() != null)
                ? provedorLojaHub.formatarLinkAfiliado(oferta.getLoja(), oferta.getUrl())
                : aplicarTagAfiliado(oferta.getUrl());

        String texto = extrairTextoParaLegenda(oferta, linkFinal);

        String nomeLoja = (oferta.getLoja() != null) ? oferta.getLoja().getNomeExibicao().toUpperCase() : "MERCADO LIVRE";
        String textoBotao = "🛒 COMPRE NO " + (nomeLoja.contains("MERCADO") ? nomeLoja : "NA " + nomeLoja);

        Map<String, Object> replyMarkup = Map.of(
                "inline_keyboard", List.of(
                        List.of(
                                Map.of(
                                        "text", textoBotao,
                                        "url", linkFinal
                                )
                        )
                )
        );

        Long messageId = null;
        boolean usouFoto = false;

        // Se tiver foto válida, envia com sendPhoto
        if (oferta.getFotoUrl() != null && !oferta.getFotoUrl().isBlank()) {
            messageId = dispararSendPhoto(chatIdDestino, oferta.getFotoUrl(), texto, replyMarkup);
            if (messageId != null) {
                usouFoto = true;
            }
        }

        // Fallback: Se não tiver foto ou se o envio da foto falhar, envia mensagem de texto
        if (messageId == null) {
            messageId = dispararSendMessage(chatIdDestino, texto, replyMarkup);
        }

        if (messageId != null) {
            oferta.setStatusEnvio("ENVIADO");
            oferta.setTelegramMessageId(messageId);
            oferta.setTelegramChatId(chatIdDestino);
            oferta.setDataEnvioWhatsApp(java.time.LocalDateTime.now());
            ofertaDescobertaRepository.save(oferta);
            logger.info("TelegramNotificador: Oferta [{}] publicada com sucesso no Telegram (Message ID: {})",
                    oferta.getTitulo(), messageId);
        }
    }

    private Long dispararSendPhoto(String targetChatId, String fotoUrl, String caption, Map<String, Object> replyMarkup) {
        try {
            String endpoint = String.format("https://api.telegram.org/bot%s/sendPhoto", botToken.trim());

            // Limite de 1024 caracteres na legenda do Telegram
            String captionTruncada = caption.length() > 1020 ? caption.substring(0, 1015) + "..." : caption;

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("chat_id", targetChatId.trim());
            body.put("photo", fotoUrl.trim());
            body.put("caption", captionTruncada);
            body.put("parse_mode", "Markdown");
            if (modoNoturnoService != null && modoNoturnoService.isEnvioSilencioso()) {
                body.put("disable_notification", true);
            }
            if (replyMarkup != null && !replyMarkup.isEmpty()) {
                body.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("TelegramNotificador: Foto e oferta enviadas com sucesso para o Telegram [{}]", targetChatId);
                return extrairMessageId(response.getBody());
            }
        } catch (Exception ex) {
            logger.warn("TelegramNotificador: Falha no sendPhoto com Markdown ({}). Tentando sem parse_mode...", ex.getMessage());
            try {
                String endpoint = String.format("https://api.telegram.org/bot%s/sendPhoto", botToken.trim());
                Map<String, Object> fallbackBody = new java.util.HashMap<>();
                fallbackBody.put("chat_id", targetChatId.trim());
                fallbackBody.put("photo", fotoUrl.trim());
                fallbackBody.put("caption", caption.length() > 1020 ? caption.substring(0, 1015) + "..." : caption);
                if (modoNoturnoService != null && modoNoturnoService.isEnvioSilencioso()) {
                    fallbackBody.put("disable_notification", true);
                }
                if (replyMarkup != null && !replyMarkup.isEmpty()) {
                    fallbackBody.put("reply_markup", replyMarkup);
                }
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> retryReq = new HttpEntity<>(fallbackBody, h);
                ResponseEntity<Map> retryResp = restTemplate.postForEntity(endpoint, retryReq, Map.class);
                if (retryResp.getStatusCode().is2xxSuccessful() && retryResp.getBody() != null) {
                    logger.info("TelegramNotificador: Foto enviada com sucesso no fallback sem parse_mode.");
                    return extrairMessageId(retryResp.getBody());
                }
            } catch (Exception e2) {
                logger.warn("TelegramNotificador: Falha também sem parse_mode ({}), tentando sendMessage...", e2.getMessage());
            }
        }
        return null;
    }

    private Long dispararSendMessage(String targetChatId, String text, Map<String, Object> replyMarkup) {
        try {
            String endpoint = String.format("https://api.telegram.org/bot%s/sendMessage", botToken.trim());

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("chat_id", targetChatId.trim());
            body.put("text", text);
            body.put("parse_mode", "Markdown");
            if (modoNoturnoService != null && modoNoturnoService.isEnvioSilencioso()) {
                body.put("disable_notification", true);
            }
            if (replyMarkup != null && !replyMarkup.isEmpty()) {
                body.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("TelegramNotificador: Mensagem de texto enviada com sucesso para o Telegram [{}]", targetChatId);
                return extrairMessageId(response.getBody());
            }
        } catch (Exception ex) {
            logger.error("TelegramNotificador: Falha no sendMessage: {}", ex.getMessage());
        }
        return null;
    }

    private Long extrairMessageId(Map<?, ?> body) {
        try {
            if (body != null && body.containsKey("result")) {
                Object resultObj = body.get("result");
                if (resultObj instanceof Map<?, ?> resultMap) {
                    Object msgIdObj = resultMap.get("message_id");
                    if (msgIdObj instanceof Number num) {
                        return num.longValue();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("TelegramNotificador: Não foi possível extrair message_id da resposta: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Edita dinamicamente uma postagem no Telegram marcando-a como ESGOTADA / ENCERRADA.
     * Atualiza o texto com banner de aviso e altera o botão para status desativado.
     */
    public boolean editarMensagemOfertaEsgotada(OfertaDescoberta oferta, String motivo) {
        if (oferta == null || oferta.getTelegramMessageId() == null || oferta.getTelegramChatId() == null) {
            logger.debug("TelegramNotificador: Oferta não possui telegramMessageId registrado para edição.");
            return false;
        }

        String targetChatId = oferta.getTelegramChatId();
        Long messageId = oferta.getTelegramMessageId();

        String textoAviso = (motivo != null && !motivo.isBlank())
                ? "🛑 *ESGOTADO / ENCERRADO: " + motivo.toUpperCase() + "* 🛑\n\n"
                : "🛑 *OFERTA ENCERRADA / ESGOTADO NO MERCADO LIVRE* 🛑\n\n";

        String textoOriginal = (oferta.getCopyIa() != null && !oferta.getCopyIa().isBlank())
                ? oferta.getCopyIa()
                : ("🏷️ " + oferta.getTitulo());

        String novoTexto = textoAviso + "~" + textoOriginal + "~\n\n⚠️ _Este item esgotou ou o valor promocional foi alterado._";

        Map<String, Object> replyMarkupEsgotado = Map.of(
                "inline_keyboard", List.of(
                        List.of(
                                Map.of(
                                        "text", "❌ ESGOTADO / PROMOÇÃO ENCERRADA",
                                        "url", (oferta.getUrl() != null ? oferta.getUrl() : "https://www.mercadolivre.com.br")
                                )
                        )
                )
        );

        // Se a oferta foi enviada com foto, edita a legenda (editMessageCaption); caso contrário, edita o texto (editMessageText)
        boolean editou = false;
        if (oferta.getFotoUrl() != null && !oferta.getFotoUrl().isBlank()) {
            editou = dispararEditMessageCaption(targetChatId, messageId, novoTexto, replyMarkupEsgotado);
        }
        if (!editou) {
            editou = dispararEditMessageText(targetChatId, messageId, novoTexto, replyMarkupEsgotado);
        }

        if (editou) {
            oferta.setDisponivel(false);
            ofertaDescobertaRepository.save(oferta);
            logger.info("TelegramNotificador: Mensagem #{} do produto [{}] editada com sucesso como ESGOTADA!",
                    messageId, oferta.getTitulo());
        }

        return editou;
    }

    private boolean dispararEditMessageCaption(String targetChatId, Long messageId, String caption, Map<String, Object> replyMarkup) {
        try {
            String endpoint = String.format("https://api.telegram.org/bot%s/editMessageCaption", botToken.trim());
            String captionTruncada = caption.length() > 1020 ? caption.substring(0, 1015) + "..." : caption;

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("chat_id", targetChatId.trim());
            body.put("message_id", messageId);
            body.put("caption", captionTruncada);
            body.put("parse_mode", "Markdown");
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            logger.debug("TelegramNotificador: Falha ao editar legenda com editMessageCaption: {}", ex.getMessage());
            return false;
        }
    }

    private boolean dispararEditMessageText(String targetChatId, Long messageId, String text, Map<String, Object> replyMarkup) {
        try {
            String endpoint = String.format("https://api.telegram.org/bot%s/editMessageText", botToken.trim());
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("chat_id", targetChatId.trim());
            body.put("message_id", messageId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            logger.debug("TelegramNotificador: Falha ao editar texto com editMessageText: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Envia um anúncio exclusivo e atrativo de um novo cupom liberado para o grupo/canal do Telegram.
     */
    public boolean notificarCupomAvulso(com.smart.price.entity.Cupom cupom, String textoCopy, String linkFinal) {
        String targetChatId = (cupom != null && cupom.getNicho() != null && cupom.getNicho().getTelegramChatId() != null
                && !cupom.getNicho().getTelegramChatId().isBlank())
                ? cupom.getNicho().getTelegramChatId().trim()
                : (this.chatId != null ? this.chatId.trim() : null);

        if (!enabled || botToken == null || botToken.isBlank() || targetChatId == null || targetChatId.isBlank()) {
            logger.debug("TelegramNotificador: Bot desabilitado ou credenciais não preenchidas no application.yaml.");
            return false;
        }

        if (cupom == null || textoCopy == null || textoCopy.isBlank()) {
            return false;
        }

        String urlBotao = (linkFinal != null && !linkFinal.isBlank()) ? linkFinal : "https://www.mercadolivre.com.br";
        Map<String, Object> replyMarkup = Map.of(
                "inline_keyboard", List.of(
                        List.of(
                                Map.of(
                                        "text", "🎟️ ATIVAR CUPOM NO MERCADO LIVRE",
                                        "url", urlBotao
                                )
                        )
                )
        );

        logger.info("TelegramNotificador: Enviando anúncio avulso do cupom [{}] para o canal [{}]...",
                cupom.getCodigo(), targetChatId);

        return dispararSendMessage(targetChatId, textoCopy, replyMarkup) != null;
    }

    /**
     * Dispara alerta no canal do Telegram comunicando que um cupom esgotou / atingiu o limite.
     */
    public boolean notificarCupomEsgotado(com.smart.price.entity.Cupom cupom) {
        if (!enabled || botToken == null || botToken.isBlank()) {
            logger.debug("TelegramNotificador: Bot desabilitado ou credenciais não preenchidas no application.yaml.");
            return false;
        }

        String targetChatId = (cupom != null && cupom.getNicho() != null && cupom.getNicho().getTelegramChatId() != null
                && !cupom.getNicho().getTelegramChatId().isBlank())
                ? cupom.getNicho().getTelegramChatId().trim()
                : (this.chatId != null ? this.chatId.trim() : null);

        if (targetChatId == null || targetChatId.isBlank() || cupom == null || cupom.getCodigo() == null) {
            return false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🛑 *ALERTA DE CUPOM ESGOTADO!* 🛑\n\n");
        sb.append("O cupom `").append(cupom.getCodigo().trim()).append("` atingiu o limite de utilizações e *não está mais disponível* no checkout do Mercado Livre.\n\n");

        if (cupom.getDescricao() != null && !cupom.getDescricao().isBlank()) {
            sb.append("🏷️ *Cupom:* ").append(cupom.getDescricao()).append("\n\n");
        }

        sb.append("⚠️ *Nosso sistema já desativou este cupom* e o removeu das ofertas para que você não perca tempo tentando usá-lo.\n\n");
        sb.append("🔔 *Fique atento ao grupo:* assim que a nossa IA capturar novos cupons reais liberados, avisaremos aqui imediatamente!");

        logger.info("TelegramNotificador: Enviando alerta de cupom [{}] esgotado para o canal [{}]...",
                cupom.getCodigo(), targetChatId);

        return dispararSendMessage(targetChatId, sb.toString(), null) != null;
    }

    private String extrairTextoParaLegenda(OfertaDescoberta oferta, String linkFinal) {
        String copy = oferta.getCopyIa();

        // Se a copy for nula, vazia ou NÃO contiver o preço ("R$"), formata de forma 100% garantida
        if (copy == null || copy.isBlank() || !copy.contains("R$")) {
            logger.info("TelegramNotificador: Bloco de preços não detectado na copy de [{}]. Formatando bloco canônico completo...",
                    oferta.getTitulo());
            copy = copywriterIaService.formatarMensagemCompleta(
                    oferta,
                    (copy != null && !copy.isBlank()) ? copy : null,
                    oferta.getCupomAplicado(),
                    oferta.getPrecoComCupom(),
                    linkFinal
            );
        } else {
            // Se já tem o preço, apenas substitui a URL original pelo link de afiliado oficial
            if (linkFinal != null && !linkFinal.isBlank()) {
                if (oferta.getUrl() != null && !oferta.getUrl().isBlank()) {
                    String urlSemQuery = oferta.getUrl().split("\\?")[0].trim();
                    if (copy.contains(urlSemQuery)) {
                        copy = copy.replace(urlSemQuery, linkFinal);
                    }
                }
                if (!copy.contains(linkFinal)) {
                    String textoCurto = copywriterIaService.formatarTextoLinkCurto(linkFinal, oferta.getMlbId());
                    copy = copy + "\n\n🛒 *Compre aqui:*\n👉 [" + textoCurto + "](" + linkFinal + ")";
                }
            }
        }

        // Remove aviso de afiliados se existir na copy
        copy = copy.replace("\n\n_Participamos de programas de afiliados. Compras qualificadas podem gerar comissão sem custo adicional para você._", "");
        copy = copy.replace("_Participamos de programas de afiliados. Compras qualificadas podem gerar comissão sem custo adicional para você._", "").trim();

        return sanitizarMarkdownTelegram(copy);
    }

    /**
     * Sanitiza caracteres de formatação Markdown do Telegram para evitar erro 400 Bad Request
     * quando houver tags não fechadas.
     */
    private String sanitizarMarkdownTelegram(String texto) {
        if (texto == null) return "";
        // Balanceia asteriscos
        long asteriscos = texto.chars().filter(ch -> ch == '*').count();
        if (asteriscos % 2 != 0) {
            texto = texto + "*";
        }
        // Balanceia crases
        long crases = texto.chars().filter(ch -> ch == '`').count();
        if (crases % 2 != 0) {
            texto = texto + "`";
        }
        // Balanceia tils
        long tils = texto.chars().filter(ch -> ch == '~').count();
        if (tils % 2 != 0) {
            texto = texto + "~";
        }
        return texto;
    }

    public String aplicarTagAfiliado(String url) {
        if (url == null) return "";
        if (tagAfiliado == null || tagAfiliado.isBlank()) {
            return url;
        }

        String tag = tagAfiliado.trim();
        if (url.contains(tag)) {
            return url;
        }

        String separator = url.contains("?") ? "&" : "?";
        return url + separator + tag;
    }
}

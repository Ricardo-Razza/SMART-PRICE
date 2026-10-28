package com.smart.price.service;

import java.util.Collections;
import java.util.HashMap;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Cupom;
import com.smart.price.entity.Envio;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.CanalEnvio;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.service.formatacao.TelegramHtml;
import com.smart.price.service.provedor.ProvedorLojaHub;

@Service
public class TelegramNotificadorService implements NotificadorOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificadorService.class);

    /** Limite de legenda do sendPhoto; acima disso a oferta sai como mensagem de texto (limite 4096). */
    private static final int LIMITE_LEGENDA = 1024;

    private final RestTemplate restTemplate;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;
    private final ProvedorLojaHub provedorLojaHub;
    private final CopywriterIaService copywriterIaService;
    private final ModoNoturnoService modoNoturnoService;
    private final EnvioService envioService;

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
            OfertaDescobertaRepository ofertaDescobertaRepository,
            ProvedorLojaHub provedorLojaHub,
            CopywriterIaService copywriterIaService,
            ModoNoturnoService modoNoturnoService,
            EnvioService envioService) {
        this.restTemplate = restTemplate;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.provedorLojaHub = provedorLojaHub;
        this.copywriterIaService = copywriterIaService;
        this.modoNoturnoService = modoNoturnoService;
        this.envioService = envioService;
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
                envioService.registrarFalha(oferta, CanalEnvio.TELEGRAM, chatIdDestino, ex.getMessage());
            }
        }
    }

    private void enviarOferta(OfertaDescoberta oferta, String chatIdDestino) {
        if (oferta == null || oferta.getUrl() == null || oferta.getUrl().isBlank()) {
            logger.warn("TelegramNotificador: Oferta nula ou sem URL ignorada.");
            return;
        }

        String linkFinal = (provedorLojaHub != null && oferta.getLoja() != null)
                ? provedorLojaHub.formatarLinkAfiliado(oferta.getLoja(), oferta.getUrl())
                : aplicarTagAfiliado(oferta.getUrl());

        String html = copywriterIaService.formatarMensagemCompleta(
                oferta, null, oferta.getCupomAplicado(), oferta.getPrecoComCupom(), linkFinal, TelegramHtml.INSTANCIA);

        String nomeLoja = (oferta.getLoja() != null) ? oferta.getLoja().getNomeExibicao().toUpperCase() : "MERCADO LIVRE";
        String textoBotao = "🛒 COMPRE NO " + (nomeLoja.contains("MERCADO") ? nomeLoja : "NA " + nomeLoja);
        Map<String, Object> replyMarkup = botaoUnico(textoBotao, linkFinal);

        Long messageId = null;
        if (oferta.getFotoUrl() != null && !oferta.getFotoUrl().isBlank() && html.length() <= LIMITE_LEGENDA) {
            Map<String, Object> body = corpoBase(chatIdDestino, replyMarkup);
            body.put("photo", oferta.getFotoUrl().trim());
            messageId = extrairMessageId(postarHtml("sendPhoto", body, "caption", html));
        }

        // Sem foto, legenda longa demais ou foto recusada: envia como mensagem de texto
        if (messageId == null) {
            messageId = extrairMessageId(postarHtml("sendMessage", corpoBase(chatIdDestino, replyMarkup), "text", html));
        }

        if (messageId != null) {
            envioService.registrarSucesso(oferta, CanalEnvio.TELEGRAM, chatIdDestino, String.valueOf(messageId));
            logger.info("TelegramNotificador: Oferta [{}] publicada com sucesso no Telegram (Message ID: {})",
                    oferta.getTitulo(), messageId);
        } else {
            envioService.registrarFalha(oferta, CanalEnvio.TELEGRAM, chatIdDestino, "Telegram não aceitou sendPhoto nem sendMessage");
        }
    }

    private Map<String, Object> corpoBase(String targetChatId, Map<String, Object> replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", targetChatId.trim());
        if (modoNoturnoService != null && modoNoturnoService.isEnvioSilencioso()) {
            body.put("disable_notification", true);
        }
        if (replyMarkup != null && !replyMarkup.isEmpty()) {
            body.put("reply_markup", replyMarkup);
        }
        return body;
    }

    private Map<String, Object> botaoUnico(String texto, String url) {
        return Map.of("inline_keyboard", List.of(List.of(Map.of("text", texto, "url", url))));
    }

    /**
     * Chama um método da Bot API com parse_mode HTML. Se o Telegram recusar a marcação (400),
     * reenvia o mesmo conteúdo como texto puro para não perder o post.
     */
    private Map<?, ?> postarHtml(String metodo, Map<String, Object> body, String campoTexto, String html) {
        Map<String, Object> comHtml = new HashMap<>(body);
        comHtml.put(campoTexto, html);
        comHtml.put("parse_mode", "HTML");
        try {
            return postar(metodo, comHtml);
        } catch (HttpClientErrorException.BadRequest ex) {
            logger.warn("TelegramNotificador: {} recusou o HTML ({}). Reenviando como texto puro...", metodo, ex.getMessage());
        } catch (Exception ex) {
            logger.warn("TelegramNotificador: Falha no {}: {}", metodo, ex.getMessage());
            return null;
        }

        Map<String, Object> semFormatacao = new HashMap<>(body);
        semFormatacao.put(campoTexto, TelegramHtml.paraTextoPuro(html));
        try {
            return postar(metodo, semFormatacao);
        } catch (Exception ex) {
            logger.warn("TelegramNotificador: Falha no {} também sem formatação: {}", metodo, ex.getMessage());
            return null;
        }
    }

    private Map<?, ?> postar(String metodo, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String endpoint = String.format("https://api.telegram.org/bot%s/%s", botToken.trim(), metodo);
        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), Map.class);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    private Long extrairMessageId(Map<?, ?> body) {
        if (body != null && body.get("result") instanceof Map<?, ?> resultMap
                && resultMap.get("message_id") instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    /**
     * Edita um post já publicado marcando a oferta como ESGOTADA / ENCERRADA:
     * o texto original fica riscado e o botão passa a avisar que a promoção acabou.
     */
    public boolean editarMensagemOfertaEsgotada(Envio envio, String motivo) {
        if (!enabled || botToken == null || botToken.isBlank()
                || envio == null || envio.getOferta() == null || envio.getMessageId() == null) {
            return false;
        }

        OfertaDescoberta oferta = envio.getOferta();
        Long messageId;
        try {
            messageId = Long.valueOf(envio.getMessageId());
        } catch (NumberFormatException ex) {
            logger.warn("TelegramNotificador: message_id inválido no envio #{}: {}", envio.getId(), envio.getMessageId());
            return false;
        }

        String aviso = (motivo != null && !motivo.isBlank())
                ? "ESGOTADO / ENCERRADO: " + motivo.toUpperCase()
                : "OFERTA ENCERRADA / ESGOTADO NO MERCADO LIVRE";

        String original = copywriterIaService.formatarMensagemCompleta(
                oferta, null, oferta.getCupomAplicado(), oferta.getPrecoComCupom(), oferta.getUrl(), TelegramHtml.INSTANCIA);

        String html = "🛑 " + TelegramHtml.INSTANCIA.negrito(aviso) + " 🛑\n\n<s>" + original + "</s>\n\n⚠️ "
                + TelegramHtml.INSTANCIA.italico("Este item esgotou ou o valor promocional foi alterado.");

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", envio.getDestino());
        body.put("message_id", messageId);
        body.put("reply_markup", botaoUnico("❌ ESGOTADO / PROMOÇÃO ENCERRADA",
                oferta.getUrl() != null ? oferta.getUrl() : "https://www.mercadolivre.com.br"));

        // Post com foto tem legenda (editMessageCaption); post de texto usa editMessageText
        boolean editou = false;
        if (oferta.getFotoUrl() != null && !oferta.getFotoUrl().isBlank() && html.length() <= LIMITE_LEGENDA) {
            editou = postarHtml("editMessageCaption", body, "caption", html) != null;
        }
        if (!editou) {
            editou = postarHtml("editMessageText", body, "text", html) != null;
        }

        if (editou) {
            oferta.setDisponivel(false);
            ofertaDescobertaRepository.save(oferta);
            logger.info("TelegramNotificador: Mensagem #{} do produto [{}] editada com sucesso como ESGOTADA!",
                    messageId, oferta.getTitulo());
        }
        return editou;
    }

    /**
     * Envia um anúncio de cupom (texto já em HTML do Telegram) para o grupo/canal.
     */
    public boolean notificarCupomAvulso(Cupom cupom, String html, String linkFinal) {
        String targetChatId = destinoDoCupom(cupom);

        if (!enabled || botToken == null || botToken.isBlank() || targetChatId == null || targetChatId.isBlank()) {
            logger.debug("TelegramNotificador: Bot desabilitado ou credenciais não preenchidas no application.yaml.");
            return false;
        }

        if (cupom == null || html == null || html.isBlank()) {
            return false;
        }

        String urlBotao = (linkFinal != null && !linkFinal.isBlank()) ? linkFinal : "https://www.mercadolivre.com.br";
        logger.info("TelegramNotificador: Enviando anúncio avulso do cupom [{}] para o canal [{}]...",
                cupom.getCodigo(), targetChatId);

        Map<String, Object> body = corpoBase(targetChatId, botaoUnico("🎟️ ATIVAR CUPOM NO MERCADO LIVRE", urlBotao));
        return extrairMessageId(postarHtml("sendMessage", body, "text", html)) != null;
    }

    /**
     * Dispara alerta no canal do Telegram comunicando que um cupom esgotou / atingiu o limite.
     */
    public boolean notificarCupomEsgotado(Cupom cupom) {
        if (!enabled || botToken == null || botToken.isBlank()) {
            logger.debug("TelegramNotificador: Bot desabilitado ou credenciais não preenchidas no application.yaml.");
            return false;
        }

        String targetChatId = destinoDoCupom(cupom);
        if (targetChatId == null || targetChatId.isBlank() || cupom == null || cupom.getCodigo() == null) {
            return false;
        }

        TelegramHtml f = TelegramHtml.INSTANCIA;
        StringBuilder sb = new StringBuilder();
        sb.append("🛑 ").append(f.negrito("ALERTA DE CUPOM ESGOTADO!")).append(" 🛑\n\n");
        sb.append("O cupom ").append(f.codigo(cupom.getCodigo().trim()))
                .append(" atingiu o limite de utilizações e ").append(f.negrito("não está mais disponível"))
                .append(" no checkout do Mercado Livre.\n\n");

        if (cupom.getDescricao() != null && !cupom.getDescricao().isBlank()) {
            sb.append("🏷️ ").append(f.negrito("Cupom:")).append(" ").append(f.texto(cupom.getDescricao())).append("\n\n");
        }

        sb.append("⚠️ ").append(f.negrito("Nosso sistema já desativou este cupom"))
                .append(" e o removeu das ofertas para que você não perca tempo tentando usá-lo.\n\n");
        sb.append("🔔 ").append(f.negrito("Fique atento ao grupo:"))
                .append(" assim que a nossa IA capturar novos cupons reais liberados, avisaremos aqui imediatamente!");

        logger.info("TelegramNotificador: Enviando alerta de cupom [{}] esgotado para o canal [{}]...",
                cupom.getCodigo(), targetChatId);

        return extrairMessageId(postarHtml("sendMessage", corpoBase(targetChatId, null), "text", sb.toString())) != null;
    }

    private String destinoDoCupom(Cupom cupom) {
        if (cupom != null && cupom.getNicho() != null && cupom.getNicho().getTelegramChatId() != null
                && !cupom.getNicho().getTelegramChatId().isBlank()) {
            return cupom.getNicho().getTelegramChatId().trim();
        }
        return this.chatId != null ? this.chatId.trim() : null;
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

package com.smart.price.service;

import java.time.LocalDateTime;
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
import org.springframework.web.client.RestTemplate;

import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.service.provedor.ProvedorLojaHub;

/**
 * Serviço responsável pelo envio de notificações de ofertas e cupons para grupos de WhatsApp
 * utilizando a Evolution API v2 com suporte a múltiplos grupos e enquadramento inteligente de imagem.
 */
@Service
public class WhatsAppNotificadorService implements NotificadorOfertasService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppNotificadorService.class);

    private static final java.util.regex.Pattern SEPARADOR_GRUPOS = java.util.regex.Pattern.compile("[,;\\s]+");

    private final RestTemplate restTemplate;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;
    private final ProvedorLojaHub provedorLojaHub;
    private final CopywriterIaService copywriterIaService;
    private final ModoNoturnoService modoNoturnoService;
    private final WhatsAppImagemFormatterService imagemFormatterService;

    @Value("${whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${whatsapp.api-url:${WHATSAPP_API_URL:http://localhost:8081}}")
    private String apiUrl;

    @Value("${whatsapp.api-key:${WHATSAPP_API_KEY:smartprice_whatsapp_secret_key}}")
    private String apiKey;

    @Value("${whatsapp.instance-name:${WHATSAPP_INSTANCE_NAME:smartprice}}")
    private String instanceName;

    @Value("${whatsapp.group-id:${WHATSAPP_GROUP_ID:}}")
    private String defaultGroupId;

    @Value("${afiliado.mercadolivre.tag:${ML_AFILIADO_TAG:}}")
    private String tagAfiliado;

    public WhatsAppNotificadorService(
            RestTemplate restTemplate,
            OfertaDescobertaRepository ofertaDescobertaRepository,
            ProvedorLojaHub provedorLojaHub,
            CopywriterIaService copywriterIaService,
            ModoNoturnoService modoNoturnoService,
            WhatsAppImagemFormatterService imagemFormatterService) {
        this.restTemplate = restTemplate;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.provedorLojaHub = provedorLojaHub;
        this.copywriterIaService = copywriterIaService;
        this.modoNoturnoService = modoNoturnoService;
        this.imagemFormatterService = imagemFormatterService;
    }

    @Override
    public void notificar(Nicho nicho, List<OfertaDescoberta> ofertas) {
        if (!enabled) {
            logger.debug("WhatsAppNotificador: Serviço desabilitado nas configurações.");
            return;
        }

        if (modoNoturnoService != null && modoNoturnoService.devePausarEnvios()) {
            logger.info("WhatsAppNotificador: Modo Noturno ativo. Envios pausados para respeitar o descanso dos membros.");
            return;
        }

        List<String> gruposDestino = extrairGruposDestino(nicho);
        if (gruposDestino.isEmpty()) {
            logger.warn("WhatsAppNotificador: Nenhum Group ID válido configurado (nem no nicho nem no application.yaml).");
            return;
        }

        if (ofertas == null || ofertas.isEmpty()) {
            return;
        }

        logger.info("WhatsAppNotificador: Enviando {} ofertas do nicho [{}] para {} grupo(s) WhatsApp: {}",
                ofertas.size(), nicho != null ? nicho.getNome() : "GERAL", gruposDestino.size(), gruposDestino);

        for (OfertaDescoberta oferta : ofertas) {
            try {
                if (Boolean.FALSE.equals(oferta.getDisponivel())) {
                    continue;
                }

                for (String groupId : gruposDestino) {
                    enviarOferta(oferta, groupId);
                    if (gruposDestino.size() > 1) {
                        Thread.sleep(800); // Intervalo de segurança anti-ban entre grupos
                    }
                }

                // Intervalo de segurança anti-ban entre ofertas (1500ms)
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger.error("WhatsAppNotificador: Erro ao enviar oferta '{}': {}", oferta.getTitulo(), ex.getMessage());
            }
        }
    }

    /**
     * Extrai a lista de grupos de destino a partir da configuração do nicho ou do padrão geral.
     * Suporta múltiplos IDs separados por vírgula, ponto e vírgula ou espaço.
     */
    public List<String> extrairGruposDestino(Nicho nicho) {
        String rawConfig = (nicho != null && nicho.getWhatsappGroupId() != null && !nicho.getWhatsappGroupId().isBlank())
                ? nicho.getWhatsappGroupId()
                : this.defaultGroupId;

        if (rawConfig == null || rawConfig.isBlank()) {
            return Collections.emptyList();
        }

        return SEPARADOR_GRUPOS.splitAsStream(rawConfig)
                .map(String::trim)
                .filter(s -> !s.isBlank() && s.contains("@g.us"))
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean enviarOferta(OfertaDescoberta oferta, String groupIdDestino) {
        if (oferta == null || Boolean.FALSE.equals(oferta.getDisponivel()) || oferta.getUrl() == null || oferta.getUrl().isBlank()) {
            return false;
        }

        String linkFinal = (provedorLojaHub != null && oferta.getLoja() != null)
                ? provedorLojaHub.formatarLinkAfiliado(oferta.getLoja(), oferta.getUrl())
                : aplicarTagAfiliado(oferta.getUrl());

        String texto = formatarTextoWhatsApp(oferta, linkFinal);

        boolean enviou = false;
        if (oferta.getFotoUrl() != null && !oferta.getFotoUrl().isBlank()) {
            // Enquadra a foto em canvas quadrado 1:1 com bordas e respiro para caber perfeitamente no card do WhatsApp
            String base64Enquadrado = imagemFormatterService != null
                    ? imagemFormatterService.formatarImagemParaWhatsApp(oferta.getFotoUrl())
                    : null;

            String mediaEnvio = (base64Enquadrado != null && !base64Enquadrado.isBlank())
                    ? base64Enquadrado
                    : oferta.getFotoUrl();

            enviou = dispararSendMedia(groupIdDestino, mediaEnvio, texto);
        }

        if (!enviou) {
            enviou = dispararSendText(groupIdDestino, texto);
        }

        if (enviou) {
            oferta.setDataEnvioWhatsApp(LocalDateTime.now());
            oferta.setStatusEnvio("ENVIADO");
            ofertaDescobertaRepository.save(oferta);
            logger.info("WhatsAppNotificador: Oferta [{}] enviada com sucesso para o WhatsApp [{}]",
                    oferta.getTitulo(), groupIdDestino);
        }
        return enviou;
    }

    /**
     * Envia imagem com legenda para a Evolution API.
     * Suporta tanto URLs públicas quanto imagens em Base64 pré-enquadradas.
     * Endpoint: POST /message/sendMedia/{instance}
     */
    private boolean dispararSendMedia(String targetGroupId, String mediaData, String caption) {
        try {
            String endpoint = String.format("%s/message/sendMedia/%s", apiUrl.replaceAll("/+$", ""), instanceName.trim());

            Map<String, Object> body = new HashMap<>();
            body.put("number", targetGroupId.trim());
            body.put("mediatype", "image");
            body.put("mimetype", "image/jpeg");
            body.put("caption", caption);
            body.put("media", mediaData.trim());
            body.put("fileName", "oferta.jpg");

            // Suporte retrocompatível com instâncias que utilizam mediaMessage aninhado
            Map<String, Object> mediaMessage = new HashMap<>();
            mediaMessage.put("mediatype", "image");
            mediaMessage.put("media", mediaData.trim());
            mediaMessage.put("caption", caption);
            body.put("mediaMessage", mediaMessage);

            HttpHeaders headers = criarHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            logger.warn("WhatsAppNotificador: Falha no sendMedia ({}), tentando sendText...", ex.getMessage());
            return false;
        }
    }

    /**
     * Envia mensagem de texto simples para a Evolution API.
     * Endpoint: POST /message/sendText/{instance}
     */
    public boolean dispararSendText(String targetGroupId, String text) {
        try {
            String endpoint = String.format("%s/message/sendText/%s", apiUrl.replaceAll("/+$", ""), instanceName.trim());

            Map<String, Object> body = new HashMap<>();
            body.put("number", targetGroupId.trim());
            body.put("text", text);

            // Suporte retrocompatível com instâncias que utilizam textMessage aninhado
            Map<String, Object> textMessage = new HashMap<>();
            textMessage.put("text", text);
            body.put("textMessage", textMessage);

            HttpHeaders headers = criarHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            logger.error("WhatsAppNotificador: Falha no sendText para o WhatsApp: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Consulta o status de conexão da instância do WhatsApp na Evolution API.
     * Endpoint: GET /instance/connectionState/{instance}
     */
    public Map<String, Object> consultarStatusConexao() {
        if (!enabled) {
            return Map.of(
                    "status", "DISABLED",
                    "conectado", false,
                    "mensagem", "WhatsApp está desabilitado no application.yaml (whatsapp.enabled=false)"
            );
        }
        try {
            String endpoint = String.format("%s/instance/connectionState/%s", apiUrl.replaceAll("/+$", ""), instanceName.trim());
            HttpHeaders headers = criarHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(endpoint, org.springframework.http.HttpMethod.GET, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                Object instanceObj = body.get("instance");
                String state = "UNKNOWN";
                if (instanceObj instanceof Map<?, ?> instMap && instMap.get("state") != null) {
                    state = instMap.get("state").toString().toUpperCase();
                } else if (body.get("state") != null) {
                    state = body.get("state").toString().toUpperCase();
                }

                boolean conectado = "OPEN".equalsIgnoreCase(state) || "CONNECTED".equalsIgnoreCase(state);
                Map<String, Object> resultado = new HashMap<>();
                resultado.put("status", conectado ? "CONNECTED" : ("CLOSE".equalsIgnoreCase(state) ? "DISCONNECTED" : state));
                resultado.put("conectado", conectado);
                resultado.put("instancia", instanceName);
                resultado.put("groupIdPadrao", defaultGroupId);
                resultado.put("detalhes", body);
                return resultado;
            }
        } catch (Exception ex) {
            logger.error("WhatsAppNotificador: Erro ao consultar status da instância: {}", ex.getMessage());
            return Map.of(
                    "status", "DISCONNECTED",
                    "conectado", false,
                    "erro", ex.getMessage() != null ? ex.getMessage() : "Falha na comunicação com Evolution API",
                    "instancia", instanceName
            );
        }
        return Map.of("status", "UNKNOWN", "conectado", false);
    }

    /**
     * Solicita QR Code ou dados de conexão da instância na Evolution API.
     * Endpoint: GET /instance/connect/{instance}
     */
    public Map<String, Object> obterQrCodeOuConexao() {
        if (!enabled) {
            return Map.of("status", "DISABLED", "mensagem", "WhatsApp está desabilitado no application.yaml");
        }
        try {
            String endpoint = String.format("%s/instance/connect/%s", apiUrl.replaceAll("/+$", ""), instanceName.trim());
            HttpHeaders headers = criarHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(endpoint, org.springframework.http.HttpMethod.GET, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            logger.error("WhatsAppNotificador: Erro ao conectar/obter QR Code: {}", ex.getMessage());
            return Map.of("erro", ex.getMessage() != null ? ex.getMessage() : "Erro ao requisitar conexão");
        }
        return Map.of("mensagem", "Nenhum retorno recebido da Evolution API.");
    }

    /**
     * Lista todos os grupos onde a instância conectada participa.
     * Endpoint: GET /group/fetchAllGroups/{instance}?getParticipants=false
     */
    public List<Map<String, Object>> listarGrupos() {
        if (!enabled) {
            return Collections.emptyList();
        }
        try {
            String endpoint = String.format("%s/group/fetchAllGroups/%s?getParticipants=false", apiUrl.replaceAll("/+$", ""), instanceName.trim());
            HttpHeaders headers = criarHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(endpoint, org.springframework.http.HttpMethod.GET, request, List.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<?> rawList = response.getBody();
                List<Map<String, Object>> grupos = new java.util.ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> grupo = new HashMap<>();
                        grupo.put("id", m.get("id")); // Ex: 120363028471928374@g.us
                        grupo.put("nome", m.get("subject") != null ? m.get("subject") : m.get("name"));
                        grupo.put("size", m.get("size"));
                        grupos.add(grupo);
                    }
                }
                return grupos;
            }
        } catch (Exception ex) {
            logger.error("WhatsAppNotificador: Erro ao listar grupos do WhatsApp: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    private HttpHeaders criarHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("apikey", apiKey.trim());
        }
        return headers;
    }

    private String formatarTextoWhatsApp(OfertaDescoberta oferta, String linkFinal) {
        String texto = copywriterIaService.formatarMensagemCompleta(
                oferta,
                null,
                oferta.getCupomAplicado(),
                oferta.getPrecoComCupom(),
                linkFinal
        );

        // O WhatsApp não suporta hyperlinks markdown com texto [texto](url) como o Telegram.
        // Ele precisa da URL explícita para gerar o preview clicável.
        if (texto.contains("](") && linkFinal != null && !linkFinal.isBlank()) {
            texto = texto.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", linkFinal);
        }

        return texto;
    }

    public String aplicarTagAfiliado(String urlOriginal) {
        if (urlOriginal == null || urlOriginal.isBlank()) {
            return "";
        }
        if (tagAfiliado == null || tagAfiliado.isBlank()) {
            return urlOriginal;
        }
        if (urlOriginal.contains("matt_tool") || urlOriginal.contains("matt_word")) {
            return urlOriginal;
        }
        String sep = urlOriginal.contains("?") ? "&" : "?";
        return urlOriginal + sep + tagAfiliado;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDefaultGroupId() {
        return defaultGroupId;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getApiUrl() {
        return apiUrl;
    }
}

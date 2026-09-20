package com.smart.price.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class MercadoLivreAuthService {

    private static final Logger logger = LoggerFactory.getLogger(MercadoLivreAuthService.class);

    private final RestTemplate restTemplate;

    @Value("${mercadolivre.api.client-id:${ML_CLIENT_ID:}}")
    private String clientId;

    @Value("${mercadolivre.api.client-secret:${ML_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${mercadolivre.api.auth-url:https://api.mercadolibre.com/oauth/token}")
    private String authUrl;

    @Value("${mercadolivre.api.access-token:${MERCADO_LIVRE_ACCESS_TOKEN:}}")
    private String staticAccessToken;

    private String cachedToken;
    private LocalDateTime tokenExpiration;

    public MercadoLivreAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Obtém um Access Token válido.
     * Caso o usuário tenha configurado um token estático, utiliza ele.
     * Caso tenha configurado Client ID e Client Secret, renova automaticamente via OAuth 2.0.
     */
    public synchronized String obterAccessToken() {
        // Se houver um token estático configurado no application.yaml e não houver Client Secret
        if (staticAccessToken != null && !staticAccessToken.isBlank() && (clientSecret == null || clientSecret.isBlank())) {
            return staticAccessToken.trim();
        }

        // Verifica se o token em cache ainda é válido (com margem de 5 minutos de segurança)
        if (cachedToken != null && tokenExpiration != null && LocalDateTime.now().isBefore(tokenExpiration.minusMinutes(5))) {
            return cachedToken;
        }

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            if (staticAccessToken != null && !staticAccessToken.isBlank()) {
                return staticAccessToken.trim();
            }
            logger.warn("MercadoLivreAuthService: Nem Client ID/Secret nem Access Token foram configurados.");
            return null;
        }

        try {
            logger.info("MercadoLivreAuthService: Gerando novo Access Token via OAuth 2.0 (Client Credentials) para App ID: {}", clientId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId.trim());
            body.add("client_secret", clientSecret.trim());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(authUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> respBody = response.getBody();
                this.cachedToken = (String) respBody.get("access_token");
                Object exp = respBody.get("expires_in");
                long expiresIn = (exp instanceof Number n) ? n.longValue() : 21600L;
                this.tokenExpiration = LocalDateTime.now().plusSeconds(expiresIn);

                logger.info("MercadoLivreAuthService: Token OAuth gerado com sucesso! Expira em {} segundos ({}).",
                        expiresIn, this.tokenExpiration);
                return this.cachedToken;
            }

            logger.warn("MercadoLivreAuthService: Falha ao gerar token. Status HTTP: {}", response.getStatusCode());

        } catch (Exception ex) {
            logger.error("MercadoLivreAuthService: Erro ao solicitar token OAuth no Mercado Livre: {}", ex.getMessage(), ex);
        }

        return cachedToken != null ? cachedToken : staticAccessToken;
    }
}

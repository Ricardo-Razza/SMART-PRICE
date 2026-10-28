package com.smart.price.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige o header X-API-Key em todas as rotas da API.
 * Sem chave configurada (API_KEY vazio), a API recusa tudo: falha fechada, nunca aberta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyFilter.class);
    public static final String HEADER = "X-API-Key";

    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${seguranca.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? new byte[0] : apiKey.trim().getBytes(StandardCharsets.UTF_8);
        if (this.apiKey.length == 0) {
            logger.warn("ApiKeyFilter: API_KEY não configurada. Todas as rotas da API vão responder 401.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Swagger só existe fora do perfil prod; a UI precisa carregar sem header para pedir a chave.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebida = request.getHeader(HEADER);
        if (apiKey.length > 0 && recebida != null
                && MessageDigest.isEqual(apiKey, recebida.trim().getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"sucesso\":false,\"mensagem\":\"Header X-API-Key ausente ou inválido.\"}");
    }
}

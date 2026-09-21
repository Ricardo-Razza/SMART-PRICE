package com.smart.price.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por formatar e enquadrar imagens de produtos para o WhatsApp.
 * 
 * Problema resolvido: O WhatsApp corta produtos altos/retangulares nos cards de mensagem.
 * Solução: Enquadra a foto em um canvas quadrado 1:1 (800x800) com fundo branco e margem
 * de respiro de 12% a 15%, garantindo que nenhum detalhe (ex: tampas, bordas) seja cortado.
 * 
 * Otimizado com cache LRU em memória e timeouts estritos de rede para evitar travamentos.
 */
@Service
public class WhatsAppImagemFormatterService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppImagemFormatterService.class);

    private static final int TARGET_SIZE = 800; // Resolução ideal 1:1 para cards do WhatsApp
    private static final double PADDING_FACTOR = 0.12; // 12% de margem em cada borda (produto ocupa ~76% da área)
    private static final int NETWORK_TIMEOUT_MS = 3500; // Timeout de conexão e leitura HTTP

    // Cache LRU em memória para até 60 imagens recentes, evitando reprocessar a mesma foto para múltiplos grupos
    private static final int MAX_CACHE_ENTRIES = 60;
    private final Map<String, String> cacheImagens = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    /**
     * Baixa a imagem da oferta e a enquadra em um canvas quadrado (1:1) com fundo branco
     * e respiro nas bordas.
     *
     * @param fotoUrl URL pública da imagem do produto
     * @return String em Base64 pronta para envio ou null caso ocorra erro (para fallback)
     */
    public String formatarImagemParaWhatsApp(String fotoUrl) {
        if (fotoUrl == null || fotoUrl.isBlank()) {
            return null;
        }

        String urlLimpa = fotoUrl.trim();
        String cached = cacheImagens.get(urlLimpa);
        if (cached != null) {
            return cached;
        }

        try {
            URI uri = URI.create(urlLimpa);
            URLConnection conn = uri.toURL().openConnection();
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);

            BufferedImage imagemOriginal;
            try (InputStream in = conn.getInputStream()) {
                imagemOriginal = ImageIO.read(in);
            }

            if (imagemOriginal == null) {
                logger.warn("WhatsAppImagemFormatter: Não foi possível decodificar imagem da URL: {}", fotoUrl);
                return null;
            }

            int origWidth = imagemOriginal.getWidth();
            int origHeight = imagemOriginal.getHeight();

            if (origWidth <= 0 || origHeight <= 0) {
                return null;
            }

            // Canvas quadrado branco
            BufferedImage canvasQuadrado = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = canvasQuadrado.createGraphics();

            try {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, TARGET_SIZE, TARGET_SIZE);

                // Anti-aliasing e renderização bilinear de alta qualidade
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Área útil calculada para deixar a margem de respiro pedida
                int areaUtil = (int) Math.round(TARGET_SIZE * (1.0 - (PADDING_FACTOR * 2)));

                // Calcula fator de escala mantendo a proporção original do produto
                double scale = Math.min((double) areaUtil / origWidth, (double) areaUtil / origHeight);
                int scaledWidth = (int) Math.round(origWidth * scale);
                int scaledHeight = (int) Math.round(origHeight * scale);

                // Centraliza no canvas 800x800
                int x = (TARGET_SIZE - scaledWidth) / 2;
                int y = (TARGET_SIZE - scaledHeight) / 2;

                g2d.drawImage(imagemOriginal, x, y, scaledWidth, scaledHeight, null);
            } finally {
                g2d.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(48 * 1024);
            ImageIO.write(canvasQuadrado, "jpg", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            cacheImagens.put(urlLimpa, base64);
            return base64;

        } catch (Exception ex) {
            logger.warn("WhatsAppImagemFormatter: Falha ao enquadrar imagem da URL '{}': {}. Usando fallback para URL direta.",
                    fotoUrl, ex.getMessage());
            return null;
        }
    }
}

package com.smart.price.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.enums.LojaEnum;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.service.WhatsAppNotificadorService;

/**
 * Controller REST para gerenciamento e diagnóstico da integração com o WhatsApp via Evolution API.
 */
@RestController
@RequestMapping("/whatsapp")
public class WhatsAppController {

    private final WhatsAppNotificadorService whatsAppService;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;

    public WhatsAppController(WhatsAppNotificadorService whatsAppService, OfertaDescobertaRepository ofertaDescobertaRepository) {
        this.whatsAppService = whatsAppService;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
    }

    /**
     * Consulta se a instância do WhatsApp está conectada (CONNECTED/DISCONNECTED).
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> obterStatus() {
        Map<String, Object> status = whatsAppService.consultarStatusConexao();
        return ResponseEntity.ok(status);
    }

    /**
     * Lista todos os grupos onde o número/instância está presente, exibindo o JID (group-id) e o nome.
     */
    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> listarGrupos() {
        List<Map<String, Object>> grupos = whatsAppService.listarGrupos();
        return ResponseEntity.ok(grupos);
    }

    /**
     * Dispara uma mensagem de teste para o grupo do WhatsApp configurado ou informado no body.
     */
    @PostMapping("/testar-envio")
    public ResponseEntity<Map<String, Object>> testarEnvio(@RequestBody(required = false) Map<String, String> body) {
        String targetGroupId = (body != null && body.containsKey("groupId") && !body.get("groupId").isBlank())
                ? body.get("groupId").trim()
                : whatsAppService.getDefaultGroupId();

        if (targetGroupId == null || targetGroupId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "mensagem", "Nenhum groupId foi informado nem configurado no application.yaml (whatsapp.group-id)."
            ));
        }

        String mensagem = (body != null && body.containsKey("mensagem") && !body.get("mensagem").isBlank())
                ? body.get("mensagem")
                : "🚀 *Smart Price Monitor*\n\nTeste de integração com WhatsApp realizado com sucesso!\nCanal de ofertas e cupons pronto para publicação automática.";

        boolean enviado = whatsAppService.dispararSendText(targetGroupId, mensagem);
        if (enviado) {
            return ResponseEntity.ok(Map.of(
                    "sucesso", true,
                    "mensagem", "Mensagem de teste disparada com sucesso para o WhatsApp!",
                    "groupId", targetGroupId
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "sucesso", false,
                    "mensagem", "Falha ao enviar mensagem de teste. Verifique se a instância está conectada e se o groupId existe.",
                    "groupId", targetGroupId
            ));
        }
    }

    /**
     * Dispara uma oferta real de teste com imagem enquadrada 1:1 e link canônico do Mercado Livre para o WhatsApp.
     */
    @PostMapping("/testar-oferta")
    public ResponseEntity<Map<String, Object>> testarOferta(@RequestBody(required = false) Map<String, String> body) {
        String targetGroupId = (body != null && body.containsKey("groupId") && !body.get("groupId").isBlank())
                ? body.get("groupId").trim()
                : whatsAppService.getDefaultGroupId();

        if (targetGroupId == null || targetGroupId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "mensagem", "Nenhum groupId foi informado nem configurado no application.yaml (whatsapp.group-id)."
            ));
        }

        // Obtém uma oferta recente do banco ou gera uma demonstração rica do Mercado Livre
        OfertaDescoberta oferta = null;
        if (ofertaDescobertaRepository != null) {
            List<OfertaDescoberta> recentes = ofertaDescobertaRepository.findTop20ByOrderByDataDescobertaDesc();
            if (!recentes.isEmpty()) {
                oferta = recentes.get(0);
            }
        }

        if (oferta == null) {
            oferta = new OfertaDescoberta();
            oferta.setMlbId("MLB24525624");
            oferta.setTitulo("Fritadeira Sem Óleo Air Fryer Philips Walita Série 3000 4.1L 1400W");
            oferta.setPreco(new BigDecimal("349.90"));
            oferta.setPrecoOriginal(new BigDecimal("499.90"));
            oferta.setDescontoPercentual(30);
            oferta.setCupomAplicado("PROMO30");
            oferta.setPrecoComCupom(new BigDecimal("319.90"));
            oferta.setFreteGratis(Boolean.TRUE);
            oferta.setFotoUrl("http://http2.mlstatic.com/D_674519-MLU74274944983_012024-O.jpg");
            oferta.setUrl("https://produto.mercadolivre.com.br/MLB-24525624");
            oferta.setLoja(LojaEnum.MERCADO_LIVRE);
            oferta.setDisponivel(Boolean.TRUE);
        }

        boolean enviado = whatsAppService.enviarOferta(oferta, targetGroupId);
        if (enviado) {
            return ResponseEntity.ok(Map.of(
                    "sucesso", true,
                    "mensagem", "Oferta com foto enquadrada e link oficial do Mercado Livre enviada com sucesso!",
                    "produto", oferta.getTitulo(),
                    "groupId", targetGroupId
            ));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "sucesso", false,
                    "mensagem", "Falha ao enviar oferta para o WhatsApp. Verifique o status da conexão da instância.",
                    "groupId", targetGroupId
            ));
        }
    }

    /**
     * Endpoint facilitador para exibir dados de pareamento/QR Code gerados pela Evolution API.
     */
    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> obterQrCode() {
        Map<String, Object> dados = whatsAppService.obterQrCodeOuConexao();
        return ResponseEntity.ok(dados);
    }
}

package com.smart.price.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smart.price.dto.request.CupomRequest;
import com.smart.price.dto.response.CupomResponseDTO;
import com.smart.price.entity.Cupom;
import com.smart.price.service.CupomService;

@RestController
@RequestMapping("/cupons")
@Validated
public class CupomController {

    private final CupomService cupomService;
    private final com.smart.price.service.CupomCrawlerService cupomCrawlerService;
    private final com.smart.price.service.CupomMonitorStatusService cupomMonitorStatusService;

    public CupomController(
            CupomService cupomService,
            com.smart.price.service.CupomCrawlerService cupomCrawlerService,
            com.smart.price.service.CupomMonitorStatusService cupomMonitorStatusService) {
        this.cupomService = cupomService;
        this.cupomCrawlerService = cupomCrawlerService;
        this.cupomMonitorStatusService = cupomMonitorStatusService;
    }

    /**
     * Cadastra um novo cupom de desconto com opção de anúncio avulso imediato no Telegram.
     */
    @PostMapping
    public ResponseEntity<CupomResponseDTO> cadastrarCupom(@Valid @RequestBody CupomRequest req) {
        Cupom salvo = cupomService.cadastrarCupom(req);
        return ResponseEntity.created(URI.create("/api/cupons/" + salvo.getId()))
                .body(CupomResponseDTO.fromEntity(salvo));
    }

    /**
     * Lista todos os cupons cadastrados.
     */
    @GetMapping
    public ResponseEntity<List<CupomResponseDTO>> listarTodos() {
        List<CupomResponseDTO> lista = cupomService.listarTodos().stream()
                .map(CupomResponseDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    /**
     * Lista apenas os cupons que estão válidos e vigentes no momento atual.
     */
    @GetMapping("/ativos")
    public ResponseEntity<List<CupomResponseDTO>> listarAtivos() {
        List<CupomResponseDTO> lista = cupomService.listarValidosAgora().stream()
                .map(CupomResponseDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    /**
     * Obtém os detalhes de um cupom específico por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CupomResponseDTO> obterPorId(@PathVariable Long id) {
        return cupomService.obterPorId(id)
                .map(c -> ResponseEntity.ok(CupomResponseDTO.fromEntity(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Atualiza os dados de um cupom existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CupomResponseDTO> atualizarCupom(
            @PathVariable Long id, @Valid @RequestBody CupomRequest req) {
        try {
            Cupom atualizado = cupomService.atualizarCupom(id, req);
            return ResponseEntity.ok(CupomResponseDTO.fromEntity(atualizado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Alterna o status ativo/inativo do cupom.
     */
    @PutMapping("/{id}/alternar-status")
    public ResponseEntity<Map<String, Object>> alternarStatus(@PathVariable Long id) {
        try {
            cupomService.alternarStatus(id);
            return ResponseEntity.ok(Map.of("mensagem", "Status do cupom alternado com sucesso!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Dispara manualmente o anúncio avulso e urgente do cupom no Telegram.
     */
    @PostMapping("/{id}/anunciar")
    public ResponseEntity<Map<String, Object>> anunciarCupomAvulso(@PathVariable Long id) {
        try {
            boolean enviado = cupomService.anunciarCupomAvulso(id);
            if (enviado) {
                return ResponseEntity.ok(Map.of("mensagem", "Anúncio do cupom publicado com sucesso no Telegram!"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Não foi possível enviar o anúncio (verifique se o cupom está válido ou se o bot está ativo)."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Desativa imediatamente um cupom pelo código (ex: quando esgota no Mercado Livre).
     */
    @PostMapping("/desativar-por-codigo/{codigo}")
    public ResponseEntity<Map<String, Object>> desativarPorCodigo(@PathVariable String codigo) {
        boolean desativado = cupomService.desativarPorCodigo(codigo);
        if (desativado) {
            return ResponseEntity.ok(Map.of("mensagem", "Cupom [" + codigo.toUpperCase() + "] desativado e alerta emitido com sucesso!"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Endpoint público para reporte rápido de cupom esgotado.
     * Desativa imediatamente o cupom e dispara alerta no Telegram para a comunidade.
     */
    @PostMapping("/reportar-esgotado/{codigo}")
    public ResponseEntity<Map<String, Object>> reportarEsgotado(@PathVariable String codigo) {
        boolean desativado = cupomService.desativarCupomEAlertar(codigo, "Reportado como esgotado no checkout em " + java.time.LocalDateTime.now());
        if (desativado) {
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Cupom [" + codigo.toUpperCase() + "] confirmado como esgotado, desativado do banco e comunicado ao Telegram com sucesso!"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensagem", "Cupom não encontrado ou já estava inativo no sistema."
            ));
        }
    }

    /**
     * Dispara manualmente a varredura do Crawler de cupons.
     */
    @PostMapping("/crawler/executar")
    public ResponseEntity<Map<String, Object>> executarCrawlerManual() {
        int novos = cupomCrawlerService.executarVarreduraManual();
        return ResponseEntity.ok(Map.of(
                "mensagem", "Varredura do crawler de cupons concluída com sucesso!",
                "novosCuponsDescobertos", novos
        ));
    }

    /**
     * Dispara manualmente a verificação do Monitor de status dos cupons.
     */
    @PostMapping("/monitor/executar")
    public ResponseEntity<Map<String, Object>> executarMonitorManual() {
        int desativados = cupomMonitorStatusService.executarMonitoramento();
        return ResponseEntity.ok(Map.of(
                "mensagem", "Monitoramento de status de cupons concluído com sucesso!",
                "cuponsEsgotadosDesativados", desativados
        ));
    }

    /**
     * Confirma que um cupom capturado automaticamente está funcionando no checkout do Mercado Livre.
     * Após confirmação (testado=true), o cupom passa a ser elegível para pareamento
     * automático com produtos anunciados pelo bot.
     *
     * Use após testar o cupom manualmente no checkout e confirmar que ele aplica o desconto.
     */
    @PostMapping("/{id}/confirmar-testado")
    public ResponseEntity<Map<String, Object>> confirmarCupomTestado(@PathVariable Long id) {
        try {
            boolean confirmado = cupomService.confirmarCupomTestado(id);
            if (confirmado) {
                return ResponseEntity.ok(Map.of(
                        "mensagem", "Cupom confirmado como testado e funcional! A partir de agora será elegível para anúncios automáticos de produtos."
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "mensagem", "Cupom não encontrado ou já estava marcado como testado."
                ));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Exclui um cupom pelo ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCupom(@PathVariable Long id) {
        cupomService.deletarCupom(id);
        return ResponseEntity.noContent().build();
    }
}

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

import com.smart.price.dto.request.NichoRequest;
import com.smart.price.dto.request.TermoBuscaRequest;
import com.smart.price.dto.response.NichoResponseDTO;
import com.smart.price.dto.response.OfertaDescobertaDTO;
import com.smart.price.dto.response.TermoBuscaDTO;
import com.smart.price.entity.Nicho;
import com.smart.price.entity.OfertaDescoberta;
import com.smart.price.entity.TermoBusca;
import com.smart.price.repository.NichoRepository;
import com.smart.price.repository.OfertaDescobertaRepository;
import com.smart.price.repository.TermoBuscaRepository;
import com.smart.price.service.NichoRotativoAgendadorService;

@RestController
@RequestMapping("/nichos")
@Validated
public class NichoController {

    private final NichoRepository nichoRepository;
    private final TermoBuscaRepository termoBuscaRepository;
    private final OfertaDescobertaRepository ofertaDescobertaRepository;
    private final NichoRotativoAgendadorService agendadorService;

    public NichoController(
            NichoRepository nichoRepository,
            TermoBuscaRepository termoBuscaRepository,
            OfertaDescobertaRepository ofertaDescobertaRepository,
            NichoRotativoAgendadorService agendadorService) {
        this.nichoRepository = nichoRepository;
        this.termoBuscaRepository = termoBuscaRepository;
        this.ofertaDescobertaRepository = ofertaDescobertaRepository;
        this.agendadorService = agendadorService;
    }

    /**
     * Dispara manualmente o próximo ciclo da fila Round-Robin sem esperar os 30 minutos.
     */
    @PostMapping("/executar-proximo")
    public ResponseEntity<Map<String, Object>> executarProximoCiclo() {
        List<OfertaDescoberta> ofertas = agendadorService.executarProximoCiclo();
        List<OfertaDescobertaDTO> dtos = ofertas.stream()
                .map(OfertaDescobertaDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "mensagem", "Ciclo executado com sucesso!",
                "totalOfertasEncontradas", dtos.size(),
                "ofertas", dtos
        ));
    }

    /**
     * Lista todos os nichos cadastrados e seus termos.
     */
    @GetMapping
    public ResponseEntity<List<NichoResponseDTO>> listarNichos() {
        List<NichoResponseDTO> resposta = nichoRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(resposta);
    }

    /**
     * Obtém um nicho específico por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NichoResponseDTO> obterPorId(@PathVariable Long id) {
        return nichoRepository.findById(id)
                .map(n -> ResponseEntity.ok(toResponseDTO(n)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Cadastra um novo nicho e seus termos iniciais.
     */
    @PostMapping
    public ResponseEntity<NichoResponseDTO> criarNicho(@Valid @RequestBody NichoRequest req) {
        Nicho nicho = new Nicho();
        nicho.setNome(req.getNome());
        nicho.setAtivo(req.getAtivo() != null ? req.getAtivo() : true);
        nicho.setCategoriaMlb(req.getCategoriaMlb());
        nicho.setTelegramChatId(req.getTelegramChatId());
        nicho.setWhatsappGroupId(req.getWhatsappGroupId());

        if (req.getTermos() != null) {
            for (String t : req.getTermos()) {
                nicho.getTermos().add(new TermoBusca(nicho, t));
            }
        }

        Nicho salvo = nichoRepository.save(nicho);
        return ResponseEntity.created(URI.create("/api/nichos/" + salvo.getId())).body(toResponseDTO(salvo));
    }

    /**
     * Atualiza os dados de um nicho existente (nome, categoria MLB, Telegram Chat ID, WhatsApp Group ID, ativo).
     */
    @PutMapping("/{id}")
    public ResponseEntity<NichoResponseDTO> atualizarNicho(
            @PathVariable Long id, @Valid @RequestBody NichoRequest req) {
        return nichoRepository.findById(id)
                .map(nicho -> {
                    nicho.setNome(req.getNome());
                    if (req.getAtivo() != null) {
                        nicho.setAtivo(req.getAtivo());
                    }
                    nicho.setCategoriaMlb(req.getCategoriaMlb());
                    nicho.setTelegramChatId(req.getTelegramChatId());
                    nicho.setWhatsappGroupId(req.getWhatsappGroupId());
                    Nicho atualizado = nichoRepository.save(nicho);
                    return ResponseEntity.ok(toResponseDTO(atualizado));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Alterna o status ativo/inativo de um nicho.
     */
    @PutMapping("/{id}/alternar-status")
    public ResponseEntity<NichoResponseDTO> alternarStatus(@PathVariable Long id) {
        return nichoRepository.findById(id)
                .map(n -> {
                    n.setAtivo(!Boolean.TRUE.equals(n.getAtivo()));
                    Nicho atualizado = nichoRepository.save(n);
                    return ResponseEntity.ok(toResponseDTO(atualizado));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Adiciona um novo termo a um nicho existente.
     */
    @PostMapping("/{id}/termos")
    public ResponseEntity<TermoBuscaDTO> adicionarTermo(
            @PathVariable Long id, @Valid @RequestBody TermoBuscaRequest req) {
        return nichoRepository.findById(id)
                .map(nicho -> {
                    TermoBusca termo = new TermoBusca(nicho, req.getTermo());
                    termo.setAtivo(req.getAtivo() != null ? req.getAtivo() : true);
                    TermoBusca salvo = termoBuscaRepository.save(termo);
                    return ResponseEntity.created(URI.create("/api/nichos/" + id + "/termos/" + salvo.getId()))
                            .body(new TermoBuscaDTO(salvo.getId(), salvo.getTermo(), salvo.getAtivo(), salvo.getDataUltimaBusca(), salvo.getTotalBuscas()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Adiciona múltiplos termos em lote a um nicho existente.
     */
    @PostMapping("/{id}/termos/em-lote")
    public ResponseEntity<Map<String, Object>> adicionarTermosEmLote(
            @PathVariable Long id, @RequestBody List<String> novosTermos) {
        java.util.Optional<Nicho> optNicho = nichoRepository.findById(id);
        if (optNicho.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Nicho nicho = optNicho.get();
        java.util.Set<String> existentes = nicho.getTermos().stream()
                .map(t -> t.getTermo().trim().toLowerCase())
                .collect(Collectors.toSet());

        int adicionados = 0;
        for (String termoStr : novosTermos) {
            if (termoStr != null && !termoStr.isBlank() && !existentes.contains(termoStr.trim().toLowerCase())) {
                nicho.getTermos().add(new TermoBusca(nicho, termoStr.trim()));
                existentes.add(termoStr.trim().toLowerCase());
                adicionados++;
            }
        }
        if (adicionados > 0) {
            nichoRepository.save(nicho);
        }

        Map<String, Object> resposta = new java.util.LinkedHashMap<>();
        resposta.put("mensagem", "Termos processados com sucesso!");
        resposta.put("adicionados", adicionados);
        resposta.put("totalTermos", nicho.getTermos().size());

        return ResponseEntity.ok(resposta);
    }

    /**
     * Lista todos os termos cadastrados para um nicho específico com estatísticas de rotação.
     */
    @GetMapping("/{id}/termos")
    public ResponseEntity<List<TermoBuscaDTO>> listarTermosDoNicho(@PathVariable Long id) {
        return nichoRepository.findById(id)
                .map(nicho -> {
                    List<TermoBuscaDTO> dtos = nicho.getTermos().stream()
                            .map(t -> new TermoBuscaDTO(t.getId(), t.getTermo(), t.getAtivo(), t.getDataUltimaBusca(), t.getTotalBuscas()))
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(dtos);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Remove um termo de busca.
     */
    @DeleteMapping("/termos/{termoId}")
    public ResponseEntity<Void> removerTermo(@PathVariable Long termoId) {
        if (!termoBuscaRepository.existsById(termoId)) {
            return ResponseEntity.notFound().build();
        }
        termoBuscaRepository.deleteById(termoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista as ofertas descobertas mais recentes salvas no banco.
     */
    @GetMapping("/ofertas")
    public ResponseEntity<List<OfertaDescobertaDTO>> listarOfertasDescobertas() {
        List<OfertaDescobertaDTO> lista = ofertaDescobertaRepository.findTop20ByOrderByDataDescobertaDesc().stream()
                .map(OfertaDescobertaDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    private NichoResponseDTO toResponseDTO(Nicho n) {
        List<TermoBuscaDTO> termosDTO = n.getTermos().stream()
                .map(t -> new TermoBuscaDTO(t.getId(), t.getTermo(), t.getAtivo(), t.getDataUltimaBusca(), t.getTotalBuscas()))
                .collect(Collectors.toList());

        NichoResponseDTO dto = new NichoResponseDTO(
                n.getId(),
                n.getNome(),
                n.getAtivo(),
                n.getDataUltimaBusca(),
                termosDTO
        );
        dto.setTotalCiclos(n.getTotalCiclos());
        dto.setCategoriaMlb(n.getCategoriaMlb());
        dto.setTelegramChatId(n.getTelegramChatId());
        dto.setWhatsappGroupId(n.getWhatsappGroupId());
        return dto;
    }
}

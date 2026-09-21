package com.smart.price.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NichoRequest {

    @NotBlank
    @Size(max = 100)
    private String nome;

    private Boolean ativo = true;

    private String categoriaMlb;

    private String telegramChatId;
    private String whatsappGroupId;

    private List<String> termos;
}

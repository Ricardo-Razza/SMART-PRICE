package com.smart.price.dto.request;

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
public class TermoBuscaRequest {

    @NotBlank
    @Size(max = 200)
    private String termo;

    private Boolean ativo = true;
}

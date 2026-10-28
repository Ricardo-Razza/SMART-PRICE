package com.smart.price.enums;

/**
 * Resultado da validação de um cupom.
 * CONFIRMADO mostra o preço com cupom; NAO_VERIFICADO só avisa que há cupom; INVALIDO nunca é pareado.
 */
public enum StatusCupom {
    CONFIRMADO,
    NAO_VERIFICADO,
    INVALIDO
}

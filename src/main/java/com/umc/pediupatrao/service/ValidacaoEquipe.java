package com.umc.pediupatrao.service;

// Validações compartilhadas pelos cadastros de pessoas da equipe.
final class ValidacaoEquipe {
    private ValidacaoEquipe() { }

    static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.trim().length() > limite) {
            throw new IllegalArgumentException("Informe " + campo + " com até " + limite + " caracteres.");
        }
        return valor.trim();
    }

    static String telefone(String valor) {
        String telefone = valor == null ? "" : valor.replaceAll("[^0-9]", "");
        if (!telefone.matches("[0-9]{10,11}")) {
            throw new IllegalArgumentException("Informe um telefone com DDD e 10 ou 11 dígitos.");
        }
        return telefone;
    }
}
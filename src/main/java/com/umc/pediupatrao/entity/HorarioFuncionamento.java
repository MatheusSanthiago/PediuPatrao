package com.umc.pediupatrao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "horariosAtendimento")
public class HorarioFuncionamento {
    @Id
    private String id;
    private boolean fechado = true;
    private String abertura;
    private String fechamento;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public boolean isFechado() { return fechado; }
    public void setFechado(boolean fechado) { this.fechado = fechado; }
    public String getAbertura() { return abertura; }
    public void setAbertura(String abertura) { this.abertura = abertura; }
    public String getFechamento() { return fechamento; }
    public void setFechamento(String fechamento) { this.fechamento = fechamento; }

    public String getNomeDia() {
        return switch (id) {
            case "MONDAY" -> "Segunda-feira";
            case "TUESDAY" -> "Terça-feira";
            case "WEDNESDAY" -> "Quarta-feira";
            case "THURSDAY" -> "Quinta-feira";
            case "FRIDAY" -> "Sexta-feira";
            case "SATURDAY" -> "Sábado";
            case "SUNDAY" -> "Domingo";
            default -> "";
        };
    }
    public boolean isFechaNoDiaSeguinte() {
        return !fechado && abertura != null && fechamento != null && fechamento.compareTo(abertura) < 0;
    }
}
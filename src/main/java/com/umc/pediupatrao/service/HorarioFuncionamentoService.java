package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.HorarioFuncionamento;
import com.umc.pediupatrao.repository.HorarioFuncionamentoRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
public class HorarioFuncionamentoService {
    private final HorarioFuncionamentoRepository repository;

    public HorarioFuncionamentoService(HorarioFuncionamentoRepository repository) {
        this.repository = repository;
    }

    public List<HorarioFuncionamento> listar() {
        List<HorarioFuncionamento> salvos = repository.findAll();
        List<HorarioFuncionamento> semana = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            HorarioFuncionamento horario = salvos.stream().filter(h -> dia.name().equals(h.getId()))
                    .findFirst().orElseGet(() -> {
                        HorarioFuncionamento novo = new HorarioFuncionamento();
                        novo.setId(dia.name());
                        return novo;
                    });
            semana.add(horario);
        }
        return semana;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void salvar(HorarioFuncionamento dados) {
        if (dados.getId() == null) throw new IllegalArgumentException("Selecione um dia da semana.");
        try {
            DayOfWeek.valueOf(dados.getId());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dia da semana inválido.");
        }
        HorarioFuncionamento horario = new HorarioFuncionamento();
        horario.setId(dados.getId());
        horario.setFechado(dados.isFechado());
        if (!dados.isFechado()) {
            validarHora(dados.getAbertura());
            validarHora(dados.getFechamento());
            if (dados.getAbertura().equals(dados.getFechamento())) {
                throw new IllegalArgumentException("A abertura e o fechamento devem ter horários diferentes.");
            }
            horario.setAbertura(dados.getAbertura());
            horario.setFechamento(dados.getFechamento());
        }
        repository.save(horario);
    }

    private void validarHora(String hora) {
        if (hora == null || !hora.matches("[0-9]{2}:[0-9]{2}")) {
            throw new IllegalArgumentException("Preencha abertura e fechamento no formato HH:mm.");
        }
        try {
            LocalTime.parse(hora);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário inválido.");
        }
    }
}
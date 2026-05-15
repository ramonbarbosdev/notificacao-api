package com.notificacao_api.config;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notificacao.protecao")
public record PropriedadesProtecaoNotificacao(
        int limitePorMinuto,
        int limitePorHora,
        int limitePorDia,
        long delayMinimoSegundos,
        long delayMaximoSegundos,
        LocalTime inicioPermitido,
        LocalTime fimPermitido,
        String fusoHorario,
        int maximoTentativas,
        long pausaAutomaticaSegundos,
        int maximoFalhasConsecutivas,
        long janelaDuplicidadeMinutos,
        int tamanhoLoteAgendador,
        long intervaloAgendadorMillis,
        int tamanhoMaximoLote) {

    public PropriedadesProtecaoNotificacao {
        if (limitePorMinuto <= 0) {
            limitePorMinuto = 5;
        }
        if (limitePorHora <= 0) {
            limitePorHora = 60;
        }
        if (limitePorDia <= 0) {
            limitePorDia = 300;
        }
        if (delayMinimoSegundos <= 0) {
            delayMinimoSegundos = 15;
        }
        if (delayMaximoSegundos < delayMinimoSegundos) {
            delayMaximoSegundos = delayMinimoSegundos;
        }
        if (inicioPermitido == null) {
            inicioPermitido = LocalTime.of(8, 0);
        }
        if (fimPermitido == null) {
            fimPermitido = LocalTime.of(18, 0);
        }
        if (fusoHorario == null || fusoHorario.isBlank()) {
            fusoHorario = "America/Bahia";
        }
        if (maximoTentativas <= 0) {
            maximoTentativas = 3;
        }
        if (pausaAutomaticaSegundos <= 0) {
            pausaAutomaticaSegundos = 900;
        }
        if (maximoFalhasConsecutivas <= 0) {
            maximoFalhasConsecutivas = 5;
        }
        if (janelaDuplicidadeMinutos <= 0) {
            janelaDuplicidadeMinutos = 60;
        }
        if (tamanhoLoteAgendador <= 0) {
            tamanhoLoteAgendador = 1;
        }
        if (intervaloAgendadorMillis <= 0) {
            intervaloAgendadorMillis = 5000;
        }
        if (tamanhoMaximoLote <= 0) {
            tamanhoMaximoLote = 50;
        }
    }
}

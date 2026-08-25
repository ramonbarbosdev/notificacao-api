package com.notificacao_api.service.whatsapp;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappResposta;
import com.notificacao_api.dto.whatsapp.GatewaySessoesListaResponseDTO;
import com.notificacao_api.dto.whatsapp.StatusWhatsappResposta;
import com.notificacao_api.dto.whatsapp.WhatsappConversasOperacionaisGatewayResposta;
import com.notificacao_api.dto.whatsapp.WhatsappHistoricoCarregarMaisGatewayResposta;
import com.notificacao_api.dto.whatsapp.WhatsappMensagensGatewayResposta;
import com.notificacao_api.dto.whatsapp.WhatsappDiagnosticoContatoResposta;
import com.notificacao_api.enums.WhatsappMensagemDirecao;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WhatsAppGatewayClient {

    private final RestClient restClient;

    public WhatsAppGatewayClient(
            RestClient.Builder builder,
            @Value("${whatsapp.gateway.base-url}") String baseUrl,
            @Value("${whatsapp.gateway.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    public StatusWhatsappResposta conectar(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/conectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "conectar sessao WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public StatusWhatsappResposta obterStatus(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.get()
                    .uri("/sessoes/{idOrganizacao}/status", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "consultar status WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public EnviarMensagemWhatsappResposta enviarMensagem(
            Long idOrganizacao,
            EnviarMensagemWhatsappRequisicao requisicao) {
        try {
            EnviarMensagemWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/enviar-mensagem", idOrganizacao)
                    .body(requisicao)
                    .retrieve()
                    .body(EnviarMensagemWhatsappResposta.class);
            if (resposta != null) {
                return normalizarRespostaEnvio(idOrganizacao, requisicao.telefone(), resposta);
            }
            return new EnviarMensagemWhatsappResposta(
                    false, idOrganizacao, null, requisicao.telefone(), null, null, null,
                    "Gateway WhatsApp nao retornou resposta ao enviar mensagem.");
        } catch (HttpStatusCodeException ex) {
            String respostaErro = ex.getResponseBodyAsString();
            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    null,
                    null,
                    extrairMensagemErroEnvio(respostaErro, ex));
        } catch (Exception ex) {
            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    null,
                    requisicao.telefone(),
                    null,
                    null,
                    null,
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public WhatsappDiagnosticoContatoResposta diagnosticarContato(Long idOrganizacao, String telefone) {
        try {
            WhatsappDiagnosticoContatoResposta resposta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sessoes/{idOrganizacao}/diagnostico")
                            .queryParam("telefone", telefone)
                            .build(idOrganizacao))
                    .retrieve()
                    .body(WhatsappDiagnosticoContatoResposta.class);
            if (resposta != null) {
                return resposta;
            }
            return respostaErroDiagnostico(idOrganizacao, telefone, "Gateway WhatsApp nao respondeu ao diagnostico.");
        } catch (HttpStatusCodeException ex) {
            return respostaErroDiagnostico(
                    idOrganizacao,
                    telefone,
                    extrairMensagemErroEnvio(ex.getResponseBodyAsString(), ex));
        } catch (Exception ex) {
            return respostaErroDiagnostico(
                    idOrganizacao,
                    telefone,
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public WhatsappMensagensGatewayResposta listarMensagensSessao(
            Long idOrganizacao,
            String telefone,
            int limite,
            WhatsappMensagemDirecao direcao) {
        try {
            WhatsappMensagensGatewayResposta resposta = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/sessoes/{idOrganizacao}/conversas/{telefone}/mensagens")
                                .queryParam("limite", limite);
                        if (direcao != null) {
                            builder = builder.queryParam("direcao", direcao.name());
                        }
                        return builder.build(idOrganizacao, telefone);
                    })
                    .retrieve()
                    .body(WhatsappMensagensGatewayResposta.class);
            if (resposta != null) {
                return resposta;
            }
            return new WhatsappMensagensGatewayResposta(
                    false,
                    idOrganizacao,
                    telefone,
                    0,
                    List.of(),
                    "Gateway WhatsApp nao respondeu ao listar mensagens da sessao.");
        } catch (HttpStatusCodeException ex) {
            return new WhatsappMensagensGatewayResposta(
                    false,
                    idOrganizacao,
                    telefone,
                    0,
                    List.of(),
                    extrairMensagemErroLegado(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            return new WhatsappMensagensGatewayResposta(
                    false,
                    idOrganizacao,
                    telefone,
                    0,
                    List.of(),
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public WhatsappHistoricoCarregarMaisGatewayResposta carregarMaisHistorico(
            Long idOrganizacao,
            String telefone,
            int limite) {
        try {
            WhatsappHistoricoCarregarMaisGatewayResposta resposta = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sessoes/{idOrganizacao}/conversas/{telefone}/historico/carregar-mais")
                            .queryParam("limite", limite)
                            .build(idOrganizacao, telefone))
                    .retrieve()
                    .body(WhatsappHistoricoCarregarMaisGatewayResposta.class);
            if (resposta != null) {
                return resposta;
            }
            return new WhatsappHistoricoCarregarMaisGatewayResposta(
                    false,
                    String.valueOf(idOrganizacao),
                    telefone,
                    0,
                    true,
                    List.of(),
                    "Gateway WhatsApp nao respondeu ao carregar mais historico.");
        } catch (HttpStatusCodeException ex) {
            return new WhatsappHistoricoCarregarMaisGatewayResposta(
                    false,
                    String.valueOf(idOrganizacao),
                    telefone,
                    0,
                    true,
                    List.of(),
                    extrairMensagemErroLegado(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            return new WhatsappHistoricoCarregarMaisGatewayResposta(
                    false,
                    String.valueOf(idOrganizacao),
                    telefone,
                    0,
                    true,
                    List.of(),
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public WhatsappConversasOperacionaisGatewayResposta listarConversasOperacionais(Long idOrganizacao) {
        try {
            WhatsappConversasOperacionaisGatewayResposta resposta = restClient.get()
                    .uri("/sessoes/{idOrganizacao}/conversas-operacionais", idOrganizacao)
                    .retrieve()
                    .body(WhatsappConversasOperacionaisGatewayResposta.class);
            return normalizarRespostaConversasOperacionais(idOrganizacao, resposta);
        } catch (HttpStatusCodeException ex) {
            return respostaErroConversasOperacionais(idOrganizacao, ex);
        } catch (Exception ex) {
            return new WhatsappConversasOperacionaisGatewayResposta(
                    false,
                    idOrganizacao,
                    0,
                    0,
                    List.of(),
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    public StatusWhatsappResposta desconectar(Long idOrganizacao) {
        try {
            StatusWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/desconectar", idOrganizacao)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "desconectar sessao WhatsApp");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public StatusWhatsappResposta atualizarOrganizacao(Long idOrganizacao, Long idOrganizacaoAnterior) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            if (idOrganizacaoAnterior != null) {
                body.put("idOrganizacaoAnterior", idOrganizacaoAnterior);
            }

            StatusWhatsappResposta resposta = restClient.post()
                    .uri("/sessoes/{idOrganizacao}/atualizar-org", idOrganizacao)
                    .body(body)
                    .retrieve()
                    .body(StatusWhatsappResposta.class);
            return normalizarResposta(idOrganizacao, resposta, "atualizar organizacao no gateway");
        } catch (Exception ex) {
            return respostaErro(idOrganizacao, ex);
        }
    }

    public GatewaySessoesListaResponseDTO listarSessoes() {
        try {
            GatewaySessoesListaResponseDTO resposta = restClient.get()
                    .uri("/sessoes")
                    .retrieve()
                    .body(GatewaySessoesListaResponseDTO.class);
            if (resposta == null) {
                return new GatewaySessoesListaResponseDTO(
                        false,
                        List.of(),
                        "Gateway WhatsApp nao respondeu ao listar sessoes.");
            }
            if (Boolean.FALSE.equals(resposta.sucesso())) {
                return new GatewaySessoesListaResponseDTO(
                        false,
                        List.of(),
                        resposta.erro() != null && !resposta.erro().isBlank()
                                ? WhatsappGatewayErroUtil.mensagemTextoGateway(resposta.erro())
                                : "Falha ao listar sessoes no gateway WhatsApp.");
            }
            return new GatewaySessoesListaResponseDTO(
                    true,
                    resposta.sessoes() != null ? resposta.sessoes() : List.of(),
                    null);
        } catch (Exception ex) {
            return new GatewaySessoesListaResponseDTO(
                    false,
                    List.of(),
                    WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
        }
    }

    private EnviarMensagemWhatsappResposta normalizarRespostaEnvio(
            Long idOrganizacao,
            String telefone,
            EnviarMensagemWhatsappResposta resposta) {
        if (Boolean.TRUE.equals(resposta.sucesso())
                && (resposta.idMensagem() == null || resposta.idMensagem().isBlank())) {
            return new EnviarMensagemWhatsappResposta(
                    false,
                    idOrganizacao,
                    resposta.identificadorContato(),
                    telefone,
                    resposta.estrategia(),
                    null,
                    resposta.confirmado(),
                    "Gateway WhatsApp nao confirmou o id da mensagem enviada.");
        }

        Boolean confirmado = resposta.confirmado();
        if (confirmado == null && Boolean.TRUE.equals(resposta.sucesso())) {
            confirmado = true;
        }

        return new EnviarMensagemWhatsappResposta(
                resposta.sucesso(),
                resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                resposta.identificadorContato(),
                resposta.telefone() != null ? resposta.telefone() : telefone,
                resposta.estrategia(),
                resposta.idMensagem(),
                confirmado,
                resposta.erro());
    }

    private StatusWhatsappResposta normalizarResposta(
            Long idOrganizacao,
            StatusWhatsappResposta resposta,
            String acao) {
        if (resposta == null) {
            return respostaErro(idOrganizacao,
                    "Gateway WhatsApp nao respondeu ao " + acao + ".");
        }
        if (Boolean.FALSE.equals(resposta.sucesso()) && resposta.erro() != null && !resposta.erro().isBlank()) {
            return StatusWhatsappResposta.respostaGateway(
                    false,
                    resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                    resposta.status() != null ? resposta.status() : "ERRO",
                    false,
                    resposta.qr(),
                    resposta.qrImagem(),
                    resposta.telefone(),
                    WhatsappGatewayErroUtil.mensagemTextoGateway(resposta.erro()));
        }
        return StatusWhatsappResposta.respostaGateway(
                resposta.sucesso(),
                resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                WhatsappGatewayStatusMapper.normalizar(resposta.status()),
                resposta.conectado(),
                resposta.qr(),
                resposta.qrImagem(),
                resposta.telefone(),
                resposta.erro());
    }

    private StatusWhatsappResposta respostaErro(Long idOrganizacao, Throwable ex) {
        return StatusWhatsappResposta.respostaGateway(
                false,
                idOrganizacao,
                "ERRO",
                false,
                null,
                null,
                null,
                WhatsappGatewayErroUtil.mensagemParaUsuario(ex));
    }

    private StatusWhatsappResposta respostaErro(Long idOrganizacao, String mensagem) {
        return StatusWhatsappResposta.respostaGateway(
                false, idOrganizacao, "ERRO", false, null, null, null, mensagem);
    }

    private String extrairMensagemErroEnvio(String responseBody, HttpStatusCodeException ex) {
        int status = ex.getStatusCode().value();
        if (status >= 502 && status <= 504) {
            return "O gateway WhatsApp esta temporariamente indisponivel.";
        }
        if (status == 422) {
            return WhatsappGatewayErroUtil.mensagemDoCorpoResposta(responseBody);
        }
        return extrairMensagemErroLegado(responseBody);
    }

    private String extrairMensagemErroLegado(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return WhatsappGatewayErroUtil.mensagemParaUsuario(
                    new RestClientResponseException("Gateway", 502, "Bad Gateway", null, null, null));
        }

        if (responseBody.contains("Sessão não iniciada") || responseBody.contains("Sessao nao iniciada")) {
            return "Sessao do WhatsApp nao iniciada para esta organizacao.";
        }

        if (responseBody.contains("WhatsApp não conectado") || responseBody.contains("WhatsApp nao conectado")) {
            return "WhatsApp nao conectado para esta organizacao.";
        }

        if (responseBody.contains("Número não encontrado") || responseBody.contains("Numero nao encontrado")) {
            return "Numero informado nao encontrado no WhatsApp.";
        }

        return WhatsappGatewayErroUtil.mensagemDoCorpoResposta(responseBody);
    }

    private WhatsappConversasOperacionaisGatewayResposta normalizarRespostaConversasOperacionais(
            Long idOrganizacao,
            WhatsappConversasOperacionaisGatewayResposta resposta) {
        if (resposta == null) {
            return new WhatsappConversasOperacionaisGatewayResposta(
                    false,
                    idOrganizacao,
                    0,
                    0,
                    List.of(),
                    "Gateway WhatsApp nao respondeu ao listar conversas operacionais.");
        }

        if (Boolean.FALSE.equals(resposta.sucesso())) {
            return new WhatsappConversasOperacionaisGatewayResposta(
                    false,
                    resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                    0,
                    0,
                    List.of(),
                    resposta.erro() != null && !resposta.erro().isBlank()
                            ? WhatsappGatewayErroUtil.mensagemTextoGateway(resposta.erro())
                            : "Falha ao listar conversas operacionais no gateway WhatsApp.");
        }

        return new WhatsappConversasOperacionaisGatewayResposta(
                true,
                resposta.idOrganizacao() != null ? resposta.idOrganizacao() : idOrganizacao,
                resposta.total() != null ? resposta.total() : 0,
                resposta.prontas() != null ? resposta.prontas() : 0,
                resposta.conversas() != null ? resposta.conversas() : List.of(),
                null);
    }

    private WhatsappConversasOperacionaisGatewayResposta respostaErroConversasOperacionais(
            Long idOrganizacao,
            HttpStatusCodeException ex) {
        return new WhatsappConversasOperacionaisGatewayResposta(
                false,
                idOrganizacao,
                0,
                0,
                List.of(),
                extrairMensagemErroLegado(ex.getResponseBodyAsString()));
    }

    private WhatsappDiagnosticoContatoResposta respostaErroDiagnostico(
            Long idOrganizacao,
            String telefone,
            String erro) {
        return new WhatsappDiagnosticoContatoResposta(
                false,
                String.valueOf(idOrganizacao),
                erro,
                telefone,
                null,
                false,
                "ERRO",
                false,
                false,
                null,
                null,
                null,
                null,
                List.of());
    }
}

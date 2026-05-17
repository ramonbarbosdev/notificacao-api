package com.notificacao_api.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.template.AtualizarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.CriarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.EnviarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.RenderizarTemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TestarTemplateNotificacaoRequestDTO;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.TemplateNotificacao;
import com.notificacao_api.repository.TemplateNotificacaoRepository;

@Service
public class TemplateNotificacaoService {

    private static final Pattern VARIAVEL_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}");
    private static final Pattern CHAVE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern NOME_VARIAVEL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    private final TenantContextService tenantContextService;
    private final TemplateNotificacaoRepository templateRepository;
    private final NotificacaoService notificacaoService;

    public TemplateNotificacaoService(
            TenantContextService tenantContextService,
            TemplateNotificacaoRepository templateRepository,
            NotificacaoService notificacaoService) {

        this.tenantContextService = tenantContextService;
        this.templateRepository = templateRepository;
        this.notificacaoService = notificacaoService;
    }

    @Transactional(readOnly = true)
    public Page<TemplateNotificacaoResponseDTO> listar(Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        return templateRepository
                .findAll((root, query, cb) -> cb.equal(root.get("idOrganizacao"), idOrganizacao), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TemplateNotificacaoResponseDTO buscar(Long idModelo) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return toResponse(buscarDaOrganizacao(idOrganizacao, idModelo));
    }

    @Transactional
    public TemplateNotificacaoResponseDTO criar(CriarTemplateNotificacaoRequestDTO request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String chave = normalizarChave(request.chave());

        validarChave(chave);
        validarChaveUnica(idOrganizacao, chave, null);

        TemplateNotificacao template = new TemplateNotificacao();
        template.setIdOrganizacao(idOrganizacao);
        aplicarDados(
                template,
                request.nome(),
                chave,
                request.canal(),
                request.assunto(),
                request.conteudo(),
                request.ativo(),
                request.variaveisObrigatorias());

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateNotificacaoResponseDTO atualizar(Long idModelo, AtualizarTemplateNotificacaoRequestDTO request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        TemplateNotificacao template = buscarDaOrganizacao(idOrganizacao, idModelo);
        String chave = normalizarChave(request.chave());

        validarChave(chave);
        validarChaveUnica(idOrganizacao, chave, idModelo);

        aplicarDados(
                template,
                request.nome(),
                chave,
                request.canal(),
                request.assunto(),
                request.conteudo(),
                request.ativo(),
                request.variaveisObrigatorias());

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateNotificacaoResponseDTO alterarStatus(Long idModelo, boolean ativo) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        TemplateNotificacao template = buscarDaOrganizacao(idOrganizacao, idModelo);
        template.setAtivo(ativo);
        return toResponse(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public RenderizarTemplateNotificacaoResponseDTO testar(String chave, TestarTemplateNotificacaoRequestDTO request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        TemplateNotificacao template = buscarPorChave(idOrganizacao, chave);
        return renderizar(template, request.variaveis(), false);
    }

    @Transactional
    public EnviarNotificacaoResposta enviar(EnviarTemplateNotificacaoRequestDTO request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        TemplateNotificacao template = buscarPorChave(idOrganizacao, request.templateKey());

        if (!Boolean.TRUE.equals(template.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Template esta inativo.");
        }

        RenderizarTemplateNotificacaoResponseDTO renderizado = renderizar(template, request.variaveis(), true);

        return notificacaoService.enviar(new EnviarNotificacaoRequisicao(
                template.getCanal(),
                request.destinatario(),
                renderizado.assunto(),
                renderizado.mensagem()));
    }

    private TemplateNotificacao buscarDaOrganizacao(Long idOrganizacao, Long idModelo) {
        return templateRepository.findById(idModelo)
                .filter(template -> idOrganizacao.equals(template.getIdOrganizacao()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template nao encontrado."));
    }

    private TemplateNotificacao buscarPorChave(Long idOrganizacao, String chave) {
        return templateRepository.findByIdOrganizacaoAndChave(idOrganizacao, normalizarChave(chave))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template nao encontrado."));
    }

    private void aplicarDados(
            TemplateNotificacao template,
            String nome,
            String chave,
            CanalNotificacao canal,
            String assunto,
            String conteudo,
            Boolean ativo,
            Set<String> variaveisObrigatorias) {

        template.setNome(nome.trim());
        template.setChave(chave);
        template.setCanal(canal);
        template.setAssunto(assunto);
        template.setCorpo(conteudo);
        template.setAtivo(ativo == null ? Boolean.TRUE : ativo);
        template.setVariaveisObrigatorias(normalizarVariaveis(assunto, conteudo, variaveisObrigatorias));

        if (template.getVersao() == null) {
            template.setVersao(1);
        }
    }

    private RenderizarTemplateNotificacaoResponseDTO renderizar(
            TemplateNotificacao template,
            Map<String, String> variaveis,
            boolean validarStatusAtivo) {

        if (validarStatusAtivo && !Boolean.TRUE.equals(template.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Template esta inativo.");
        }

        Set<String> faltantes = new LinkedHashSet<>();
        for (String variavel : template.getVariaveisObrigatorias()) {
            if (variaveis == null || !variaveis.containsKey(variavel) || variaveis.get(variavel) == null
                    || variaveis.get(variavel).isBlank()) {
                faltantes.add(variavel);
            }
        }

        if (!faltantes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Variaveis obrigatorias ausentes: " + String.join(", ", faltantes) + ".");
        }

        return new RenderizarTemplateNotificacaoResponseDTO(
                template.getChave(),
                template.getCanal(),
                substituir(template.getAssunto(), variaveis),
                substituir(template.getCorpo(), variaveis),
                template.getVersao());
    }

    private String substituir(String texto, Map<String, String> variaveis) {
        if (texto == null) {
            return null;
        }

        Matcher matcher = VARIAVEL_PATTERN.matcher(texto);
        StringBuffer resultado = new StringBuffer();

        while (matcher.find()) {
            String nome = matcher.group(1);
            String valor = variaveis == null ? "" : variaveis.getOrDefault(nome, "");
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }

        matcher.appendTail(resultado);
        return resultado.toString();
    }

    private Set<String> normalizarVariaveis(String assunto, String conteudo, Set<String> variaveisObrigatorias) {
        Set<String> variaveis = new LinkedHashSet<>();

        if (variaveisObrigatorias != null) {
            for (String variavel : variaveisObrigatorias) {
                if (variavel == null || variavel.isBlank()) {
                    continue;
                }
                String nome = variavel.trim();
                validarNomeVariavel(nome);
                variaveis.add(nome);
            }
        }

        extrairVariaveis(assunto, variaveis);
        extrairVariaveis(conteudo, variaveis);

        return variaveis;
    }

    private void extrairVariaveis(String texto, Set<String> variaveis) {
        if (texto == null) {
            return;
        }

        Matcher matcher = VARIAVEL_PATTERN.matcher(texto);
        while (matcher.find()) {
            variaveis.add(matcher.group(1));
        }
    }

    private void validarChave(String chave) {
        if (!CHAVE_PATTERN.matcher(chave).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chave do template deve usar apenas letras minusculas, numeros e hifens.");
        }
    }

    private void validarNomeVariavel(String variavel) {
        if (!NOME_VARIAVEL_PATTERN.matcher(variavel).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome de variavel invalido: " + variavel + ".");
        }
    }

    private void validarChaveUnica(Long idOrganizacao, String chave, Long idModelo) {
        boolean existe = idModelo == null
                ? templateRepository.existsByIdOrganizacaoAndChave(idOrganizacao, chave)
                : templateRepository.existsByIdOrganizacaoAndChaveAndIdModeloNot(idOrganizacao, chave, idModelo);

        if (existe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe template com esta chave.");
        }
    }

    private String normalizarChave(String chave) {
        String semAcentos = Normalizer.normalize(chave == null ? "" : chave.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private TemplateNotificacaoResponseDTO toResponse(TemplateNotificacao template) {
        return new TemplateNotificacaoResponseDTO(
                template.getIdModelo(),
                template.getIdOrganizacao(),
                template.getNome(),
                template.getChave(),
                template.getCanal(),
                template.getAssunto(),
                template.getCorpo(),
                template.getAtivo(),
                template.getVariaveisObrigatorias(),
                template.getVersao(),
                template.getDtCriacao(),
                template.getDtAtualizacao());
    }
}

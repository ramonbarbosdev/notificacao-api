package com.notificacao_api.service;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.dto.template.AtualizarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.CriarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.EnviarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.ExtrairVariaveisTemplateResponseDTO;
import com.notificacao_api.dto.template.RenderizarTemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TemplateNotificacaoFilter;
import com.notificacao_api.dto.template.TemplateNotificacaoResponseDTO;
import com.notificacao_api.dto.template.TemplateVariavelDTO;
import com.notificacao_api.dto.template.TestarTemplateNotificacaoRequestDTO;
import com.notificacao_api.dto.template.ValidarTemplateRequestDTO;
import com.notificacao_api.dto.template.ValidarTemplateResponseDTO;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.TipoVariavelTemplate;
import com.notificacao_api.model.TemplateNotificacao;
import com.notificacao_api.model.TemplateVariavel;
import com.notificacao_api.repository.TemplateNotificacaoRepository;

@Service
public class TemplateNotificacaoService {

    private static final Pattern VARIAVEL_PATTERN =
            Pattern.compile("\\{\\{\\s*([\\p{L}_][\\p{L}0-9_.-]*)\\s*}}");
    private static final Pattern CHAVE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONE_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");

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
    public Page<TemplateNotificacaoResponseDTO> listar(TemplateNotificacaoFilter filter, Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        Specification<TemplateNotificacao> spec = (root, query, cb) ->
                cb.equal(root.get("idOrganizacao"), idOrganizacao);

        if (filter != null && filter.termo() != null && !filter.termo().isBlank()) {
            String termo = "%" + filter.termo().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nome")), termo),
                    cb.like(cb.lower(root.get("chave")), termo),
                    cb.like(cb.lower(root.get("corpo")), termo)));
        }

        if (filter != null && filter.canal() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("canal"), filter.canal()));
        }

        if (filter != null && filter.ativo() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ativo"), filter.ativo()));
        }

        return templateRepository.findAll(spec, pageable).map(this::toResponse);
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
                request.variaveis(),
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
                request.variaveis(),
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
    public ExtrairVariaveisTemplateResponseDTO extrairVariaveis(String conteudo) {
        return new ExtrairVariaveisTemplateResponseDTO(List.copyOf(extrairVariaveisDoTexto(conteudo)));
    }

    @Transactional(readOnly = true)
    public ValidarTemplateResponseDTO validar(ValidarTemplateRequestDTO request) {
        return validarConteudoVariaveis(request.conteudo(), request.variaveis(), true);
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
            List<TemplateVariavelDTO> variaveis,
            Set<String> variaveisObrigatorias) {

        List<TemplateVariavel> variaveisNormalizadas = normalizarVariaveis(
                assunto,
                conteudo,
                variaveis,
                variaveisObrigatorias);

        template.setNome(nome.trim());
        template.setChave(chave);
        template.setCanal(canal);
        template.setAssunto(assunto);
        template.setCorpo(conteudo);
        template.setAtivo(ativo == null ? Boolean.TRUE : ativo);
        template.setVariaveis(variaveisNormalizadas);
        template.setVariaveisObrigatorias(variaveisObrigatorias(variaveisNormalizadas));

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

        validarValoresRenderizacao(template.getVariaveis(), variaveis);

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

    private List<TemplateVariavel> normalizarVariaveis(
            String assunto,
            String conteudo,
            List<TemplateVariavelDTO> variaveis,
            Set<String> variaveisObrigatorias) {

        List<TemplateVariavelDTO> declaradas = variaveis != null && !variaveis.isEmpty()
                ? variaveis
                : variaveisLegadas(variaveisObrigatorias);

        ValidarTemplateResponseDTO validacao = validarConteudoVariaveis(juntarTexto(assunto, conteudo), declaradas, true);
        if (!validacao.valido()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", validacao.erros()));
        }

        Map<String, TemplateVariavel> porChave = new LinkedHashMap<>();
        for (TemplateVariavelDTO variavel : declaradas) {
            TemplateVariavel normalizada = toModel(variavel);
            porChave.put(normalizada.getChave(), normalizada);
        }

        return new ArrayList<>(porChave.values());
    }

    private ValidarTemplateResponseDTO validarConteudoVariaveis(
            String conteudo,
            List<TemplateVariavelDTO> variaveis,
            boolean validarExemplo) {

        Set<String> encontradas = extrairVariaveisDoTexto(conteudo);
        Set<String> declaradas = new LinkedHashSet<>();
        Set<String> duplicadas = new LinkedHashSet<>();
        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        if (variaveis != null) {
            for (TemplateVariavelDTO variavel : variaveis) {
                if (variavel == null || variavel.chave() == null || variavel.chave().isBlank()) {
                    erros.add("Nao e permitido declarar variavel com chave vazia.");
                    continue;
                }

                String chave = variavel.chave().trim();
                if (!declaradas.add(chave)) {
                    duplicadas.add(chave);
                }

                validarChaveVariavel(chave, erros);

                if (variavel.tipo() == null) {
                    erros.add("A variavel " + chave + " deve possuir tipo.");
                }

                if (validarExemplo && variavel.exemplo() != null && !variavel.exemplo().isBlank()) {
                    validarValor(chave, variavel.tipo(), variavel.exemplo(), erros);
                }
            }
        }

        for (String duplicada : duplicadas) {
            erros.add("A variavel " + duplicada + " foi declarada mais de uma vez.");
        }

        List<String> naoDeclaradas = encontradas.stream()
                .filter(chave -> !declaradas.contains(chave))
                .toList();

        List<String> declaradasNaoUsadas = declaradas.stream()
                .filter(chave -> !encontradas.contains(chave))
                .toList();

        for (String variavel : naoDeclaradas) {
            erros.add("A variavel " + variavel + " foi usada no conteudo, mas nao foi declarada.");
        }

        for (String variavel : declaradasNaoUsadas) {
            avisos.add("A variavel " + variavel + " foi declarada, mas nao foi usada no conteudo.");
        }

        return new ValidarTemplateResponseDTO(
                erros.isEmpty(),
                List.copyOf(encontradas),
                List.copyOf(declaradas),
                naoDeclaradas,
                declaradasNaoUsadas,
                erros,
                avisos);
    }

    private void validarValoresRenderizacao(List<TemplateVariavel> metadados, Map<String, String> valores) {
        List<String> erros = new ArrayList<>();

        for (TemplateVariavel variavel : metadados) {
            String valor = valores == null ? null : valores.get(variavel.getChave());

            if (Boolean.TRUE.equals(variavel.getObrigatoria()) && (valor == null || valor.isBlank())) {
                erros.add("Variavel obrigatoria ausente: " + variavel.getChave() + ".");
                continue;
            }

            if (valor != null && !valor.isBlank()) {
                validarValor(variavel.getChave(), variavel.getTipo(), valor, erros);
            }
        }

        if (!erros.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", erros));
        }
    }

    private void validarValor(String chave, TipoVariavelTemplate tipo, String valor, List<String> erros) {
        TipoVariavelTemplate tipoEfetivo = tipo == null ? TipoVariavelTemplate.TEXTO : tipo;

        try {
            switch (tipoEfetivo) {
                case NUMERO -> new BigDecimal(valor.replace(",", "."));
                case MOEDA -> new BigDecimal(valor.replaceAll("[^0-9,.-]", "").replace(",", "."));
                case DATA -> LocalDate.parse(valor);
                case TELEFONE -> {
                    if (!TELEFONE_PATTERN.matcher(valor).matches()) {
                        erros.add("Valor invalido para variavel " + chave + ": esperado TELEFONE.");
                    }
                }
                case EMAIL -> {
                    if (!EMAIL_PATTERN.matcher(valor).matches()) {
                        erros.add("Valor invalido para variavel " + chave + ": esperado EMAIL.");
                    }
                }
                case URL -> URI.create(valor).toURL();
                case BOOLEANO -> {
                    String normalizado = removerAcentos(valor).toLowerCase(Locale.ROOT);
                    if (!Set.of("true", "false", "sim", "nao", "1", "0").contains(normalizado)) {
                        erros.add("Valor invalido para variavel " + chave + ": esperado BOOLEANO.");
                    }
                }
                case TEXTO -> {
                }
            }
        } catch (DateTimeParseException ex) {
            erros.add("Valor invalido para variavel " + chave + ": esperado DATA no formato ISO yyyy-MM-dd.");
        } catch (Exception ex) {
            erros.add("Valor invalido para variavel " + chave + ": esperado " + tipoEfetivo + ".");
        }
    }

    private Set<String> extrairVariaveisDoTexto(String texto) {
        Set<String> variaveis = new LinkedHashSet<>();
        if (texto == null) {
            return variaveis;
        }

        Matcher matcher = VARIAVEL_PATTERN.matcher(texto);
        while (matcher.find()) {
            variaveis.add(matcher.group(1));
        }

        return variaveis;
    }

    private List<TemplateVariavelDTO> variaveisLegadas(Set<String> variaveisObrigatorias) {
        if (variaveisObrigatorias == null || variaveisObrigatorias.isEmpty()) {
            return List.of();
        }

        return variaveisObrigatorias.stream()
                .map(chave -> new TemplateVariavelDTO(
                        chave,
                        humanizar(chave),
                        TipoVariavelTemplate.TEXTO,
                        true,
                        null))
                .toList();
    }

    private TemplateVariavel toModel(TemplateVariavelDTO dto) {
        TemplateVariavel variavel = new TemplateVariavel();
        variavel.setChave(dto.chave().trim());
        variavel.setLabel(dto.label() == null || dto.label().isBlank()
                ? humanizar(dto.chave())
                : dto.label().trim());
        variavel.setTipo(dto.tipo() == null ? TipoVariavelTemplate.TEXTO : dto.tipo());
        variavel.setObrigatoria(dto.obrigatoria() == null ? Boolean.TRUE : dto.obrigatoria());
        variavel.setExemplo(dto.exemplo());
        return variavel;
    }

    private TemplateVariavelDTO toDTO(TemplateVariavel variavel) {
        return new TemplateVariavelDTO(
                variavel.getChave(),
                variavel.getLabel(),
                variavel.getTipo(),
                variavel.getObrigatoria(),
                variavel.getExemplo());
    }

    private Set<String> variaveisObrigatorias(List<TemplateVariavel> variaveis) {
        Set<String> obrigatorias = new LinkedHashSet<>();
        for (TemplateVariavel variavel : variaveis) {
            if (Boolean.TRUE.equals(variavel.getObrigatoria())) {
                obrigatorias.add(variavel.getChave());
            }
        }
        return obrigatorias;
    }

    private void validarChave(String chave) {
        if (!CHAVE_PATTERN.matcher(chave).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chave do template deve usar apenas letras minusculas, numeros e hifens.");
        }
    }

    private void validarChaveVariavel(String chave, List<String> erros) {
        if (!VARIAVEL_PATTERN.matcher("{{" + chave + "}}").matches()) {
            erros.add("Chave de variavel invalida: " + chave + ".");
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
        String semAcentos = removerAcentos(chave == null ? "" : chave.trim());

        return semAcentos
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private String removerAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private String humanizar(String chave) {
        if (chave == null || chave.isBlank()) {
            return "";
        }

        String texto = chave.replace(".", " ").replace("_", " ").replace("-", " ");
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    private String juntarTexto(String assunto, String conteudo) {
        return (assunto == null ? "" : assunto) + "\n" + (conteudo == null ? "" : conteudo);
    }

    private TemplateNotificacaoResponseDTO toResponse(TemplateNotificacao template) {
        List<TemplateVariavel> variaveis = template.getVariaveis();
        if ((variaveis == null || variaveis.isEmpty()) && template.getVariaveisObrigatorias() != null) {
            variaveis = variaveisLegadas(template.getVariaveisObrigatorias()).stream()
                    .map(this::toModel)
                    .toList();
        }

        return new TemplateNotificacaoResponseDTO(
                template.getIdModelo(),
                template.getIdOrganizacao(),
                template.getNome(),
                template.getChave(),
                template.getCanal(),
                template.getAssunto(),
                template.getCorpo(),
                template.getAtivo(),
                variaveis.stream().map(this::toDTO).toList(),
                template.getVariaveisObrigatorias(),
                template.getVersao(),
                template.getDtCriacao(),
                template.getDtAtualizacao());
    }
}

package com.notificacao_api.service.github;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.enums.GithubDestinatariosModo;
import com.notificacao_api.model.OrganizacaoConfiguracao;

@Service
public class GithubRegrasPorStatusService {

    public static final int VERSAO_ATUAL = 1;

    private final ObjectMapper objectMapper;

    public GithubRegrasPorStatusService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean temRegrasPorColunaPersistidas(OrganizacaoConfiguracao config) {
        return StringUtils.hasText(config != null ? config.getDsGithubRegrasPorStatus() : null);
    }

    public GithubRegrasPorStatusDocumento ler(OrganizacaoConfiguracao config) {
        if (config == null) {
            return GithubRegrasPorStatusDocumento.vazio();
        }
        String raw = config.getDsGithubRegrasPorStatus();
        if (StringUtils.hasText(raw)) {
            try {
                GithubRegrasPorStatusDocumento doc =
                        objectMapper.readValue(raw, GithubRegrasPorStatusDocumento.class);
                if (doc.colunas == null) {
                    doc.colunas = new LinkedHashMap<>();
                }
                return doc;
            } catch (JsonProcessingException ex) {
                return derivarDeLegacy(config);
            }
        }
        return derivarDeLegacy(config);
    }

    public void aplicarJson(OrganizacaoConfiguracao config, String json) {
        if (!StringUtils.hasText(json)) {
            config.setDsGithubRegrasPorStatus(null);
            return;
        }
        config.setDsGithubRegrasPorStatus(json.trim());
        try {
            GithubRegrasPorStatusDocumento doc = objectMapper.readValue(json, GithubRegrasPorStatusDocumento.class);
            sincronizarListasLegadas(config, doc);
        } catch (JsonProcessingException ignored) {
            // mantem JSON; listas legadas nao atualizadas
        }
    }

    public Optional<RegraColunaResolvida> resolverPorStatusDestino(
            OrganizacaoConfiguracao config, String statusDestino) {
        if (!StringUtils.hasText(statusDestino) || config == null) {
            return Optional.empty();
        }
        GithubRegrasPorStatusDocumento doc = ler(config);
        if (!temRegrasPorColunaPersistidas(config) && doc.colunas.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, GithubRegraColunaJson> entry : doc.colunas.entrySet()) {
            GithubRegraColunaJson coluna = entry.getValue();
            if (coluna == null) {
                continue;
            }
            String nome = coluna.nome != null ? coluna.nome : "";
            if (nomesStatusEquivalentes(statusDestino, nome)) {
                return Optional.of(new RegraColunaResolvida(entry.getKey(), coluna));
            }
        }
        return Optional.empty();
    }

    public void sincronizarListasLegadas(OrganizacaoConfiguracao config, GithubRegrasPorStatusDocumento doc) {
        if (doc == null || doc.colunas == null || doc.colunas.isEmpty()) {
            return;
        }
        Set<String> geral = new TreeSet<>();
        Set<String> pr = new TreeSet<>();
        Set<String> issue = new TreeSet<>();
        for (GithubRegraColunaJson coluna : doc.colunas.values()) {
            if (coluna == null || !StringUtils.hasText(coluna.nome)) {
                continue;
            }
            String nome = coluna.nome.trim();
            AoEntrarJson ao = coluna.aoEntrar != null ? coluna.aoEntrar : new AoEntrarJson();
            if (ao.fluxoGeral) {
                geral.add(nome);
            }
            if (ao.prAvaliadores) {
                pr.add(nome);
            }
            if (ao.issueAvaliadores) {
                issue.add(nome);
            }
        }
        config.setDsGithubStatusDisparo(geral.isEmpty() ? null : String.join(", ", geral));
        config.setDsGithubPrStatusDisparo(pr.isEmpty() ? null : String.join(", ", pr));
        config.setDsGithubIssueStatusDisparo(issue.isEmpty() ? null : String.join(", ", issue));
    }

    public OrganizacaoConfiguracao configEfetivaDestinatarios(
            OrganizacaoConfiguracao base, GithubRegraColunaJson coluna) {
        if (coluna == null || coluna.destinatarios == null) {
            return base;
        }
        DestinatariosJson dest = coluna.destinatarios;
        if (!StringUtils.hasText(dest.modo) || "INHERIT".equalsIgnoreCase(dest.modo.trim())) {
            return base;
        }
        OrganizacaoConfiguracao copia = clonarDestinatarios(base);
        try {
            copia.setDsGithubDestinatariosModo(
                    GithubDestinatariosModo.fromString(dest.modo.trim()).name());
        } catch (Exception ex) {
            return base;
        }
        if (dest.extras != null) {
            String extras = dest.extras.trim();
            copia.setDsGithubDestinatariosExtras(extras.isEmpty() ? null : extras);
        }
        return copia;
    }

    public String cenarioTemplateColuna(GithubRegraColunaJson coluna) {
        if (coluna == null || coluna.mensagem == null) {
            return null;
        }
        MensagemJson msg = coluna.mensagem;
        if (Boolean.TRUE.equals(msg.textoProprioColuna)) {
            return null;
        }
        if (msg.usarTemplatePadrao == null || msg.usarTemplatePadrao) {
            return null;
        }
        return StringUtils.hasText(msg.cenarioId) ? msg.cenarioId.trim() : null;
    }

    public Optional<TextoTemplateColuna> textoTemplateColuna(GithubRegraColunaJson coluna) {
        if (coluna == null || coluna.mensagem == null) {
            return Optional.empty();
        }
        MensagemJson msg = coluna.mensagem;
        if (!Boolean.TRUE.equals(msg.textoProprioColuna)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(msg.mensagemColuna)) {
            return Optional.empty();
        }
        String assunto = StringUtils.hasText(msg.assuntoColuna) ? msg.assuntoColuna.trim() : null;
        return Optional.of(new TextoTemplateColuna(assunto, msg.mensagemColuna.trim()));
    }

    public record TextoTemplateColuna(String assunto, String mensagem) {}

    private OrganizacaoConfiguracao clonarDestinatarios(OrganizacaoConfiguracao base) {
        OrganizacaoConfiguracao copia = new OrganizacaoConfiguracao();
        copia.setDsGithubDestinatariosModo(base.getDsGithubDestinatariosModo());
        copia.setDsGithubDestinatariosExtras(base.getDsGithubDestinatariosExtras());
        copia.setGithubIgnorarSemResponsavel(base.getGithubIgnorarSemResponsavel());
        copia.setGithubNaoNotificarMovimentador(base.getGithubNaoNotificarMovimentador());
        return copia;
    }

    private GithubRegrasPorStatusDocumento derivarDeLegacy(OrganizacaoConfiguracao config) {
        GithubRegrasPorStatusDocumento doc = new GithubRegrasPorStatusDocumento();
        doc.versao = VERSAO_ATUAL;
        doc.colunas = new LinkedHashMap<>();
        Set<String> nomes = new LinkedHashSet<>();
        nomes.addAll(parseNomesLista(config.getDsGithubStatusDisparo()));
        nomes.addAll(parseNomesLista(config.getDsGithubPrStatusDisparo()));
        nomes.addAll(parseNomesLista(config.getDsGithubIssueStatusDisparo()));
        int idx = 0;
        for (String nome : nomes) {
            AoEntrarJson ao = new AoEntrarJson();
            ao.fluxoGeral = contemNomeLista(config.getDsGithubStatusDisparo(), nome);
            ao.prAvaliadores = contemNomeLista(config.getDsGithubPrStatusDisparo(), nome);
            ao.issueAvaliadores = contemNomeLista(config.getDsGithubIssueStatusDisparo(), nome);
            GithubRegraColunaJson col = new GithubRegraColunaJson();
            col.nome = nome;
            col.aoEntrar = ao;
            col.destinatarios = new DestinatariosJson();
            col.destinatarios.modo = "INHERIT";
            col.mensagem = new MensagemJson();
            col.mensagem.usarTemplatePadrao = true;
            doc.colunas.put("legacy-" + idx++, col);
        }
        return doc;
    }

    private boolean contemNomeLista(String lista, String nome) {
        for (String parte : parseNomesLista(lista)) {
            if (nomesStatusEquivalentes(parte, nome)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseNomesLista(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String parte : raw.split("[,;]+")) {
            String nome = parte.trim();
            if (!nome.isEmpty()) {
                out.add(nome);
            }
        }
        return out;
    }

    public static boolean nomesStatusEquivalentes(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        String na = normalizarStatusComparacao(a);
        String nb = normalizarStatusComparacao(b);
        if (na.equals(nb)) {
            return true;
        }
        String sufixoA = extrairTextoEntreParentesesFinal(na);
        String sufixoB = extrairTextoEntreParentesesFinal(nb);
        if (StringUtils.hasText(sufixoA) && sufixoA.equals(nb)) {
            return true;
        }
        if (StringUtils.hasText(sufixoB) && sufixoB.equals(na)) {
            return true;
        }
        return na.endsWith("(" + nb + ")") || nb.endsWith("(" + na + ")");
    }

    private static String extrairTextoEntreParentesesFinal(String textoNormalizado) {
        int open = textoNormalizado.lastIndexOf('(');
        int close = textoNormalizado.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        return textoNormalizado.substring(open + 1, close).trim();
    }

    private static String normalizarStatusComparacao(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }

    public record RegraColunaResolvida(String optionId, GithubRegraColunaJson regra) {

        public boolean fluxoGeral() {
            return regra != null && regra.aoEntrar != null && Boolean.TRUE.equals(regra.aoEntrar.fluxoGeral);
        }

        public boolean prAvaliadores() {
            return regra != null && regra.aoEntrar != null && Boolean.TRUE.equals(regra.aoEntrar.prAvaliadores);
        }

        public boolean issueAvaliadores() {
            return regra != null && regra.aoEntrar != null && Boolean.TRUE.equals(regra.aoEntrar.issueAvaliadores);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GithubRegrasPorStatusDocumento {
        public int versao = VERSAO_ATUAL;
        public Map<String, GithubRegraColunaJson> colunas = new LinkedHashMap<>();

        public static GithubRegrasPorStatusDocumento vazio() {
            GithubRegrasPorStatusDocumento doc = new GithubRegrasPorStatusDocumento();
            doc.colunas = new LinkedHashMap<>();
            return doc;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GithubRegraColunaJson {
        public String nome;
        public AoEntrarJson aoEntrar;
        public DestinatariosJson destinatarios;
        public MensagemJson mensagem;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AoEntrarJson {
        public boolean fluxoGeral;
        public boolean prAvaliadores;
        public boolean issueAvaliadores;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DestinatariosJson {
        public String modo = "INHERIT";
        public String extras;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MensagemJson {
        public Boolean usarTemplatePadrao = true;
        public String cenarioId;
        /** Texto exclusivo desta coluna do kanban (não compartilha com outras colunas). */
        public Boolean textoProprioColuna;
        public String assuntoColuna;
        public String mensagemColuna;
    }
}

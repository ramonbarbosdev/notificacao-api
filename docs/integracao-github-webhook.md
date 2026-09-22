# GitHub App / Webhook → WhatsApp

Integracao multi-tenant: **super admin** habilita a feature `GITHUB_WEBHOOK` na organizacao. O tenant usa **API Key** com scope `NOTIFICACOES_ENVIAR` para autenticar o webhook e identificar a organizacao.

## Habilitar

1. Super admin: `PUT /admin/organizacoes/{id}/features` com `"GITHUB_WEBHOOK": true`
2. Conectar o WhatsApp da organizacao (sessao gateway) — numero de **origem** das notificacoes
3. Cada desenvolvedor abre o **link de ativacao** (`linkWhatsappAtivacao` em `GET /app/integracao/github/webhook`) e envia a frase configurada (`fraseAtivacaoWhatsapp`; padrao: `Quero receber notificação, do github!`)
4. Admin pode personalizar em `PUT /app/configuracoes` com `dsGithubFraseAtivacaoWhatsapp` (string vazia volta ao padrao)
5. Em seguida, responde com o **login do GitHub** (ex.: `octocat`) para vincular o numero (cadastro em `organizacao_github_responsavel`; login nulo = aguardando resposta)
6. **Regras de notificacao** (aba GitHub / `PUT /app/configuracoes`): gatilhos (`githubNotificarStatusAlterado`, `githubNotificarTarefaCriada`, etc.), destinatarios (`dsGithubDestinatariosModo`, `dsGithubDestinatariosExtras`), `githubNaoNotificarMovimentador`, `githubIgnorarSemResponsavel`.
7. **Regras por coluna (fluxograma na UI):** `dsGithubRegrasPorStatus` (JSON, migration V42). Chave de cada coluna = `optionId` do campo Status no Project v2 (GraphQL). Na aba GitHub → **Regras → Fluxos por coluna**, o admin monta o pipeline (entrou na coluna → tipos Geral/PR/Issue → destinatarios/mensagem no ramo Geral). No nó **Mensagem**, **Editar texto do cenário** abre o mesmo editor de templates por tipo de evento; **Editar template padrão** leva à aba **Mensagem** com o editor global. Sub-aba **Eventos gerais**: padroes da org, gatilhos sem coluna e `dsGithubStatusDisparoGatilhos`. Ao salvar, a API espelha nomes nas listas legadas `dsGithubStatusDisparo`, `dsGithubPrStatusDisparo`, `dsGithubIssueStatusDisparo`. No webhook, com JSON persistido, o fluxo usa o mapa (match por nome normalizado de `changes.field_value.to.name`); sem JSON, vale listas em virgula.
8. Opcional (legado / espelho): `dsGithubStatusDisparo` (virgula). Com JSON por coluna, o fluxo **Geral** ativo no fluxograma substitui mentalmente essa lista. `dsGithubStatusDisparoGatilhos` define quais gatilhos exigem coluna com **Geral** ativo (**Padrao** se NULL: `STATUS_ALTERADO,REORDENADO`). Configuracao em **Regras → Eventos gerais**.
9. Templates WhatsApp em `dsGithubTemplateMensagemWhatsapp` (principal) e opcionalmente `dsGithubTemplateAssuntoWhatsapp` (`PUT /app/configuracoes` ou aba GitHub — editor completo no frontend). **So o corpo da mensagem vai no WhatsApp**; o assunto e prefixado ao corpo. Use chaves ASCII `{{` `}}`. Apos `*Responsaveis:*` use `{{responsaveis}}`, nao `{{responsaveis_linha}}`.
10. Catálogo de variáveis (chave, descrição, origem no payload, exemplo): `variaveisTemplateDetalhadas` em `GET /app/integracao/github/webhook`. Cenários de preview: `cenariosPreview` no mesmo endpoint.
11. Preview do template (sem enviar WhatsApp): `POST /app/integracao/github/webhook/template/preview` com `templateAssunto`, `templateMensagem`, `cenarioId` (ex.: `projects_v2_edited`). Retorna `textoWhatsapp` e `variaveisDesconhecidas` para placeholders nao suportados.
12. Criar API Key com scope `NOTIFICACOES_ENVIAR`

Destino WhatsApp e variavel `{{responsaveis}}`: **assignee** da issue no payload; se vazio (comum em Project v2), usa **`sender`** (quem moveu/editou o card). Opt-in WhatsApp obrigatorio para envio real.

Sem responsavel com opt-in: por padrao **gera registro na fila** (bloqueado, `github:@login`). Desative em `PUT /app/configuracoes` com `webhookRegistrarFilaSemDestinatario: false` para ignorar o evento (nao entra na fila).

`projects_v2_item`: `edited` (ex.: mudanca de Status), `reordered` (com ou sem mudanca de coluna; sem coluna usa status **Reordenado**), `deleted` (status **Removido**). Inclua esses nomes em `dsGithubStatusDisparo` se usar filtro.

### Issue no Project v2 (avaliadores)

Para cards com `content_type` **Issue** (tarefa no board, nao PR):

1. Ative `githubIssueAvisarAvaliadores` em `PUT /app/configuracoes`.
2. Defina `dsGithubIssueStatusDisparo` (virgula), ex.: `Validação Interna (Develop)` ou atalho `Develop` (casa com o texto entre parenteses do nome da coluna) — dispara quando o card **entra** na coluna (`changes.field_value.to.name`).
3. Destinatarios: mesma lista `dsGithubPrLoginsAvaliadores` (logins com opt-in WhatsApp).

Independente do filtro geral `dsGithubStatusDisparo` e do gatilho `githubNotificarStatusAlterado` quando o fluxo Issue/PR avaliadores casa. PR e Issue usam listas de status **separadas** (`dsGithubPrStatusDisparo` vs `dsGithubIssueStatusDisparo`).

### Pull Request no Project v2 (avaliadores)

Para cards com `content_type` **PullRequest** (ou payload com `pull_request`):

1. Ative `githubPrAvisarAvaliadores` em `PUT /app/configuracoes` (aba GitHub).
2. Defina `dsGithubPrStatusDisparo` (virgula), ex.: `Em revisao, Validacao` — quando o card do PR entrar nessa coluna/status, dispara o aviso.
3. Defina `dsGithubPrLoginsAvaliadores` com logins GitHub do time (virgula). Cada login precisa de **opt-in WhatsApp** na organizacao.
4. O envio usa o template GitHub padrao; `{{titulo}}` e `{{url}}` vêm do `pull_request` quando disponivel.

Se o status do PR estiver na lista PR, os destinatarios sao **somente** os logins configurados (nao assignees). Fora dessa lista, vale o fluxo normal de issues/cards e `dsGithubStatusDisparo`.

**Atencao:** `dsGithubPrStatusDisparo` e **independente** de `dsGithubStatusDisparo` (filtro geral do kanban). Para avisar avaliadores, o status da coluna precisa estar em `dsGithubPrStatusDisparo` com o **mesmo texto** que o GitHub envia em `changes.field_value.to.name` (acentos ignorados). Se o webhook nao trazer o nome da coluna, a API usa status `Editado` — que **nao** casa com o nome da coluna.

Checklist PR avaliadores sem WhatsApp:

1. Card no Project v2 com `content_type` **PullRequest** (issue comum nao entra no fluxo PR).
2. `githubPrAvisarAvaliadores` ligado e `dsGithubPrLoginsAvaliadores` com os logins (virgula).
3. Status configurado em **`dsGithubPrStatusDisparo`** (nao so no filtro geral).
4. Cada login com **opt-in** WhatsApp (`GET /app/integracao/github/responsaveis`, `habilitado: true`). Admin pode **desativar** (`PATCH .../responsaveis/{id}` body `{"ativo":false}`) ou **excluir** (`DELETE .../responsaveis/{id}`) na aba Habilitados do frontend. Quando o dev refaz opt-in em **outro numero** (mesmo login), a API grava `nu_whatsapp_anterior` / `dt_mudanca_whatsapp` e devolve `alertaAdministrador` na lista — o numero antigo deixa de receber alertas. Eventos ficam em auditoria (`GITHUB_RESPONSAVEL`).
5. Logs: `GitHub webhook PR avaliadores` (disparou) ou `PR avaliadores nao aplicado` (motivo: `pullRequest=false` ou status fora da lista).

### Vincular Project v2 (kanban) na configuracao

Um **Project v2** por organizacao; a API lista colunas reais do campo **Status** via GraphQL para o frontend montar multi-select (sem digitar nomes manualmente).

| Campo `PUT /app/configuracoes` | Uso |
|--------------------------------|-----|
| `dsGithubOrganizationLogin` | Login da org GitHub (ex. `gpi-organizacao`); preenchido automaticamente no primeiro webhook com `organization.login` |
| `dsGithubProjectV2NodeId` | Node id do project (ex. `PVT_kwDOEKnzAs4BWPN4` em `projects_v2_item.project_node_id`) |
| `nuGithubProjectV2Number` | Numero do project (opcional, exibicao) |

Endpoints (JWT admin ou `GLOBAL_API_KEY`):

| Metodo | Path |
|--------|------|
| `GET` | `/app/integracao/github/projects?orgLogin=` (opcional; default = config) |
| `GET` | `/app/integracao/github/project/status-opcoes?projectNodeId=` (opcional; default = project vinculado) |
| `GET` | `/app/integracao/github/project/vinculo` — project, opcoes de Status e listas atuais de disparo (geral, Issue, PR) |
| `GET` | `/app/integracao/github/webhook/decisoes?pagina=0&tamanho=20` — historico visual de decisoes do webhook (resultado, fluxo, logins, detalhes) |

Requisitos: GitHub App (ou PAT) com acesso de leitura a **Projects** na organizacao. Os filtros `dsGithubStatusDisparo`, `dsGithubIssueStatusDisparo` e `dsGithubPrStatusDisparo` continuam sendo listas de **nomes** de coluna (virgula), alinhados a `changes.field_value.to.name` do webhook.

### Enriquecimento GraphQL (Project v2)

Muitos webhooks `projects_v2_item` trazem apenas `content_node_id` e `content_type`, sem `issue`/`pull_request` no JSON. Sem isso, `{{url}}` no template WhatsApp pode ficar vazio.

**Nao ha configuracao GitHub obrigatoria em `.env` ou `application.properties`.** Tudo abaixo e por organizacao em `organizacao_configuracao` (aba **GitHub → Conexão** ou `PUT /app/configuracoes`).

#### GitHub App (recomendado)

| Campo API | Coluna | Observacao |
|-----------|--------|------------|
| `githubAppId` | `nu_github_app_id` | ID numerico do App |
| `githubAppPrivateKey` | `ds_github_app_private_key_enc` | PEM write-only; criptografado |
| `githubInstallationId` | `nu_github_installation_id` | Pode ser preenchido automaticamente pelo `installation.id` do webhook |

A API gera JWT RS256, solicita `POST /app/installations/{id}/access_tokens` e mantem o `ghs_` **somente em memoria** (cache com renovacao antes do `expires_at`, usando `nu_github_installation_token_skew_segundos`).

#### Rede e timeouts (null = default no codigo)

| Campo API | Default |
|-----------|---------|
| `githubGraphqlUrl` | `https://api.github.com/graphql` |
| `githubApiBaseUrl` | `https://api.github.com` |
| `githubHttpConnectTimeoutMs` | `10000` |
| `githubHttpReadTimeoutMs` | `30000` |
| `githubInstallationTokenSkewSegundos` | `300` |

#### PAT opcional (fallback)

1. `githubGraphqlToken` — PAT com leitura nos repositorios do project, se o App nao estiver configurado ou falhar.
2. Armazenado criptografado (`ds_github_graphql_token_enc`). A API **nunca** devolve o valor; `githubGraphqlTokenConfigurado: true/false`.
3. String vazia remove o PAT; omitir o campo mantem o atual.

#### Ordem do bearer no GraphQL

1. Installation token (`ghs_`) via App + `installation.id` do webhook ou `githubInstallationId` salvo.
2. PAT (`githubGraphqlToken`).

Ao processar o webhook, se houver `content_node_id`, a API chama a URL GraphQL configurada com:

```graphql
query($nodeId: ID!) {
  node(id: $nodeId) {
    __typename
    ... on Issue {
      title
      number
      url
      assignees(first: 20) { nodes { login name } }
    }
    ... on PullRequest {
      title
      number
      url
      assignees(first: 20) { nodes { login name } }
    }
    ... on DraftIssue { title }
  }
}
```

5. `title`, `url`, `number` e logins em `assignees.nodes` substituem/complementam o payload quando presentes (`{{responsaveis}}` usa os logins do GraphQL se o webhook nao trouxer `issue.assignees`). Falha de rede, token ausente ou `errors` no GraphQL **nao** interrompem o webhook.
6. Template: use `{{numero}}` para o numero da issue/PR; `{{responsaveis}}` para `@joao, @maria` (nomes exibidos no GraphQL nao entram automaticamente no texto — apenas logins).

Exemplo de payload minimo:

```json
{
  "action": "edited",
  "projects_v2_item": {
    "content_type": "Issue",
    "content_node_id": "I_kwDOSOM5YM8AAAABRzO-Gw"
  },
  "changes": { "field_value": { "to": { "name": "Em Andamento" } } },
  "sender": { "login": "dev1" }
}
```

Com token valido, `{{url}}` pode virar `https://github.com/org/repo/issues/42` e `{{titulo}}` o titulo real da issue.

## URL do webhook (GitHub App)

```text
POST https://<host>/api/webhooks/github?key=<API_KEY_COMPLETA>
```

Consulte `GET /app/integracao/github/webhook` (autenticado) para o template.

## Webhook secret no GitHub

No GitHub App, configure o **Webhook secret** com o **mesmo valor da API Key completa** (`nak_xxx.yyy`). A API valida `X-Hub-Signature-256` usando essa chave.

Alternativa: enviar a API Key no header `X-API-KEY` (util em testes manuais; o GitHub App em geral usa apenas a query `key=` na URL).

## Eventos tratados

| `X-GitHub-Event` | Acao |
|------------------|------|
| `ping` | Apenas confirma (sem WhatsApp) |
| `project_card` | `moved`, `created` |
| `projects_v2_item` | `edited`, `reordered`, `deleted` |
| `issues` | `opened`, `closed`, `reopened`, `assigned`, `labeled` |

Demais eventos retornam `200` sem envio.

O header `X-GitHub-Event` deve ser `projects_v2_item` (a API tambem infere pelo JSON se o header vier vazio).

## Diagnostico (webhook 200 mas sem WhatsApp / fila vazia)

Com o payload de coluna **Em Andamento** e `sender` **ramonbarbosdev**, a fila so recebe item se:

1. **Fila:** com opt-in envia WhatsApp; sem opt-in aparece na fila como bloqueado (`github:@login`).
2. **Filtro:** `dsGithubStatusDisparo` vazio **ou** contem `Em Andamento` (acentos ignorados).
3. **Evento:** `X-GitHub-Event: projects_v2_item` (nao `push` nem outro).
4. **Enfileiramento:** WhatsApp da org conectado; se falhar, o GitHub recebe **4xx** (nao 200).

Logs da API (mesmo `delivery` do GitHub):

| Log | Significado |
|-----|-------------|
| `GitHub webhook analisado ... logins=[ramonbarbosdev]` | Sender/assignee ok |
| `login sem opt-in` / `WhatsApp nao cadastrado` | Falta opt-in |
| `ignorado por filtro de status` | Ajuste `dsGithubStatusDisparo` |
| `ignorado (evento/acao nao tratado)` | Header/evento errado |
| `WhatsApp enfileirado via GitHub webhook` | Entrou na fila |
| `falhou ao enfileirar` | Sessao WhatsApp / validacao de envio |

## Variaveis de ambiente

Nao ha mais `GITHUB_WEBHOOK_*` no servidor. Tudo por **feature flag**, **API Key** e **configuracao da organizacao**.

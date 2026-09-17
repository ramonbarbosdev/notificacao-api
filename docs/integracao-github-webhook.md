# GitHub App / Webhook → WhatsApp

Integracao multi-tenant: **super admin** habilita a feature `GITHUB_WEBHOOK` na organizacao. O tenant usa **API Key** com scope `NOTIFICACOES_ENVIAR` para autenticar o webhook e identificar a organizacao.

## Habilitar

1. Super admin: `PUT /admin/organizacoes/{id}/features` com `"GITHUB_WEBHOOK": true`
2. Conectar o WhatsApp da organizacao (sessao gateway) — numero de **origem** das notificacoes
3. Cada desenvolvedor abre o **link de ativacao** (`linkWhatsappAtivacao` em `GET /app/integracao/github/webhook`) e envia a frase configurada (`fraseAtivacaoWhatsapp`; padrao: `Quero receber notificação, do github!`)
4. Admin pode personalizar em `PUT /app/configuracoes` com `dsGithubFraseAtivacaoWhatsapp` (string vazia volta ao padrao)
5. Em seguida, responde com o **login do GitHub** (ex.: `octocat`) para vincular o numero (cadastro em `organizacao_github_responsavel`; login nulo = aguardando resposta)
6. Opcional: `dsGithubStatusDisparo` nas configuracoes (virgula) para filtrar colunas/status
7. Criar API Key com scope `NOTIFICACOES_ENVIAR`

Destino WhatsApp: opt-in do **assignee** ou **sender** (Project v2) cadastrado via bot.

Sem responsavel com opt-in: por padrao **gera registro na fila** (bloqueado, `github:@login`). Desative em `PUT /app/configuracoes` com `webhookRegistrarFilaSemDestinatario: false` para ignorar o evento (nao entra na fila).

Evento `reordered` sem mudanca de coluna Status e ignorado.

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
| `projects_v2_item` | `edited`, `created`, `reordered` |
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

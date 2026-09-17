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

O WhatsApp e enviado para o **assignee** da issue, somente se o login foi ativado pelo opt-in no WhatsApp.

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

## Variaveis de ambiente

Nao ha mais `GITHUB_WEBHOOK_*` no servidor. Tudo por **feature flag**, **API Key** e **configuracao da organizacao**.

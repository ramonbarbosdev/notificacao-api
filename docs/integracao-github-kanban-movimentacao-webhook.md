# GitHub — webhook externo de movimentação no kanban

Encaminha eventos de mudança de coluna/status no Project v2 para uma URL configurada **por organização** (painel ou API).

## API

```http
GET   /api/app/integracao/github/kanban-movimentacao-webhook
PATCH /api/app/integracao/github/kanban-movimentacao-webhook
POST  /api/app/integracao/github/kanban-movimentacao-webhook/teste
```

### PATCH — exemplo

```json
{
  "kanbanMovimentacaoWebhookUrl": "https://…",
  "kanbanMovimentacaoWebhookAuthorization": "Bearer …",
  "kanbanMovimentacaoWebhookHabilitado": true,
  "githubWhatsappDiretoHabilitado": true
}
```

### GET — resposta

```json
{
  "kanbanMovimentacaoWebhookUrl": "https://…",
  "kanbanMovimentacaoWebhookHabilitado": true,
  "kanbanMovimentacaoWebhookAuthorizationConfigurado": true,
  "githubWhatsappDiretoHabilitado": true
}
```

Migrations: `V48` (colunas legadas `seven_*` se aplicável) e `V49` (rename para `github_kanban_movimentacao_*`).

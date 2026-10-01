# GitHub — webhook externo de movimentação no kanban

Encaminha **mudanças de coluna (Status)** no Project v2 para uma URL configurada **por organização**. Usa as mesmas regras de coluna do módulo Project v2 (status disparo / fluxograma) que o WhatsApp.

## Destino: WhatsApp, bot, ambos ou nenhum

No painel (**GitHub → Módulos → Project v2**) ou via `PATCH`:

| Objetivo | `kanbanMovimentacaoWebhookHabilitado` | `githubWhatsappDiretoHabilitado` |
|----------|--------------------------------------|----------------------------------|
| Somente WhatsApp | `false` | `true` |
| Somente webhook (bot) | `true` | `false` |
| Ambos | `true` | `true` |
| Nenhum | `false` | `false` |

## Modo de envio ao bot

| Campo | Valores | Padrão |
|-------|---------|--------|
| `kanbanMovimentacaoWebhookModoEnvio` | `LOTE`, `IMEDIATO` | `LOTE` |
| `kanbanMovimentacaoWebhookIntervaloMinutos` | 5–1440 (só em `LOTE`) | `30` |

- **LOTE:** a API acumula movimentações na fila e envia **um POST** quando o intervalo vence e há itens novos.
- **IMEDIATO:** um POST por movimentação (comportamento anterior).

## Filtros (ruído descartado)

O webhook externo **não** é chamado para:

- `reordered` / `deleted` no Project v2
- `edited` sem mudança do campo **Status**
- mudança para a **mesma coluna** (`de` ≈ `para`)

## Payload

### Item (`movimentacoes[]` ou POST imediato)

Campos principais (snake_case): `repo`, `tipo` (`issue` / `pull_request`), `numero`, `titulo`, `url`, `de`, `para`, `movido_por`, `movido_por_detalhe` (`login`, `nome`), `responsaveis`, `responsaveis_detalhe`, `labels`, `prioridade`, `target_date`, `ocorrido_em`.

Nomes vêm do payload GitHub, cadastro da equipe/WhatsApp e, no flush do lote, GraphQL GitHub para logins faltantes.

### Lote

```json
{
  "tipo": "lote",
  "id_organizacao": 1,
  "periodo_inicio": "2026-10-01T15:00:00Z",
  "periodo_fim": "2026-10-01T15:30:00Z",
  "total": 2,
  "movimentacoes": [ { "repo": "…", "tipo": "issue", "…": "…" } ]
}
```

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
  "githubWhatsappDiretoHabilitado": false,
  "kanbanMovimentacaoWebhookModoEnvio": "LOTE",
  "kanbanMovimentacaoWebhookIntervaloMinutos": 30
}
```

### GET — resposta

```json
{
  "kanbanMovimentacaoWebhookUrl": "https://…",
  "kanbanMovimentacaoWebhookHabilitado": true,
  "kanbanMovimentacaoWebhookAuthorizationConfigurado": true,
  "githubWhatsappDiretoHabilitado": false,
  "kanbanMovimentacaoWebhookModoEnvio": "LOTE",
  "kanbanMovimentacaoWebhookIntervaloMinutos": 30
}
```

Migrations: `V48` (webhook org), `V49` (lote + fila `github_kanban_webhook_evento_pendente`).

Config opcional: `notificacao.github.kanban-webhook.flush-intervalo-millis` (padrão 60000) — frequência do job que verifica lotes.

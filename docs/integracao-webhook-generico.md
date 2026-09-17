# Webhook generico → fila / WhatsApp

Integracao multi-tenant: super admin habilita `WEBHOOK_GENERICO` na organizacao. Qualquer sistema (Jira, n8n, script proprio) pode `POST` eventos para enfileirar notificacao WhatsApp.

## Habilitar

1. Super admin: `PUT /admin/organizacoes/{id}/features` com `"WEBHOOK_GENERICO": true`
2. API Key com scope `NOTIFICACOES_ENVIAR`
3. WhatsApp da organizacao conectado (quando houver `destinatario` e envio real)

Instrucoes e exemplo JSON: `GET /app/integracao/webhook/generico`

## URL

```text
POST https://<host>/api/webhooks/generico?key=<API_KEY_COMPLETA>
```

Headers uteis:

| Header | Obrigatorio | Descricao |
|--------|-------------|-----------|
| `X-API-KEY` | key= ou este | Autenticacao |
| `Content-Type` | recomendado | `application/json` ou `text/plain` |
| `X-Webhook-Signature-256` | opcional | `sha256=<hmac>` do body com a API Key |
| `X-Webhook-Delivery` | opcional | Id de entrega (vira referencia se o body nao tiver) |

## Corpo JSON

```json
{
  "destinatario": "5571999999999",
  "assunto": "Titulo curto",
  "mensagem": "Corpo da mensagem",
  "referenciaExterna": "id-unico-evento"
}
```

Aliases aceitos no JSON: `titulo` (assunto), `message` / `text` (mensagem).

## Sem destinatario

Com `webhookRegistrarFilaSemDestinatario: true` (padrao) na configuracao da org, evento sem `destinatario` **registra na fila** bloqueado (`webhook:sem-destinatario`).

Com `false`, o POST retorna `200` com `sucesso: false` e **nao cria** item na fila (mesma flag da aba GitHub em Configuracoes).

## Texto puro

`Content-Type: text/plain` — o corpo inteiro e a `mensagem` (sem destinatario → mesmo comportamento de registro bloqueado).

## Resposta

`200` com `EnviarNotificacaoResposta` (`idNotificacao`, `status`, etc.).

## GitHub

Eventos de kanban/issues continuam em `POST /api/webhooks/github` (feature `GITHUB_WEBHOOK`).

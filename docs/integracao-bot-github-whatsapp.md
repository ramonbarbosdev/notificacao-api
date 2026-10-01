# Bot GitHub Project v2 + WhatsApp (API Key)

Orquestradores externos autenticam com header `X-API-KEY` e scope **`NOTIFICACOES_ENVIAR`** na chave da organização.

Documentação interativa: frontend → **Documentação da API → Bot GitHub + WhatsApp**.

## Segurança HTTP

Rotas `/app/integracao/github/**` aceitam `ROLE_ADMIN`, `ROLE_USER` (painel) ou `SCOPE_NOTIFICACOES_ENVIAR` (API Key).

`PATCH` de configuração GitHub e gestão de responsáveis exige `ROLE_ADMIN` (JWT do painel).

## Criar a chave

Scopes mínimos para o bot:

- `NOTIFICACOES_ENVIAR` — leitura GitHub integrada + envio (fila e webhook)
- `NOTIFICACOES_CONSULTAR` — opcional (acompanhar fila)

## Endpoints principais

| Área | Método | Path |
|------|--------|------|
| Saúde | GET | `/app/integracao/status` |
| GitHub meta | GET | `/app/integracao/github/webhook` |
| Hub / módulos (leitura) | GET | `/app/integracao/github`, `/modulos/projects-v2`, … |
| Project v2 | GET | `/app/integracao/github/projects`, `/project/vinculo`, `/project/status-opcoes` |
| Item | POST | `/app/integracao/github/graphql/consulta` |
| Destinatários | GET | `/app/integracao/github/responsaveis` |
| Envio | POST | `/app/notificacoes/enviar` |

Webhook GitHub: `POST /webhooks/github?key={API_KEY}` — ver `integracao-github-webhook.md`.

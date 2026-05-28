# Tutorial de configuracoes do WhatsApp

Este tutorial explica como configurar os parametros de WhatsApp de uma organizacao na `notificacao-api`.

As configuracoes ficam vinculadas a organizacao selecionada pelo usuario logado. Para alterar esses dados, use JWT de um usuario com permissao de administrador da organizacao.

API Key (`X-API-KEY`) e indicada para sistemas externos enviarem notificacoes. Para alterar configuracoes, use login normal com `Authorization: Bearer`.

## 1. Endpoints

Consultar configuracoes da organizacao atual:

```http
GET /app/configuracoes
Authorization: Bearer TOKEN_ADMIN
```

Atualizar configuracoes da organizacao atual:

```http
PUT /app/configuracoes
Authorization: Bearer TOKEN_ADMIN
Content-Type: application/json
```

URL local completa:

```text
http://localhost:8080/api/app/configuracoes
```

## 2. Campos de WhatsApp

Campos disponiveis:

| Campo | Tipo | Padrao | Descricao |
| --- | --- | --- | --- |
| `whatsappReconexaoAutomatica` | boolean | `true` | Permite tentar reconectar automaticamente quando a sessao cair. |
| `whatsappDelayMinSegundos` | integer | `2` | Tempo minimo de espera entre envios. |
| `whatsappDelayMaxSegundos` | integer | `8` | Tempo maximo de espera entre envios. |
| `whatsappSimularDigitando` | boolean | `true` | Indica se o envio deve simular digitacao, quando suportado pelo gateway. |
| `whatsappLimitePorMinuto` | integer | `20` | Limite operacional de mensagens por minuto. |
| `whatsappLimitePorDia` | integer | `1000` | Limite operacional de mensagens por dia. |
| `whatsappModoEnvio` | string | `FILA` | Define o modo de envio. Use `FILA` para processamento assincrono/controlado. |

## 3. Configuracao recomendada

Para uso normal em producao:

```json
{
  "whatsappReconexaoAutomatica": true,
  "whatsappDelayMinSegundos": 2,
  "whatsappDelayMaxSegundos": 8,
  "whatsappSimularDigitando": true,
  "whatsappLimitePorMinuto": 20,
  "whatsappLimitePorDia": 1000,
  "whatsappModoEnvio": "FILA"
}
```

Para um ambiente mais conservador, com menor risco de bloqueios:

```json
{
  "whatsappReconexaoAutomatica": true,
  "whatsappDelayMinSegundos": 5,
  "whatsappDelayMaxSegundos": 15,
  "whatsappSimularDigitando": true,
  "whatsappLimitePorMinuto": 10,
  "whatsappLimitePorDia": 300,
  "whatsappModoEnvio": "FILA"
}
```

## 4. Exemplo de atualizacao via cURL

```bash
curl -X PUT "http://localhost:8080/api/app/configuracoes" \
  -H "Authorization: Bearer TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "nmExibicao": "Minha Organizacao",
    "dsIdioma": "pt-BR",
    "timezone": "America/Bahia",
    "nuTelefoneOperacional": "5571999999999",
    "dsEmailOperacional": "suporte@empresa.com",
    "whatsappReconexaoAutomatica": true,
    "whatsappDelayMinSegundos": 2,
    "whatsappDelayMaxSegundos": 8,
    "whatsappSimularDigitando": true,
    "whatsappLimitePorMinuto": 20,
    "whatsappLimitePorDia": 1000,
    "whatsappModoEnvio": "FILA",
    "exigirConsentimento": true,
    "consentimentoExpira": false,
    "diasExpiracaoConsentimento": null,
    "bloqueioAutomatico": true,
    "limiteFalhasParaBloqueio": 5,
    "templatesVersionamento": true,
    "templatesExigirAprovacao": false,
    "templatesValidarVariaveis": true,
    "retryAutomatico": true,
    "retryTentativas": 3,
    "retryIntervaloSegundos": 60,
    "prioridadePadrao": "NORMAL",
    "expiracaoFilaHoras": 24,
    "auditoriaHabilitada": true
  }'
```

## 5. Exemplo de resposta

```json
{
  "idOrganizacaoConfiguracao": 1,
  "idOrganizacao": 1,
  "nmExibicao": "Minha Organizacao",
  "dsLogoUrl": null,
  "dsIdioma": "pt-BR",
  "timezone": "America/Bahia",
  "nuTelefoneOperacional": "5571999999999",
  "dsEmailOperacional": "suporte@empresa.com",
  "whatsappReconexaoAutomatica": true,
  "whatsappDelayMinSegundos": 2,
  "whatsappDelayMaxSegundos": 8,
  "whatsappSimularDigitando": true,
  "whatsappLimitePorMinuto": 20,
  "whatsappLimitePorDia": 1000,
  "whatsappModoEnvio": "FILA",
  "exigirConsentimento": true,
  "consentimentoExpira": false,
  "diasExpiracaoConsentimento": null,
  "bloqueioAutomatico": true,
  "limiteFalhasParaBloqueio": 5,
  "templatesVersionamento": true,
  "templatesExigirAprovacao": false,
  "templatesValidarVariaveis": true,
  "retryAutomatico": true,
  "retryTentativas": 3,
  "retryIntervaloSegundos": 60,
  "prioridadePadrao": "NORMAL",
  "expiracaoFilaHoras": 24,
  "auditoriaHabilitada": true,
  "dtCriacao": "2026-05-28T08:51:19.416223",
  "dtAtualizacao": "2026-05-28T09:10:00.000000"
}
```

## 6. Como usar no front

Fluxo recomendado para a tela de configuracoes:

1. Ao abrir a tela, chamar `GET /app/configuracoes`.
2. Preencher os campos do formulario com a resposta.
3. Ao salvar, chamar `PUT /app/configuracoes`.
4. Exibir mensagem de sucesso quando receber HTTP `200`.

Sugestao de controles:

- `whatsappReconexaoAutomatica`: toggle.
- `whatsappDelayMinSegundos`: input numerico.
- `whatsappDelayMaxSegundos`: input numerico.
- `whatsappSimularDigitando`: toggle.
- `whatsappLimitePorMinuto`: input numerico.
- `whatsappLimitePorDia`: input numerico.
- `whatsappModoEnvio`: select, usando `FILA` como valor padrao.

## 7. Cuidados importantes

- `whatsappDelayMinSegundos` deve ser menor ou igual a `whatsappDelayMaxSegundos`.
- Limites muito altos podem aumentar risco operacional da sessao WhatsApp.
- O modo `FILA` e o mais indicado para controlar volume, retry e auditoria.
- Para integracoes externas, nao use esse endpoint com API Key; use API Key apenas para envio de notificacoes.
- Depois de alterar configuracoes, faca um envio pequeno de teste antes de liberar volume maior.

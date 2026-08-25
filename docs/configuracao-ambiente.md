# Configuracao: ambiente, properties e banco

Este documento explica onde cada tipo de configuracao deve ficar e como as camadas se sobrepõem.

## Camadas

```
.env / variaveis do deploy     → segredos e URLs por ambiente
application.properties         → defaults globais da instalacao (com :valor)
organizacao_configuracao       → override por tenant (UI Configuracoes)
Flyway (migrations)            → estrutura, seeds iniciais, feature flags
configuracao_global            → flags da plataforma SaaS (admin)
```

## O que vai no `.env`

| Categoria | Exemplos | Motivo |
|-----------|----------|--------|
| Obrigatorio | `DB_*`, `JWT_SECRET` | Segredo ou conexao por deploy |
| Integracao | `WHATSAPP_GATEWAY_*`, `META_*` | URL/chave muda entre dev e prod |
| Opcional | `NOTIFICACAO_*` | So se quiser sobrescrever o default global |

Nao commite o `.env`. Use `.env.exemple` como modelo.

## O que fica no `application.properties`

- Infra Spring (datasource, flyway, jpa, swagger).
- Defaults de produto com sintaxe `${VAR:default}` — a app sobe sem dezenas de variaveis no `.env`.
- Lista completa de tunables da fila em `notificacao.protecao.*`.

## O que fica no banco (migrations)

- Tabelas e relacionamentos.
- Seeds: plano Demo, `configuracao_global`, super admin, provedores WhatsApp.
- Feature flags por organizacao (`organizacao_feature_flag`, ex. V24).

**Nunca** coloque em migration: senhas, JWT, API keys, tokens Meta, URLs de gateway.

## Precedencia na fila de envio

1. **Organizacao** (`organizacao_configuracao`) — quando o campo esta preenchido na UI.
2. **Instalacao** (`notificacao.protecao.*` / env `NOTIFICACAO_*`) — default global da API.
3. **Validacao** (`PropriedadesProtecaoNotificacao`) — corrige valores invalidos (ex. limite <= 0).

## Fuso horario

`NOTIFICACAO_FUSO_HORARIO` (default `America/Bahia`) e aplicado no startup via `DotenvLoader` e tambem em `notificacao.protecao.fuso-horario` para a janela de envio.

## Referencias

- Tunables WhatsApp por org: [tutorial-configuracoes-whatsapp.md](./tutorial-configuracoes-whatsapp.md)
- Integracao por API key: [integracao-api-key-envio-simples.md](./integracao-api-key-envio-simples.md)

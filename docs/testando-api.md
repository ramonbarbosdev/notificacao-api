# Testando a API

Este guia mostra o caminho mais simples para testar autenticação, seleção de organização, WhatsApp e envio genérico de notificações.

## 1. Configurar `.env`

Crie ou ajuste o arquivo `.env` na raiz do projeto:

```env
DB_DATABASE=db_notificacao
DB_USERNAME=postgres
DB_PASSWORD=admin
DB_PORT=5432
DB_HOST=localhost

SPRING_PROFILES_ACTIVE=dev

JWT_SECRET=uma-chave-bem-grande-com-mais-de-32-caracteres
JWT_EXPIRATION_MINUTES=120

WHATSAPP_GATEWAY_BASE_URL=http://localhost:3001
WHATSAPP_GATEWAY_API_KEY=sua-chave-do-gateway
```

O gateway Node precisa estar rodando na URL configurada em `WHATSAPP_GATEWAY_BASE_URL`.

## 2. Subir a API

No PowerShell, dentro da pasta do projeto:

```powershell
.\mvnw.cmd spring-boot:run
```

URL base local:

```text
http://localhost:8080/api
```

Swagger:

```text
http://localhost:8080/api/swagger-ui.html
```

## 3. Usuários de teste

A migration `V1__auth_multiorganizacao.sql` cria estes usuários:

```text
SUPER_ADMIN
CPF: 85778905548
Senha: 123456

ADMIN da organização 1
CPF: 55308042098
Senha: 123456

USER da organização 1
CPF: 98765432100
Senha: 123456
```

## 4. Login

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

Body para admin:

```json
{
  "nuCpf": "55308042098",
  "dsSenha": "123456"
}
```

Guarde o campo `token` retornado. Esse primeiro token ainda não tem organização selecionada.

## 5. Selecionar organização

```http
POST http://localhost:8080/api/auth/selecionar-organizacao
Authorization: Bearer TOKEN_DO_LOGIN
Content-Type: application/json
```

Body:

```json
{
  "idOrganizacao": 1
}
```

Guarde o novo `token` retornado. Esse é o token que deve ser usado nas rotas `/app/**`.

## 6. Testar usuário autenticado

```http
GET http://localhost:8080/api/auth/me
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

## 7. Testar rota protegida por organização

```http
GET http://localhost:8080/api/app/organizacao/acesso
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

Resposta esperada:

```json
{
  "message": "Acesso permitido para ADMIN ou USER da organizacao",
  "idUsuario": 2,
  "idOrganizacao": 1,
  "role": "ADMIN"
}
```

## 8. Conectar WhatsApp

```http
POST http://localhost:8080/api/app/whatsapp/conectar
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

A API Spring chama o gateway Node em:

```text
POST /sessoes/1/conectar
```

E salva/atualiza a tabela `whatsapp_sessao`.

Enquanto a sessao estiver em tentativa de conexao (`CONECTANDO` ou `AGUARDANDO_QR`),
uma nova chamada para conectar fica bloqueada por 30 segundos por organizacao. Nesse caso
a API retorna `409 Conflict`. Para liberar antes desse tempo, cancele a tentativa atual.

O front deve acompanhar a liberacao por WebSocket. Existem duas opcoes:

```text
STOMP com WebSocket nativo: ws://localhost:8080/api/ws
STOMP com SockJS: http://localhost:8080/api/ws-sockjs
Topico da organizacao: /topic/whatsapp/organizacao/1
```

Se usar SockJS, nao use `ws://` na URL. Use `http://localhost:8080/api/ws-sockjs`
e deixe a biblioteca criar o transporte correto.

Eventos publicados:

```json
{
  "idOrganizacao": 1,
  "tipo": "TENTATIVA_INICIADA",
  "status": "CONECTANDO",
  "podeConectar": false,
  "segundosRestantes": 30,
  "mensagem": "Tentativa de conexao WhatsApp iniciada.",
  "dataHora": "2026-05-14T17:10:00"
}
```

Tipos possiveis:

```text
TENTATIVA_INICIADA
TENTATIVA_BLOQUEADA
STATUS_ATUALIZADO
CONEXAO_LIBERADA
CONEXAO_CANCELADA
```

Quando receber `CONEXAO_LIBERADA` ou `CONEXAO_CANCELADA`, o front pode liberar o botao
de conectar novamente.

## 9. Consultar status e QR Code

```http
GET http://localhost:8080/api/app/whatsapp/status
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

Resposta esperada, dependendo do estado:

```json
{
  "sucesso": true,
  "idOrganizacao": 1,
  "status": "AGUARDANDO_QR",
  "conectado": false,
  "qr": "...",
  "qrImagem": "...",
  "telefone": null,
  "erro": null
}
```

## 10. Enviar mensagem WhatsApp direta

```http
POST http://localhost:8080/api/app/whatsapp/enviar-mensagem
Authorization: Bearer TOKEN_COM_ORGANIZACAO
Content-Type: application/json
```

Body:

```json
{
  "telefone": "5571999999999",
  "mensagem": "Teste de mensagem via notificacao-api"
}
```

## 11. Enviar notificação genérica

Essa rota usa a arquitetura por provider e busca a configuração ativa do canal para a organização atual.

```http
POST http://localhost:8080/api/app/notificacoes/enviar
Authorization: Bearer TOKEN_COM_ORGANIZACAO
Content-Type: application/json
```

WhatsApp:

```json
{
  "canal": "WHATSAPP",
  "destinatario": "5571999999999",
  "assunto": null,
  "mensagem": "Teste via NotificationService"
}
```

Email simulado:

```json
{
  "canal": "EMAIL",
  "destinatario": "teste@email.com",
  "assunto": "Teste",
  "mensagem": "Mensagem de teste"
}
```

Resposta esperada:

```json
{
  "sucesso": true,
  "idNotificacao": 1,
  "canal": "EMAIL",
  "status": "ENVIADO",
  "erro": null
}
```

## 12. Desconectar WhatsApp

```http
POST http://localhost:8080/api/app/whatsapp/desconectar
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

Tambem pode ser usado como cancelamento explicito da tentativa de conexao:

```http
POST http://localhost:8080/api/app/whatsapp/cancelar-conexao
Authorization: Bearer TOKEN_COM_ORGANIZACAO
```

## 13. Testar SUPER_ADMIN

Login:

```json
{
  "nuCpf": "85778905548",
  "dsSenha": "123456"
}
```

Depois use o token retornado em:

```http
GET http://localhost:8080/api/admin/status
Authorization: Bearer TOKEN_SUPER_ADMIN
```

`SUPER_ADMIN` não seleciona organização. Ele acessa rotas `/admin/**`.

## 14. Checklist rápido

```text
1. PostgreSQL rodando
2. Banco db_notificacao criado
3. .env configurado
4. Gateway Node rodando
5. Spring Boot rodando em http://localhost:8080/api
6. Login feito
7. Organização selecionada
8. Usar token com organização em todas as rotas /app/**
```

## 15. Erros comuns

```text
401 Unauthorized
- Token ausente, expirado ou inválido.

403 Forbidden
- Usuário não selecionou organização ou não possui role esperada.

400 Configuracao ativa nao encontrada
- Não existe configuração ativa do canal para a organização.

502/erro do gateway
- Gateway Node está fora do ar, URL errada ou X-API-KEY inválida.
```

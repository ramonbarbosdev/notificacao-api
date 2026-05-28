# Integracao com API Key e envio sem template

Este guia mostra como um sistema externo pode enviar notificacoes para a `notificacao-api` usando `X-API-KEY`, sem precisar fazer login por JWT.

## 1. Criar a API Key

No painel ou pela API, crie uma API Key para a organizacao desejada com o scope:

```text
NOTIFICACOES_ENVIAR
```

Ao criar, copie o campo `chave`. Ele aparece somente uma vez.

Exemplo de chave:

```text
nak_wLku5PjG.wLku5PjGKX6BMUGapwiIM5EmPghU9snVFWImRZ-AU1M
```

Use a chave completa, nao apenas o prefixo `nak_wLku5PjG`.

## 2. Configurar o sistema externo

Exemplo de `.env`:

```env
NOTIFICACAO_API_URL=http://localhost:8080/api
NOTIFICACAO_API_KEY=nak_wLku5PjG.sua_chave_completa
```

Em producao, troque a URL local pela URL hospedada da API.

## 3. Endpoint de envio

```http
POST /app/notificacoes/enviar
X-API-KEY: sua_chave_completa
Content-Type: application/json
```

URL local completa:

```text
http://localhost:8080/api/app/notificacoes/enviar
```

## 4. Payload

```json
{
  "canal": "WHATSAPP",
  "destinatario": "5571999999999",
  "assunto": "Novo registro cadastrado",
  "mensagem": "A fonte de recurso foi cadastrada com sucesso"
}
```

Campos:

- `canal`: canal de envio. Exemplo: `WHATSAPP`.
- `destinatario`: telefone, e-mail ou destino conforme o canal.
- `assunto`: titulo/assunto da notificacao.
- `mensagem`: conteudo que sera enviado.

## 5. Exemplo em PHP

```php
$payload = [
    'canal' => 'WHATSAPP',
    'destinatario' => preg_replace('/\D/', '', '5571999999999'),
    'assunto' => 'Novo registro cadastrado',
    'mensagem' => 'A fonte de recurso foi cadastrada com sucesso',
];

$url = rtrim($_ENV['NOTIFICACAO_API_URL'], '/') . '/app/notificacoes/enviar';

$ch = curl_init($url);

curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_POST => true,
    CURLOPT_CONNECTTIMEOUT => 10,
    CURLOPT_TIMEOUT => 30,
    CURLOPT_HTTPHEADER => [
        'Content-Type: application/json',
        'X-API-KEY: ' . $_ENV['NOTIFICACAO_API_KEY'],
    ],
    CURLOPT_POSTFIELDS => json_encode($payload),
]);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$curlError = curl_error($ch);

curl_close($ch);

if ($response === false) {
    throw new Exception('Erro ao chamar notificacao-api: ' . $curlError);
}

if ($httpCode < 200 || $httpCode >= 300) {
    throw new Exception("Erro notificacao-api HTTP {$httpCode}: {$response}");
}

$data = json_decode($response, true);
```

## 6. Resposta esperada

```json
{
  "sucesso": true,
  "idNotificacao": 1,
  "canal": "WHATSAPP",
  "status": "PENDENTE",
  "erro": null
}
```

## 7. Problemas comuns

- `401`: API Key ausente, invalida, expirada ou usando apenas o prefixo.
- `403`: API Key sem o scope `NOTIFICACOES_ENVIAR`.
- `400`: payload invalido ou campos obrigatorios ausentes.
- WhatsApp nao envia: verificar se a sessao da organizacao esta conectada no gateway.

# Integracao com API Key e envio com template

Este guia mostra como um sistema externo pode enviar notificacoes usando um template cadastrado na `notificacao-api`.

## 1. Criar a API Key

Crie uma API Key para a organizacao desejada com o scope:

```text
NOTIFICACOES_ENVIAR
```

Guarde a chave completa retornada na criacao.

Exemplo:

```env
NOTIFICACAO_API_URL=http://localhost:8080/api
NOTIFICACAO_API_KEY=nak_wLku5PjG.sua_chave_completa
```

## 2. Criar ou identificar o template

Antes de enviar, o template precisa existir na `notificacao-api`.

Exemplo de template:

```text
{{titulo}}

{{mensagem}}

Usuario: {{usuario}}
Data: {{data}}
```

Cada valor entre `{{ }}` e uma variavel que sera enviada pelo sistema externo.

Exemplo de chave do template:

```text
fonte_recurso_criada
```

Essa chave sera usada no campo `templateKey`.

## 3. Endpoint de envio com template

```http
POST /app/notificacoes/templates/enviar
X-API-KEY: sua_chave_completa
Content-Type: application/json
```

URL local completa:

```text
http://localhost:8080/api/app/notificacoes/templates/enviar
```

## 4. Payload

```json
{
  "templateKey": "fonte_recurso_criada",
  "destinatario": "5571999999999",
  "variaveis": {
    "titulo": "Novo registro cadastrado",
    "mensagem": "A fonte de recurso foi cadastrada com sucesso",
    "usuario": "Ramon",
    "data": "28/05/2026 10:56"
  }
}
```

Campos:

- `templateKey`: chave do template cadastrado na `notificacao-api`.
- `destinatario`: telefone, e-mail ou destino conforme o canal do template.
- `variaveis`: mapa com os valores que substituem as variaveis do template.

A regra e:

```text
{{nome_no_template}} = chave dentro de variaveis
```

Exemplo:

```text
{{titulo}} usa variaveis.titulo
{{mensagem}} usa variaveis.mensagem
```

## 5. Exemplo em PHP

```php
$payload = [
    'templateKey' => 'fonte_recurso_criada',
    'destinatario' => preg_replace('/\D/', '', '5571999999999'),
    'variaveis' => [
        'titulo' => 'Novo registro cadastrado',
        'mensagem' => 'A fonte de recurso foi cadastrada com sucesso',
        'usuario' => 'Ramon',
        'data' => date('d/m/Y H:i'),
    ],
];

$url = rtrim($_ENV['NOTIFICACAO_API_URL'], '/') . '/app/notificacoes/templates/enviar';

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

- `401`: API Key ausente, invalida, expirada ou usando somente o prefixo.
- `403`: API Key sem o scope `NOTIFICACOES_ENVIAR`.
- `404`: template nao encontrado para a organizacao.
- `400`: variavel obrigatoria ausente ou payload invalido.
- Mensagem sem substituicao: nome da variavel no payload diferente do nome usado no template.

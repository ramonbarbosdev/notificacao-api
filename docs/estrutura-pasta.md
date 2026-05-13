notificacao-api
├─ config
│  ├─ DotenvLoader
│  ├─ SecurityConfig
│  ├─ SwaggerDevConfig
│  └─ SwaggerProdConfig
│
├─ controller
│  ├─ AuthController
│  │  ├─ POST /auth/login
│  │  ├─ POST /auth/selecionar-organizacao
│  │  ├─ GET  /auth/me
│  │  └─ POST /auth/logout
│  │
│  ├─ AdminController
│  │  └─ GET /admin/status
│  │     somente SUPER_ADMIN
│  │
│  └─ OrganizacaoAcessoController
│     └─ GET /app/organizacao/acesso
│        somente ADMIN ou USER da organização
│
├─ security
│  ├─ JwtService
│  │  gera e valida tokens JWT
│  │
│  ├─ JwtAuthenticationFilter
│  │  lê o Bearer Token e autentica a requisição
│  │
│  └─ JwtAuthentication
│     guarda idUsuario, tipoGlobal, idOrganizacao e role
│
├─ service
│  ├─ AuthService
│  │  faz login, seleção de organização e /me
│  │
│  └─ TenantContextService
│     pega o usuário/organização atual da requisição
│
├─ model
│  ├─ Usuario
│  ├─ Organizacao
│  └─ UsuarioOrganizacao
│
├─ repository
│  ├─ UsuarioRepository
│  └─ UsuarioOrganizacaoRepository
│
├─ dto
│  ├─ LoginRequestDTO
│  ├─ LoginResponseDTO
│  ├─ OrganizacaoLoginDTO
│  ├─ SelecionarOrganizacaoRequestDTO
│  ├─ SelecionarOrganizacaoResponseDTO
│  └─ MeResponseDTO
│
└─ resources
   ├─ application.properties
   └─ db/migration
      └─ V1__auth_multiorganizacao.sql

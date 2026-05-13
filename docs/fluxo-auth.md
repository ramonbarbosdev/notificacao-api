Login
  ↓
POST /auth/login
  ↓
SUPER_ADMIN?
  ├─ sim → token global → acessa /admin/**
  └─ não → recebe lista de organizações
              ↓
        POST /auth/selecionar-organizacao
              ↓
        token com idOrganizacao + role
              ↓
        acessa /app/** como ADMIN ou USER

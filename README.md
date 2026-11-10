# AABB · Organizador de Pelada

Bot de WhatsApp + painel web para a pelada da AABB (segunda e quinta, 19h).
As regras estão em `1_arquitetura_e_infraestrutura.md` a `4_integracao_whatsapp_evolution_api.md`.

| Pasta | O que é |
|---|---|
| `aabb-backend/` | Spring Boot 4.1 + Java 25: webhook, comandos, sorteio, VAR, API do painel |
| `aabb-frontend/` | Angular 22 + Tailwind 4: painel administrativo (Nginx com proxy para `/api` e `/auth`) |
| `infra/` | Realm do Keycloak e script de criação do banco do Keycloak |
| `docker-compose.yml` | Sistema completo: MongoDB, Postgres, Evolution API, backend, Keycloak e painel |

## Subir o sistema

1. Copie `.env.example` para `.env` e preencha as chaves e senhas.
   Deixe `AABB_GRUPO_JID` vazio por enquanto.
2. Suba tudo, de uma das duas formas:

   ```sh
   # Com as imagens publicadas no Docker Hub (ex: Raspberry Pi, sem compilar nada)
   docker compose -p aabb pull && docker compose -p aabb up -d

   # Compilando a partir do código
   docker compose -p aabb up -d --build
   ```

### Servidor só com as imagens (Raspberry Pi)

O servidor precisa apenas do `docker-compose.yml` e do `.env` (o realm do Keycloak e o script do Postgres já
estão dentro das imagens):

```sh
mkdir aabb && cd aabb
curl -fsSLO https://raw.githubusercontent.com/jadsonmd/aabb/main/docker-compose.yml
curl -fsSL https://raw.githubusercontent.com/jadsonmd/aabb/main/.env.example -o .env
nano .env   # preencha as senhas e a AABB_URL_PUBLICA (antes da primeira subida)
docker compose -p aabb pull && docker compose -p aabb up -d
```

Para atualizar depois de uma nova publicação: `docker compose -p aabb pull && docker compose -p aabb up -d`.
Para fixar uma versão, use `AABB_VERSAO=<commit>` no `.env`.

### Publicar as imagens (na máquina de desenvolvimento)

```sh
docker login
scripts/publicar-imagens.sh            # publica jadsonmd/aabb-* para amd64 e arm64
```

O script exige o código commitado e publica as tags `latest` e a do commit atual. As imagens não contêm
segredos: senhas e chaves vêm do `.env` do servidor.

3. Painel: abra `AABB_URL_PUBLICA` (padrão `http://localhost:8088`) e entre com o usuário `admin` e a senha
   `AABB_PAINEL_SENHA_INICIAL`. O Keycloak pede uma senha nova no primeiro login.
   Outros usuários do painel são criados no console do Keycloak (`<URL pública>/auth/admin`, realm `aabb`).

## Conectar o chip do bot (uma vez)

Use um chip pré-pago dedicado ao bot. No host do sistema:

```sh
source .env
# Cria a instância aabb-bot
curl -s -X POST http://localhost:8081/instance/create \
  -H "apikey: $EVOLUTION_API_KEY" -H "Content-Type: application/json" \
  -d '{"instanceName":"aabb-bot","integration":"WHATSAPP-BAILEYS","qrcode":true}'
```

Abra `http://localhost:8081/manager` (login com a `EVOLUTION_API_KEY`), entre na instância `aabb-bot` e leia o
QR code com o WhatsApp do chip (Aparelhos conectados → Conectar aparelho).

## Descobrir o JID do grupo

Com o bot conectado e `AABB_GRUPO_JID` vazio, mande qualquer mensagem no grupo da pelada e veja o log:

```sh
docker logs aabb-backend 2>&1 | grep "Webhook recebido do chat"
# ... Webhook recebido do chat 120363xxxxxxxxxxxx@g.us ...
```

Coloque esse valor em `AABB_GRUPO_JID` no `.env` e recrie o backend:

```sh
docker compose -p aabb up -d aabb-backend
```

Os webhooks brutos dos últimos 7 dias ficam na collection `webhooks_brutos` do MongoDB, úteis para diagnóstico.

## Cadastrar os membros

Quem manda mensagem no grupo sem cadastro é **cadastrado automaticamente** como membro (skill 5, Lateral,
apelido = nome do WhatsApp). No painel, em **Jogadores**, ajuste o apelido e as posições e desative quem não joga.

Também dá para cadastrar manualmente: nome, apelido (nome que o bot usa), WhatsApp com DDI e DDD
(só dígitos), posições (a primeira é a principal) e skill base. Marque **Administrador** para quem pode
usar `#sortear`, `#ressortear`, `#abrirlista` e `#jogocancelado`.

## Comandos do grupo

| Comando | Quem | O que faz |
|---|---|---|
| `#vou` | membro | entra na lista do dia |
| `#dm` | membro | sai da lista |
| `#convidado Nome` / `#desconvidar Nome` | membro | coloca ou tira um convidado |
| `#lista` | membro | mostra a lista ou os times sorteados |
| `#ranking` | membro | skill de quem já jogou, da maior para a menor (qualquer dia) |
| `#presenca` | membro | presenças no ano, de quem tem mais para quem tem menos (qualquer dia) |
| `#titulos` | membro | títulos (vitórias) no ano, de quem tem mais para quem tem menos (qualquer dia) |
| `#sortear` / `#ressortear` | admin | fecha a lista (mín. 10) e sorteia / sorteia de novo |
| `#abrirlista [horário]` | admin | abre o jogo de hoje em qualquer dia (jogo extra ou se o scheduler das 6h não rodou), ex: `#abrirlista 9h`; com o jogo aberto, muda o horário |
| `#jogocancelado` | admin | cancela o jogo de hoje |
| `#canetou @vitima 2x` / `#chapelou @vitima 2x` | membro | jogada de efeito **sua** no VAR (vale com 3 👍; sem `2x`, vale 1) |
| `@autor #canetou @vitima 2x` | membro | jogada de efeito de outra pessoa |
| `#marcou 2x` / `@jogador #marcou 2x` | membro | gols seus / de outra pessoa no VAR (vale com 3 👍) |
| `#timeazulganhou` / `#timeamareloganhou` / `#empate` | qualquer um | resultado (vale o primeiro) |

## Desenvolvimento

```sh
cd aabb-backend && ./gradlew test   # precisa de Docker (Testcontainers com MongoDB)
```

O Angular CLI 22 exige Node 24.15+ (ou 22.22.3+). Com um Node compatível:

```sh
cd aabb-frontend && npm ci && npx ng test --watch=false && npx ng build
```

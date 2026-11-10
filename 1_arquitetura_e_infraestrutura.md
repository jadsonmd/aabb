# Documentação do Sistema AABB: Organizador de Pelada (FIFA Style)

## 1. Visão Geral da Arquitetura
O sistema é uma plataforma híbrida de gestão esportiva amadora. Ele combina a facilidade de uso do WhatsApp (para engajamento e comandos rápidos) com um painel web administrativo (para governança e auditoria). Todo o ecossistema é projetado para rodar em uma infraestrutura local e enxuta (como um Raspberry Pi), utilizando a conteinerização para garantir isolamento e portabilidade.

### Convenção de Nomes
Tudo que pertence ao projeto usa o prefixo do clube, **`aabb`**:

| Item | Nome |
|---|---|
| Repositórios / projetos | `aabb-backend`, `aabb-frontend` |
| Pacote Java | `br.com.aabb` |
| Rede Docker | `aabb-network` |
| Serviços / containers | `aabb-evolution-api`, `aabb-backend`, `aabb-mongodb`, `aabb-postgres`, `aabb-keycloak`, `aabb-frontend` |
| Instância do bot na Evolution API | `aabb-bot` |
| Banco de dados MongoDB | `aabb` |
| Realm do Keycloak | `aabb` |
| Client do painel no Keycloak | `aabb-painel` |

## 2. Topologia de Rede e Componentes (Docker)
Todo o sistema rodará dentro de uma única rede Docker interna (`aabb-network`). Os containers conversam entre si pelo **nome do serviço** definido no `docker-compose`, que o DNS interno do Docker resolve (ex: `http://aabb-backend:8080`, `mongodb://aabb-mongodb:27017`). Dentro de um container, `localhost` aponta para o próprio container, não para os demais, por isso não deve ser usado na comunicação entre serviços.

Apenas a porta do painel (`aabb-frontend`, **8088**) é publicada para a rede, e nenhuma é exposta para a internet. O Keycloak é servido pelo mesmo Nginx em `/auth`. Backend (8080) e Evolution API (8081) ficam publicados **só no loopback do host** (`127.0.0.1`), para diagnóstico e para conectar o chip pelo QR code; MongoDB e Postgres ficam acessíveis somente dentro da rede interna.

O painel acessa o backend pelo **Nginx do `aabb-frontend`**, que faz proxy reverso de `/api/` para `http://aabb-backend:8080` e de `/auth/` para `http://aabb-keycloak:8080`. Assim o navegador fala com uma única origem (a URL pública configurada em `AABB_URL_PUBLICA`). A rota do webhook (`/api/whatsapp/webhook`) **não** é exposta pelo proxy: ela só é chamada pela Evolution API dentro da rede interna.

| Serviço (`docker-compose`) | Endereço interno |
|---|---|
| `aabb-evolution-api` | `http://aabb-evolution-api:8080` |
| `aabb-backend` | `http://aabb-backend:8080` |
| `aabb-mongodb` | `mongodb://aabb-mongodb:27017` |
| `aabb-postgres` | `postgresql://aabb-postgres:5432` (bancos `evolution` e `keycloak`) |
| `aabb-keycloak` | `http://aabb-keycloak:8080/auth` |
| `aabb-frontend` | `http://aabb-frontend:80` |

### Componentes:
1. **Container 1: Evolution API (Node.js)**
   * **Papel:** O "Carteiro". Mantém a conexão WebSocket com a Meta, recebe mensagens do grupo e dispara Webhooks locais.
2. **Container 2: Backend (Spring Boot / Java)**
   * **Papel:** O "Cérebro". Recebe webhooks, gerencia o estado, aplica regras de negócio (VAR, notas, sorteio dos times), abre a lista automaticamente nos dias de jogo e envia as respostas pela Evolution API.
3. **Container 3: Banco de Dados (MongoDB)**
   * **Papel:** O "Cofre". Armazena estado dos jogadores, histórico de partidas e eventos. Utiliza volumes mapeados para evitar perda de dados em reinicializações. Roda como **replica set de um nó** para permitir transações (usadas no registro do resultado da partida).
4. **Container 4: Keycloak (Autenticação) + Postgres**
   * **Papel:** O "Segurança". Gerencia a identidade, login e geração de tokens JWT (OAuth2) para acesso seguro ao painel Frontend. O realm `aabb` (client `aabb-painel`, usuário inicial `admin`) é importado automaticamente na primeira subida.
   * O `aabb-postgres` guarda os dados do Keycloak e da Evolution API (que exige Postgres).
5. **Container 5: Frontend (Angular / Nginx)**
   * **Papel:** O "Painel do Chefe". Interface gráfica para administradores lançarem os dados oficiais das partidas.

## 3. Segurança e Acesso Externo (Zero Trust)
* **Sem Port Forwarding:** Nenhuma porta do roteador doméstico deve ser aberta.
* **Acesso Web:** Para acessar o painel Angular ou o Keycloak de fora da rede Wi-Fi local, utiliza-se uma VPN Mesh Zero Trust (como **Tailscale**) instalada no host (Raspberry Pi) e no celular do administrador, ou um **Cloudflare Tunnel** para criar um domínio seguro com HTTPS.

## 4. Tolerância a Falhas e Resiliência
* **Auto-Restart:** Todos os containers Docker devem ter a política `restart: always` ou `unless-stopped`.
* **Idempotência:** Garantia de que a flutuação de rede não processe a mesma mensagem de WhatsApp duas vezes.
* **Retry no envio (Backend):** o envio de mensagens à Evolution API é repetido até 3 vezes com espera crescente (`@Retryable` do Spring Framework 7). Se ainda falhar, o erro é registrado em log e o fluxo de negócio não é interrompido.
* **Reenvio de webhooks:** a Evolution API reenvia o webhook quando o backend responde com erro. Como cada mensagem é registrada antes do processamento (e liberada em caso de falha), o reenvio é processado uma única vez.
* **Sem dependências externas para regras de negócio:** o sorteio dos times é calculado localmente no backend (sem IA). O único serviço externo do sistema é o WhatsApp, via Evolution API.
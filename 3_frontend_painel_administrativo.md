# Módulo Frontend: Painel Administrativo em Angular

## 1. Stack Tecnológico
* **Framework:** Angular 22 (versão estável mais recente; Standalone Components, Signals e change detection zoneless, padrões do framework)
* **Estilização:** Tailwind CSS 4 (tema voltado para mobile-first)
* **Autenticação:** Keycloak (OAuth2 com PKCE) usando a biblioteca `angular-oauth2-oidc` (v22, compatível com Angular 22). Realm `aabb`, client público `aabb-painel`, servido em `<URL pública>/auth`.
* **API:** o painel chama `/api/painel/**` na mesma origem; o Nginx do `aabb-frontend` repassa ao backend com o token JWT.

## 2. Conceito da Abordagem Híbrida
O frontend não concorre com o WhatsApp; ele o complementa. O WhatsApp é para a "resenha" e lances plásticos subjetivos validados pelo VAR. O Frontend é o "Cartório" do jogo. É a fonte da verdade para dados concretos (placares, artilharia, assistências).

## 3. Telas Principais

### A. Dashboard e Ranking (Leitura)
* Exibe uma tabela (Leaderboard) ordenável.
* Mostra apenas quem teve alguma atividade no ano (presença, gol, caneta, chapéu ou título), para quem só está no grupo não poluir o ranking.
* Colunas: Posição, Nome, Skill Atual, Gols no Ano (`#marcou` aprovados pelo VAR), Canetas e Chapéus (aprovados pelo VAR), Títulos (vitórias registradas por `#timeazulganhou`/`#timeamareloganhou`), Presenças no Ano.
* Design esportivo, com destaques para o Top 3 e a zona de "Bola Murcha".

### B. Gestão de Partidas (A Pós-Pelada)
* **Fluxo pós-jogo (segunda e quinta):** Após o jogo das 19h, o administrador loga no painel pelo celular.
* A tela exibe a partida atual gerada pelo último `#sortear`/`#ressortear` (Time Azul vs Time Amarelo), com os titulares (até 7, indicando o goleiro quando houver) e os reservas de cada time.
* Formulário simples e rápido para inserir:
  * Placar final (Azul X x Y Amarelo).
  * Gols: exibidos a partir dos `#marcou` aprovados pelo VAR no WhatsApp; não são lançados pelo painel.
  * Adicionar eventos: Dropdown para escolher jogador -> Dropdown do tipo de evento (Assistência, Gol Contra) -> Botão "+".
  * Convidados aparecem no dropdown (marcados como convidado) para que o placar e o histórico da partida fiquem corretos, mas eventos de convidados **não** alteram skill e não entram no ranking.
* **Resultado:** exibido a partir do registro feito no WhatsApp (`#timeazulganhou`/`#timeamareloganhou`/`#empate`). O painel **não** aplica bônus de vitória ou empate nem penalidade de derrota, e **não** permite corrigir o resultado; isso é feito só pelo comando no WhatsApp.
* **Finalização:** ao enviar o resultado, a partida passa para o status `FINALIZADA`; a partir daí `#ressortear` deixa de ser aceito.
* **Impacto na Skill:** o painel **não** altera skill. Gols (`#marcou`), resultado (`#timeazulganhou`/`#timeamareloganhou`/`#empate`) e jogadas de efeito (VAR) são computados só pelo WhatsApp. Assistência e Gol Contra ficam apenas no histórico da partida.

### C. Lista de Presença (Leitura)
* Por jogador: total de presenças (no ano e geral) e o histórico com as datas dos jogos em que esteve na lista sorteada.
* Por partida: quais membros receberam presença naquele dia.
* Presença é computada automaticamente pelo backend no `#sortear`; o painel apenas exibe (não há lançamento manual).

### D. Cadastro de Jogadores (CRUD)
* **Cadastro automático (sem aprovação):** quem manda qualquer mensagem no grupo sem estar cadastrado vira **membro ativo na hora**, com o nome do WhatsApp como apelido, **skill 5** e posição **Lateral** (valores em `aabb.cadastro`). O nivelamento acontece naturalmente com os jogos, e o próprio comando que gerou o cadastro (ex: o primeiro `#vou`) já é processado.
  * O admin pode **editar** o apelido e as posições, ou **desativar** quem não joga. Desativado não é reconhecido nos comandos e não é recadastrado nas próximas mensagens.
  * Só é possível cadastrar automaticamente quem chega com número de telefone no webhook; quem aparece apenas com `@lid` precisa ser cadastrado manualmente.
* Tela para adicionar novos mensalistas/avulsos manualmente (o formulário já vem com skill 5 e posição Lateral).
* Campos obrigatórios: Nome, Apelido (nome exibido nas mensagens do bot), Número do WhatsApp (para link com os webhooks), Array de Posições (G, Z, L, M, A; a primeira é a posição principal) e Skill Base (nota inicial de chegada).
* Flag **Administrador**: define quem pode usar os comandos exclusivos de admin no WhatsApp (`#sortear`, `#ressortear`, `#abrirlista`, `#jogocancelado`).
* **Edição pelo admin:**
  * **Skill atual:** o admin pode definir a skill diretamente (0.0 a 10.0). Cada alteração fica registrada no histórico de skill como `AJUSTE_MANUAL`, com o valor da diferença. Ao editar só outros campos, a skill não é tocada (o VAR pode tê-la alterado no meio tempo).
  * **Presenças no ano:** o admin informa o total de presenças do ano atual. A diferença para as presenças computadas nos sorteios vira um ajuste manual daquele ano (útil para lançar o histórico de antes do bot ou corrigir erros). O valor ajustado vale no painel, no `#presenca` e no `#ranking` (quem tem presença ajustada conta como "já jogou").
* Campo somente leitura: **LIDs vinculados** (identificadores `@lid` aprendidos automaticamente pelo backend). O admin pode desvincular um LID errado, mas não precisa cadastrá-lo.

## 4. API do Painel (backend)
Todas as rotas exigem o token JWT do Keycloak (`Authorization: Bearer`), enviado automaticamente pelo `angular-oauth2-oidc`.

| Método e rota | Uso |
|---|---|
| `GET /api/painel/ranking?ano=` | Ranking do ano: skill, gols, canetas, chapéus, títulos e presenças |
| `GET /api/painel/presencas?ano=` | Presenças por jogador (no ano, total e datas) |
| `GET /api/painel/partidas` | Partidas recentes (resumo) |
| `GET /api/painel/partidas/{id}` | Partida completa: sorteio vigente, gols aprovados e presentes |
| `POST /api/painel/partidas/{id}/finalizar` | Placar e eventos (assistência, gol contra); muda para `FINALIZADA` |
| `GET/POST /api/painel/jogadores`, `PUT /api/painel/jogadores/{id}` | Cadastro de membros. Campos opcionais `skill` (skill atual) e `presencasNoAno` (total do ano): ausentes, não alteram nada |
| `POST /api/painel/jogadores/importar` | Carga inicial em lote |
| `POST /api/painel/jogadores/{id}/ativar` / `desativar` | Membro inativo não é reconhecido nos comandos |
| `DELETE /api/painel/jogadores/{id}/lids/{lid}` | Desvincula um `@lid` aprendido errado |


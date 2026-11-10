# Módulo Backend: Spring Boot & Regras de Negócio

## 1. Stack Tecnológico
* **Framework:** Java 25 (LTS) + Spring Boot 4.1.x (Spring Framework 7), build com Gradle
* **Dados:** Spring Data MongoDB
* **Cliente HTTP:** `RestClient` (cliente HTTP síncrono recomendado no Spring Boot 4, substituto do `RestTemplate`) para enviar mensagens pela Evolution API.
* **Resiliência:** `@Retryable` do próprio Spring Framework 7 (`@EnableResilientMethods`) nas chamadas à Evolution API, sem dependência externa.
* **Segurança:** Spring Security como OAuth2 Resource Server (JWT do Keycloak) na API do painel (`/api/painel/**`). O webhook não exige token, pois só é alcançável pela rede interna.
* **Concorrência:** virtual threads do Java 25 (`spring.threads.virtual.enabled`).
* **Sem IA:** o sorteio dos times é um algoritmo determinístico local; o projeto não depende do Gemini nem de outra API de IA.

## 2. Fluxos Principais (Webhooks)

O Backend expõe um endpoint principal, `POST /api/whatsapp/webhook`, que consome o payload da Evolution API.

### 0. Agenda dos Jogos e Abertura da Lista
* **Dias e horário:** os jogos acontecem **toda segunda-feira e toda quinta-feira, às 19h** (fuso `America/Sao_Paulo`). Dias e horários ficam em configuração (`application.yaml`), não fixos no código.
* **Uma partida por dia de jogo:** cada dia de jogo tem um único documento de partida, identificado pela `data` (índice único em `data`). A lista **começa e termina no dia do jogo**.
* **Status da partida:** `ABERTA` → `SORTEADA` → `FINALIZADA`, ou `CANCELADA` (a partir de `ABERTA` ou `SORTEADA`; estado final, sem volta).
* **Partida do dia:** todos os comandos de lista (`#vou`, `#convidado`, `#desconvidar`, `#dm`, `#lista`, `#sortear`, `#ressortear`, `#timeazulganhou`, `#timeamareloganhou`, `#empate`, `#marcou`, `#abrirlista`, `#jogocancelado`) atuam **somente na partida com `data` igual a hoje**. Eles valem nos dias de jogo programados **e em qualquer dia em que o admin tenha aberto um jogo extra** com `#abrirlista`. Nos demais dias **nada é realizado**: esses comandos são ignorados em silêncio. O VAR (`#canetou`, `#chapelou` e reações) e as consultas `#ranking`, `#presenca` e `#titulos` (seção G) não dependem da partida e funcionam em qualquer dia.
* **Lista ainda não aberta:** em dia de jogo, antes de a partida existir (antes das 6h ou se o scheduler não rodou), os comandos de lista respondem *"A lista de hoje ainda não foi aberta."* A exceção é o `#abrirlista`, que cria a partida.
* **Grupo oficial:** o backend só processa mensagens do grupo configurado (`remoteJid` igual ao JID do grupo em `application.yaml`). Mensagens privadas ao bot ou de outros grupos são ignoradas.
* **Administradores:** os comandos exclusivos de admin (`#sortear`, `#ressortear`, `#abrirlista`, `#jogocancelado`) só são aceitos de membros com a flag `admin: true` no cadastro (definida no painel). Se um não-admin enviar um desses comandos, o bot responde *"Comando exclusivo de administradores."* e nada é alterado.

#### 0.1 Abertura automática (scheduler)
1. **Quando:** às **6h** de cada dia de jogo (cron `0 0 6 * * MON,THU`, zona `America/Sao_Paulo`), via `@Scheduled` do Spring.
2. **Ação (idempotente):** cria a partida do dia com `status: "ABERTA"` e `confirmados: []` usando upsert pela `data` (`$setOnInsert`). Se a partida do dia já existir (aberta manualmente, por exemplo), nada é alterado e nenhuma mensagem é enviada.
3. **Retorno:** o bot posta no grupo: *"⚽ Lista aberta para o jogo de hoje (segunda, 19h)! Mande #vou para confirmar."*
4. **Sem lembretes:** o bot não envia lembretes nem mensagens em outros dias ou horários.
5. **Sem recuperação automática:** o backend abre a lista **somente** pelo scheduler, no horário programado. Se o servidor estiver fora do ar às 6h, a execução perdida **não** é recuperada quando ele voltar (nenhuma abertura na inicialização nem execução atrasada). A partir daí, abrir a lista é responsabilidade do administrador, com `#abrirlista`.

#### 0.2 Abertura manual (`#abrirlista [horário]`)
Plano B para quando o servidor estiver fora do ar às 6h e o scheduler não rodar, e também para abrir um **jogo extra** em qualquer dia.
1. **Gatilho:** Administrador envia `#abrirlista` (comando exclusivo de admin), com horário opcional: `#abrirlista 9h`, `#abrirlista 20h30`, `#abrirlista 20:30`. Sem horário, vale o padrão da agenda (19h). Horário inválido: *"Horário inválido. Ex: #abrirlista 9h ou #abrirlista 20h30"*.
2. **Quando:** **qualquer dia da semana**. Fora de segunda e quinta, abre um jogo extra que funciona como um jogo normal (lista, convidados, sorteio, VAR, resultado, presença e títulos). O horário fica gravado na partida e aparece nas mensagens da lista e do sorteio.
3. **Ação:** executa **a mesma operação idempotente** do scheduler. Se a partida do dia já existir **e um horário for informado**, o horário do jogo é alterado (*"🕗 Horário do jogo de hoje alterado: sábado, 10h30."*), exceto se o jogo estiver cancelado. Sem horário, se a partida do dia já existir, responde *"A lista de hoje já está aberta."* (ou *"Os times já foram sorteados!"*, se já estiver `SORTEADA` ou `FINALIZADA`).
4. **Jogo cancelado:** se a partida do dia estiver `CANCELADA`, nada é alterado e o bot responde *"O jogo de hoje foi cancelado."*
5. **Retorno:** mesma mensagem de abertura do scheduler.

#### 0.3 Encerramento
* A lista é encerrada pelo **`#sortear`** (ver seção C).
* Se o dia terminar sem `#sortear`, a partida fica como está; como os comandos só atuam na partida do dia, ela simplesmente deixa de receber comandos.

#### 0.4 Cancelamento (`#jogocancelado`)
1. **Gatilho:** Administrador envia `#jogocancelado` (comando exclusivo de admin).
2. **Quando:** somente no dia do jogo, com a partida `ABERTA` ou `SORTEADA` e **antes** de o resultado ser registrado (seção E). Depois do resultado, o cancelamento é recusado.
3. **Ação (atômica):** update condicional `status ∈ {ABERTA, SORTEADA}` → `CANCELADA`.
4. **Efeito:** a partir daí todos os comandos de lista do dia respondem *"O jogo de hoje foi cancelado."*. Nenhuma skill é alterada pela partida cancelada e **nenhuma presença é computada**; se o cancelamento ocorrer depois do `#sortear`, as presenças já registradas dessa partida são removidas (seção D). O cancelamento é **definitivo**: não pode ser desfeito e a lista do dia não pode ser reaberta, nem por `#abrirlista`.
5. **Retorno:** *"❌ Jogo de hoje cancelado."*

### A. Lista de Confirmação (`#vou`)
**Formato do jogo:** futebol 7 (society), 7 contra 7, idealmente cada time com **1 goleiro + 6 jogadores de linha**. São até **14 titulares** por partida. Não existe limite de inscrições: no `#sortear`, quem não couber entre os 14 titulares (pela regra de prioridade do item 5) fica como reserva. O sorteio exige **no mínimo 10 jogadores** na lista. A partir disso, **a prioridade é sempre dar jogo**: falta de goleiro ou lista incompleta (menos de 14) nunca impede o sorteio.

1. **Gatilho:** Mensagem contendo a string `#vou`.
2. **Identificação:** Resolve o `jogadorId` de quem enviou a mensagem a partir do `participant` (número ou `@lid`), conforme a seção "Identificação de Participantes" do módulo de mensageria.
3. **Lista sempre aberta:** A "Lista do Dia" é o documento da partida do dia (seção 0) com `status` e o array `confirmados`, em ordem de chegada. A lista fica `ABERTA` até o administrador enviar `#sortear` e **não fecha ao atingir 14 nomes**.
4. **Inclusão (atômica):** A checagem e a inclusão acontecem numa **única operação atômica** no MongoDB (`findAndModify` retornando o documento atualizado), nunca em dois passos separados ("ler e depois inserir"):
   ```js
   // filtro: só atualiza se TODAS as condições forem verdadeiras no momento da escrita
   {
     _id: partidaId,
     status: "ABERTA",
     "confirmados.jogadorId": { $ne: jogadorId }   // ainda não está na lista
   }
   // update
   { $push: { confirmados: { tipo: "MEMBRO", jogadorId, nome, confirmadoEm: agora } } }
   ```
   * O `$push` é atômico, então a **ordem do array é a ordem oficial de chegada**, mesmo com vários `#vou` simultâneos.
   * Se o update não alterar nenhum documento, o backend consulta a partida para saber o motivo (lista já sorteada ou jogador já confirmado) e responde de acordo.
5. **Titulares e reservas (definidos só no `#sortear`, membros têm prioridade):** Enquanto a lista está aberta, ela é apenas a ordem de confirmação; ninguém é titular ou reserva ainda. No `#sortear`, as vagas são calculadas em duas filas:
   1. **Membros** (`tipo: "MEMBRO"`), em ordem de chegada;
   2. **Convidados** (`tipo: "CONVIDADO"`), em ordem de chegada.

   As **14 primeiras posições dessa fila combinada são titulares**; as demais são **reservas** (primeiro os membros excedentes, depois os convidados). Ou seja, um membro que confirma depois **passa à frente** de qualquer convidado. Exemplo: 9 membros + Marcelo (convidado) + 5 membros que confirmaram depois = no sorteio, os 14 membros são titulares e Marcelo é reserva.
6. **Retorno:** Envia comando via REST para a Evolution API postar a **Mensagem da Lista** (abaixo), montada a partir do documento retornado pelo próprio `findAndModify` (não uma nova consulta), para que a lista publicada seja exatamente a que foi gravada.
   * Jogador já confirmado: não duplica e responde apenas *"João já está na lista."* (sem republicar a lista).
   * Lista já sorteada: *"Os times já foram sorteados!"*.

#### Mensagem da Lista
Publicada a cada confirmação bem-sucedida de `#vou` (membro) e `#convidado` (convidado), e sob demanda pelo `#lista`. Mostra **todos os confirmados até o momento**, membros e convidados:
* **Cabeçalho:** quem acabou de entrar (no `#lista`, sem essa linha).
* **Lista única em ordem de confirmação** (ordem de chegada no array), numerada, com o total de confirmados. **Não** separa titulares e reservas: isso só é definido no `#sortear`, e a lista final com os times é publicada pelo bot logo após o sorteio (seção C.1).
* **Convidados:** marcados com quem convidou, ex: *"Marcelo (convidado de Jadson)"*.
* **Nomes:** membros aparecem pelo **apelido** do cadastro.

Exemplo após `#convidado Marcelo` enviado por Jadson:
```
✅ Marcelo (convidado de Jadson) entrou na lista!

⚽ Jogo de hoje — segunda, 19h
Confirmados (10):
1. João
2. Pedro
3. Jadson
4. Leandro
5. Lucas
6. Rafa
7. Bruno
8. Thiago
9. Diego
10. Marcelo (convidado de Jadson)
```

#### `#lista`
1. **Gatilho:** Mensagem `#lista`.
2. **Quem pode:** qualquer membro identificado.
3. **Quando:** somente no dia do jogo (seção 0).
4. **Retorno, conforme o status da partida do dia:**
   * `ABERTA`: publica a **Mensagem da Lista**, sem a linha de cabeçalho de entrada.
   * `SORTEADA` ou `FINALIZADA`: republica os times do sorteio vigente (mesmo formato do retorno do `#sortear`).
   * `CANCELADA`: *"O jogo de hoje foi cancelado."*
   * Partida do dia ainda não aberta: *"A lista de hoje ainda não foi aberta."*
5. **Somente leitura:** não altera nada no banco.

### A.1 Jogadores Convidados (`#convidado`)
Nem todo mundo do grupo pode jogar toda semana. Para completar o time, **qualquer membro identificado** pode colocar um convidado (alguém de fora do grupo ou sem cadastro) na lista.

1. **Gatilho:** Mensagem `#convidado <nome>`, ex: `#convidado Marcelo`. Um convidado por mensagem.
2. **Quem pode:** qualquer membro cujo `jogadorId` seja resolvido (seção "Identificação de Participantes"). Quem convida **não precisa** estar na lista. Remetente não identificado recebe a mesma resposta de erro do `#vou`.
3. **Registro na lista:** o convidado entra no **mesmo array `confirmados`**, na ordem de chegada, com `tipo: "CONVIDADO"`, `jogadorId: null`, `nome`, `nomeNormalizado` (minúsculo, sem acento e sem espaços extras), `convidadoPor` (`jogadorId` de quem convidou) e `confirmadoEm`. Membros entram com `tipo: "MEMBRO"`.
4. **Inclusão (atômica):** mesmo `findAndModify` do `#vou`, com o filtro impedindo convidado repetido:
   ```js
   {
     _id: partidaId,
     status: "ABERTA",
     confirmados: { $not: { $elemMatch: { tipo: "CONVIDADO", nomeNormalizado } } }
   }
   // update
   { $push: { confirmados: { tipo: "CONVIDADO", jogadorId: null, nome, nomeNormalizado, convidadoPor, confirmadoEm: agora } } }
   ```
5. **Contagem:** o convidado **conta** para o mínimo de 10 do `#sortear`. Se ele será titular ou reserva só é definido no sorteio, onde membros têm prioridade sobre convidados (regra 5 do `#vou`).
6. **Sem skill:** convidado não tem cadastro, posição nem skill. Ele **nunca** tem skill computada (VAR ou resultado da partida) e não aparece no ranking.
7. **Retorno:**
   * Sucesso: publica a **Mensagem da Lista** (seção A), com o cabeçalho *"✅ Marcelo (convidado de Jadson) entrou na lista!"*.
   * Nome repetido: *"Marcelo já está na lista."* (sem republicar a lista).
   * Lista já sorteada: *"Os times já foram sorteados!"*.

### A.2 Remover Convidado (`#desconvidar`)
1. **Gatilho:** Mensagem `#desconvidar <nome>`, ex: `#desconvidar Marcelo`.
2. **Quem pode:** **qualquer membro identificado**, não apenas quem convidou.
3. **Quando:** somente com a lista `ABERTA`. Depois do `#sortear`, responde *"Os times já foram sorteados!"*.
4. **Remoção (atômica):** busca pelo `nomeNormalizado` (mesma normalização do `#convidado`) e remove com uma única operação condicional:
   ```js
   // filtro
   {
     _id: partidaId,
     status: "ABERTA",
     confirmados: { $elemMatch: { tipo: "CONVIDADO", nomeNormalizado } }
   }
   // update
   { $pull: { confirmados: { tipo: "CONVIDADO", nomeNormalizado } } }
   ```
   Se nada for alterado, o backend consulta a partida para responder o motivo (convidado não encontrado ou lista já sorteada).
5. **Efeito na lista:** o convidado sai da lista e os demais mantêm sua ordem de confirmação. Titulares e reservas continuam sendo definidos só no `#sortear`.
6. **Retorno:** *"Marcelo removido da lista. Confirmados: 9."*

### A.3 Sair da Lista (`#dm`)
Um membro que confirmou com `#vou` e não poderá mais jogar sai da lista com `#dm`.
1. **Gatilho:** Mensagem contendo `#dm`.
2. **Quem pode:** o próprio membro (resolvido pelo `jogadorId` do remetente). O `#dm` remove somente quem enviou; para remover convidados, usar `#desconvidar`.
3. **Quando:** somente com a lista `ABERTA`. Depois do `#sortear`, responde *"Os times já foram sorteados!"* (desistência depois do sorteio não é um cenário previsto).
4. **Remoção (atômica):**
   ```js
   // filtro
   { _id: partidaId, status: "ABERTA", "confirmados.jogadorId": jogadorId }
   // update
   { $pull: { confirmados: { tipo: "MEMBRO", jogadorId } } }
   ```
   Se nada for alterado, o backend consulta a partida para responder o motivo (jogador não estava na lista ou lista já sorteada).
5. **Efeito na lista:** igual ao `#desconvidar`: o membro sai e os demais mantêm sua ordem de confirmação.
6. **Voltar atrás:** o membro pode mandar `#vou` de novo enquanto a lista estiver aberta, mas entra no **fim da fila** de membros (perde a posição original).
7. **Retorno:** *"João saiu da lista. Confirmados: 13."* Jogador fora da lista: *"João não está na lista."*

### B. O Sistema de VAR (Gamificação via WhatsApp)
O cálculo dinâmico de *Skill* é o coração do engajamento. A Skill varia de 0.0 a 10.0.

* **Jogadas de efeito:** todas seguem exatamente as mesmas regras (Fase 1 e Fase 2) e os mesmos pesos; muda apenas a hashtag e a ação gravada.

  | Hashtag | Ação (`events.acao`) | Autor | Vítima |
  |---|---|---|---|
  | `#canetou` | `CANETA` | +0.2 por vez | -0.2 por vez |
  | `#chapelou` | `CHAPEU` | +0.2 por vez | -0.2 por vez |

  **Quantidade:** um mesmo lance pode se repetir na partida. Informe quantas vezes com `Nx` (de 1x a 10x, configurável em `aabb.skill.maximo-por-lance`); sem quantidade, vale 1. Ex: `#canetou @joao 4x` → +0.8 para o autor e -0.8 para o João após a aprovação. O número sem `x` também é aceito.

  Os exemplos abaixo usam `#canetou`, mas valem igualmente para `#chapelou`.

* **Fase 1: O Lance (Registro Pendente)**
  * Formato: `<autor> #canetou <vítima>` ou `<autor> #chapelou <vítima>`, ex: `@joao #canetou @leandro`, `@pedro #chapelou @lucas`.
  * Com quantidade: `@joao #canetou @leandro 2x`, `#chapelou @lucas 3x`.
  * **Lance próprio:** o WhatsApp não permite mencionar a si mesmo. Sem autor antes da hashtag (ou com `eu`), o **autor é quem enviou**: `#canetou @leandro` ou `eu #canetou @leandro`. As regras de votação não mudam (o autor continua sem poder votar no próprio lance). Autor e vítima podem ser uma **menção** (`@número` ou `@lid`) ou um **nome em texto livre** (ex: `marcelo`, um convidado).
  * O backend faz o parse dos dois lados e tenta resolver cada um para o `jogadorId`:
    * Menção por número ou por `@lid` vinculado → **identificado** (membro do time).
    * Menção por `@lid` ainda não vinculado → **não identificado**.
    * Nome em texto livre → **não identificado** (tratado como convidado; não é feita busca por nome/apelido, para evitar vincular a pessoa errada).
  * **Regra do lado identificado:** o lance é registrado desde que **pelo menos um** dos lados (autor ou vítima) seja identificado. A skill só é alterada para quem foi identificado; o lado não identificado é registrado apenas para histórico, sem efeito na skill.
    * `@joao #canetou @lid-desconhecido` → João ganha skill; a vítima não perde (não identificada).
    * `marcelo #canetou @leandro` → Marcelo (convidado) não ganha; Leandro perde skill por ser membro do time.
    * `marcelo #canetou @lid-desconhecido` (ninguém identificado) → o lance **não** é registrado e o bot responde *"Não reconheci nenhum jogador nesse lance."*
  * Salva na collection `events` um documento com status `PENDING`, ação conforme a hashtag (`CANETA` ou `CHAPEU`), o `messageId` original, uma lista vazia de aprovadores (`approvers`) e, para autor e vítima: `jogadorId` (ou `null`), o JID original (quando houver menção) e o nome exibido.
  * Responde no grupo: *"🔍 Caneta de João na cabine do VAR. Precisa de 3 👍 para valer."* (ou *"🔍 Chapéu de Pedro…"*) Quando um lado não for identificado, acrescenta: *"(Leandro não identificado, não terá alteração de skill.)"*

* **Fase 2: A Validação (Voto do Grupo)**
  * A aprovação é feita pelo grupo, não por um administrador: o lance só vale quando a mensagem original recebe **3 ou mais reações "👍" (polegar para cima)** de pessoas diferentes.
  * O backend recebe um webhook do tipo `reaction` e busca o evento pelo `messageId` da mensagem reagida. Se não existir ou não estiver `PENDING`, ignora.
  * **Contagem de votos:** cada participante conta uma única vez (o WhatsApp permite uma reação por pessoa por mensagem).
    * Reação "👍" → resolve o `jogadorId` de quem reagiu (número ou `@lid`) e adiciona em `approvers` com `$addToSet` (sem duplicar).
    * Reação removida (texto vazio) ou trocada por outro emoji → remove o `jogadorId` de `approvers`.
    * Reações do próprio autor do lance (o beneficiado) não contam, quando o autor for identificado.
  * **Aprovação:** quando `approvers` atinge **3**, o status muda de `PENDING` para `APPROVED`. A atualização deve ser atômica no MongoDB (condicionada a `status = PENDING`), para que reações simultâneas não apliquem a pontuação duas vezes.
  * **Cálculo:** somente na aprovação aplica o peso predefinido (+0.2 para quem deu, -0.2 para quem tomou), **apenas para o lado identificado** (`jogadorId` não nulo). O lado não identificado é ignorado no cálculo. Garante limites (Min 0, Max 10).
  * **Sem retroatividade:** se um `@lid` não identificado for vinculado a um jogador depois, os lances já aprovados não são recalculados.
  * Depois de `APPROVED`, novas reações ou remoções não alteram mais a skill.
  * Responde no grupo com o novo placar das skills.

### C. Sorteio dos Times (Algoritmo Determinístico)
O sorteio é calculado localmente pelo backend, sem IA. Como o número de jogadores é pequeno, o algoritmo **testa todas as divisões possíveis** e escolhe entre as melhores, garantindo times equilibrados em milissegundos.

#### C.1 `#sortear`
1. **Gatilho:** Administrador envia `#sortear` (comando exclusivo de admin).
2. **Fechamento da lista (atômico):** O backend muda o `status` da partida de `ABERTA` para `SORTEADA` com um update condicional que exige `status: "ABERTA"` **e pelo menos 10 confirmados** (`$expr: { $gte: [ { $size: "$confirmados" }, 10 ] }`). A partir daí, `#vou`, `#convidado`, `#desconvidar` e `#dm` são recusados. O sorteio usa o documento retornado por essa operação, garantindo que nenhuma alteração concorrente da lista entre depois do corte. Se a partida já estiver `SORTEADA`, o comando é ignorado (para trocar os times, usar `#ressortear`). Se houver menos de 10 confirmados, a lista **continua `ABERTA`**, nada é sorteado e o bot responde: *"Mínimo de 10 jogadores para sortear. Temos 8/10."*
3. **Coleta de Dados:** Do array `confirmados`, aplica a regra de prioridade do `#vou` (membros antes de convidados, cada fila em ordem de chegada): as **14 primeiras posições são titulares** e as demais são **reservas**. Se houver 14 ou menos confirmados, todos são titulares e não há reservas. Cada um desses grupos é separado em **membros** (`tipo: "MEMBRO"`, com skill) e **convidados** (`tipo: "CONVIDADO"`, sem skill). Para cada membro, o backend usa Nome, Posições (G, Z, L, M, A) e Skill Atual. A **posição principal** é a primeira do array de posições; um jogador é **goleiro** quando a posição principal é `G`.
4. **Regras do sorteio:**
   * **Titulares:** divididos igualmente entre os times (7 x 7 com a lista completa), contando membros e convidados.
   * **Membros primeiro, convidados depois:** o equilíbrio de skill e posição considera **somente os membros**. Os convidados não entram no cálculo e são **distribuídos aleatoriamente** entre os times, apenas para completar o número de jogadores de cada lado.
   * **Entre 10 e 13 confirmados:** o sorteio acontece **com os nomes que estiverem na lista**, sem bloqueio. Os times ficam com o mesmo número de jogadores ou diferença de no máximo 1 (ex: 11 confirmados → 6 x 5).
   * **Goleiros:** se houver goleiros entre os titulares, cada time recebe no máximo 1, para não ficarem os dois no mesmo time. **Se não houver goleiro suficiente, o sorteio segue normalmente sem apontar goleiro** para o time que ficar sem; os jogadores resolvem em campo. Reservas **não** são promovidos para cobrir a falta de goleiro. Goleiros titulares além do segundo entram como jogadores de linha.
   * **Reservas:** também são divididos entre os dois times (banco do Azul e banco do Amarelo), **considerando posição e skill** da mesma forma que os titulares, para que o banco de cada time fique equilibrado. Um titular nunca vira reserva, e vice-versa.
5. **Algoritmo (titulares):**
   1. Gera **todas** as divisões válidas dos **membros titulares** em dois grupos com diferença de tamanho de no máximo 1 (ex: 14 jogadores → 1.716 divisões; 13 → 1.716; 10 → 126), descartando as que colocam 2 goleiros no mesmo time quando houver 2 ou mais goleiros.
   2. Para cada divisão calcula:
      * `difSkill` = diferença absoluta entre as somas de skill dos dois times (critério principal);
      * `difPosicao` = soma, para cada posição de linha (Z, L, M, A), da diferença de quantidade de jogadores com aquela posição principal em cada time (critério secundário).
   3. **Candidatas:** mantém as divisões com `difSkill <= menorDifSkill + toleranciaSkill` (padrão **0.5**, configurável) e, dentre elas, as com `difPosicao <= menorDifPosicao + 1`.
   4. **Escolha:** sorteia uma das candidatas com um gerador pseudoaleatório inicializado por uma **semente (`seed`)** gerada no momento do sorteio. A mesma semente sorteia também qual grupo será o Time Azul e qual será o Time Amarelo.
   5. **Convidados titulares:** embaralhados com a mesma `seed` e distribuídos um a um, sempre para o time com **menos jogadores** no momento (em caso de empate, o time é escolhido pela `seed`). Assim os times terminam com o mesmo número de jogadores, ou diferença de no máximo 1, independentemente de quantos convidados existam. Se todos os titulares forem convidados, a divisão é totalmente aleatória.
6. **Algoritmo (reservas):** mesmo procedimento aplicado aos reservas (sem a regra de goleiro): membros reservas equilibrados por skill e posição, convidados reservas distribuídos aleatoriamente para igualar os bancos. Com número ímpar de reservas, o reserva a mais vai para o time com **menor** soma de skill nos titulares. Com 1 reserva, ele vai para esse mesmo time.
7. **Reprodutibilidade:** a partida guarda o histórico de sorteios (`sorteios[]`), cada um com `versao`, `seed`, `versaoAlgoritmo`, times resultantes e `criadoEm`. Com a mesma lista, a mesma `seed` e a mesma `versaoAlgoritmo`, o resultado é sempre idêntico, o que permite auditar qualquer sorteio.
8. **Retorno:** o bot publica os dois times no grupo (titulares, goleiro quando houver, reservas e soma de skill dos titulares de cada time). Convidados aparecem marcados, ex: *"Marcelo (convidado)"*, e a soma de skill indica quantos convidados não entraram na conta, ex: *"Skill 38.5 (+1 convidado)"*.
9. **Presença:** no mesmo processamento do sorteio, registra a presença dos membros sorteados (seção D).

#### C.2 `#ressortear`
Permite ao administrador gerar **outros times** quando o grupo não gostar do sorteio, sem reabrir a lista.
1. **Gatilho:** Administrador envia `#ressortear` (comando exclusivo de admin).
2. **Pré-condição:** a partida precisa estar `SORTEADA`. Se estiver `ABERTA`, o bot responde *"Ainda não houve sorteio. Use #sortear."*; se o resultado já tiver sido registrado (seção E) ou a partida estiver `FINALIZADA`, o comando é recusado: *"O resultado já foi registrado, os times não podem mais mudar."*
3. **Mesma lista:** usa exatamente os mesmos titulares e reservas do sorteio anterior. A lista **não** é reaberta e novos `#vou` continuam recusados.
4. **Nova semente:** gera uma nova `seed` e roda o mesmo algoritmo, **excluindo as divisões de titulares já usadas** em sorteios anteriores dessa partida (comparando a composição completa dos times, membros e convidados). Se todas as candidatas já tiverem sido usadas, volta a considerar as anteriores (os times podem repetir).
5. **Atômico:** grava o novo sorteio com update condicional na versão atual (`status: "SORTEADA"` e `sorteioAtual: <versão lida>`), incrementando `sorteioAtual`. Se dois `#ressortear` chegarem juntos, só um é aplicado; o outro é ignorado.
6. **Retorno:** o bot publica os novos times com o aviso *"🔄 Novo sorteio (#2)"*. O sorteio vigente é sempre o último; os anteriores ficam só no histórico.

### D. Lista de Presença
Todo membro tem um histórico de presenças, usado no painel (ranking e perfil do jogador).

1. **Quando é computada:** no **`#sortear`**, logo após o sorteio ser gravado.
2. **Quem recebe presença:** todos os **membros** (`tipo: "MEMBRO"`) que estão no sorteio, **titulares e reservas**.
3. **Quem não recebe:** membros que não estavam na lista sorteada (não mandaram `#vou` ou saíram com `#dm` antes do sorteio) e **convidados**, que não têm cadastro.
4. **Registro:** collection `presencas`, um documento por membro por partida: `{ jogadorId, partidaId, data, criadoEm }`, com **índice único em `(jogadorId, partidaId)`**. A gravação é feita com upsert, então reprocessar o mesmo sorteio (ex: webhook reenviado) não duplica presença.
5. **`#ressortear`:** não altera presenças, pois a lista de jogadores é a mesma; só os times mudam.
6. **`#jogocancelado`:** se a partida for cancelada depois do sorteio, todas as presenças daquela partida são removidas (`deleteMany({ partidaId })`). Partida cancelada nunca conta presença.
7. **Consulta:** o total de presenças de um membro é a contagem dos seus documentos em `presencas` (por ano, por período ou geral) **mais o ajuste manual** do admin; o histórico é a lista de datas.
8. **Ajuste manual:** no painel, o admin pode informar o total de presenças do ano atual de um membro. A diferença para as presenças computadas é guardada no jogador (`ajustesDePresenca`, por ano) e somada nas consultas.

### E. Resultado da Partida (`#timeazulganhou` / `#timeamareloganhou` / `#empate`)
Registra o resultado pelo WhatsApp e aplica o bônus de skill.

1. **Gatilho:** Mensagem `#timeazulganhou`, `#timeamareloganhou` ou `#empate`.
2. **Quem pode:** **qualquer pessoa do grupo** (não é preciso ser admin nem estar identificado).
3. **Quando:** somente no dia do jogo, com a partida `SORTEADA` ou `FINALIZADA`. Partida `ABERTA`: *"Ainda não houve sorteio."*; `CANCELADA`: *"O jogo de hoje foi cancelado."*
4. **Uma única vez por partida:** o primeiro comando válido registra o resultado. Update condicional atômico na partida (`status ∈ {SORTEADA, FINALIZADA}` e `resultado: null`) → grava `resultado` (`AZUL`, `AMARELO` ou `EMPATE`), `resultadoInformadoPor` (JID de quem enviou) e `resultadoEm`. Se outro comando de resultado chegar depois (igual ou diferente), nada muda e o bot responde *"O resultado de hoje já foi registrado: Time Azul venceu."* (ou *"…: empate."*).
   * **Sem correção:** o resultado registrado é **definitivo**. Se alguém enviar o resultado errado, ele permanece; não há desfazer pelo WhatsApp nem pelo painel.
5. **Skill:**
   * **Vencedor:** **+0.3** para **todos os membros** do time vencedor no sorteio vigente, **titulares e reservas**. Respeita o limite máximo de 10.0.
   * **Perdedor:** nenhuma alteração.
   * **Empate (`#empate`):** **+0.1** para **todos os membros que jogaram**, dos dois times, **titulares e reservas**. Respeita o limite máximo de 10.0.
   * **Convidados:** ignorados (não têm skill).
6. **Consistência:** o registro do resultado e o bônus de skill são gravados na **mesma transação** do MongoDB (o `aabb-mongodb` roda como replica set de um nó para permitir transações). Assim o bônus nunca é aplicado duas vezes nem fica pela metade.
7. **Efeitos:** depois do resultado registrado, `#ressortear` e `#jogocancelado` são recusados. A vitória conta como **título** para os membros do time vencedor (coluna "Títulos" do ranking); empate não conta título.
8. **Retorno:**
   * Vitória: *"🏆 Time Azul venceu! +0.3 de skill para: João, Pedro, Lucas, ..."*
   * Empate: *"🤝 Empate! +0.1 de skill para todos que jogaram."*

### F. Gols (`#marcou`)
Registra os gols de um jogador na partida do dia. Assim como as jogadas de efeito, o gol **passa pelo VAR**: só aumenta a skill depois de aprovado pelo grupo.

1. **Gatilho:** Mensagem `<jogador> #marcou <quantidade>x`, ex: `@jadson #marcou 4x`. Um jogador por mensagem. Sem quantidade, vale **1** gol (`@jadson #marcou`). Sem jogador antes da hashtag (ou com `eu`), os gols são de **quem enviou**: `#marcou 2x`. O número sem `x` também é aceito.
2. **Quem pode enviar:** qualquer membro identificado.
3. **Quando:** somente no dia do jogo, com a partida `SORTEADA` ou `FINALIZADA`. Partida `ABERTA`: *"Ainda não houve sorteio."*; `CANCELADA`: *"O jogo de hoje foi cancelado."* A votação (reações) pode terminar depois, como no VAR.
4. **Quem marcou:** a menção é resolvida para o `jogadorId` (número ou `@lid`, seção "Identificação de Participantes"). Só é registrado se for um **membro** que está no **sorteio vigente** (titular ou reserva).
   * Convidado (nome em texto livre), `@lid` não vinculado ou membro fora do sorteio: nada é registrado e o bot responde *"Gols de Marcelo não computados: só membros que estão no jogo de hoje."*
5. **Quantidade:** de **1x a 10x** (mesmo limite das jogadas de efeito). Fora disso, o comando é recusado: *"Quantidade inválida. Use de 1x a 10x, ex: #marcou 2x."*
6. **Fase 1, registro pendente:** salva na collection `events` um documento com status `PENDING`, ação `GOL`, `partidaId`, `jogadorId` de quem marcou, `quantidade`, quem informou, o `messageId` original e `approvers` vazio. Responde: *"🔍 4 gols de Jadson na cabine do VAR. Precisa de 3 👍 para valer."*
7. **Fase 2, votação:** **mesmas regras da Fase 2 do VAR** (seção B): 3 reações 👍 de pessoas diferentes na mensagem do `#marcou`, votos por `jogadorId`, remoção da reação tira o voto, e o próprio jogador que marcou **não vota** nos seus gols. A aprovação (`PENDING` → `APPROVED`) é atômica.
8. **Uma única aprovação por jogador por partida:** enquanto nenhum `#marcou` do jogador foi aprovado, outros podem ser enviados (ex: para corrigir a quantidade antes da votação). O **primeiro aprovado** é o que vale; a unicidade é garantida por um índice único parcial em `(partidaId, jogadorId)` para eventos `GOL` com status `APPROVED`. Quando um é aprovado, os demais `#marcou` pendentes do mesmo jogador naquela partida passam para `DESCARTADO`. Se um novo `#marcou` chegar depois da aprovação, o bot responde *"Gols de Jadson já registrados hoje: 4."* O gol aprovado é **definitivo**.
9. **Skill:** na aprovação, **+0.3 por gol** (`0.3 × quantidade`), somente para membros. Respeita o limite máximo de 10.0. A aprovação, o descarte dos pendentes e o aumento de skill são gravados na **mesma transação** do MongoDB.
10. **Retorno da aprovação:** *"⚽ VAR confirmou: 4 gols de Jadson! +1.2 de skill."*
11. **Ranking:** a coluna "Gols no Ano" do painel soma a `quantidade` dos eventos `GOL` aprovados.

### G. Consultas no Grupo (`#ranking` / `#presenca` / `#titulos`)
Qualquer membro pode pedir, em **qualquer dia**. Só leitura: não altera nada.

* **`#ranking`:** todos os membros ativos que **já jogaram ao menos uma vez** (têm presença), com a skill atual, da maior para a menor. Empate de skill: ordem alfabética.
* **`#presenca`** (ou `#presença`): presenças no **ano atual**, de quem tem mais para quem tem menos; só aparece quem tem ao menos uma presença no ano. Empate: ordem alfabética.
* **`#titulos`** (ou `#títulos`): títulos no **ano atual** (vitórias registradas por `#timeazulganhou` / `#timeamareloganhou`; empate não conta), de quem tem mais para quem tem menos; só aparece quem tem ao menos um título no ano. Todos os membros do time vencedor recebem o título, titulares e reservas. Empate: ordem alfabética.
* Quem só está no grupo e nunca jogou não aparece em nenhuma das listas.
* Formato: os três primeiros com 🥇 🥈 🥉 e o restante numerado.
  ```
  🏆 Ranking de habilidade
  🥇 João — 7.6
  🥈 Pedro — 7.2
  🥉 Lucas — 6.9
  4. Rafa — 6.5
  ```
  ```
  📋 Presenças em 2026
  🥇 João — 12
  🥈 Pedro — 11
  ```

## 3. Idempotência e Concorrência
* **Identidade:** jogadores são sempre referenciados pelo `jogadorId` interno, nunca pelo número ou `@lid`, para que a mesma pessoa não seja contada duas vezes quando aparecer em formatos diferentes.
* Toda mensagem recebida da Evolution API possui um `id` único.
* **Toda mensagem é processada uma única vez:** antes de executar qualquer comando ou reação, o backend registra o `id` numa collection `mensagens_processadas` com **índice único** (e TTL de alguns dias). Se o insert falhar por duplicidade, o webhook é um reenvio e é ignorado. Isso protege todos os comandos (ex: um `#ressortear` reenviado não gera um segundo sorteio) e as pontuações.
* **Regra geral de concorrência:** toda regra do tipo "verificar e depois alterar" (abertura da lista, cancelamento, inclusão no `#vou` e no `#convidado`, remoção no `#desconvidar` e no `#dm`, fechamento da lista e registro de presenças no `#sortear`, troca de times no `#ressortear`, registro do resultado, aprovação do VAR e dos gols) deve ser feita com update condicional atômico no MongoDB, colocando a condição no filtro do update. Não usar o padrão ler → decidir em Java → gravar, pois webhooks são processados em paralelo.
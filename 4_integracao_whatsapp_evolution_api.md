# Módulo Mensageria: Evolution API (WhatsApp Headless)

## 1. Visão Geral e Mitigação de Riscos
A Evolution API envelopa a biblioteca Baileys, criando um servidor que simula um WhatsApp Web. 
* **Regra de Ouro (Anti-Ban):** Utilizar OBRIGATORIAMENTE um chip de celular pré-pago dedicado exclusivamente para o bot. Nunca usar contas pessoais ou empresariais vitais.
* **Humanização:** toda mensagem enviada pelo backend informa um `delay` no body do `sendText` (seção 4); durante esse tempo a Evolution API mostra "digitando..." no grupo, simulando interação humana.

## 2. Configuração do Webhook
O webhook é configurado de forma **global** pelas variáveis de ambiente da Evolution API no `docker-compose.yml` (`WEBHOOK_GLOBAL_*`), sem passo manual: toda instância envia os eventos `MESSAGES_UPSERT` para a rota local do backend Java. Esse evento traz tanto as mensagens de texto (comandos) quanto as **reações** do VAR (`reactionMessage`); o `MESSAGES_UPDATE` (status de entrega/leitura) não é necessário. Rota: `http://aabb-backend:8080/api/whatsapp/webhook` (`aabb-backend` é o nome do serviço no `docker-compose`).

## 3. Estruturas de Payload (JSON)

### Exemplo de Entrada: Comando com Menções (`#canetou` / `#chapelou`)
Quando o backend receber o webhook, a estrutura fundamental a ser lida será semelhante a esta:
```json
{
  "instance": "aabb-bot",
  "data": {
    "key": {
      "remoteJid": "5511999999999-1612345678@g.us", // ID do Grupo
      "participant": "123456789012345@lid", // Quem enviou (pode vir como @lid ou @s.whatsapp.net)
      "participantAlt": "5511933333333@s.whatsapp.net", // Identificador alternativo do remetente (quando disponível)
      "addressingMode": "lid", // "lid" ou "pn" (phone number)
      "id": "BAE5XXXXXXXXXXXXXXXX" // ID Único da Mensagem (Chave de Idempotência)
    },
    "messageType": "conversation",
    "message": {
      "conversation": "@123456789012 #canetou @987654321098765"
    },
    "contextInfo": { // Na v2 as menções vêm aqui, fora de "message"
      "mentionedJid": [
        "123456789012@lid",   // João (Autor)
        "987654321098765@lid" // Leandro (Vítima)
      ]
    }
  }
}
```
*Formato confirmado em webhooks reais: em grupos com `addressingMode: "lid"`, as menções chegam como `@lid` e o texto traz `@<número do LID>`. O backend também aceita o formato antigo (`message.extendedTextMessage.contextInfo.mentionedJid`).*

### Exemplo de Entrada: Reação (O VAR)
Cada participante do grupo que concorda com o lance reage com "👍":
```json
{
  "instance": "aabb-bot",
  "data": {
    "key": {
      "remoteJid": "5511999999999-1612345678@g.us", // ID do Grupo
      "participant": "5511922222222@s.whatsapp.net", // Quem reagiu (votante) – pode vir como @lid
      "participantAlt": "112233445566778@lid", // Identificador alternativo do votante (quando disponível)
      "id": "BAE5YYYYYYYYYYYYYYYY" // ID da própria reação
    },
    "message": {
      "reactionMessage": {
        "key": {
          "id": "BAE5XXXXXXXXXXXXXXXX" // ID da mensagem original do #canetou / #chapelou / #marcou
        },
        "text": "👍" // Vazio ("") quando a reação é removida
      }
    }
  }
}
```
*A lógica do backend utiliza `reactionMessage.key.id` para achar o evento `PENDING` no MongoDB e `data.key.participant` / `participantAlt` para identificar o jogador que reagiu (ver seção 5) e registrar o voto dele. Quando o evento soma 3 votos "👍" distintos, ele é aprovado e a skill é calculada.*

## 4. Envio de Mensagens (Saída)
O Spring Boot envia a resposta consumindo a Evolution API **v2** (formato validado na versão 2.3.7).
* **Endpoint:** `POST http://aabb-evolution-api:8080/message/sendText/aabb-bot`
* **Headers:** `apikey: SUACHAVEDAEVOLUTION` e `Content-Type: application/json`
* **Body:**
```json
{
  "number": "5511999999999-1612345678@g.us", // ID do Grupo
  "text": "🔥 Ousadia! @5511900000000 subiu para 7.4 e @5511911111111 caiu para 6.8!",
  "delay": 2000, // ms em "digitando..." antes de enviar
  "linkPreview": false,
  "mentioned": [ // Opcional: transforma os @número do texto em menções clicáveis
    "5511900000000@s.whatsapp.net",
    "5511911111111@s.whatsapp.net"
  ]
}
```

**Campos do body (v2):**
| Campo | Obrigatório | Descrição |
|---|---|---|
| `number` | Sim | Destino: JID do grupo (`...@g.us`) ou número em formato internacional sem `+`. |
| `text` | Sim | Texto da mensagem (fica na raiz do body). |
| `delay` | Não | Tempo em milissegundos que o bot fica com status "digitando..." antes de enviar. A Evolution API envia a presença `composing` automaticamente durante esse tempo (humanização anti-ban). |
| `linkPreview` | Não | Gera prévia de links do texto. Usar `false` nas mensagens do bot. |
| `mentioned` | Não | Lista de JIDs mencionados. O texto deve conter `@número` correspondente para a menção aparecer. **Omitir quando não houver menção**: a Evolution API rejeita lista vazia com erro 400. |
| `mentionsEveryOne` | Não | `true` menciona todos os participantes do grupo. Usar com moderação. |
| `quoted` | Não | Responde citando uma mensagem (`{ "key": { "id": "<messageId>" } }`), útil para responder ao `#canetou` / `#chapelou` original. |

> **Formato antigo (v1), não usar:** os campos `options.delay`, `options.presence` e `textMessage.text` foram substituídos por `delay` e `text` na raiz do body.

## 5. Identificação de Participantes (Número x `@lid`)
O WhatsApp está migrando a identificação de usuários em grupos do número de telefone (`5511900000000@s.whatsapp.net`) para um identificador anônimo, o **LID** (`987654321098765@lid`). Um mesmo jogador pode aparecer **ora pelo número, ora pelo LID**, no remetente (`key.participant`), nas reações e nas menções (`mentionedJid`). O sistema deve funcionar com qualquer um dos dois formatos.

### Regras
1. **Nunca identificar jogador pelo JID bruto.** Todo JID recebido passa por um `ResolvedorDeParticipante` que devolve o **`jogadorId` interno** (id do documento no MongoDB). Regras de negócio (comandos de lista, VAR, votos, presenças) usam sempre o `jogadorId`, nunca o número ou o LID.
2. **Normalização do JID:**
   * Remover o sufixo de dispositivo (`5511900000000:12@s.whatsapp.net` → `5511900000000@s.whatsapp.net`).
   * Classificar pelo sufixo: `@s.whatsapp.net` → **número** (somente dígitos, formato internacional); `@lid` → **LID**; `@g.us` → grupo (nunca é jogador).
3. **Cadastro do jogador:** guarda o `whatsapp` (número, informado no cadastro) e uma lista `lids` (aprendida automaticamente). Índices únicos em `whatsapp` e em `lids`.
   * **Nono dígito:** o WhatsApp ainda identifica muitos celulares brasileiros sem o nono dígito (ex: cadastro `5548999990011`, webhook `554899990011`). Na busca por número, o backend considera os dois formatos como o mesmo celular, e o cadastro não aceita o mesmo celular duas vezes (com e sem o 9).
4. **Resolução (nessa ordem):**
   1. JID por número → busca por `whatsapp`.
   2. JID por LID → busca por `lids`.
   3. Se o remetente/votante veio com `participant` **e** `participantAlt`, tenta os dois formatos.
5. **Aprendizado automático do vínculo LID ↔ número:** sempre que um webhook trouxer, para a mesma pessoa, um JID por número e outro por LID (`participant` + `participantAlt`, ou `remoteJid` + `remoteJidAlt`), o backend grava o LID no jogador encontrado pelo número com `$addToSet: { lids: lid }`. A operação é idempotente e pode rodar em todo webhook.
6. **LID desconhecido:**
   * **Remetente sem cadastro:** se o webhook trouxer o número (`participantAlt`), a pessoa é **cadastrada automaticamente como membro ativo** (ver doc 3, Cadastro de Jogadores) e o comando é processado normalmente. Se vier só o `@lid`, ou se o cadastro estiver desativado, o bot responde *"Não reconheci seu número. Peça a um admin para conferir seu cadastro."* e não processa o comando.
   * **Menção** (`mentionedJid` com LID ainda não vinculado): aquele lado do lance fica **não identificado**. O lance continua valendo para o outro lado, se ele for identificado (regra do VAR no módulo backend), e o lado não identificado não tem alteração de skill. Assim que esse jogador enviar qualquer mensagem no grupo, o vínculo é aprendido pela regra 5 e os próximos lances passam a contar para ele.
   * **Votante** (reação 👍): o voto é ignorado até o vínculo existir.
7. **Votos e duplicidade:** como os votos do VAR e a lista do `#vou` guardam o `jogadorId`, a mesma pessoa aparecendo por número numa reação e por LID em outra **conta uma única vez**.
8. **Menções na saída (`sendText`):** usar o JID por número (`<whatsapp>@s.whatsapp.net`) no campo `mentioned` e `@<whatsapp>` no texto, pois o número sempre existe no cadastro.

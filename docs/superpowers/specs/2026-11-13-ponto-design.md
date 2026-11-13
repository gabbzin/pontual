# Pontual — App de Ponto Pessoal — Design

**Data:** 2026-11-13
**Status:** aguardando aprovação

## Por quê

App de bater ponto só pra mim, no estágio. Serve pra ter registro próprio das minhas horas (independente do controle da empresa), saber meu saldo de horas e quanto recesso já acumulei. Também é um side project pra praticar Android nativo com Kotlin.

## Objetivos

- Bater entrada e saída em **um toque** no celular.
- Respeitar o expediente: só aceitar batidas **entre 08:00 e 18:00, com 5 minutos de tolerância** (07:55–18:05). Dentro dessa janela, entrada e saída são livres.
- Ver as batidas e o total de horas do dia e da semana.
- Ver o **saldo de horas** contra a jornada de **6h/dia** em dias úteis.
- Registrar feriados e dias de recesso, que não contam como dia devido.
- Lançar manualmente uma batida **somente quando eu esquecer**, sempre com observação.
- Ver o **recesso acumulado** proporcional ao tempo de estágio, menos os dias já usados.

## Fora do escopo

Versão única: tudo acima é entregue junto. Fica de fora:

- Mais de um usuário, cadastro ou login com senha.
- Funcionar offline / fila de batidas.
- Batida automática por Wi-Fi ou geofence, widget e notificações.
- Exportar em PDF ou CSV.
- Frontend web.

## Anti-objetivos

- **Acoplar ao Axon.** Nenhum código, tabela ou rota do ponto entra no repositório nem no schema do Axon.
- **Overengineering.** Nada de Clean Architecture completa, CQRS ou fila. São três tabelas e um punhado de rotas.
- **Gravar o horário pelo relógio do celular.** Na batida normal, quem define o horário é o servidor.
- **Reescrever o passado.** Batidas feitas pelo app nunca são editadas nem apagadas. A entrada manual só preenche buracos.

## Abordagens consideradas

| Opção | Trade-off | Decisão |
|---|---|---|
| Endpoint escondido na API do Axon | Mais rápido, mas fura o `TenantContextGuard` e mistura um dado pessoal no produto | ❌ |
| **NestJS separado no mesmo VPS** | Um serviço a mais no Compose, mas fica isolado e sem custo extra | ✅ escolhida |
| App Android falando direto com o Postgres | Credencial do banco dentro do APK | ❌ |

## Arquitetura

```
[App Android (Kotlin/Compose)] --HTTPS + token--> [Nginx: ponto.<dominio>] --> [NestJS ponto-api (container)] --> [Neon: database `ponto`]
```

- **Backend:** NestJS + Prisma, num container próprio no Docker Compose do VPS e num subdomínio próprio no Nginx.
- **Banco:** um database `ponto` separado no Neon, com migrations próprias.
- **Auth:** um usuário só, então um **token estático** (long random) no `.env`. Um guard global verifica `Authorization: Bearer <token>`. O app guarda o token no DataStore.

## Modelo de dados

Uso **sessões** (entrada + saída) em vez de batidas IN/OUT soltas, então o banco nunca fica com duas entradas seguidas.

```prisma
model WorkSession {
  id          String    @id @default(uuid())
  startedAt   DateTime  @db.Timestamptz
  endedAt     DateTime? @db.Timestamptz   // null = sessão aberta
  startSource Source                      // APP | MANUAL
  endSource   Source?                     // null enquanto aberta
  note        String?                     // obrigatória se qualquer lado for MANUAL
  createdAt   DateTime  @default(now())
  updatedAt   DateTime  @updatedAt
}

model DayOff {
  date      DateTime   @id @db.Date      // dia no fuso de Settings.timezone
  kind      DayOffKind                   // HOLIDAY | RECESS
  note      String?
  createdAt DateTime   @default(now())
}

model Settings {            // linha única (id = 1), criada no seed
  id               Int      @id @default(1)
  dailyMinutes     Int      @default(360)   // 6h
  workdays         Int[]    @default([1,2,3,4,5]) // ISO weekday
  windowStart      String   @default("08:00")
  windowEnd        String   @default("18:00")
  toleranceMinutes Int      @default(5)
  internshipStart  DateTime @db.Date        // base do recesso
  balanceStart     DateTime @db.Date        // a partir de quando o saldo conta
  timezone         String   @default("America/Sao_Paulo")
}

enum Source     { APP MANUAL }
enum DayOffKind { HOLIDAY RECESS }
```

Garantias no banco (SQL puro na migration, não só validação na aplicação):

- **No máximo uma sessão aberta:** `CREATE UNIQUE INDEX one_open_session ON "WorkSession" ((true)) WHERE "endedAt" IS NULL;`
- **Sem sobreposição:** `CREATE EXTENSION IF NOT EXISTS btree_gist;` e um exclusion constraint em `tstzrange("startedAt", COALESCE("endedAt", 'infinity'))` com `&&`.
- **`endedAt > startedAt`** via `CHECK`.

Como o Prisma não conhece esses objetos, o construtor precisa conferir que `prisma migrate dev` não gera `DROP` deles nas migrations seguintes.

Tudo é gravado em UTC (`timestamptz`). A divisão por dia e semana usa `Settings.timezone`. Como a janela vai de 07:55 a 18:05, **nenhuma sessão atravessa a meia-noite**.

## Regras de batida

**Janela válida do dia** = `[windowStart − tolerância, windowEnd + tolerância]` no fuso configurado, ou seja, 07:55–18:05. Vale para qualquer dia da semana. Bater num fim de semana ou num `DayOff` é permitido e conta como hora a mais.

**Batida normal (`POST /punch`):**
- O app manda a intenção: `{ "action": "IN" | "OUT" }`. O horário é o `now()` do servidor.
- **Idempotente:** se o estado já é o pedido (`IN` com sessão aberta, `OUT` sem sessão aberta), não altera nada e retorna `200` com o estado atual. Assim, um "tentar de novo" depois de uma resposta perdida não desfaz a batida.
- Fora da janela, retorna `422` com uma mensagem clara.

**Sessão esquecida aberta:** é uma sessão aberta cujo dia já passou, ou que continua aberta depois do fim da janela de hoje.
- `/status` sinaliza `forgottenOpen: true`.
- `OUT` normal é recusado com `422` e o app manda você fechar a sessão manualmente.
- Para o cálculo, ela conta até o fim da janela daquele dia, no máximo.
- `IN` também é recusado enquanto ela existir, porque o índice permite só uma sessão aberta.

**Entrada manual (só para esquecimento):**
- `POST /sessions/:id/close { endedAt, note }`: fecha uma sessão aberta. Grava `endSource = MANUAL`.
- `POST /sessions { startedAt, endedAt, note }`: lança um período inteiro que ficou sem batida. Grava `startSource = endSource = MANUAL`.
- Regras comuns:
  - `note` é obrigatória.
  - Os horários ficam dentro da janela do dia, no passado (`≤ now()`) e no mesmo dia.
  - O período não pode sobrepor outra sessão. Se sobrepor, retorna `409`.
- **Não existe** edição de horário nem exclusão de sessão `APP`.
- *(Minha decisão)* Uma sessão **totalmente manual** pode ser apagada (`DELETE /sessions/:id`), pra corrigir um lançamento digitado errado. Sessões com algum lado `APP` não podem ser apagadas e retornam `403`.

## API

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/punch` | `{action}` idempotente, como descrito acima. Retorna a sessão afetada e o estado atual. |
| `GET` | `/status` | Diz se há sessão aberta, se ela é `forgottenOpen` e dá o resumo de hoje (trabalhado, previsto, saldo do dia) e a janela de hoje. Alimenta a tela inicial. |
| `GET` | `/sessions?from=&to=` | Lista as sessões de um período (datas locais, inclusivas). |
| `POST` | `/sessions` | Lança um período manual esquecido. |
| `POST` | `/sessions/:id/close` | Fecha manualmente uma sessão aberta esquecida. |
| `DELETE` | `/sessions/:id` | Apaga uma sessão, só se for totalmente `MANUAL`. |
| `GET` | `/summary?week=YYYY-Www` | Total por dia, previsto, `DayOff` do dia e saldo da semana (semana ISO). |
| `GET` | `/balance` | Saldo acumulado de `balanceStart` até **ontem**, mais o saldo de hoje separado. |
| `GET` | `/recess` | Recesso acumulado, dias usados e dias disponíveis. |
| `GET/POST/DELETE` | `/days-off` | Lista (`?from=&to=`), cria (`{date, kind, note?}`) e apaga feriados e recessos. |
| `GET/PUT` | `/settings` | Lê e edita a configuração. |

Erros seguem o padrão do NestJS (`{ statusCode, message }`), com mensagens em português que o app mostra direto.

## Regras de cálculo

Ficam todas num módulo puro, sem banco, testável:

- **Trabalhado no dia:** soma de `endedAt − startedAt` das sessões que começaram naquele dia. Uma sessão aberta conta até `min(now(), fim da janela do dia)`.
- **Previsto no dia:**
  - `dailyMinutes` se o dia está em `workdays`, não é `DayOff` e está entre `balanceStart` e hoje.
  - `0` em qualquer outro caso.
- **Saldo:** trabalhado menos previsto, somado no período.
- **Recesso:** a Lei do Estágio (11.788, art. 13) prevê 30 dias por ano, proporcionais ao tempo estagiado. Então `floor(30 × mesesCompletos(internshipStart, hoje) / 12) − count(DayOff RECESS)`. *Confirmar a regra exata no TCE.*
- **Mudança em `Settings`:** vale retroativamente, porque o saldo é sempre recalculado. *(Minha decisão; histórico de jornada fica de fora.)*

## App Android

- **Stack:**
  - Kotlin, Jetpack Compose (Material 3), ViewModel + `StateFlow`.
  - Retrofit + kotlinx.serialization.
  - DataStore pro token e a URL.
  - `java.time` pras datas.
  - DI manual.
- **Telas:**
  1. **Hoje.**
     - Um botão grande que alterna entre "Entrar" e "Sair", com cor por estado. Fora da janela, ele fica desabilitado e mostra a janela (07:55–18:05).
     - Embaixo: o cronômetro da sessão aberta, o total de hoje, o saldo do dia e a lista de sessões de hoje.
     - Se houver `forgottenOpen`, um aviso no topo com "Fechar sessão esquecida" abre um seletor de horário e o campo de observação.
  2. **Semana.**
     - Uma lista por dia com o trabalhado e o previsto, marcando feriados e recessos, além do saldo da semana e da navegação entre semanas.
     - Tocar num dia abre as sessões dele, com "Lançar período esquecido". Sessões manuais aparecem marcadas e podem ser apagadas.
  3. **Banco.** O saldo acumulado, o recesso disponível e a lista de feriados e recessos, com adicionar e remover.
  4. **Config.** URL da API, token, jornada, dias úteis, janela e tolerância, início do estágio e início do saldo.

```
┌─────────────────────┐
│  Sexta, 13/11       │
│                     │
│    ┌───────────┐    │
│    │   SAIR    │    │   ← verde/vermelho conforme o estado
│    └───────────┘    │
│  Em andamento 02:14 │
│  Hoje 4h12 / 6h00   │
│  Saldo do dia −1h48 │
│  ─────────────────  │
│  09:40 – 12:00      │
│  13:01 – em aberto  │
├─────────────────────┤
│ Hoje  Semana  Banco │
└─────────────────────┘
```

- **Erros:** se a API não responder, o app mostra uma snackbar com "tentar de novo", que reenvia a mesma ação (é seguro, porque `/punch` é idempotente). Um `422` ou `409` mostra a mensagem do servidor.

## Deixado pro construtor

- Organização de pastas: no NestJS, módulos `sessions`, `summary`, `days-off`, `settings` e um `calc` puro; no Android, por feature.
- Datas no Node: `date-fns-tz` ou Temporal, se o runtime suportar.
- Monorepo: `api/` e `android/` neste repositório.
- Nome do subdomínio, Nginx e CI/CD.
- Testes, no mínimo unitários no `calc` e nas validações:
  - janela com tolerância nas bordas (07:54, 07:55, 18:05, 18:06);
  - idempotência do `/punch`;
  - sessão esquecida aberta;
  - sobreposição;
  - `DayOff` zerando o previsto;
  - `balanceStart`;
  - recesso proporcional.

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
- Fazer **backup e restauração** dos dados num arquivo, pra não perder o histórico se trocar ou formatar o celular.

## Fora do escopo

Versão única: tudo acima é entregue junto. Fica de fora:

- Qualquer servidor, API ou sincronização na nuvem.
- Mais de um usuário ou login.
- Batida automática por Wi-Fi ou geofence, widget e notificações.
- Relatório em PDF ou CSV. O backup em JSON é só pra restaurar, não é relatório.

## Anti-objetivos

- **Mexer no servidor do Axon.** Nada roda no VPS nem toca o Axon. O app é 100% local.
- **Overengineering.** Nada de Clean Architecture completa nem multi-módulo. Três tabelas e algumas telas.
- **Reescrever o passado.** Batidas feitas pelo botão nunca são editadas nem apagadas. A entrada manual só preenche buracos.

## Abordagens consideradas

| Opção | Trade-off | Decisão |
|---|---|---|
| API NestJS no VPS (do Axon) + Neon | Horário confiável do servidor e dados fora do celular, mas mexe no servidor do Axon e exige backend, deploy e token | ❌ |
| API NestJS + Postgres em container no VPS | Mesmo problema: continua no servidor do Axon | ❌ |
| **Só o app Android, com Room (SQLite) local** | Sem infra nenhuma e funciona offline. Em troca, o horário vem do relógio do celular e os dados ficam só no aparelho (resolvido com backup manual) | ✅ escolhida |

## Arquitetura

```
[App Android (Kotlin/Compose)] --> [Room / SQLite no aparelho]
                               --> [Arquivo JSON de backup (Storage Access Framework)]
```

- **Stack:**
  - Kotlin, Jetpack Compose (Material 3), ViewModel + `StateFlow`.
  - Room pro banco e DataStore não é necessário: a configuração fica numa tabela.
  - kotlinx.serialization pro backup.
  - `java.time` pras datas.
  - DI manual: um `AppContainer` na `Application`.
- **Camadas:**
  - `data/`: entidades, DAOs e um `PontoRepository`, que concentra todas as regras de escrita (janela, idempotência, sobreposição) dentro de transações Room.
  - `calc/`: Kotlin puro, sem Android, com o cálculo de trabalhado, previsto, saldo, recesso e janela. Recebe um `Clock` injetado, pra ser testável.
  - `ui/`: uma pasta por tela.
- **Relógio:** o horário da batida é `Instant.now()` do aparelho, via `Clock`. *(Aceito: é um app pessoal, e o Android mantém o relógio sincronizado pela rede.)*

## Modelo de dados (Room)

```kotlin
@Entity
data class WorkSession(
    @PrimaryKey val id: String,          // UUID
    val startedAt: Instant,              // armazenado como epoch millis (UTC)
    val endedAt: Instant?,               // null = sessão aberta
    val startSource: Source,             // APP | MANUAL
    val endSource: Source?,              // null enquanto aberta
    val note: String?,                   // obrigatória se qualquer lado for MANUAL
    val createdAt: Instant,
)

@Entity
data class DayOff(
    @PrimaryKey val date: LocalDate,     // armazenado como "YYYY-MM-DD"
    val kind: DayOffKind,                // HOLIDAY | RECESS
    val note: String?,
)

@Entity
data class Settings(                     // linha única (id = 1), criada na primeira abertura
    @PrimaryKey val id: Int = 1,
    val dailyMinutes: Int = 360,         // 6h
    val workdays: Set<DayOfWeek> = MON..FRI,
    val windowStart: LocalTime = 08:00,
    val windowEnd: LocalTime = 18:00,
    val toleranceMinutes: Int = 5,
    val internshipStart: LocalDate,      // base do recesso
    val balanceStart: LocalDate,         // a partir de quando o saldo conta
    val zoneId: ZoneId = America/Sao_Paulo,
)
```

Conversores Room (`TypeConverter`) cuidam de `Instant`, `LocalDate`, `LocalTime`, `ZoneId` e `Set<DayOfWeek>`.

Garantias:

- **No máximo uma sessão aberta:** índice único parcial criado em SQL puro num `RoomDatabase.Callback.onCreate` e nas migrations: `CREATE UNIQUE INDEX one_open_session ON WorkSession((1)) WHERE endedAt IS NULL;`
- **Sem sobreposição:** o SQLite não tem exclusion constraint, então a verificação fica no `PontoRepository`, dentro da mesma transação da escrita.
- **`endedAt > startedAt`:** validada no repositório.

Tudo é gravado em UTC. A divisão por dia e semana usa `Settings.zoneId`. Como a janela vai de 07:55 a 18:05, **nenhuma sessão atravessa a meia-noite**.

## Regras de batida

**Janela válida do dia** = `[windowStart − tolerância, windowEnd + tolerância]` no fuso configurado, ou seja, 07:55–18:05. Vale para qualquer dia da semana. Bater num fim de semana ou num `DayOff` é permitido e conta como hora a mais.

**Batida normal (botão):**
- `punch(action: IN | OUT)`, com o horário vindo do `Clock`.
- **Idempotente:** se o estado já é o pedido (`IN` com sessão aberta, `OUT` sem sessão aberta), não faz nada. Assim, um toque duplo não desfaz a batida.
- Fora da janela, recusa com uma mensagem clara.

**Sessão esquecida aberta:** é uma sessão aberta cujo dia já passou, ou que continua aberta depois do fim da janela de hoje.
- O app mostra um aviso.
- `OUT` normal é recusado, e o app pede pra você fechar a sessão manualmente.
- Para o cálculo, ela conta até o fim da janela daquele dia, no máximo.
- `IN` também é recusado enquanto ela existir.

**Entrada manual (só para esquecimento):**
- **Fechar sessão esquecida** `(endedAt, note)`: grava `endSource = MANUAL`.
- **Lançar período esquecido** `(startedAt, endedAt, note)`: grava `startSource = endSource = MANUAL`.
- Regras comuns:
  - `note` é obrigatória.
  - Os horários ficam dentro da janela do dia, no passado (`≤ now`) e no mesmo dia.
  - O período não pode sobrepor outra sessão.
- **Não existe** edição de horário nem exclusão de sessão com algum lado `APP`.
- *(Minha decisão)* Uma sessão **totalmente manual** pode ser apagada, pra corrigir um lançamento digitado errado.

Erros de regra viram um `sealed class PunchError` com mensagens em português, que a UI mostra numa snackbar.

## Regras de cálculo

Ficam todas em `calc/`, Kotlin puro e testável:

- **Trabalhado no dia:** soma de `endedAt − startedAt` das sessões que começaram naquele dia. Uma sessão aberta conta até `min(now, fim da janela do dia)`.
- **Previsto no dia:**
  - `dailyMinutes` se o dia está em `workdays`, não é `DayOff` e está entre `balanceStart` e hoje.
  - `0` em qualquer outro caso.
- **Saldo:** trabalhado menos previsto, somado no período.
  - O saldo acumulado vai de `balanceStart` até **ontem**.
  - O saldo de hoje aparece separado.
- **Recesso:** a Lei do Estágio (11.788, art. 13) prevê 30 dias por ano, proporcionais ao tempo estagiado. Então `floor(30 × mesesCompletos(internshipStart, hoje) / 12) − count(DayOff RECESS)`. *Confirmar a regra exata no TCE.*
- **Mudança em `Settings`:** vale retroativamente, porque o saldo é sempre recalculado. *(Minha decisão.)*

## Backup

- **Exportar:**
  - Gera um JSON versionado (`{ version, exportedAt, settings, sessions, daysOff }`).
  - Salva onde você escolher, via `ACTION_CREATE_DOCUMENT`, então pode ir pro Drive pelo próprio seletor do Android.
- **Importar:**
  - Lê um JSON via `ACTION_OPEN_DOCUMENT`, valida e **substitui** todos os dados numa transação, depois de uma confirmação.
  - Se o arquivo for inválido, nada muda.
- *(Minha decisão)* O backup automático do Android (Auto Backup) também fica habilitado pro banco, como rede de segurança extra.

## Telas

1. **Hoje.**
   - Um botão grande que alterna entre "Entrar" e "Sair", com cor por estado. Fora da janela, ele fica desabilitado e mostra a janela (07:55–18:05).
   - Embaixo: o cronômetro da sessão aberta, o total de hoje, o saldo do dia e a lista de sessões de hoje.
   - Se houver sessão esquecida aberta, um aviso no topo com "Fechar sessão esquecida" abre um seletor de horário e o campo de observação.
2. **Semana.**
   - Uma lista por dia com o trabalhado e o previsto, marcando feriados e recessos, além do saldo da semana e da navegação entre semanas.
   - Tocar num dia abre as sessões dele, com "Lançar período esquecido". Sessões manuais aparecem marcadas e podem ser apagadas.
3. **Banco.** O saldo acumulado, o recesso disponível e a lista de feriados e recessos, com adicionar e remover.
4. **Config.** Jornada, dias úteis, janela e tolerância, início do estágio, início do saldo, e os botões de exportar e importar o backup.

Na primeira abertura, o app abre direto na Config pra você preencher o início do estágio e o início do saldo.

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
│ Hoje Semana Banco ⚙ │
└─────────────────────┘
```

## Deixado pro construtor

- Projeto Android na raiz do repo (Gradle com version catalog), `minSdk 26`, por causa do `java.time` nativo.
- Navegação: Navigation Compose com barra inferior.
- Atualização do cronômetro: um ticker de 1s só enquanto a tela Hoje está visível.
- Testes:
  - Unitários (JVM) em `calc/`:
    - janela com tolerância nas bordas (07:54, 07:55, 18:05, 18:06);
    - sessão esquecida aberta;
    - `DayOff` zerando o previsto;
    - `balanceStart`;
    - recesso proporcional.
  - Testes do `PontoRepository` com Room em memória (Robolectric ou instrumentado):
    - idempotência do `punch`;
    - sobreposição;
    - índice de uma sessão aberta;
    - regras da entrada manual;
    - ida e volta do backup.

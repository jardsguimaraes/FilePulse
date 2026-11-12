# File Pulse

Plataforma backend para ingestão e processamento assíncrono de arquivos
CSV de alto volume, desenvolvida com **Java 25, Spring Boot 3.5 e Spring
Batch**.

O File Pulse monitora a chegada de arquivos no filesystem, valida sua
integridade, controla idempotência por conteúdo, processa registros em
chunks, persiste dados no PostgreSQL e mantém rastreabilidade do ciclo
de vida dos arquivos.

------------------------------------------------------------------------

## 🎯 Objetivo

O objetivo do **File Pulse** é explorar decisões de engenharia aplicadas
ao processamento confiável de arquivos CSV com grande volume de
registros, sem carregar o arquivo inteiro em memória.

O projeto trabalha conceitos como processamento em lote, chunk-oriented
processing, idempotência, concorrência, tolerância a falhas,
rastreabilidade, reconciliação de estado após interrupções,
versionamento de schema e processamento parcial controlado.

------------------------------------------------------------------------

## 📌 Status atual

🚧 **Em desenvolvimento**

O núcleo de ingestão e processamento local está funcional. A estrutura
de cargas lógicas foi incorporada, mas a conclusão automática da carga
ainda é uma evolução pendente.

### Implementado

- Java NIO `WatchService`;
- validação de extensão, cabeçalho e metadados CSV;
- verificação de estabilidade do arquivo com retry configurável;
- movimentação entre diretórios de estado;
- proteção contra colisão de nomes;
- SHA-256 do arquivo completo e idempotência por conteúdo;
- proteção concorrente com restrição `UNIQUE` no PostgreSQL;
- Spring Batch com Reader, Processor e Writer;
- processamento transacional por chunks;
- skip de registros inválidos e auditoria dos registros ignorados;
- `JdbcBatchItemWriter`;
- PostgreSQL e Flyway;
- rastreabilidade `customer -> file_processing`;
- controle do ciclo de vida em `file_processing`;
- auditoria de rejeições em `file_rejection`;
- `final_file_path`;
- restart de processamentos com falha, preservando chunks confirmados;
- recuperação de processamentos abandonados;
- detecção de arquivos órfãos em `/processamento`;
- reconciliação entre estado persistido e filesystem no startup;
- tabela `logical_load` e associação de arquivos e registros à carga;
- suporte inicial a arquivos `ORIGINAL` e `COMPLEMENTAR`;
- identificação de registros por `id_registro` e unicidade por carga;
- testes automatizados e de integração com Testcontainers.

### Em evolução

- conclusão automática da carga lógica após conciliação dos registros;
- política de tratamento de duplicidades entre arquivos complementares;
- testes ponta a ponta do ciclo ORIGINAL → COMPLEMENTAR → CONCLUIDA;
- benchmarks com grandes massas de dados.

### Arquitetura planejada

- API REST e Spring WebFlux;
- Server-Sent Events (SSE);
- dashboard;
- métricas com Micrometer, Prometheus e Grafana;
- Docker / Docker Compose para distribuição da aplicação;
- OpenAPI / Swagger;
- RabbitMQ e processamento distribuído.

------------------------------------------------------------------------

## 🛠️ Stack tecnológica

| Tecnologia | Papel | Estado |
| --- | --- | --- |
| Java 25 | Linguagem principal | ✅ |
| Spring Boot 3.5.16 | Framework | ✅ |
| Spring Batch 5.x | Processamento em lote | ✅ |
| Java NIO `WatchService` | Monitoramento do filesystem | ✅ |
| PostgreSQL | Persistência | ✅ |
| JDBC Batch | Escrita otimizada | ✅ |
| Flyway | Versionamento do schema | ✅ |
| Maven | Build e dependências | ✅ |
| JUnit / Testcontainers | Testes automatizados e de integração | ✅ |
| Spring Boot Actuator | Dependência presente | ✅ |
| Spring WebFlux | API reativa planejada | 🔜 |
| SSE | Eventos em tempo real | 🔜 |
| RabbitMQ | Mensageria/distribuição | 🔜 |
| Micrometer / Prometheus / Grafana | Métricas e monitoramento | 🔜 |
| Docker Compose | Distribuição da aplicação | 🔜 |
| OpenAPI / Swagger | Documentação de API | 🔜 |

O projeto também possui dependências de Spring Web e Spring Data JPA,
mas o Writer de ingestão utiliza JDBC Batch.

------------------------------------------------------------------------

## 🏗️ Arquitetura atual

```text
CSV
 │
 ▼
/entrada
 │
 ▼
Java NIO WatchService
 │
 ▼
FileEventHandler
 │
 ├── validação de formato e cabeçalho
 ├── estabilidade + retry
 └── SHA-256
        │
        ▼
   Idempotência
    /       \
duplicado    novo
   │          │
   ▼          ▼
/rejeitados  /processamento
   │          │
   ▼          ▼
auditoria   LeitorMetadadosArquivoCsv
              │
              ▼
        CargaLogicaService
         /           \
    ORIGINAL      COMPLEMENTAR
       │               │
   cria carga      valida carga
       └──────┬────────┘
              ▼
       file_processing
              │
              ▼
          Spring Batch
     Reader → Processor → Writer
              │
              ▼
          PostgreSQL
       customer + logical_load
              │
         /          \
      sucesso       falha
         │            │
         ▼            ▼
 /processados       /erro
```

A carga lógica agrupa registros provenientes de um ou mais arquivos
físicos. A conclusão de cada arquivo não implica, por si só, a conclusão
da carga lógica.

------------------------------------------------------------------------

## 📂 Ciclo de vida dos arquivos

``` text
./files/
├── entrada/
├── processamento/
├── processados/
├── erro/
└── rejeitados/
```

-   `/entrada`: ponto monitorado pelo `WatchService`.
-   `/processamento`: arquivos aceitos para processamento.
-   `/processados`: arquivos concluídos com sucesso.
-   `/erro`: falhas, abandonos e recuperações operacionais.
-   `/rejeitados`: arquivos recusados antes do processamento normal.

Configuração atual:

``` yaml
fileprocessor:
  directory:
    diretorio-entrada: ./files/entrada
    diretorio-processamento: ./files/processamento
    diretorio-processados: ./files/processados
    diretorio-erro: ./files/erro
    diretorio-rejeitados: ./files/rejeitados

  stability:
    verificacoes-estabilidade: 3
    maximo-tentativas: 10
    intervalo-verificacao-ms: 1000
    maximo-retentativas: 3
    intervalo-retentativa-ms: 5000

  recovery:
    abandoned-after-minutes: 30
```

------------------------------------------------------------------------

## 🔄 Fluxo atual

```text
1. WatchService detecta ENTRY_CREATE
        ↓
2. FileEventHandler recebe o Path
        ↓
3. Validação de formato e estabilidade + retry
        ↓
4. SHA-256 do arquivo completo
        ↓
5. Verificação de idempotência
   ├── duplicado → /rejeitados + auditoria
   └── novo
        ↓
6. Movimentação para /processamento
        ↓
7. Leitura e validação de metadados CSV
        ↓
8. Preparação da carga lógica
   ├── ORIGINAL → cria carga INCOMPLETA
   └── COMPLEMENTAR → exige carga INCOMPLETA existente
        ↓
9. Registro em file_processing
        ↓
10. Spring Batch: Reader → Processor → Writer
        ↓
11. Persistência por chunks com vínculos de origem
        ↓
12. Resultado do arquivo
   ├── COMPLETO / COMPLETO_COM_FALHAS → /processados
   └── FALHOU → /erro
        ↓
13. Atualização dos contadores e final_file_path
```

A restrição `UNIQUE` sobre o hash protege a identidade física do
arquivo. A unicidade de `(logical_load_id, record_identifier)` protege
a identidade de cada registro dentro da carga lógica.

------------------------------------------------------------------------

## 📄 Formato do CSV

O CSV utiliza UTF-8, separador `|`, três linhas de metadados e um
cabeçalho obrigatório antes dos registros.

```text
idCarga|CAIXA-20261112-000001
tipoArquivo|ORIGINAL
totalRegistros|3
id_registro|nome|cpf|agencia|conta|valor|mes_referencia
REG-000001|Cliente 1|52998224725|0001|000001|100.00|10/26
REG-000002|Cliente 2|52998224725|0001|000002|150.00|10/26
REG-000003|Cliente 3|52998224725|0001|000003|200.00|10/26
```

Regras verificadas pelo `LeitorMetadadosArquivoCsv`:

- `idCarga`: padrão `CAIXA-AAAAMMDD-NNNNNN`;
- `tipoArquivo`: `ORIGINAL` ou `COMPLEMENTAR`;
- `totalRegistros`: inteiro maior que zero;
- cabeçalho exatamente igual ao exemplo;
- `id_registro`: formato `REG-` seguido de pelo menos seis dígitos,
  validado também pela restrição do banco.

`totalRegistros` representa o total esperado da **carga lógica**, e não
necessariamente a quantidade de linhas de cada arquivo complementar.

O Reader ignora as quatro primeiras linhas (metadados e cabeçalho).
O Processor valida e converte os dados e associa cada registro ao
arquivo físico e à carga lógica.

------------------------------------------------------------------------

## 🧷 Cargas lógicas e arquivos complementares

Uma carga lógica identifica um conjunto de registros que pode ser
entregue em mais de um arquivo físico.

```text
logical_load (idCarga)
       │
       ├── file_processing ORIGINAL
       │        └── customer REG-000001 ...
       │
       └── file_processing COMPLEMENTAR
                └── customer REG-000002 ...
```

### Arquivo ORIGINAL

- Deve informar um `idCarga` ainda não utilizado.
- Cria a carga em `logical_load` com status `INCOMPLETA`.
- Registra o total esperado de registros.
- Vincula o processamento físico à carga criada.

### Arquivo COMPLEMENTAR

- Deve informar um `idCarga` já existente.
- A carga precisa estar em `INCOMPLETA`.
- O `totalRegistros` deve ser igual ao da carga original.
- O arquivo possui seu próprio `file_processing`, mas compartilha a
  mesma `logical_load`.

### Unicidade por carga

A migration V9 introduz os campos `logical_load_id` e
`record_identifier` em `customer` e cria um índice único para:

```sql
(logical_load_id, record_identifier)
```

Assim, o mesmo `id_registro` não pode ser persistido duas vezes na
mesma carga lógica.

> **Limite atual:** a estrutura de cargas e a proteção por unicidade
> estão implementadas. A contagem conciliada de registros únicos e a
> transição automática de `INCOMPLETA` para `CONCLUIDA` ainda não foram
> implementadas no fluxo consultado.

------------------------------------------------------------------------

## 👀 Monitoramento com Java NIO

O `DirectoryWatcher` utiliza `WatchService` para detectar eventos do
filesystem.

``` text
WatchService.take()
       ↓
WatchKey
       ↓
pollEvents()
       ↓
ENTRY_CREATE
       ↓
Path
       ↓
FileEventHandler
```

O watcher detecta; regras de validação, hash, idempotência, Batch e
movimentação permanecem em componentes próprios.

------------------------------------------------------------------------

## 🧪 Estabilidade do arquivo

`ENTRY_CREATE` não garante que o produtor terminou de gravar o arquivo.

``` text
arquivo detectado
      ↓
verificações sucessivas
   ├── continua mudando → aguarda/retry
   └── permanece estável → prossegue
```

Se o arquivo não estabilizar dentro da política configurada, ele é
rejeitado e auditado.

------------------------------------------------------------------------

## 🔐 Idempotência por SHA-256

A identidade técnica do arquivo é baseada no conteúdo, não no nome.

``` text
arquivo estável
     ↓
SHA-256
     ↓
consulta file_processing
   /             \
existe          não existe
  ↓                ↓
duplicado       prossegue
```

A estratégia possui duas barreiras:

1.  **pre-check na aplicação**, para interromper cedo o fluxo;
2.  **`UNIQUE(file_hash)` no PostgreSQL**, para proteger contra
    concorrência.

A aplicação trata a violação de unicidade quando duas tentativas
concorrentes tentam reservar o mesmo hash.

------------------------------------------------------------------------

## ⚙️ Spring Batch

```text
FlatFileItemReader
        ↓
ClienteItemProcessor
        ↓
JdbcBatchItemWriter
        ↓
PostgreSQL
```

O Reader lê progressivamente os registros após as quatro linhas
iniciais. O Processor valida e transforma os dados, preenchendo
`file_processing_id`, `logical_load_id` e `record_identifier`.
O Writer executa inserts em lote na tabela `customer`.

Registros inválidos podem ser ignorados pela configuração fault
tolerant do Step e auditados para posterior análise.

------------------------------------------------------------------------

## 📦 Chunk-oriented processing

O processamento ocorre em unidades transacionais menores:

``` text
chunk 1 → processa → grava → COMMIT
chunk 2 → processa → grava → COMMIT
chunk 3 → processa → grava → COMMIT
...
```

Isso limita memória e tamanho das transações. O tamanho ideal do chunk
será definido por benchmark, não por suposição.

------------------------------------------------------------------------

## 🧭 Processamento parcial controlado

A estratégia adotada é **processamento parcial controlado**.

```text
chunk 1 → COMMIT
chunk 2 → COMMIT
chunk 3 → COMMIT
chunk 4 → falha / ROLLBACK
```

Os chunks anteriores permanecem persistidos. O projeto não pretende
substituir isso por uma única transação para o arquivo inteiro.

A rastreabilidade de origem já está implementada:

```text
logical_load
    │ 1:N
    ▼
file_processing
    │ 1:N
    ▼
  customer
```

Cada cliente registra seu processamento de origem e sua carga lógica.

### Restart de processamento com falha

O `JobService.reiniciar(idProcessamentoArquivo)` aceita processamentos
com status `FALHOU` e caminho de arquivo recuperável.

O serviço recupera o `logical_load_id` associado, move o arquivo de
volta para `/processamento` e reinicia o job com os identificadores
originais. O Spring Batch utiliza seu estado persistido para retomar
o processamento, preservando os chunks já confirmados.

Testes de integração verificam falhas controladas, persistência parcial,
retomada e contadores finais.

------------------------------------------------------------------------

## 🚨 Tolerância a falhas por registro

```text
Reader
  ↓
Processor
  ├── válido ─────────► Writer
  └── inválido
         ↓
      exceção
         ↓
        skip
         ↓
 auditoria do registro
```

Enquanto o limite configurado de skips não for ultrapassado, o job
pode continuar. Se o limite for excedido, o processamento falha e
o arquivo segue para `/erro`.

Ao concluir com registros ignorados, o arquivo recebe status
`COMPLETO_COM_FALHAS`. A auditoria permite localizar as linhas
problemáticas, inclusive após restart.

------------------------------------------------------------------------

## 💾 PostgreSQL

### `logical_load`

Agrupa os arquivos de uma mesma carga de negócio.

```text
id
load_identifier
expected_records
status
created_at
completed_at
```

Estados previstos no schema:

```text
INCOMPLETA
CONCLUIDA
```

A carga é criada como `INCOMPLETA`. A atualização automática para
`CONCLUIDA` ainda é uma evolução pendente.

### `file_processing`

Controla identidade, estado e rastreabilidade do arquivo físico.

```text
id
file_name
file_path
final_file_path
file_hash
logical_load_id
file_type
status
read_count
write_count
skip_count
started_at
finished_at
error_message
```

Estados de domínio relevantes:

```text
EM_PROCESSAMENTO
COMPLETO
COMPLETO_COM_FALHAS
FALHOU
ABANDONADO
```

O tipo de arquivo é `ORIGINAL` ou `COMPLEMENTAR`.

### `customer`

Armazena os registros válidos, incluindo a origem física e lógica.

```text
file_processing_id
logical_load_id
record_identifier
name
cpf
agency
account
value
reference_month
```

O índice único `(logical_load_id, record_identifier)` impede
identificadores repetidos dentro da mesma carga.

### `file_processing_error`

Mantém a auditoria dos registros ignorados, incluindo informações
necessárias para localizar o erro no arquivo.

### `file_rejection`

Registra rejeições e recuperações que não representam uma execução
normal do Batch.

```text
file_name
file_path
file_hash
reason
details
rejected_at
```

Motivos incluem:

```text
INVALID_FORMAT
INVALID_HEADER
UNSTABLE_FILE
DUPLICATE
ORPHAN_PROCESSING_FILE
ORPHAN_DUPLICATE
```

------------------------------------------------------------------------

## 🗃️ Flyway

O schema é versionado com **Flyway**.

```text
código da aplicação
        +
migrations versionadas
        =
schema reproduzível
```

Além das estruturas de processamento, rejeição e auditoria, as
migrações recentes introduzem:

- **V8:** tabela `logical_load`, vínculo de `file_processing` com a
  carga lógica e tipo de arquivo;
- **V9:** `customer.logical_load_id`, `record_identifier`, restrições
  de formato e unicidade por carga.

As migrations ficam em `src/main/resources/db/migration/`.

A configuração atual também possui `spring.jpa.hibernate.ddl-auto:
update`. Como o Flyway é o mecanismo escolhido para versionar o schema,
essa configuração merece revisão antes de um ambiente de produção.

------------------------------------------------------------------------

## 🚚 Movimentação e colisão de nomes

O `MovimentacaoArquivoService` centraliza movimentações com Java NIO
`Files.move(...)`.

O File Pulse não sobrescreve silenciosamente um arquivo já existente. Em
colisões, utiliza nome alternativo com UUID:

``` text
destino/arquivo.csv
       já existe
           ↓
destino/arquivo_<uuid>.csv
```

O caminho efetivamente utilizado pode ser persistido em
`final_file_path`.

------------------------------------------------------------------------

## 🧾 Rastreabilidade do caminho final

`file_path` representa o caminho do arquivo associado ao início do
processamento.

`final_file_path` representa o destino físico efetivo:

``` text
file_path
./files/processamento/clientes.csv

final_file_path
./files/processados/clientes.csv
```

Em colisões, `final_file_path` registra o nome real gerado.

------------------------------------------------------------------------

## 🛡️ Recuperação após interrupções

Filesystem e PostgreSQL não participam da mesma transação distribuída.
Por isso, o File Pulse executa recuperação no startup:

``` text
ApplicationReadyEvent
        ↓
FileProcessingRecoveryService
```

### Processamentos abandonados

Registros antigos em `PROCESSING`, sem finalização, são candidatos a
abandono:

``` text
PROCESSING antigo
       ↓
arquivo ainda em /processamento
       ↓
moveToError()
       ↓
ABANDONED
```

O registro recebe `finished_at`, mensagem de erro e `final_file_path`.

A política é conservadora: **não reprocessar automaticamente**, pois
chunks anteriores podem já ter sido confirmados.

### Reconciliação de `/processamento`

``` text
arquivo em /processamento
        ↓
findByFilePath()
     /        \
 encontrado   ausente
     ↓           ↓
  status       SHA-256
```

Para registros existentes:

``` text
COMPLETED  → /processados
FAILED     → /erro
ABANDONED  → /erro
PROCESSING → política de abandono
```

A reconciliação preserva o histórico e corrige o estado físico/caminho
final quando necessário.

------------------------------------------------------------------------

## 🧩 Arquivos órfãos

Uma interrupção pode ocorrer depois da movimentação física e antes da
criação do registro:

``` text
moveToProcessing()
        ↓
arquivo em /processamento
        X aplicação encerra
        ↓
sem file_processing correspondente
```

O SHA-256 diferencia:

``` text
órfão
  ↓
SHA-256
 /     \
hash    hash
existe  não existe
 ↓        ↓
ORPHAN_  ORPHAN_
DUPLICATE PROCESSING_FILE
```

O arquivo é movido para `/erro` e a ocorrência é auditada em
`file_rejection`.

------------------------------------------------------------------------

## ⚖️ Consistência entre filesystem e banco

O projeto trabalha com recursos independentes:

``` text
filesystem + PostgreSQL
```

Uma movimentação de arquivo e um `INSERT`/`UPDATE` não formam uma única
transação ACID.

Por isso, a arquitetura utiliza operações defensivas, idempotência,
restrições de banco, rastreabilidade de caminhos, auditoria e
reconciliação no startup.

------------------------------------------------------------------------

## 🧠 Decisões arquiteturais

  -----------------------------------------------------------------------
  Decisão                             Motivo
  ----------------------------------- -----------------------------------
  `WatchService`                      Detectar eventos sem polling
                                      contínuo

  Verificação de estabilidade         `ENTRY_CREATE` não garante gravação
                                      concluída

  SHA-256 completo                    Identidade baseada no conteúdo

  Pre-check + `UNIQUE(file_hash)`     Fluxo controlado + proteção
                                      concorrente

  Spring Batch                        Processamento de alto volume

  Chunks                              Limitar memória e transações

  JDBC Batch                          Reduzir round-trips ao PostgreSQL

  Flyway                              Versionar e reproduzir o schema

  Diretórios por estado               Explicitar ciclo físico

  `final_file_path`                   Registrar destino efetivo

  UUID em colisões                    Evitar sobrescrita silenciosa

  Recovery no startup                 Reconciliar filesystem e banco

  Não reprocessar abandonados         Evitar duplicidade após commits
  automaticamente                     parciais

  Processamento parcial controlado    Preservar o modelo transacional por
                                      chunks
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 🏗️ Arquitetura planejada

``` text
File System
    ↓
WatchService
    ↓
Ingestão / Idempotência
    ↓
Spring Batch ─────► PostgreSQL
    ↓
Eventos de progresso
    ↓
WebFlux
    ↓
SSE
    ↓
Dashboard
```

Etapa distribuída futura:

``` text
WatchService
    ↓
RabbitMQ
    ↓
┌───┼───┐
▼   ▼   ▼
W1  W2  W3
└───┼───┘
    ↓
Spring Batch
    ↓
PostgreSQL
```

RabbitMQ **não faz parte do fluxo executável atual**.

------------------------------------------------------------------------

## ⚡ WebFlux e SSE --- planejados

WebFlux não será responsável por observar o filesystem. Essa
responsabilidade permanece no Java NIO.

A camada reativa será destinada à exposição de estado e progresso:

``` text
Spring Batch
     ↓
evento de progresso
     ↓
WebFlux
     ↓
SSE
     ↓
cliente/dashboard
```

------------------------------------------------------------------------

## 📈 Observabilidade --- planejada

``` text
Spring Boot Actuator
        +
Micrometer
        +
Prometheus
        +
Grafana
```

Métricas de interesse incluem arquivos processados, falhas, registros
lidos/escritos/ignorados, throughput, duração dos Jobs/chunks, CPU,
memória, I/O e pressão sobre o PostgreSQL.

------------------------------------------------------------------------

## 🧪 Benchmark de performance

Cenário de referência planejado:

``` text
5.000.000 registros
```

  Teste     Chunk
  ------- -------
  1           100
  2           500
  3         1.000
  4         2.000
  5         5.000

Serão avaliados tempo total, throughput, memória, CPU, I/O, commits,
tempo por chunk, skips e impacto sobre o PostgreSQL.

Nenhum tamanho será declarado ideal antes de medições reais.

------------------------------------------------------------------------

## ▶️ Execução local e testes

Pré-requisitos: JDK 25, Maven, PostgreSQL e Docker para os testes
de integração com Testcontainers.

O `application.yaml` aponta para o banco
`jdbc:postgresql://localhost:5432/filepulse` e lê as credenciais das
variáveis de ambiente:

```bash
export POSTGRES_USERNAME="seu_usuario"
export POSTGRES_PASSWORD="sua_senha"
```

Para validar e executar:

```bash
mvn clean verify
mvn spring-boot:run
```

Para executar individualmente os cenários de restart:

```bash
mvn -Dtest=FileProcessingRestartIntegrationTest test
mvn -Dtest=RegistroIgnoradoRestartIntegrationTest test
```

Os jobs não são disparados automaticamente no startup
(`spring.batch.job.enabled: false`); a ingestão é iniciada pelo
fluxo da aplicação.

------------------------------------------------------------------------

## 🗺️ Roadmap

### Core de ingestão e Batch

- [x] WatchService
- [x] Validação de formato, cabeçalho e metadados
- [x] Estabilidade e retry
- [x] Movimentação entre diretórios
- [x] Colisão de nomes
- [x] Spring Batch Job/Step
- [x] FlatFileItemReader
- [x] ItemProcessor
- [x] JdbcBatchItemWriter
- [x] PostgreSQL
- [x] Chunk processing
- [x] Skip de registros inválidos

### Banco e confiabilidade

- [x] Flyway
- [x] `file_processing`
- [x] SHA-256 e idempotência por conteúdo
- [x] `UNIQUE` contra concorrência
- [x] `file_rejection` e `/rejeitados`
- [x] `final_file_path`
- [x] Recuperação de processamento abandonado
- [x] Detecção e auditoria de órfãos
- [x] Reconciliação no startup
- [x] Rastreabilidade `customer -> file_processing`
- [x] Restart com preservação de chunks confirmados
- [x] `logical_load` e tipos ORIGINAL / COMPLEMENTAR
- [x] Unicidade de `id_registro` por carga
- [ ] Conclusão automática da carga lógica
- [ ] Política completa de duplicidades entre complementares
- [ ] Testes ponta a ponta da conciliação de cargas

### Testes e performance

- [x] Testes unitários
- [x] Testes de integração
- [x] Testcontainers com PostgreSQL
- [x] Testes de rollback e restart
- [ ] Massa de milhões de registros
- [ ] Benchmark de chunks
- [ ] Métricas de throughput/recursos

### API e reatividade

- [ ] API REST de acompanhamento
- [ ] Spring WebFlux
- [ ] SSE
- [ ] Eventos de progresso
- [ ] Dashboard

### Observabilidade

- [x] Dependência do Actuator
- [ ] Métricas operacionais com Micrometer
- [ ] Prometheus
- [ ] Grafana

### Infraestrutura/documentação

- [ ] Docker / Docker Compose para execução da aplicação
- [ ] OpenAPI / Swagger
- [ ] Revisar `ddl-auto` para uso consistente do Flyway

### Arquitetura distribuída

- [ ] RabbitMQ
- [ ] Evento de chegada
- [ ] Workers
- [ ] Múltiplos consumidores
- [ ] Processamento distribuído
- [ ] Testes de concorrência/escalabilidade

------------------------------------------------------------------------

## 🎓 Conceitos demonstrados

### Implementados

```text
Java NIO
WatchService
Files.move
Spring Boot
Spring Batch
Chunk Processing
FlatFileItemReader
ItemProcessor
JdbcBatchItemWriter
JDBC Batch
PostgreSQL
Flyway
SHA-256
Idempotência
Concorrência
Fault Tolerance / Skip
Rastreabilidade
Recovery
Reconciliação de estado
Processamento parcial controlado
Restart com estado persistido
Carga lógica ORIGINAL / COMPLEMENTAR
Unicidade de registros por carga
JUnit / Testcontainers
```

### Planejados

```text
Conclusão automática da carga lógica
Conciliação de arquivos complementares
WebFlux
SSE
Métricas operacionais
RabbitMQ
Processamento distribuído
```

------------------------------------------------------------------------

## 🔎 Princípios do projeto

**Não carregar grandes arquivos inteiros em memória.** A leitura
permanece incremental.

**Não confiar no nome do arquivo para idempotência.** A identidade
técnica é baseada no conteúdo.

**Não confiar apenas em um `SELECT` para impedir concorrência.** A
garantia final pertence ao banco.

**Não sobrescrever arquivos silenciosamente.** Colisões devem ser
preservadas e rastreáveis.

**Não assumir atomicidade entre filesystem e PostgreSQL.**
Inconsistências possíveis devem ser detectáveis e reconciliáveis.

**Não reprocessar automaticamente um arquivo potencialmente parcial.**
Chunks já confirmados precisam ser considerados.

**Não declarar otimizações sem benchmark.** Chunk size e concorrência
serão definidos por medições.

------------------------------------------------------------------------

## 🔮 Próxima evolução

A próxima etapa é concluir a **conciliação de cargas lógicas**.

```text
logical_load (INCOMPLETA)
          │
          ├── ORIGINAL
          └── COMPLEMENTAR
                   │
                   ▼
        registros únicos gravados
                   │
                   ▼
       comparar com expected_records
                   │
          /----------------\
      pendentes          completo
         │                  │
         ▼                  ▼
    INCOMPLETA           CONCLUIDA
```

O objetivo é atualizar o status da carga somente quando a quantidade
de registros **únicos efetivamente persistidos** atingir o total
esperado. Antes disso, será necessário definir e testar o tratamento
de duplicidades e os cenários de falha entre arquivos.

------------------------------------------------------------------------

## 📄 Licença

Este projeto está disponível para fins de estudo, experimentação e
demonstração técnica.

------------------------------------------------------------------------

## 👨‍💻 Autor

**Jards Guimarães**

Backend Developer \| Java \| Spring Boot \| Quarkus

------------------------------------------------------------------------

> **File Pulse** --- processamento confiável de arquivos em escala, do
> filesystem à evolução para arquitetura distribuída.

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

O núcleo de ingestão e processamento local está funcional.

### Implementado

-   Java NIO `WatchService`;
-   validação de extensão e cabeçalho;
-   verificação de estabilidade do arquivo com retry configurável;
-   movimentação entre diretórios de estado;
-   proteção contra colisão de nomes;
-   SHA-256 do arquivo completo;
-   idempotência baseada no conteúdo;
-   proteção concorrente com restrição `UNIQUE` no PostgreSQL;
-   Spring Batch com Reader, Processor e Writer;
-   processamento transacional por chunks;
-   skip de registros inválidos;
-   `JdbcBatchItemWriter`;
-   PostgreSQL;
-   Flyway;
-   controle do ciclo de vida em `file_processing`;
-   auditoria de rejeições em `file_rejection`;
-   `final_file_path`;
-   recuperação de processamentos abandonados;
-   detecção de arquivos órfãos em `/processamento`;
-   reconciliação entre estado persistido e filesystem no startup.

### Em evolução

-   rastreabilidade `customer -> file_processing`;
-   estratégia completa de processamento parcial controlado;
-   testes automatizados e de integração;
-   benchmarks com grandes massas de dados.

### Arquitetura planejada

-   API REST e Spring WebFlux;
-   Server-Sent Events (SSE);
-   dashboard;
-   Actuator, Micrometer, Prometheus e Grafana;
-   Docker / Docker Compose;
-   Testcontainers;
-   OpenAPI / Swagger;
-   RabbitMQ e processamento distribuído.

------------------------------------------------------------------------

## 🛠️ Stack tecnológica

  Tecnologia                Papel                         Estado
  ------------------------- ----------------------------- --------
  Java 25                   Linguagem principal           ✅
  Spring Boot 3.5.x         Framework                     ✅
  Spring Batch 5.x          Processamento em lote         ✅
  Java NIO WatchService     Monitoramento do filesystem   ✅
  PostgreSQL                Persistência                  ✅
  JDBC Batch                Escrita otimizada             ✅
  Flyway                    Versionamento do schema       ✅
  Maven                     Build e dependências          ✅
  Spring WebFlux            API reativa                   🔜
  SSE                       Eventos em tempo real         🔜
  RabbitMQ                  Mensageria/distribuição       🔜
  Actuator / Micrometer     Observabilidade               🔜
  Prometheus / Grafana      Métricas                      🔜
  JUnit / Testcontainers    Testes                        🔜
  Docker / Docker Compose   Containerização               🔜
  OpenAPI / Swagger         Documentação da API           🔜

------------------------------------------------------------------------

## 🏗️ Arquitetura atual

``` text
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
 ├── validação de formato
 ├── estabilidade + retry
 ├── validação de cabeçalho
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
auditoria   file_processing
              │
              ▼
          Spring Batch
     Reader → Processor → Writer
              │
              ▼
          PostgreSQL
          /        \
      sucesso      falha
         │           │
         ▼           ▼
 /processados      /erro
```

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
    input-directory: ./files/entrada
    processing-directory: ./files/processamento
    processed-directory: ./files/processados
    error-directory: ./files/erro
    rejected-directory: ./files/rejeitados

  stability:
    stable-checks: 3
    max-attempts: 10
    check-interval-ms: 1000
    max-retries: 3
    retry-interval-ms: 5000

  recovery:
    abandoned-after-minutes: 30
```

------------------------------------------------------------------------

## 🔄 Fluxo atual

``` text
1. WatchService detecta ENTRY_CREATE
        ↓
2. FileEventHandler recebe o Path
        ↓
3. Validação do formato
        ↓
4. Verificação de estabilidade + retry
        ↓
5. Validação do cabeçalho
        ↓
6. SHA-256 do arquivo completo
        ↓
7. Verificação de idempotência
   ├── duplicado → /rejeitados + auditoria
   └── novo
        ↓
8. Movimentação para /processamento
        ↓
9. Reserva do processamento no PostgreSQL
        ↓
10. Spring Batch
        ↓
11. Reader → Processor → Writer
        ↓
12. Persistência por chunks
   ├── COMPLETED → /processados
   └── FAILED    → /erro
        ↓
13. Persistência do final_file_path
```

A restrição `UNIQUE` sobre o hash é a barreira definitiva contra uma
condição de corrida entre verificações concorrentes.

------------------------------------------------------------------------

## 📄 Formato do CSV

``` text
Nome|CPF|Agência|Conta|Valor|Mês de Referência
João da Silva|12345678901|1234|123456-7|9995.88|09/26
Maria Souza|98765432100|5678|987654-3|1250.50|09/26
```

O Reader ignora o cabeçalho. O Processor valida e converte campos como
nome, CPF, agência, conta, valor e mês de referência.

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

``` text
FlatFileItemReader
        ↓
CustomerItemProcessor
        ↓
JdbcBatchItemWriter
        ↓
PostgreSQL
```

O Reader lê progressivamente; o Processor valida e transforma; o Writer
executa inserts em lote.

Registros inválidos podem lançar `InvalidRecordException` e ser tratados
pela configuração fault tolerant do Step.

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

``` text
chunk 1 → COMMIT
chunk 2 → COMMIT
chunk 3 → COMMIT
chunk 4 → falha
```

Os chunks anteriores podem permanecer persistidos. O projeto não
pretende substituir isso por uma única transação gigantesca para o
arquivo inteiro.

A próxima evolução é:

``` text
file_processing
       │ 1
       │
       │ N
       ▼
    customer
```

com `customer.file_processing_id`.

Isso permitirá rastrear a origem dos dados e sustentar futuras políticas
de restart, limpeza, retry e reprocessamento.

> **Status:** decisão arquitetural definida; associação
> `customer -> file_processing` ainda não implementada.

------------------------------------------------------------------------

## 🚨 Tolerância a falhas por registro

``` text
Reader
  ↓
Processor
  ├── válido ─────────► Writer
  └── inválido
         ↓
InvalidRecordException
         ↓
       skip
```

Enquanto o limite de skips não for ultrapassado, o processamento pode
continuar. Ao excedê-lo, Step/Job falham e o arquivo segue para `/erro`.

------------------------------------------------------------------------

## 💾 PostgreSQL

### `file_processing`

Controla identidade e estado do processamento.

Principais informações:

``` text
id
file_name
file_path
final_file_path
file_hash
status
read_count
write_count
skip_count
started_at
finished_at
error_message
```

Estados atualmente relevantes:

``` text
PROCESSING
COMPLETED
FAILED
ABANDONED
```

`REJECTED` existe no enum, mas rejeições de ingestão são atualmente
auditadas em `file_rejection`.

### `file_rejection`

Registra rejeições e recuperações que não representam uma execução
normal do Batch.

``` text
file_name
file_path
file_hash
reason
details
rejected_at
```

Motivos incluem:

``` text
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

``` text
código da aplicação
        +
migrations versionadas
        =
schema reproduzível
```

Migrations já foram utilizadas para criar/evoluir estruturas como
`file_processing`, `file_rejection` e `final_file_path`.

Isso evita depender de alterações manuais no banco e mantém a evolução
do schema associada ao histórico do repositório.

------------------------------------------------------------------------

## 🚚 Movimentação e colisão de nomes

O `FileMovementService` centraliza movimentações com Java NIO
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

## 🗺️ Roadmap

### Core de ingestão e Batch

-   [x] WatchService
-   [x] Validação de formato e cabeçalho
-   [x] Estabilidade e retry
-   [x] Movimentação entre diretórios
-   [x] Colisão de nomes
-   [x] Spring Batch Job/Step
-   [x] FlatFileItemReader
-   [x] ItemProcessor
-   [x] JdbcBatchItemWriter
-   [x] PostgreSQL
-   [x] Chunk processing
-   [x] Skip de registros inválidos

### Banco e confiabilidade

-   [x] Flyway
-   [x] `file_processing`
-   [x] SHA-256
-   [x] Idempotência por conteúdo
-   [x] `UNIQUE` contra concorrência
-   [x] Duplicidade concorrente
-   [x] `file_rejection`
-   [x] `/rejeitados`
-   [x] `final_file_path`
-   [x] Recuperação de PROCESSING abandonado
-   [x] ABANDONED
-   [x] Detecção/auditoria de órfãos
-   [x] Reconciliação de finalizados presos em `/processamento`
-   [ ] Associar `customer` a `file_processing`
-   [ ] Completar processamento parcial controlado
-   [ ] Definir política segura de restart/reprocessamento

### Testes e performance

-   [ ] Ampliar testes unitários
-   [ ] Testes de integração
-   [ ] Testcontainers
-   [ ] Massa de milhões de registros
-   [ ] Benchmark de chunks
-   [ ] Métricas de throughput/recursos

### API e reatividade

-   [ ] API REST
-   [ ] Spring WebFlux
-   [ ] SSE
-   [ ] Eventos de progresso
-   [ ] Dashboard

### Observabilidade

-   [ ] Actuator
-   [ ] Micrometer
-   [ ] Prometheus
-   [ ] Grafana

### Infraestrutura/documentação

-   [ ] Docker
-   [ ] Docker Compose
-   [ ] OpenAPI / Swagger

### Arquitetura distribuída

-   [ ] RabbitMQ
-   [ ] Evento de chegada
-   [ ] Workers
-   [ ] Múltiplos consumidores
-   [ ] Processamento distribuído
-   [ ] Testes de concorrência/escalabilidade

------------------------------------------------------------------------

## 🎓 Conceitos demonstrados

### Implementados

``` text
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
```

### Planejados

``` text
WebFlux
SSE
Observabilidade
Testcontainers
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

A próxima etapa é tornar explícita a origem de cada registro:

``` text
file_processing
       │
       │ 1:N
       ▼
    customer
```

O objetivo é adicionar `file_processing_id` aos registros processados.
Essa alteração será a base para evoluir o processamento parcial
controlado e discutir com segurança restart, retry, reprocessamento,
limpeza de dados parciais e auditoria por arquivo.

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

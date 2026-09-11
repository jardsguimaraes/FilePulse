# File Pulse

Plataforma de processamento assíncrono de arquivos CSV de alto volume, desenvolvida com **Java 21 e Spring Boot**, capaz de detectar automaticamente a chegada de arquivos, processar milhões de registros de forma eficiente e disponibilizar o progresso do processamento em tempo real.

O projeto combina **Java NIO WatchService, Spring Batch, PostgreSQL, Spring WebFlux, Server-Sent Events (SSE) e RabbitMQ**, compondo uma arquitetura que parte do processamento local e avança, em uma etapa posterior do próprio projeto, para processamento distribuído orientado a eventos.

---

## 🎯 Objetivo

O objetivo do **File Pulse** é construir uma solução capaz de processar arquivos CSV contendo milhões de registros sem carregar todo o conteúdo em memória.

O fluxo principal é:

```text
Arquivo CSV
    │
    ▼
/entrada
    │
    ▼
Java NIO WatchService
    │
    ▼
Validação do arquivo
    │
    ▼
Spring Batch
    │
    ├── Reader
    ├── Processor
    └── Writer
    │
    ▼
PostgreSQL
    │
    ▼
Eventos de progresso
    │
    ▼
Spring WebFlux
    │
    ▼
SSE
    │
    ▼
Dashboard
```

A proposta é demonstrar conceitos importantes de engenharia backend, especialmente processamento em larga escala, processamento assíncrono, controle de estado, idempotência, recuperação de falhas e monitoramento em tempo real.

---

## 🚀 Principais características

* Monitoramento automático de diretório
* Detecção de novos arquivos utilizando Java NIO `WatchService`
* Validação de arquivos CSV
* Verificação de arquivo completamente gravado antes do processamento
* Controle de duplicidade e idempotência
* Processamento orientado a chunks com Spring Batch
* Processamento de milhões de registros
* Escrita em lote no PostgreSQL
* Controle do ciclo de vida dos arquivos
* Controle do processamento através de banco de dados
* Tratamento de registros inválidos
* Skip de erros configuráveis
* Restart de Jobs interrompidos
* Monitoramento do progresso em tempo real
* API REST com Spring WebFlux
* Streaming de eventos utilizando SSE
* Observabilidade com Actuator e Micrometer
* Testes com JUnit e Testcontainers
* Containerização com Docker
* Documentação da API com OpenAPI/Swagger
* Mensageria e processamento distribuído com RabbitMQ

---

## 🛠️ Stack tecnológica

| Tecnologia            | Utilização                              |
| --------------------- | --------------------------------------- |
| Java 21               | Linguagem principal                     |
| Spring Boot 3.x       | Framework                               |
| Spring Batch          | Processamento dos arquivos              |
| Spring WebFlux        | API reativa e comunicação em tempo real |
| SSE                   | Streaming de eventos                    |
| Java NIO WatchService | Monitoramento do filesystem             |
| PostgreSQL            | Persistência dos dados                  |
| JDBC Batch            | Escrita otimizada                       |
| Maven                 | Gerenciamento e build                   |
| Docker                | Containerização                         |
| JUnit                 | Testes automatizados                    |
| Testcontainers        | Testes de integração                    |
| OpenAPI / Swagger     | Documentação da API                     |
| Spring Boot Actuator  | Monitoramento                           |
| Micrometer            | Métricas                                |
| Prometheus            | Coleta de métricas                      |
| Grafana               | Visualização                            |
| RabbitMQ              | Mensageria e distribuição do processamento |

---

## 📂 Estrutura de diretórios

O processamento utiliza diretórios distintos para representar o ciclo de vida dos arquivos:

```text
/opt/fileprocessor/
├── entrada/
├── processamento/
├── processados/
└── erro/
```

### Ciclo de vida

```text
              ┌──────────────┐
              │    entrada   │
              └──────┬───────┘
                     │
                     ▼
              ┌──────────────┐
              │ processamento│
              └──────┬───────┘
                     │
              ┌──────┴───────┐
              ▼              ▼
       ┌────────────┐   ┌────────────┐
       │ processados│   │    erro    │
       └────────────┘   └────────────┘
```

O arquivo é retirado de `/entrada` antes do processamento para evitar que permaneça no diretório de entrada enquanto o Batch estiver trabalhando nele. Os registros de acompanhamento e auditoria não são armazenados no filesystem: eles são persistidos em uma tabela de logs no PostgreSQL.

---

## 📦 Estrutura do projeto

```text
src/main/java/br/com/jards/filepulse
│
├── FilePulseApplication.java
│
├── config
│   ├── BatchConfig.java
│   ├── WebFluxConfig.java
│   └── JacksonConfig.java
│
├── watcher
│   ├── DirectoryWatcher.java
│   ├── FileEventHandler.java
│   └── FileWatcherProperties.java
│
├── batch
│   ├── job
│   │   ├── FileProcessingJob.java
│   │   └── JobCompletionListener.java
│   │
│   ├── step
│   │   └── CustomerProcessingStep.java
│   │
│   ├── reader
│   │   └── CustomerCsvReader.java
│   │
│   ├── processor
│   │   └── CustomerProcessor.java
│   │
│   ├── writer
│   │   └── CustomerWriter.java
│   │
│   └── listener
│       └── RecordSkipListener.java
│
├── domain
│   ├── Customer.java
│   ├── FileProcessing.java
│   ├── FileProcessingLog.java
│   ├── FileProcessingError.java
│   └── ProcessingStatus.java
│
├── repository
│   ├── CustomerRepository.java
│   ├── FileProcessingRepository.java
│   ├── FileProcessingLogRepository.java
│   └── FileProcessingErrorRepository.java
│
├── service
│   ├── FileProcessingService.java
│   ├── JobService.java
│   └── FileValidationService.java
│
├── web
│   ├── FileProcessingController.java
│   ├── JobController.java
│   └── DashboardController.java
│
├── event
│   ├── FileDetectedEvent.java
│   └── BatchProgressEvent.java
│
├── messaging
│   ├── publisher
│   │   └── FileCreatedPublisher.java
│   └── consumer
│       └── FileCreatedConsumer.java
│
└── exception
    ├── InvalidFileException.java
    ├── InvalidRecordException.java
    └── FileAlreadyProcessedException.java
```

---

## 🔄 Fluxo de processamento

Quando um arquivo CSV é disponibilizado no diretório `/entrada`, o sistema executa o seguinte fluxo:

```text
1. Arquivo chega em /entrada
          │
          ▼
2. WatchService detecta o arquivo
          │
          ▼
3. Validação da extensão
          │
          ▼
4. Verificação de arquivo completo
          │
          ▼
5. Leitura e validação do hash da primeira linha
          │
          ▼
6. Verificação de idempotência no banco
          │
          ▼
7. Registro do processamento e reserva do hash
          │
          ▼
8. Arquivo é movido para /processamento
          │
          ▼
9. Spring Batch Job é iniciado
          │
          ▼
10. Reader lê os registros a partir da terceira linha
          │
          ▼
11. Processor valida e transforma
          │
          ▼
12. Writer persiste em lote
          │
          ▼
13. Progresso é atualizado
          │
          ▼
14. Eventos são publicados
          │
          ▼
15. Job é finalizado
          │
          ▼
16. Arquivo é movido para /processados
```

Em caso de falha:

```text
/processamento
      │
      ▼
    erro
      │
      ▼
   /erro
```

---

## 📄 Formato do arquivo CSV

O arquivo utilizado pelo processamento possui duas partes: o cabeçalho na primeira linha e os registros a partir da segunda linha.

```text
Nome|CPF|Agência|Conta|Valor|Mês de Referência
<registros>
```

Exemplo:

```text
Nome|CPF|Agência|Conta|Valor|Mês de Referência
João da Silva|12345678901|1234|123456-7|9995.88|2026-09
Maria Souza|98765432100|5678|987654-3|1250.50|2026-09
```

O SHA-256 é calculado pelo próprio File Pulse sobre o arquivo completo após a confirmação de que a gravação foi finalizada. O hash é consultado no banco de dados para impedir o reprocessamento indevido do mesmo arquivo. Após essa validação, o `FlatFileItemReader` inicia a leitura dos registros a partir da terceira linha.

Os campos são convertidos para o domínio:

```text
Nome
  ↓
name

CPF
  ↓
cpf

Agência
  ↓
agency

Conta
  ↓
account

Valor
  ↓
amount

Mês de Referência
  ↓
referenceMonth
```

---

## ⚙️ Processamento com Spring Batch

O processamento utiliza o modelo:

```text
Reader
   │
   ▼
Processor
   │
   ▼
Writer
```

### Reader

O `FlatFileItemReader<Customer>` realiza a leitura dos registros do CSV a partir da segunda linha, pois a primeira contém o cabeçalho.

O arquivo não é carregado integralmente em memória.

Em vez de:

```text
Arquivo
   ↓
List<Customer>
   ↓
Processamento
```

o projeto utiliza:

```text
Arquivo
   ↓
Reader
   ↓
Chunk
   ↓
Processor
   ↓
Writer
```

Essa abordagem é fundamental para arquivos de grande volume.

---

## 📦 Chunk Processing

O tamanho do chunk é um dos principais parâmetros de performance do projeto.

O benchmark do projeto executará os seguintes tamanhos de chunk:

```text
100
500
1000
2000
5000
```

O cenário de referência utiliza um arquivo com:

```text
5.000.000 registros
```

que será processado repetidamente com cada tamanho de chunk definido no benchmark.

Quanto maior o chunk, menor tende a ser a quantidade de commits, porém maiores podem ser o consumo de memória e o tamanho das transações.

Por isso, o projeto executará **benchmarks reais** para identificar o melhor equilíbrio entre throughput, memória, CPU, I/O e tempo de processamento. O valor escolhido será externalizado em `fileprocessor.batch.chunk-size`.

---

## 🔄 Processor

O `CustomerProcessor` é responsável pela validação, normalização e conversão dos dados.

Exemplo conceitual:

```text
CustomerDTO
     │
     ▼
Validação
     │
     ▼
Normalização
     │
     ▼
Conversão
     │
     ▼
Customer
```

Exemplo:

```text
"9995.88"
     ↓
BigDecimal
     ↓
9995.88
```

Registros inválidos são tratados pelo mecanismo de tolerância a falhas do Spring Batch. O `CustomerProcessor` lança `InvalidRecordException` quando uma linha viola as regras de validação. O Step é configurado com `faultTolerant()` e `skip(InvalidRecordException.class)`, enquanto o `RecordSkipListener` persiste cada linha rejeitada em `file_processing_error`.

O limite máximo de registros inválidos é externalizado na propriedade `fileprocessor.batch.skip-limit`. Enquanto o limite não for ultrapassado, o processamento continua. Ao excedê-lo, o Step e o Job assumem o status `FAILED`, o processamento é registrado no histórico e o arquivo é movido para `/erro`.

---

## 💾 Writer

Para grandes volumes, a persistência individual deve ser evitada.

Em vez de:

```text
repository.save(customer)
repository.save(customer)
repository.save(customer)
...
```

o projeto utiliza escrita em lote:

```text
1.000 registros
      ↓
JDBC Batch
      ↓
PostgreSQL
```

A persistência é realizada com:

```java
JdbcBatchItemWriter<Customer>
```

O `JdbcBatchItemWriter` executa inserts em lote dentro da transação de cada chunk, reduzindo round-trips ao PostgreSQL e mantendo o commit alinhado ao limite transacional do Step.

---

## 🗄️ Banco de dados

O banco de dados possui quatro estruturas de domínio principais, além das tabelas de metadados mantidas pelo próprio Spring Batch:

```text
file_processing
      │
      ├───────────────┐
      │               │
      ▼               ▼
customer      file_processing_error
      │
      └───────────────┐
                      ▼
             file_processing_log
```

### File Processing

`file_processing` representa a identidade e o estado atual de cada arquivo recebido. O hash informado na pelo cálculo do File Pulse após confirmação que a gravação foi concluída é persistido nessa tabela e possui restrição `UNIQUE`, tornando o PostgreSQL a última barreira contra processamento duplicado.

```sql
CREATE TABLE file_processing (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_records BIGINT,
    processed_records BIGINT NOT NULL DEFAULT 0,
    error_records BIGINT NOT NULL DEFAULT 0,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    CONSTRAINT uk_file_processing_hash UNIQUE (file_hash)
);
```

O `file_hash` deve conter o SHA-256 em representação hexadecimal, totalizando 64 caracteres. A aplicação valida esse formato antes de registrar o processamento.

### Customer

`customer` armazena os registros válidos importados pelo Batch. Cada linha processada mantém referência ao arquivo de origem por meio de `file_processing_id`.

```sql
CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    file_processing_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    cpf VARCHAR(14) NOT NULL,
    agency VARCHAR(10) NOT NULL,
    account VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    reference_month VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_file_processing
        FOREIGN KEY (file_processing_id)
        REFERENCES file_processing(id)
);

CREATE INDEX idx_customer_file_processing
    ON customer(file_processing_id);
```

Essa associação garante rastreabilidade entre os dados persistidos e o arquivo que originou cada registro.

### File Processing Error

`file_processing_error` registra definitivamente cada linha rejeitada pelo processamento. A tabela faz parte da estratégia de tolerância a falhas do Spring Batch e não é apenas uma estrutura opcional.

```sql
CREATE TABLE file_processing_error (
    id BIGSERIAL PRIMARY KEY,
    file_processing_id BIGINT NOT NULL,
    line_number BIGINT NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    raw_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_processing_error_file
        FOREIGN KEY (file_processing_id)
        REFERENCES file_processing(id),
    CONSTRAINT uk_file_processing_error_line
        UNIQUE (file_processing_id, line_number)
);

CREATE INDEX idx_file_processing_error_file
    ON file_processing_error(file_processing_id);
```

A restrição composta `(file_processing_id, line_number)` evita que o mesmo erro seja gravado novamente durante um restart do Job.

### File Processing Log

`file_processing_log` mantém o histórico de eventos do ciclo de vida do arquivo. Diferentemente de `file_processing`, essa tabela **não possui unicidade por hash**, porque um mesmo processamento deve gerar vários eventos de auditoria.

```sql
CREATE TABLE file_processing_log (
    id BIGSERIAL PRIMARY KEY,
    file_processing_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_processing_log_file
        FOREIGN KEY (file_processing_id)
        REFERENCES file_processing(id)
);

CREATE INDEX idx_file_processing_log_file
    ON file_processing_log(file_processing_id);

CREATE INDEX idx_file_processing_log_hash
    ON file_processing_log(file_hash);
```

Eventos como `FILE_DETECTED`, `VALIDATED`, `PROCESSING_STARTED`, `RECORD_SKIPPED`, `PROCESSING_COMPLETED` e `PROCESSING_FAILED` são registrados nessa tabela. O hash permanece nos logs para auditoria e pesquisa, enquanto a garantia de idempotência pertence à restrição `UNIQUE` de `file_processing.file_hash`.

---

## 🔐 Idempotência

O processamento deve impedir que o mesmo arquivo seja processado indevidamente mais de uma vez. A idempotência faz parte do contrato do arquivo: **O SHA-256 é calculado pelo próprio File Pulse sobre o arquivo completo após a confirmação de que a gravação foi finalizada**.

Estrutura:

```text
Linha 1 -> cabeçalho
Linha 2 em diante -> registros
```

Exemplo:

```text
Nome|CPF|Agência|Conta|Valor|Mês de Referência
João da Silva|12345678901|1234|123456-7|9995.88|2026-09
Maria Souza|98765432100|5678|987654-3|1250.50|2026-09
```

Antes de iniciar o Spring Batch Job, o sistema executa o seguinte fluxo:

```text
Arquivo detectado
      │
      ▼
Ler primeira linha
      │
      ▼
Obter hash
      │
      ▼
Consultar file_processing
      │
      ├── hash existente -> rejeitar processamento duplicado
      │
      └── hash inexistente -> inserir file_processing e continuar
                              │
                              ▼
                       restrição UNIQUE
```

Dessa forma, o nome do arquivo não é utilizado como critério principal de idempotência. Mesmo que o mesmo conteúdo seja reenviado com outro nome, o hash identifica que ele já foi recebido e impede um novo processamento indevido.

A verificação feita pela aplicação melhora o fluxo e permite retornar uma resposta controlada, mas a garantia definitiva é a restrição `UNIQUE` de `file_processing.file_hash`. Isso protege o sistema contra condições de corrida quando múltiplos workers consumirem eventos via RabbitMQ e tentarem reservar simultaneamente o mesmo arquivo.

O `file_processing_log` recebe os eventos gerados após a criação do processamento, mas não é utilizado como trava de unicidade.

Essa estratégia evita situações como:

```text
5.000.000 registros
        +
5.000.000 registros
        =
10.000.000 registros
```

sem controle sobre a origem do processamento.

---

## 🔁 Restart

O projeto utiliza o mecanismo de restart do Spring Batch para recuperar execuções interrompidas sem criar um novo processamento para o mesmo arquivo.

Em um cenário onde:

```text
5.000.000 registros
```

e:

```text
3.200.000 processados
```

se a aplicação for interrompida, o restart reutiliza o mesmo `file_processing`, o mesmo `file_hash` e a mesma `JobInstance`. O estado persistido nas tabelas de metadados do Spring Batch determina o ponto de retomada do Step.

O endpoint `POST /api/files/{id}/retry` somente aceita processamentos em estado `FAILED`. Ele não cria um novo registro em `file_processing` nem tenta reservar novamente o hash; inicia uma nova `JobExecution` vinculada à mesma `JobInstance`.

Essa separação é fundamental:

```text
novo arquivo com hash já existente
        ↓
REJEITADO por idempotência

processamento existente com status FAILED
        ↓
RESTART da mesma JobInstance
```

Assim, idempotência impede duplicidade de ingestão e restart permite recuperação operacional sem violar essa garantia.

---

## 📊 Spring Batch Metadata

O Spring Batch mantém informações sobre a execução dos Jobs através de tabelas como:

```text
BATCH_JOB_INSTANCE
BATCH_JOB_EXECUTION
BATCH_JOB_EXECUTION_PARAMS
BATCH_STEP_EXECUTION
```

Essas informações permitem acompanhar:

* início da execução
* término da execução
* status
* quantidade processada
* commits
* skips
* falhas
* estado do Step

O projeto trabalha com dois níveis de controle:

```text
Spring Batch Metadata
        +
File Processing
```

O primeiro controla a execução do Batch.

O segundo controla o domínio da aplicação e o ciclo de vida do arquivo.

---

## ⚡ WebFlux

O Spring WebFlux é utilizado como camada de comunicação reativa.

O objetivo não é utilizar WebFlux para monitorar o filesystem.

O monitoramento do filesystem é responsabilidade do:

```text
Java NIO WatchService
```

O WebFlux entra principalmente para disponibilizar informações do processamento e eventos em tempo real.

---

## 📡 Server-Sent Events

O projeto utiliza **SSE (Server-Sent Events)** para disponibilizar o progresso do processamento em tempo real.

Endpoint:

```http
GET /api/files/{id}/events
```

Fluxo:

```text
Spring Batch
     │
     ▼
BatchProgressEvent
     │
     ▼
WebFlux
     │
     ▼
Flux
     │
     ▼
SSE
     │
     ▼
Browser
```

Exemplo de eventos:

```text
PROCESSING_STARTED

PROGRESS: 500000

PROGRESS: 1000000

PROGRESS: 1500000

...

COMPLETED
```

---

## 📈 Dashboard

O projeto adotará um dashboard para acompanhamento dos Jobs, consumindo a API reativa e os eventos SSE definidos nesta arquitetura.

Exemplo:

```text
┌─────────────────────────────────────────────┐
│              FILE PULSE                 │
├─────────────────────────────────────────────┤
│                                             │
│ clientes_001.csv                            │
│                                             │
│ Status: PROCESSING                          │
│                                             │
│ ████████████████░░░░░░░ 68%                │
│                                             │
│ Processados: 3.400.000                     │
│ Total:       5.000.000                     │
│ Erros:       127                           │
│                                             │
│ Início: 11:30:21                            │
│ Tempo: 08:42                                │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 🚨 Tratamento de erros

O tratamento de erros segue uma estratégia definida de tolerância a falhas por registro. Erros de conteúdo não interrompem imediatamente o arquivo inteiro; eles são classificados, persistidos e contabilizados pelo Spring Batch.

Exemplo:

```text
5.000.000 registros

Registro 3.481.229
        ↓
CPF inválido
        ↓
InvalidRecordException
        ↓
skip
        ↓
RecordSkipListener
        ↓
file_processing_error
```

O fluxo adotado é:

```text
Reader
  │
  ▼
Processor
  │
  ├── registro válido ───────────────► Writer ─► customer
  │
  └── registro inválido
              │
              ▼
     InvalidRecordException
              │
              ▼
            skip
              │
              ▼
     RecordSkipListener
              │
              ▼
   file_processing_error
```

Para cada linha rejeitada, o listener registra:

```text
file_processing_id
line_number
error_type
error_message
raw_data
created_at
```

O contador `error_records` de `file_processing` é atualizado durante a execução. Enquanto a quantidade de skips permanecer dentro de `fileprocessor.batch.skip-limit`, o Job continua normalmente. Se o limite for ultrapassado, o Step falha, o `file_processing.status` passa para `FAILED`, um evento `PROCESSING_FAILED` é incluído em `file_processing_log` e o arquivo é movido para `/erro`.

Ao concluir dentro do limite permitido, o arquivo pode apresentar registros processados e rejeitados no mesmo resultado:

```text
Total:       5.000.000
Processados: 4.999.873
Erros:       127
Status:      COMPLETED
```

A tabela `file_processing_error` mantém o detalhe dos 127 registros rejeitados para auditoria e diagnóstico.

---

## 🌐 API

A API disponibiliza os seguintes endpoints:

### Listar arquivos

```http
GET /api/files
```

### Consultar processamento

```http
GET /api/files/{id}
```

### Acompanhar eventos

```http
GET /api/files/{id}/events
```

### Reprocessar arquivo

```http
POST /api/files/{id}/retry
```

### Health Check

```http
GET /actuator/health
```

---

## 🔒 Segurança

A camada de segurança será implementada com Spring Security e utilizará três perfis:

```text
ADMIN
OPERATOR
VIEWER
```

A política de autorização será:

| Operação | ADMIN | OPERATOR | VIEWER |
| --- | :---: | :---: | :---: |
| `GET /api/files` | ✅ | ✅ | ✅ |
| `GET /api/files/{id}` | ✅ | ✅ | ✅ |
| `GET /api/files/{id}/events` | ✅ | ✅ | ✅ |
| `POST /api/files/{id}/retry` | ✅ | ✅ | ❌ |
| Endpoints administrativos | ✅ | ❌ | ❌ |

A entrada de arquivos ocorre pelo diretório monitorado, portanto não é definido um endpoint de upload nessa arquitetura.

---

## 📊 Observabilidade

A camada de observabilidade do projeto é composta por:

```text
Spring Boot Actuator
        +
Micrometer
        +
Prometheus
        +
Grafana
```

Métricas de interesse:

* Jobs executados
* Jobs com erro
* Arquivos processados
* Registros processados
* Registros por segundo
* Tempo médio de processamento
* CPU
* Memória
* Quantidade de erros
* Tempo médio por chunk

---

## 🐳 Docker Compose

O ambiente de desenvolvimento é orquestrado através do Docker Compose.

### Ambiente inicial

```text
docker-compose
│
├── application
└── postgres
```

### Arquitetura completa

```text
docker-compose
│
├── application
├── postgres
├── rabbitmq
├── prometheus
└── grafana
```

---

# 🏗️ Arquitetura por etapas

O projeto é desenvolvido de forma incremental em três etapas. O RabbitMQ faz parte da arquitetura definida para o projeto e é introduzido na etapa de processamento distribuído.

## Fase 1 — Fundamentos

```text
Java NIO
   ↓
WatchService
   ↓
Spring Batch
   ↓
PostgreSQL
```

### Objetivo

Processar corretamente arquivos contendo milhões de registros.

---

## Fase 2 — Reatividade

```text
Java NIO
   ↓
WatchService
   ↓
Spring Batch
   ↓
Event
   ↓
WebFlux
   ↓
SSE
   ↓
Dashboard
```

### Objetivo

Permitir o acompanhamento do processamento em tempo real.

---

## Fase 3 — Arquitetura distribuída com RabbitMQ

```text
┌──────────────┐
│ File System  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ WatchService │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  RabbitMQ    │
│ file.created │
└──────┬───────┘
       │
       ▼
 ┌─────┼─────┐
 ▼     ▼     ▼
Worker Worker Worker
 │     │     │
 ▼     ▼     ▼
Batch  Batch  Batch
 └─────┼─────┘
       ▼
  PostgreSQL
       │
       ▼
 Event Stream
       │
       ▼
    WebFlux
       │
       ▼
   Dashboard
```

Na etapa distribuída, o RabbitMQ passa a intermediar a comunicação entre a detecção do arquivo e os workers de processamento. O watcher publica o evento de chegada do arquivo.

Os workers ficam responsáveis por consumir os eventos e iniciar o processamento.

Isso permite aumentar a quantidade de consumidores conforme a necessidade.

---

# 🧪 Teste de performance

Um dos principais objetivos técnicos do projeto é realizar testes com arquivos de grande volume.

Um cenário de referência é:

```text
5.000.000 registros
```

O benchmark executará as seguintes configurações:

| Teste | Chunk |
| ----- | ----: |
| 1     |   100 |
| 2     |   500 |
| 3     | 1.000 |
| 4     | 2.000 |
| 5     | 5.000 |

As métricas avaliadas serão:

* Tempo total
* Registros por segundo
* Memória utilizada
* CPU
* I/O
* Quantidade de commits
* Tempo médio por chunk
* Quantidade de erros

Os resultados devem ser obtidos através de testes reais. Valores hipotéticos não devem ser utilizados como benchmark oficial do projeto.

---

# ⚠️ Considerações de performance

O tamanho do chunk não deve ser escolhido apenas porque um valor maior parece mais rápido.

Um chunk maior pode:

* reduzir a quantidade de commits;
* aumentar o throughput;
* aumentar o consumo de memória;
* aumentar o tamanho das transações;
* aumentar o impacto de uma falha durante a transação.

Além disso, em uma arquitetura distribuída, aumentar indiscriminadamente a quantidade de workers pode deslocar o gargalo para o PostgreSQL.

Por isso, a análise deve considerar:

```text
CPU
RAM
Disco
I/O
PostgreSQL
Conexões
Transações
Chunk
Concorrência
```

---

# 🔮 Roadmap

## Core

* [x] Implementar File Watcher
* [x] Implementar validação de arquivos
* [x] Implementar controle de arquivo completo
* [x] Implementar movimentação entre diretórios
* [ ] Implementar Spring Batch
* [ ] Implementar Reader
* [ ] Implementar Processor
* [ ] Implementar Writer
* [ ] Implementar persistência PostgreSQL
* [ ] Implementar controle de processamento

## Confiabilidade

* [ ] Implementar idempotência
* [ ] Implementar calculado pelo próprio File Pulse sobre o arquivo completo após a confirmação de que a gravação foi finalizada
* [ ] Implementar tratamento de duplicidade
* [ ] Implementar skip de registros inválidos
* [ ] Implementar armazenamento de erros
* [ ] Implementar restart de Jobs

## API e reatividade

* [ ] Implementar API REST
* [ ] Implementar WebFlux
* [ ] Implementar SSE
* [ ] Implementar eventos de progresso
* [ ] Implementar dashboard

## Observabilidade

* [ ] Actuator
* [ ] Micrometer
* [ ] Prometheus
* [ ] Grafana
* [ ] Métricas de throughput
* [ ] Métricas de Jobs
* [ ] Métricas de infraestrutura

## Infraestrutura

* [ ] Docker
* [ ] Docker Compose
* [ ] Testcontainers
* [ ] OpenAPI / Swagger

## Arquitetura distribuída

* [ ] RabbitMQ
* [ ] Evento `file.created`
* [ ] File Worker
* [ ] Múltiplos workers
* [ ] Processamento concorrente
* [ ] Testes de escalabilidade

---

# 🎓 Objetivo técnico

Mais do que demonstrar o uso isolado de tecnologias, o **File Pulse** busca demonstrar decisões de engenharia aplicadas a um cenário de processamento de alto volume.

Os principais conceitos explorados são:

```text
Processamento assíncrono
        +
Processamento em lote
        +
Chunk-oriented processing
        +
Idempotência
        +
Resiliência
        +
Restart
        +
Observabilidade
        +
Reatividade
        +
SSE
        +
Mensageria
        +
Escalabilidade
```

O projeto também permite comparar diferentes estratégias arquiteturais e medir empiricamente seus impactos.

---

# 📚 Conceitos demonstrados

* Java NIO
* WatchService
* Spring Boot
* Spring Batch
* Chunk Processing
* ItemReader
* ItemProcessor
* ItemWriter
* JDBC Batch
* PostgreSQL
* Spring WebFlux
* Server-Sent Events
* Idempotência
* Hash de idempotência
* Restartability
* Skip / Fault Tolerance
* Event-driven architecture
* RabbitMQ
* Docker
* Testcontainers
* Observabilidade
* Prometheus
* Grafana
* Performance Benchmark

---

# 📌 Status

🚧 **Em desenvolvimento**

O projeto está sendo desenvolvido de forma incremental. A arquitetura alvo contempla processamento local, comunicação reativa, observabilidade e processamento distribuído com RabbitMQ, implementados progressivamente ao longo das etapas do projeto.

---

## 📄 Licença

Este projeto está disponível para fins de estudo, experimentação e demonstração técnica.

---

## 👨‍💻 Autor

**Jards Guimarães**

Backend Developer | Java | Spring Boot | Quarkus

---

> **File Pulse** — processamento de arquivos em escala, do filesystem ao processamento distribuído.

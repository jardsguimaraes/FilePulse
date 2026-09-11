# File Pulse

Plataforma de processamento assíncrono de arquivos CSV de alto volume, desenvolvida com **Java 21 e Spring Boot**, capaz de detectar automaticamente a chegada de arquivos, processar milhões de registros de forma eficiente e disponibilizar o progresso do processamento em tempo real.

O projeto combina **Java NIO WatchService, Spring Batch, PostgreSQL, Spring WebFlux, Server-Sent Events (SSE) e RabbitMQ**, compondo uma arquitetura que parte do processamento local e avança, em uma etapa posterior do próprio projeto, para processamento distribuído orientado a eventos.

---

## 🎯 Objetivo

O objetivo do **File Processor** é construir uma solução capaz de processar arquivos CSV contendo milhões de registros sem carregar todo o conteúdo em memória.

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
src/main/java/br/com/jards/fileprocessor
│
├── FileProcessorApplication.java
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
│   └── writer
│       └── CustomerWriter.java
│
├── domain
│   ├── Customer.java
│   ├── FileProcessing.java
│   ├── FileProcessingLog.java
│   └── ProcessingStatus.java
│
├── repository
│   ├── CustomerRepository.java
│   ├── FileProcessingRepository.java
│   └── FileProcessingLogRepository.java
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

O arquivo utilizado pelo processamento possui três partes: o hash de identificação na primeira linha, o cabeçalho na segunda linha e os registros a partir da terceira linha.

```text
<hash>
Nome|CPF|Agência|Conta|Valor|Mês de Referência
<registros>
```

Exemplo:

```text
hash256325khjgfg...
Nome|CPF|Agência|Conta|Valor|Mês de Referência
João da Silva|12345678901|1234|123456-7|9995.88|2026-09
Maria Souza|98765432100|5678|987654-3|1250.50|2026-09
```

A primeira linha é lida pela etapa de validação antes do início do Job. O hash é consultado no banco de dados para impedir o reprocessamento indevido do mesmo arquivo. Após essa validação, o `FlatFileItemReader` inicia a leitura dos registros a partir da terceira linha.

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

O `FlatFileItemReader<Customer>` realiza a leitura dos registros do CSV a partir da terceira linha, pois a primeira contém o hash do arquivo e a segunda contém o cabeçalho.

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

Inicialmente podem ser avaliados valores como:

```text
100
500
1000
2000
5000
10000
```

Um arquivo com:

```text
5.000.000 registros
```

pode ser processado utilizando diferentes tamanhos de chunk.

Quanto maior o chunk, menor tende a ser a quantidade de commits, porém maiores podem ser o consumo de memória e o tamanho das transações.

Por isso, o projeto propõe realizar **benchmarks reais** para identificar o melhor equilíbrio entre throughput, memória, CPU, I/O e tempo de processamento.

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

Registros inválidos podem ser descartados através do mecanismo de `skip` do Spring Batch, dependendo da configuração do Job.

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

A implementação pode utilizar:

```java
JdbcBatchItemWriter<Customer>
```

---

## 🗄️ Banco de dados

### Customer

Tabela responsável pelos dados processados:

```sql
CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    cpf VARCHAR(14) NOT NULL,
    agency VARCHAR(10) NOT NULL,
    account VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    reference_month VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### File Processing

Tabela responsável pelo controle dos arquivos:

```sql
CREATE TABLE file_processing (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    total_records BIGINT,
    processed_records BIGINT,
    error_records BIGINT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT
);
```

### File Processing Log

Tabela responsável pelo registro de auditoria do ciclo de vida dos arquivos. O hash informado na primeira linha do CSV é persistido nessa tabela e consultado antes do processamento para garantir a idempotência.

```sql
CREATE TABLE file_processing_log (
    id BIGSERIAL PRIMARY KEY,
    file_processing_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_processing_log_file
        FOREIGN KEY (file_processing_id)
        REFERENCES file_processing(id)
);

CREATE UNIQUE INDEX uk_file_processing_log_hash
    ON file_processing_log(file_hash);
```

Para a validação de idempotência, a aplicação verifica a existência do `file_hash` antes de iniciar um novo processamento. Além da validação na aplicação, a restrição `UNIQUE` no banco impede que dois fluxos concorrentes registrem o mesmo hash. O registro ocorre antes da execução do Job, juntamente com o status inicial do arquivo.

---

## 🔐 Idempotência

O processamento deve impedir que o mesmo arquivo seja processado indevidamente mais de uma vez. A idempotência faz parte do contrato do arquivo: **a primeira linha do CSV contém o hash utilizado para identificar o processamento**.

Estrutura:

```text
Linha 1 -> hash do arquivo
Linha 2 -> cabeçalho
Linha 3 em diante -> registros
```

Exemplo:

```text
hash256325khjgfg...
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
Consultar file_processing_log
      │
      ├── hash existente -> rejeitar processamento duplicado
      │
      └── hash inexistente -> registrar hash e continuar
                              │
                              ▼
                       restrição UNIQUE
```

Dessa forma, o nome do arquivo não é utilizado como critério principal de idempotência. Mesmo que o mesmo conteúdo seja reenviado com outro nome, o hash permite identificar que ele já foi recebido e impedir um novo processamento indevido. A restrição `UNIQUE` sobre `file_hash` também protege o sistema contra condições de corrida quando houver múltiplos workers consumindo eventos via RabbitMQ.

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

Uma das vantagens do Spring Batch é o suporte ao mecanismo de restart.

Em um cenário onde:

```text
5.000.000 registros
```

e:

```text
3.200.000 processados
```

e a aplicação é interrompida, o Job pode utilizar o estado persistido pelo Spring Batch para continuar o processamento, evitando necessariamente começar novamente do zero.

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

A arquitetura permite a criação de um dashboard para acompanhamento dos Jobs.

Exemplo:

```text
┌─────────────────────────────────────────────┐
│              FILE PROCESSOR                 │
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

O projeto considera a possibilidade de erros individuais durante o processamento.

Exemplo:

```text
5.000.000 registros

Registro 3.481.229
        ↓
CPF inválido
```

Dependendo da configuração do Batch, o registro pode ser ignorado e o processamento continuar.

Ao final:

```text
Total:       5.000.000
Processados: 4.999.873
Erros:       127
```

Os erros podem ser armazenados em uma estrutura específica:

```text
file_processing_error
```

com informações como:

```text
file_id
line_number
error_type
error_message
raw_data
created_at
```

---

## 🌐 API

A API inicial pode disponibilizar os seguintes endpoints:

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

Uma evolução planejada é a utilização do Spring Security.

Possíveis perfis:

```text
ADMIN
OPERATOR
VIEWER
```

Com controle de acesso para operações como:

```text
POST /api/files
GET  /api/files
GET  /api/files/{id}
GET  /api/files/{id}/events
POST /api/files/{id}/retry
```

---

## 📊 Observabilidade

O projeto prevê uma camada de observabilidade utilizando:

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

Para o ambiente de desenvolvimento, a arquitetura pode ser executada através do Docker Compose.

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

O benchmark pode avaliar diferentes configurações:

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

* [ ] Implementar File Watcher
* [ ] Implementar validação de arquivos
* [ ] Implementar controle de arquivo completo
* [ ] Implementar movimentação entre diretórios
* [ ] Implementar Spring Batch
* [ ] Implementar Reader
* [ ] Implementar Processor
* [ ] Implementar Writer
* [ ] Implementar persistência PostgreSQL
* [ ] Implementar controle de processamento

## Confiabilidade

* [ ] Implementar idempotência
* [ ] Implementar leitura e validação do hash da primeira linha do arquivo
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

Mais do que demonstrar o uso isolado de tecnologias, o **File Processor** busca demonstrar decisões de engenharia aplicadas a um cenário de processamento de alto volume.

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

> **File Processor** — processamento de arquivos em escala, do filesystem ao processamento distribuído.

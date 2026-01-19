# CLAUDE.md - Guia do Sistema para Modelos de IA

Este documento descreve o funcionamento do sistema **Email Extractor** para auxiliar modelos de IA a entenderem e trabalharem com o código.

## Visão Geral do Sistema

O **Email Extractor** é uma aplicação Java de linha de comando que:
1. Conecta a um servidor de e-mail via protocolo IMAP com SSL
2. Lê todas as mensagens da caixa de entrada (INBOX) do mais antigo para o mais recente
3. Extrai informações dos remetentes (e-mail, nome, data)
4. Permite pausar/retomar o processamento com teclas de atalho
5. Salva o estado em tempo real para recuperação posterior
6. Gera um arquivo CSV com contatos únicos

## Arquitetura

### Arquivo Principal
- **Localização**: `src/main/java/com/emailextractor/EmailExtractor.java`
- **Classe principal**: `EmailExtractor`
- **Ponto de entrada**: método `main(String[] args)`

### Estrutura de Dados Interna

```java
// Contato em memória
private static class ContatoEmail {
    String email;           // Endereço de e-mail (chave única)
    String nome;            // Nome do remetente
    Date dataUltimoEmail;   // Data do e-mail mais recente
}

// Contato para serialização JSON
private static class ContatoSerializado {
    String email;
    String nome;
    String dataUltimoEmail; // Formato: "yyyy-MM-dd HH:mm:ss"
}

// Estado do processo para persistência
private static class EstadoProcesso {
    String servidor;
    int porta;
    String usuario;
    String arquivoCsv;
    int indiceAtual;
    int totalMensagens;
    String dataInicio;
    String dataUltimaSalva;
    Map<String, ContatoSerializado> contatos;
}
```

### Variáveis de Controle (Thread-Safe)

```java
private static volatile boolean pausado = false;    // Flag de pausa
private static volatile boolean encerrar = false;   // Flag para encerrar
private static volatile boolean processando = false; // Indica se está processando
```

## Fluxo de Execução

```
main()
  │
  ├── Verifica se existe estado salvo (estado_processo.json)
  │     ├── Se SIM: Pergunta se deseja continuar ou reiniciar
  │     │     ├── Continuar: Carrega estado e solicita apenas senha
  │     │     └── Reiniciar: Deleta estado e solicita novas credenciais
  │     └── Se NÃO: Solicita credenciais normalmente
  │
  ├── Inicializa EstadoProcesso
  │
  ├── Inicia Thread de monitoramento de teclado
  │     └── Escuta: P (pausar), R (retomar), S (sair salvando)
  │
  └── connectAndExtract()
        ├── Configura Properties para IMAP SSL
        ├── Conecta ao servidor
        ├── Abre INBOX em READ_ONLY
        ├── Obtém e ordena mensagens por data (mais antiga primeiro)
        │
        ├── Loop de processamento (com suporte a pausa):
        │     ├── Verifica flag 'encerrar'
        │     ├── Aguarda enquanto 'pausado' for true
        │     ├── extractSenderInfo() para cada mensagem
        │     ├── Atualiza barra de progresso
        │     └── Salva estado a cada 50 mensagens
        │
        ├── exportToCsv() ao finalizar
        └── deletarEstadoSalvo() após conclusão bem-sucedida
```

## Métodos Principais

### `main(String[] args)`
- Entrada da aplicação
- Verifica e gerencia estado salvo
- Coordena a execução

### `solicitarCredenciais(Scanner scanner, Console console)`
- Retorna: `String[]` com {usuario, senha, servidor, porta, nomeArquivo}
- Coleta dados do usuário via console

### `iniciarMonitoramentoTeclado(Scanner scanner)`
- Inicia thread daemon para escutar comandos
- Comandos: P (pausar), R (retomar), S (sair)

### `connectAndExtract(String usuario, String senha, String servidor, int porta, String nomeArquivo, int indiceInicial)`
- Processa mensagens do índice inicial até o final
- Suporta pausa e retomada
- Salva estado periodicamente

### `extractSenderInfo(Message mensagem, Map<String, ContatoEmail> contatos)`
- Extrai endereço e nome do remetente via `InternetAddress`
- Atualiza contato existente se e-mail já processado

### `printProgressBar(int atual, int total)`
- Exibe barra visual: `[========          ] 45% (450/1000) | Contatos: 89`
- Usa `\r` para sobrescrever linha anterior

### Métodos de Gerenciamento de Estado

| Método | Descrição |
|--------|-----------|
| `existeEstadoSalvo()` | Verifica se `estado_processo.json` existe |
| `carregarEstado()` | Carrega estado do JSON para `EstadoProcesso` |
| `salvarEstado()` | Salva estado atual no JSON (synchronized) |
| `deletarEstadoSalvo()` | Remove arquivo de estado após conclusão |

### `exportToCsv(Map<String, ContatoEmail> contatos, String nomeArquivo)`
- Usa OpenCSV para gerar arquivo
- Colunas: `email_remetente`, `nome_remetente`, `data_ultimo_email`

## Dependências (Maven)

| GroupId | ArtifactId | Versão | Uso |
|---------|------------|--------|-----|
| org.eclipse.angus | angus-mail | 2.0.2 | Jakarta Mail para IMAP |
| com.opencsv | opencsv | 5.9 | Geração de CSV |
| com.google.code.gson | gson | 2.10.1 | Serialização JSON do estado |

## Arquivo de Estado (estado_processo.json)

```json
{
  "servidor": "imap.gmail.com",
  "porta": 993,
  "usuario": "email@gmail.com",
  "arquivoCsv": "contatos.csv",
  "indiceAtual": 523,
  "totalMensagens": 1250,
  "dataInicio": "2026-01-19 10:30:00",
  "dataUltimaSalva": "2026-01-19 10:45:23",
  "contatos": {
    "exemplo@mail.com": {
      "email": "exemplo@mail.com",
      "nome": "João Silva",
      "dataUltimoEmail": "2026-01-15 14:30:00"
    }
  }
}
```

**Nota**: A senha NÃO é armazenada por segurança.

## Configurações de Conexão IMAP

```java
Properties props = new Properties();
props.put("mail.store.protocol", "imaps");
props.put("mail.imaps.host", servidor);
props.put("mail.imaps.port", String.valueOf(porta));
props.put("mail.imaps.ssl.enable", "true");
props.put("mail.imaps.ssl.trust", "*");
props.put("mail.imaps.connectiontimeout", "10000");
props.put("mail.imaps.timeout", "10000");
```

## Ordenação de Mensagens

As mensagens são ordenadas por data de envio (mais antiga primeiro):

```java
Arrays.sort(mensagens, (m1, m2) -> {
    Date d1 = m1.getSentDate();
    Date d2 = m2.getSentDate();
    if (d1 == null && d2 == null) return 0;
    if (d1 == null) return -1;
    if (d2 == null) return 1;
    return d1.compareTo(d2);
});
```

**Motivo**: Ao retomar um processo pausado, os e-mails novos que chegaram durante a pausa ainda serão processados (ficam no final da lista).

## Plugins Maven Configurados

1. **exec-maven-plugin**: Execução direta via `mvn exec:java`
2. **maven-jar-plugin**: Configura manifest com mainClass
3. **maven-shade-plugin**: Gera JAR com todas as dependências (uber-jar)

## Comandos Úteis

```bash
# Compilar
mvn clean compile

# Gerar JAR executável
mvn clean package

# Executar via Maven
mvn exec:java

# Executar JAR
java -jar target/email-extractor-1.0-SNAPSHOT.jar
```

## Teclas de Atalho Durante Execução

| Tecla | Ação |
|-------|------|
| P + Enter | Pausar processamento |
| R + Enter | Retomar processamento |
| S + Enter | Sair salvando estado |

## Pontos de Extensão Comuns

### Adicionar mais pastas além da INBOX
No método `connectAndExtract()`, modificar para iterar sobre outras pastas:
```java
Folder[] folders = store.getDefaultFolder().list("*");
```

### Extrair destinatários (TO, CC, BCC)
No método `extractSenderInfo()`, adicionar:
```java
Address[] destinatarios = mensagem.getRecipients(Message.RecipientType.TO);
```

### Filtrar por data
Usar `SearchTerm` do Jakarta Mail:
```java
SearchTerm term = new ReceivedDateTerm(ComparisonTerm.GT, dataInicio);
Message[] mensagens = inbox.search(term);
```

### Modificar intervalo de salvamento automático
Alterar a condição no loop principal:
```java
if (processadas - ultimoSalvamento >= 50) { // Mudar 50 para outro valor
    salvarEstado();
    ultimoSalvamento = processadas;
}
```

## Tratamento de Erros

- **Porta inválida**: Usa porta padrão 993
- **Mensagens não processáveis**: Ignora e continua processamento
- **Conexão falha**: Exceção propagada com mensagem de erro
- **Estado corrompido**: Deleta arquivo e inicia novo processo
- **Erro durante processamento**: Salva estado antes de encerrar

## Requisitos do Sistema

- Java 17+
- Maven 3.6+
- Acesso à internet (porta 993 para IMAP SSL)
- Conta de e-mail com IMAP habilitado

## Formato do CSV de Saída

```csv
email_remetente,nome_remetente,data_ultimo_email
"usuario@exemplo.com","Nome Completo","2026-01-15 10:30:45"
```

O formato de data segue o padrão: `yyyy-MM-dd HH:mm:ss`

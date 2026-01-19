# Email Extractor - Extrator de Contatos de E-mail para CSV

Aplicação Java que conecta a um servidor de e-mail via protocolo IMAP e extrai todos os remetentes das mensagens da caixa de entrada, gerando um arquivo CSV com a lista de contatos únicos.

## 📋 Funcionalidades

- Conexão segura via IMAP com SSL/TLS
- Extração de remetentes de todas as mensagens da caixa de entrada
- Deduplicação automática de contatos (agrupa por e-mail)
- Registro da data do último e-mail de cada contato
- Exportação para arquivo CSV
- Interface interativa via linha de comando
- Leitura segura de senha (quando disponível)
- **Barra de progresso visual em tempo real**
- **Pausar/retomar processamento com teclas de atalho**
- **Salvamento automático de estado em arquivo JSON**
- **Recuperação de processo interrompido**
- **Processamento ordenado (mais antigo → mais recente)**

## 🎮 Teclas de Atalho

Durante o processamento, você pode usar os seguintes comandos:

| Tecla | Ação |
|-------|------|
| **P** + Enter | Pausar o processamento |
| **R** + Enter | Retomar o processamento |
| **S** + Enter | Sair salvando o estado atual |

## 💾 Sistema de Salvamento de Estado

O sistema salva automaticamente o progresso em um arquivo `estado_processo.json`:

- **Salvamento automático**: A cada 50 mensagens processadas
- **Salvamento manual**: Ao pausar (P) ou sair (S)
- **Recuperação**: Ao reiniciar, pergunta se deseja continuar de onde parou

### Informações Salvas
- Servidor, porta e usuário
- Nome do arquivo CSV de destino
- Índice atual do processamento
- Todos os contatos encontrados até o momento
- Data/hora da última atualização

> **Nota de Segurança**: A senha nunca é salva no arquivo de estado.

## 🔧 Técnica Utilizada

### Protocolo IMAP
A aplicação utiliza o protocolo **IMAP (Internet Message Access Protocol)** para conectar ao servidor de e-mail. O IMAP permite acessar e manipular e-mails armazenados no servidor sem a necessidade de baixá-los permanentemente.

### Bibliotecas Principais

| Biblioteca | Versão | Finalidade |
|------------|--------|------------|
| **Jakarta Mail (Angus Mail)** | 2.0.2 | Conexão IMAP e manipulação de mensagens de e-mail |
| **OpenCSV** | 5.9 | Geração de arquivos CSV formatados corretamente |
| **Gson** | 2.10.1 | Serialização/deserialização JSON para salvamento de estado |

### Fluxo de Funcionamento

1. **Verificação de Estado**: Ao iniciar, verifica se existe processo anterior salvo
2. **Coleta de Credenciais**: Solicita informações de conexão (ou apenas senha se retomando)
3. **Conexão IMAP**: Estabelece conexão segura (SSL) com o servidor de e-mail
4. **Acesso à Caixa de Entrada**: Abre a pasta INBOX em modo somente leitura
5. **Ordenação**: Ordena mensagens por data (mais antiga primeiro)
6. **Processamento**: Itera sobre as mensagens extraindo:
   - Endereço de e-mail do remetente
   - Nome do remetente (quando disponível)
   - Data do envio
7. **Salvamento Periódico**: Salva estado a cada 50 mensagens
8. **Deduplicação**: Armazena contatos únicos usando HashMap
9. **Exportação**: Gera arquivo CSV ordenado alfabeticamente por e-mail

### Estrutura do CSV Gerado

```csv
email_remetente,nome_remetente,data_ultimo_email
contato@exemplo.com,Nome do Contato,2026-01-15 10:30:45
```

## ⚙️ Pré-requisitos

- **Java JDK 17** ou superior
- **Apache Maven 3.6+**
- Conta de e-mail com acesso IMAP habilitado

## 🛠️ Configuração do Ambiente

### 1. Instalar Java JDK 17

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

**Fedora:**
```bash
sudo dnf install java-17-openjdk-devel
```

**Arch Linux:**
```bash
sudo pacman -S jdk17-openjdk
```

**Verificar instalação:**
```bash
java -version
javac -version
```

### 2. Instalar Apache Maven

**Ubuntu/Debian:**
```bash
sudo apt install maven
```

**Fedora:**
```bash
sudo dnf install maven
```

**Arch Linux:**
```bash
sudo pacman -S maven
```

**Verificar instalação:**
```bash
mvn -version
```

### 3. Configurar o Provedor de E-mail

#### Gmail
1. Acesse [Configurações da Conta Google](https://myaccount.google.com/)
2. Ative a **Verificação em duas etapas**
3. Gere uma **Senha de App** em: Segurança → Senhas de app
4. Use a senha de app gerada (não a senha normal da conta)

**Configurações de conexão:**
- Servidor IMAP: `imap.gmail.com`
- Porta: `993`

#### Outlook/Hotmail
- Servidor IMAP: `outlook.office365.com`
- Porta: `993`

#### Yahoo Mail
- Servidor IMAP: `imap.mail.yahoo.com`
- Porta: `993`

## 🚀 Compilação com Maven

### Compilar o projeto
```bash
mvn clean compile
```

### Executar testes (se houver)
```bash
mvn test
```

### Gerar JAR executável com dependências
```bash
mvn clean package
```

O JAR será gerado em: `target/email-extractor-1.0-SNAPSHOT.jar`

### Executar via Maven (sem gerar JAR)
```bash
mvn exec:java
```

## 📦 Distribuição (Gerar JAR para outras pessoas)

Para gerar um JAR executável que pode ser enviado para outras pessoas:

### 1. Gerar o JAR
```bash
mvn clean package -DskipTests
```

### 2. Localizar o arquivo
O JAR será gerado em:
```
target/email-extractor-1.0-SNAPSHOT.jar
```

Este é um **"fat JAR"** (uber-jar) que já contém todas as dependências necessárias embutidas.

### 3. Enviar para outra pessoa
Basta enviar o arquivo `email-extractor-1.0-SNAPSHOT.jar`. Você pode renomeá-lo para algo mais amigável como `email-extractor.jar` se preferir.

### 4. Como a outra pessoa executa
**Requisito:** Ter o Java 17 ou superior instalado.

```bash
java -jar email-extractor-1.0-SNAPSHOT.jar
```

> **Nota:** O destinatário NÃO precisa ter o Maven instalado, apenas o Java.

## ▶️ Como Utilizar

### Opção 1: Executar via Maven
```bash
mvn exec:java
```

### Opção 2: Executar o JAR compilado
```bash
java -jar target/email-extractor-1.0-SNAPSHOT.jar
```

### Interação com a Aplicação

#### Início Novo (sem estado salvo)

```
==============================================
   EXTRATOR DE CONTATOS DE E-MAIL PARA CSV
==============================================

Informe o usuário (e-mail): seu.email@gmail.com
Informe a senha: ********
Informe o servidor IMAP (ex: imap.gmail.com): imap.gmail.com
Informe a porta IMAP (ex: 993): 993
Informe o nome do arquivo CSV para salvar (ex: contatos.csv): meus_contatos.csv

Conectando ao servidor...

╔════════════════════════════════════════════════════════╗
║  Comandos: [P] Pausar | [R] Retomar | [S] Sair salvando ║
╚════════════════════════════════════════════════════════╝

Conexão estabelecida com sucesso!
Total de mensagens na caixa de entrada: 1500
Processando mensagens (do mais antigo para o mais recente)...

[==================                      ]  45% (675/1500) | Contatos: 156
```

#### Retomando Processo Anterior

```
==============================================
   EXTRATOR DE CONTATOS DE E-MAIL PARA CSV
==============================================

[!] Processo anterior detectado!
    Servidor: imap.gmail.com
    Progresso: 675/1500 (45%)
    Última atualização: 2026-01-19 10:45:23
    Contatos encontrados: 156

Deseja continuar de onde parou? (S/N): S

Informe a senha para seu.email@gmail.com: ********

Conectando ao servidor...
Retomando processamento a partir da mensagem 676...
```

#### Pausando o Processamento

```
[==================                      ]  45% (675/1500) | Contatos: 156

>> Processamento PAUSADO. Pressione [R] para retomar ou [S] para sair salvando.
```

### Exemplo de Conclusão

```
[========================================] 100% (1500/1500) | Contatos: 342

Processamento concluído! 1500 mensagens processadas.
Arquivo CSV gerado com sucesso: meus_contatos.csv

==============================================
Extração concluída com sucesso!
Total de contatos únicos: 342
Arquivo salvo em: meus_contatos.csv
==============================================
```

## 📁 Estrutura do Projeto

```
email-extractor/
├── pom.xml                                    # Configuração Maven
├── README.md                                  # Este arquivo
├── CLAUDE.md                                  # Documentação técnica para IA
├── estado_processo.json                       # Estado salvo (criado automaticamente)
└── src/
    └── main/
        └── java/
            └── com/
                └── emailextractor/
                    └── EmailExtractor.java    # Classe principal
```

## 🔄 Cenários de Uso

### Processamento Completo
Execute o programa e aguarde até finalizar. O arquivo CSV será gerado e o estado temporário será removido automaticamente.

### Interrupção e Retomada
1. Inicie o processamento
2. Pressione **P** para pausar ou **S** para sair salvando
3. Feche o programa se desejar
4. Execute novamente mais tarde
5. Escolha **S** para continuar de onde parou

### Reiniciar do Zero
Se existir um estado salvo mas você quiser começar novamente:
1. Execute o programa
2. Quando perguntar se deseja continuar, digite **N**
3. O estado anterior será removido e você poderá inserir novas credenciais

## ⚠️ Observações Importantes

- **Segurança**: Nunca compartilhe suas credenciais. Use senhas de app quando disponível.
- **Senha**: A senha não é salva no arquivo de estado por segurança.
- **Volume de dados**: Para caixas de entrada muito grandes, use o recurso de pausa para dividir em sessões.
- **Acesso IMAP**: Certifique-se de que o acesso IMAP está habilitado nas configurações do seu provedor de e-mail.
- **Firewall**: A porta 993 deve estar liberada para conexões de saída.
- **Novos e-mails**: Como o processamento é do mais antigo para o mais recente, e-mails que chegarem durante uma pausa serão processados ao retomar.

## 📄 Licença

Este projeto é disponibilizado para uso livre.

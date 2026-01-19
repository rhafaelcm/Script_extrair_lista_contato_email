package com.emailextractor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVWriter;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import jakarta.mail.FetchProfile;

/**
 * Extrator de contatos de e-mail para CSV.
 * Conecta via IMAP a um servidor de e-mail, extrai os remetentes
 * de todas as mensagens da caixa de entrada e gera um arquivo CSV.
 * 
 * Funcionalidades:
 * - Pausar/retomar com teclas de atalho (P/R/S)
 * - Salvamento de estado em tempo real
 * - Recuperação de processo anterior
 */
public class EmailExtractor {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat DATE_FORMAT_DISPLAY = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final int BARRA_TAMANHO = 40;
    private static final String ARQUIVO_ESTADO = "estado_processo.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Variáveis de controle de pausa (volatile para thread-safety)
    private static volatile boolean pausado = false;
    private static volatile boolean encerrar = false;
    private static volatile boolean processando = false;

    // Estado atual do processamento
    private static EstadoProcesso estadoAtual = null;
    private static Map<String, ContatoEmail> contatosMap = new HashMap<>();

    /**
     * Classe para serialização de contatos no JSON.
     */
    private static class ContatoSerializado {
        String email;
        String nome;
        String dataUltimoEmail;

        ContatoSerializado(String email, String nome, Date data) {
            this.email = email;
            this.nome = nome != null ? nome : "";
            this.dataUltimoEmail = data != null ? DATE_FORMAT.format(data) : null;
        }

        Date getDataAsDate() {
            if (dataUltimoEmail == null) return null;
            try {
                return DATE_FORMAT.parse(dataUltimoEmail);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Classe para representar o estado do processo salvo em JSON.
     */
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

        EstadoProcesso() {
            this.contatos = new HashMap<>();
            this.dataInicio = DATE_FORMAT.format(new Date());
        }
    }

    /**
     * Classe interna para armazenar informações do contato em memória.
     */
    private static class ContatoEmail {
        String email;
        String nome;
        Date dataUltimoEmail;

        ContatoEmail(String email, String nome, Date data) {
            this.email = email;
            this.nome = nome != null ? nome : "";
            this.dataUltimoEmail = data;
        }

        void atualizarSeNecessario(String nome, Date data) {
            if (data != null && (this.dataUltimoEmail == null || data.after(this.dataUltimoEmail))) {
                this.dataUltimoEmail = data;
            }
            if ((this.nome == null || this.nome.isEmpty()) && nome != null && !nome.isEmpty()) {
                this.nome = nome;
            }
        }

        ContatoSerializado toSerializado() {
            return new ContatoSerializado(email, nome, dataUltimoEmail);
        }

        static ContatoEmail fromSerializado(ContatoSerializado cs) {
            return new ContatoEmail(cs.email, cs.nome, cs.getDataAsDate());
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Console console = System.console();

        System.out.println("==============================================");
        System.out.println("   EXTRATOR DE CONTATOS DE E-MAIL PARA CSV");
        System.out.println("==============================================");
        System.out.println();

        String usuario;
        String senha;
        String servidor;
        int porta;
        String nomeArquivo;
        int indiceInicial = 0;

        // Verificar se existe estado salvo
        if (existeEstadoSalvo()) {
            EstadoProcesso estadoSalvo = carregarEstado();
            if (estadoSalvo != null) {
                System.out.println("[!] Processo anterior detectado!");
                System.out.println("    Servidor: " + estadoSalvo.servidor);
                int porcentagem = (int) ((estadoSalvo.indiceAtual * 100.0) / estadoSalvo.totalMensagens);
                System.out.println("    Progresso: " + estadoSalvo.indiceAtual + "/" + estadoSalvo.totalMensagens + " (" + porcentagem + "%)");
                System.out.println("    Última atualização: " + estadoSalvo.dataUltimaSalva);
                System.out.println("    Contatos encontrados: " + estadoSalvo.contatos.size());
                System.out.println();

                System.out.print("Deseja continuar de onde parou? (S/N): ");
                String resposta = scanner.nextLine().trim().toUpperCase();

                if (resposta.equals("S") || resposta.equals("SIM")) {
                    // Continuar processo anterior
                    usuario = estadoSalvo.usuario;
                    servidor = estadoSalvo.servidor;
                    porta = estadoSalvo.porta;
                    nomeArquivo = estadoSalvo.arquivoCsv;
                    indiceInicial = estadoSalvo.indiceAtual;
                    estadoAtual = estadoSalvo;

                    // Restaurar contatos do estado salvo
                    for (Map.Entry<String, ContatoSerializado> entry : estadoSalvo.contatos.entrySet()) {
                        contatosMap.put(entry.getKey(), ContatoEmail.fromSerializado(entry.getValue()));
                    }

                    // Solicitar apenas a senha (por segurança, não salvamos)
                    if (console != null) {
                        char[] senhaChars = console.readPassword("Informe a senha para " + usuario + ": ");
                        senha = new String(senhaChars);
                    } else {
                        System.out.print("Informe a senha para " + usuario + ": ");
                        senha = scanner.nextLine();
                    }
                } else {
                    // Reiniciar processo - deletar estado antigo
                    deletarEstadoSalvo();
                    System.out.println("Estado anterior removido. Iniciando novo processo...");
                    System.out.println();

                    // Solicitar novas credenciais
                    String[] credenciais = solicitarCredenciais(scanner, console);
                    usuario = credenciais[0];
                    senha = credenciais[1];
                    servidor = credenciais[2];
                    porta = Integer.parseInt(credenciais[3]);
                    nomeArquivo = credenciais[4];
                }
            } else {
                // Estado corrompido - solicitar novas credenciais
                deletarEstadoSalvo();
                String[] credenciais = solicitarCredenciais(scanner, console);
                usuario = credenciais[0];
                senha = credenciais[1];
                servidor = credenciais[2];
                porta = Integer.parseInt(credenciais[3]);
                nomeArquivo = credenciais[4];
            }
        } else {
            // Não existe estado salvo - solicitar credenciais
            String[] credenciais = solicitarCredenciais(scanner, console);
            usuario = credenciais[0];
            senha = credenciais[1];
            servidor = credenciais[2];
            porta = Integer.parseInt(credenciais[3]);
            nomeArquivo = credenciais[4];
        }

        // Inicializar estado se não existir
        if (estadoAtual == null) {
            estadoAtual = new EstadoProcesso();
            estadoAtual.usuario = usuario;
            estadoAtual.servidor = servidor;
            estadoAtual.porta = porta;
            estadoAtual.arquivoCsv = nomeArquivo;
        }

        System.out.println();
        System.out.println("Conectando ao servidor...");
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  Comandos: [P] Pausar | [R] Retomar | [S] Sair salvando ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();

        // Iniciar thread de monitoramento de teclado
        iniciarMonitoramentoTeclado(scanner);

        try {
            connectAndExtract(usuario, senha, servidor, porta, nomeArquivo, indiceInicial);
        } catch (Exception e) {
            System.err.println();
            System.err.println("ERRO: " + e.getMessage());
            e.printStackTrace();
            
            // Salvar estado em caso de erro
            if (estadoAtual != null && estadoAtual.indiceAtual > 0) {
                System.out.println();
                System.out.println("Salvando estado antes de encerrar...");
                salvarEstado();
            }
        }
    }

    /**
     * Solicita credenciais do usuário.
     */
    private static String[] solicitarCredenciais(Scanner scanner, Console console) {
        System.out.print("Informe o usuário (e-mail): ");
        String usuario = scanner.nextLine().trim();

        String senha;
        if (console != null) {
            char[] senhaChars = console.readPassword("Informe a senha: ");
            senha = new String(senhaChars);
        } else {
            System.out.print("Informe a senha: ");
            senha = scanner.nextLine();
        }

        System.out.print("Informe o servidor IMAP (ex: imap.gmail.com): ");
        String servidor = scanner.nextLine().trim();

        System.out.print("Informe a porta IMAP (ex: 993): ");
        String portaStr = scanner.nextLine().trim();
        int porta;
        try {
            porta = Integer.parseInt(portaStr);
        } catch (NumberFormatException e) {
            System.err.println("Porta inválida! Usando porta padrão 993.");
            porta = 993;
        }

        System.out.print("Informe o nome do arquivo CSV para salvar (padrão: contatos.csv): ");
        String nomeArquivo = scanner.nextLine().trim();
        if (nomeArquivo.isEmpty()) {
            nomeArquivo = "contatos.csv";
            System.out.println("Usando nome padrão: contatos.csv");
        } else if (!nomeArquivo.endsWith(".csv")) {
            nomeArquivo += ".csv";
        }

        return new String[]{usuario, senha, servidor, String.valueOf(porta), nomeArquivo};
    }

    /**
     * Inicia thread para monitorar entrada do teclado.
     */
    private static void iniciarMonitoramentoTeclado(Scanner scanner) {
        Thread monitorThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                while (!encerrar) {
                    if (reader.ready()) {
                        String input = reader.readLine();
                        if (input != null) {
                            input = input.trim().toUpperCase();
                            switch (input) {
                                case "P":
                                    if (processando && !pausado) {
                                        pausado = true;
                                        System.out.println();
                                        System.out.println();
                                        System.out.println(">> Processamento PAUSADO. Pressione [R] para retomar ou [S] para sair salvando.");
                                        salvarEstado();
                                    }
                                    break;
                                case "R":
                                    if (pausado) {
                                        pausado = false;
                                        System.out.println(">> Retomando processamento...");
                                        System.out.println();
                                    }
                                    break;
                                case "S":
                                    if (processando) {
                                        encerrar = true;
                                        pausado = false;
                                        System.out.println();
                                        System.out.println(">> Encerrando e salvando estado...");
                                    }
                                    break;
                            }
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                // Thread encerrada
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Conecta ao servidor IMAP e extrai os remetentes de todas as mensagens.
     */
    private static void connectAndExtract(String usuario, String senha, String servidor, 
            int porta, String nomeArquivo, int indiceInicial) throws MessagingException, IOException {

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", servidor);
        props.put("mail.imaps.port", String.valueOf(porta));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");

        try {
            store.connect(servidor, porta, usuario, senha);
            System.out.println("Conexão estabelecida com sucesso!");

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int totalMensagens = inbox.getMessageCount();
            estadoAtual.totalMensagens = totalMensagens;
            
            System.out.println("Total de mensagens na caixa de entrada: " + totalMensagens);

            if (indiceInicial > 0) {
                System.out.println("Retomando processamento a partir da mensagem " + (indiceInicial + 1) + "...");
            } else {
                System.out.println("Processando mensagens (do mais antigo para o mais recente)...");
            }

            // Obter todas as mensagens
            Message[] mensagens = inbox.getMessages();

            // Pré-carregar informações das mensagens para evitar lazy loading lento
            System.out.print("Carregando informações das mensagens do servidor... ");
            System.out.flush();
            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            inbox.fetch(mensagens, fetchProfile);
            System.out.println("OK!");

            // Ordenar mensagens por data (mais antiga primeiro)
            // Isso garante que ao retomar, os e-mails novos ainda serão processados
            System.out.print("Ordenando mensagens por data... ");
            System.out.flush();
            Arrays.sort(mensagens, (m1, m2) -> {
                try {
                    Date d1 = m1.getSentDate();
                    Date d2 = m2.getSentDate();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return -1;
                    if (d2 == null) return 1;
                    return d1.compareTo(d2);
                } catch (MessagingException e) {
                    return 0;
                }
            });
            System.out.println("OK!");
            System.out.println();

            processando = true;
            int processadas = indiceInicial;
            int ultimoSalvamento = indiceInicial;
            int ultimoProgresso = indiceInicial;
            
            // Mostrar barra inicial
            printProgressBar(processadas, totalMensagens);

            for (int i = indiceInicial; i < mensagens.length; i++) {
                // Verificar se deve encerrar
                if (encerrar) {
                    estadoAtual.indiceAtual = i;
                    salvarEstado();
                    System.out.println();
                    System.out.println();
                    System.out.println("==============================================");
                    System.out.println("Processo pausado e estado salvo!");
                    System.out.println("Progresso: " + i + "/" + totalMensagens);
                    System.out.println("Contatos encontrados até agora: " + contatosMap.size());
                    System.out.println("Execute novamente para continuar de onde parou.");
                    System.out.println("==============================================");
                    inbox.close(false);
                    return;
                }

                // Verificar se está pausado
                while (pausado && !encerrar) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (encerrar) continue;

                try {
                    extractSenderInfo(mensagens[i], contatosMap);
                    processadas = i + 1;
                    estadoAtual.indiceAtual = processadas;

                    // Atualizar barra de progresso a cada mensagem
                    printProgressBar(processadas, totalMensagens);
                    
                    // Também mostrar progresso em texto a cada 100 mensagens (fallback)
                    if (processadas - ultimoProgresso >= 100) {
                        System.out.println(); // Nova linha para não sobrescrever
                        printProgressoSimples(processadas, totalMensagens);
                        ultimoProgresso = processadas;
                    }

                    // Salvar estado a cada 50 mensagens
                    if (processadas - ultimoSalvamento >= 50) {
                        salvarEstado();
                        ultimoSalvamento = processadas;
                    }

                } catch (MessagingException e) {
                    processadas = i + 1;
                    estadoAtual.indiceAtual = processadas;
                    printProgressBar(processadas, totalMensagens);
                }
            }

            processando = false;
            encerrar = true;

            System.out.println();
            System.out.println();
            System.out.println("Processamento concluído! " + processadas + " mensagens processadas.");

            inbox.close(false);

            // Exportar para CSV
            if (!contatosMap.isEmpty()) {
                exportToCsv(contatosMap, nomeArquivo);
                System.out.println();
                System.out.println("==============================================");
                System.out.println("Extração concluída com sucesso!");
                System.out.println("Total de contatos únicos: " + contatosMap.size());
                System.out.println("Arquivo salvo em: " + nomeArquivo);
                System.out.println("==============================================");

                // Deletar arquivo de estado após conclusão bem-sucedida
                deletarEstadoSalvo();
            } else {
                System.out.println("Nenhum e-mail encontrado na caixa de entrada.");
            }

        } finally {
            store.close();
        }
    }

    /**
     * Verifica se existe arquivo de estado salvo.
     */
    private static boolean existeEstadoSalvo() {
        return Files.exists(Paths.get(ARQUIVO_ESTADO));
    }

    /**
     * Carrega o estado do arquivo JSON.
     */
    private static EstadoProcesso carregarEstado() {
        try {
            String json = new String(Files.readAllBytes(Paths.get(ARQUIVO_ESTADO)));
            return GSON.fromJson(json, EstadoProcesso.class);
        } catch (Exception e) {
            System.err.println("Erro ao carregar estado: " + e.getMessage());
            return null;
        }
    }

    /**
     * Salva o estado atual no arquivo JSON.
     */
    private static synchronized void salvarEstado() {
        if (estadoAtual == null) return;

        try {
            estadoAtual.dataUltimaSalva = DATE_FORMAT.format(new Date());

            // Converter contatos para formato serializável
            estadoAtual.contatos = new HashMap<>();
            for (Map.Entry<String, ContatoEmail> entry : contatosMap.entrySet()) {
                estadoAtual.contatos.put(entry.getKey(), entry.getValue().toSerializado());
            }

            String json = GSON.toJson(estadoAtual);
            Files.write(Paths.get(ARQUIVO_ESTADO), json.getBytes());
        } catch (IOException e) {
            System.err.println("Erro ao salvar estado: " + e.getMessage());
        }
    }

    /**
     * Deleta o arquivo de estado salvo.
     */
    private static void deletarEstadoSalvo() {
        try {
            Files.deleteIfExists(Paths.get(ARQUIVO_ESTADO));
        } catch (IOException e) {
            System.err.println("Erro ao deletar arquivo de estado: " + e.getMessage());
        }
    }

    /**
     * Imprime uma barra de progresso no terminal.
     */
    private static void printProgressBar(int atual, int total) {
        if (total == 0) return;

        int porcentagem = (int) ((atual * 100.0) / total);
        int preenchido = (int) ((atual * BARRA_TAMANHO) / total);
        int vazio = BARRA_TAMANHO - preenchido;

        StringBuilder barra = new StringBuilder();
        barra.append("[");

        for (int i = 0; i < preenchido; i++) {
            barra.append("=");
        }

        for (int i = 0; i < vazio; i++) {
            barra.append(" ");
        }

        barra.append("] ");
        barra.append(String.format("%3d%%", porcentagem));
        barra.append(String.format(" (%d/%d)", atual, total));
        barra.append(" | Contatos: " + contatosMap.size());
        
        // Limpar a linha e imprimir a barra
        System.out.print("\r" + barra.toString() + "          ");
        System.out.flush();
    }
    
    /**
     * Imprime progresso simples (para quando a barra não funciona bem).
     */
    private static void printProgressoSimples(int atual, int total) {
        int porcentagem = (int) ((atual * 100.0) / total);
        System.out.println("Processadas: " + atual + "/" + total + " (" + porcentagem + "%) | Contatos: " + contatosMap.size());
    }

    /**
     * Extrai informações do remetente de uma mensagem.
     */
    private static void extractSenderInfo(Message mensagem, Map<String, ContatoEmail> contatos)
            throws MessagingException {

        Address[] remetentes = mensagem.getFrom();
        if (remetentes == null || remetentes.length == 0) {
            return;
        }

        Date dataEnvio = mensagem.getSentDate();
        if (dataEnvio == null) {
            dataEnvio = mensagem.getReceivedDate();
        }

        for (Address address : remetentes) {
            if (address instanceof InternetAddress) {
                InternetAddress internetAddress = (InternetAddress) address;
                String email = internetAddress.getAddress();
                String nome = internetAddress.getPersonal();

                if (email != null && !email.isEmpty()) {
                    email = email.toLowerCase().trim();

                    if (contatos.containsKey(email)) {
                        contatos.get(email).atualizarSeNecessario(nome, dataEnvio);
                    } else {
                        contatos.put(email, new ContatoEmail(email, nome, dataEnvio));
                    }
                }
            }
        }
    }

    /**
     * Exporta os contatos para um arquivo CSV.
     */
    private static void exportToCsv(Map<String, ContatoEmail> contatos, String nomeArquivo)
            throws IOException {

        try (CSVWriter writer = new CSVWriter(new FileWriter(nomeArquivo),
                ';',  // Separador ponto e vírgula
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            String[] cabecalho = {"email_remetente", "nome_remetente", "data_ultimo_email"};
            writer.writeNext(cabecalho);

            List<ContatoEmail> listaContatos = new ArrayList<>(contatos.values());
            listaContatos.sort(Comparator.comparing(c -> c.email));

            for (ContatoEmail contato : listaContatos) {
                String dataFormatada = contato.dataUltimoEmail != null
                        ? DATE_FORMAT.format(contato.dataUltimoEmail)
                        : "";

                String[] linha = {
                        contato.email,
                        contato.nome,
                        dataFormatada
                };
                writer.writeNext(linha);
            }
        }

        System.out.println("Arquivo CSV gerado com sucesso: " + nomeArquivo);
    }
}

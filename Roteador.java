import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Roteador {
    private final String ip;
    private final Map<String, Rota> tabelaRoteamento;
    private final List<String> vizinhos;
    private static final int PORTA = 9000;

    public Roteador(String ip) throws IOException {
        this.ip = ip;
        this.tabelaRoteamento = new HashMap<>();
        this.vizinhos = Files.readAllLines(Paths.get("roteadores.txt"));
        inicializarTabela();
    }

    // Inicializa a tabela de roteamento com os vizinhos e métrica de 1
    private void inicializarTabela() {
        vizinhos.forEach(vizinho -> tabelaRoteamento.put(vizinho, new Rota(1, vizinho)));
    }

    // Envia a tabela de roteamento para todos os vizinhos
    public void enviarTabela() throws IOException {
        StringBuilder mensagem = new StringBuilder();
        tabelaRoteamento.forEach((destino, rota) -> 
            mensagem.append("@").append(destino).append("-").append(rota.metrica)
        );

        for (String vizinho : vizinhos) {
            enviarMensagem(mensagem.toString(), vizinho);
        }
    }

    // Método genérico para enviar mensagens para um roteador específico
    private void enviarMensagem(String mensagem, String ipDestino) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        byte[] buffer = mensagem.getBytes();
        InetAddress address = InetAddress.getByName(ipDestino);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, PORTA);
        socket.send(packet);
        socket.close();
    }

    // Recebe tabelas de roteamento dos vizinhos e atualiza a tabela local
    public void receberTabela() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORTA);
        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String mensagemRecebida = new String(packet.getData(), 0, packet.getLength());
            String ipVizinho = packet.getAddress().getHostAddress();

            atualizarTabela(ipVizinho, mensagemRecebida);
        }
    }

    // Atualiza a tabela de roteamento de acordo com a tabela recebida de um vizinho
    private void atualizarTabela(String ipVizinho, String tabelaRecebida) {
        for (String rotaStr : tabelaRecebida.split("@")) {
            if (!rotaStr.isEmpty()) {
                String[] partes = rotaStr.split("-");
                String destino = partes[0];
                int metrica = Integer.parseInt(partes[1]) + 1; // Incrementa a métrica

                Rota rota = tabelaRoteamento.get(destino);
                if (rota == null || metrica < rota.metrica) {
                    tabelaRoteamento.put(destino, new Rota(metrica, ipVizinho));
                    System.out.println("Atualização: rota para " + destino + " via " + ipVizinho + " com métrica " + metrica);
                }

                // Atualiza o último contato
                tabelaRoteamento.get(destino).atualizarUltimoContato();
            }
        }
    }

    // Envia uma mensagem de texto para um destino específico
    public void enviarMensagemTexto(String ipOrigem, String ipDestino, String mensagem) throws IOException {
        String texto = "!" + ipOrigem + ";" + ipDestino + ";" + mensagem;
        Rota rota = tabelaRoteamento.get(ipDestino);

        if (rota != null) {
            enviarMensagem(texto, rota.saida);
        } else {
            System.out.println("Rota para " + ipDestino + " não encontrada!");
        }
    }

    // Monitora os vizinhos e remove rotas de roteadores que ficaram inativos por mais de 35 segundos
    private void monitorarVizinhos() {
        new Thread(() -> {
            while (true) {
                long agora = System.currentTimeMillis();
                tabelaRoteamento.entrySet().removeIf(entry -> {
                    Rota rota = entry.getValue();
                    long tempoUltimoContato = agora - rota.ultimoContato;

                    if (tempoUltimoContato > 35000) { // 35 segundos de timeout
                        System.out.println("Rota para " + entry.getKey() + " removida por inatividade.");
                        return true; // Remove a rota
                    }
                    return false;
                });

                try {
                    Thread.sleep(5000); // Verifica a cada 5 segundos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    // Método principal para iniciar o roteador
    public static void main(String[] args) throws IOException {
        Roteador roteador = new Roteador("192.168.1.1");
    
        // Thread para monitorar timeout de vizinhos
        new Thread(roteador::monitorarVizinhos).start();
    
        // Thread para enviar tabelas periodicamente
        new Thread(() -> {
            try {
                while (true) {
                    roteador.enviarTabela();
                    Thread.sleep(15000); // Envia a cada 15 segundos
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    
        // Thread para receber tabelas e processar atualizações
        new Thread(() -> {
            try {
                roteador.receberTabela();  // Recebe tabelas de roteamento
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    
        // Exemplo de envio de uma mensagem de texto
        roteador.enviarMensagemTexto("10.132.241.152", "10.140.99.175", "Oi tudo bem?");
    }
}
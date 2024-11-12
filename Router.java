import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Router {
    private String ipAddress;
    private Map<String, Route> routingTable;
    private List<String> neighbors;
    private DatagramSocket socket;
    private MessageSender messageSender;

    public Router(String ipAddress, String configFilePath) throws IOException {
        this.ipAddress = ipAddress;
        this.routingTable = new ConcurrentHashMap<>();
        this.neighbors = loadNeighbors(configFilePath);
        this.socket = new DatagramSocket(9000);
        this.messageSender = new MessageSender(socket, ipAddress);
    
        for (String neighbor : neighbors) {
            routingTable.put(neighbor, new Route(neighbor, 1, neighbor));
        }
    }

    private List<String> loadNeighbors(String configFilePath) throws IOException {
        List<String> neighbors = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(configFilePath));
        String line;
        while ((line = reader.readLine()) != null) {
            neighbors.add(line.trim());
        }
        reader.close();
        return neighbors;
    }

    private void updateNeighborsFile(String newRouterIP) {
        if (!neighbors.contains(newRouterIP)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("roteadores.txt", true))) {
                writer.write(newRouterIP);
                writer.newLine();
                neighbors.add(newRouterIP); // Adiciona o novo IP à lista de vizinhos
                System.out.println("roteadores.txt updated with new router IP: " + newRouterIP);
            } catch (IOException e) {
                System.out.println("Error updating roteadores.txt: " + e.getMessage());
            }
        }
    }    

    public void start() {
        // Anuncia o roteador para todos os vizinhos ao iniciar
        messageSender.sendRouterAnnouncement(neighbors.toArray(new String[0]));
    
        // Inicia o envio periódico da tabela de roteamento e a verificação de rotas inativas
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(() -> messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress), 0, 15, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkInactiveRoutes, 0, 1, TimeUnit.SECONDS);
    
        new Thread(new MessageReceiver(socket, this)).start();
    }
    
    public void updateRoutingTable(String message, String senderIP) {
        String[] routes = message.split("@");
        boolean tableUpdated = false;
        Set<String> receivedDestinations = new HashSet<>(); // Para rastrear os IPs recebidos
    
        // Processa cada rota recebida
        for (String routeInfo : routes) {
            if (routeInfo.isEmpty()) continue;
    
            String[] parts = routeInfo.split("-");
            String destinationIP = parts[0];
            int receivedMetric = Integer.parseInt(parts[1]) + 1; // Incrementa a métrica
    
            // Adiciona o destino ao conjunto de IPs recebidos
            receivedDestinations.add(destinationIP);
    
            Route currentRoute = routingTable.get(destinationIP);
    
            // Se a rota não existe ou a nova métrica é menor, atualize a rota
            if (currentRoute == null || currentRoute.metric > receivedMetric) {
                routingTable.put(destinationIP, new Route(destinationIP, receivedMetric, senderIP));
                tableUpdated = true;
            }
        }
    
        // Atualize o tempo da rota do roteador que enviou a mensagem
        if (routingTable.containsKey(senderIP)) {
            routingTable.get(senderIP).updateTimestamp();
        }
    
        // Remove rotas que não foram divulgadas nesta atualização
        Iterator<Map.Entry<String, Route>> iterator = routingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Route> entry = iterator.next();
            String destinationIP = entry.getKey();
    
            // Se o IP de destino não está nos IPs recebidos e não é um vizinho direto, remova a rota
            if (!receivedDestinations.contains(destinationIP) && !neighbors.contains(destinationIP)) {
                iterator.remove();
                System.out.println("Route removed due to missing announcement: " + destinationIP);
                tableUpdated = true;
            }
        }
    
        // Envia a tabela atualizada para os vizinhos se houver mudança
        if (tableUpdated) {
            System.out.println("Routing table updated. Sending new table to neighbors.");
            messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress);
        }
    } 

    public void addNewRouter(String newRouterIP) {
        if (!routingTable.containsKey(newRouterIP)) {
            routingTable.put(newRouterIP, new Route(newRouterIP, 1, newRouterIP));
            System.out.println("Added new router with IP: " + newRouterIP + " to routing table.");
            updateNeighborsFile(newRouterIP);
            sendRoutingUpdateToNeighbors(); // Propaga a tabela atualizada
        }
    }
    
    void sendRoutingUpdateToNeighbors() {
        messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress);
    }

    public void processUserMessage(String message) {
        // Verifica se a mensagem está no formato correto
        if (!message.startsWith("!")) {
            System.out.println("Formato de mensagem inválido. Deve começar com '!'");
            return;
        }
    
        // Divide a mensagem em suas partes
        String[] parts = message.substring(1).split(";", 3); // Limita o split para evitar problemas com ';' na mensagem
        if (parts.length < 3) {
            System.out.println("Formato de mensagem inválido. Deve seguir o formato !<ip_origem>;<ip_destino>;<mensagem>");
            return;
        }
    
        String originIP = parts[0];
        String destinationIP = parts[1];
        String text = parts[2];
    
        // Envia a mensagem usando a tabela de roteamento
        sendTextMessage(originIP, destinationIP, text);
    }


    public void handleTextMessage(String message) {
        String[] parts = message.substring(1).split(";", 3); // Limita o split para evitar problemas com ';' na mensagem
        String originIP = parts[0];
        String destinationIP = parts[1];
        String text = parts[2];
    
        if (destinationIP.equals(ipAddress)) {
            System.out.println("Mensagem recebida:");
            System.out.println("Origem: " + originIP);
            System.out.println("Destino: " + destinationIP);
            System.out.println("Texto: " + text);
            System.out.println("A mensagem chegou ao destino.");
        } else {
            System.out.println("Encaminhando mensagem:");
            System.out.println("Origem: " + originIP);
            System.out.println("Destino: " + destinationIP);
            System.out.println("Texto: " + text);
            Route route = routingTable.get(destinationIP);
            if (route != null) {
                // Encaminha a mensagem para o próximo salto com os parâmetros na ordem correta
                messageSender.sendTextMessage(route.outputIP, originIP, destinationIP, text);
            } else {
                System.out.println("Nenhuma rota encontrada para " + destinationIP + ". Não foi possível encaminhar a mensagem.");
            }
        }
    }

    private void checkInactiveRoutes() {
        Iterator<Map.Entry<String, Route>> iterator = routingTable.entrySet().iterator();
    
        while (iterator.hasNext()) {
            Map.Entry<String, Route> entry = iterator.next();
            String destinationIP = entry.getKey();
            Route route = entry.getValue();
    
            // Calcula o tempo decorrido desde a última atualização
            long timeElapsed = route.timeElapsed();
            long timeRemaining = 35 - timeElapsed;
    
            // Verifica se o tempo de inatividade excedeu 35 segundos
            if (timeElapsed > 35) {
                System.out.println("Route to " + destinationIP + " removed due to inactivity of next hop: " + route.outputIP);
                iterator.remove();
            }
        }
    }
    
    public void sendTextMessage(String originIP, String destinationIP, String text) {
        Route route = routingTable.get(destinationIP);
        if (route != null) {
            // Envia a mensagem para o próximo salto
            messageSender.sendTextMessage(route.outputIP, originIP, destinationIP, text);
            System.out.println("Mensagem enviada de " + originIP + " para " + destinationIP + " via " + route.outputIP);
        } else {
            System.out.println("Nenhuma rota encontrada para " + destinationIP);
        }
    }
    
    public static void main(String[] args) {
        try {
            // Verifica se o IP foi fornecido como argumento
            if (args.length < 1) {
                System.out.println("Uso: java Router <seu_ip>");
                return;
            }
    
            String ownIP = args[0];
            Router router = new Router(ownIP, "roteadores.txt");
            router.start();
    
            // Inicia a thread para lidar com a entrada do usuário
            new Thread(new UserInputHandler(router)).start();
    
        } catch (IOException e) {
            System.out.println("Failed to initialize router.");
        }
    }
}

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
                neighbors.add(newRouterIP);
                System.out.println("roteadores.txt updated como IP do novo roteador: " + newRouterIP + "\n");
            } catch (IOException e) {
                System.out.println("Erro updating roteadores.txt: " + e.getMessage() + "\n");
            }
        }
    }    

    public void start() {
        messageSender.sendRouterAnnouncement(neighbors.toArray(new String[0]));
    
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(() -> messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress), 0, 15, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkInactiveRoutes, 0, 1, TimeUnit.SECONDS);
    
        new Thread(new MessageReceiver(socket, this)).start();
    }
    
    public void updateRoutingTable(String message, String senderIP) {

        System.out.println("Tabela recebida do vizinho " + senderIP + "\n");

        String[] routes = message.split("@");
        boolean tableUpdated = false;
        Set<String> receivedDestinations = new HashSet<>(); 
    
        for (String routeInfo : routes) {
            if (routeInfo.isEmpty()) continue;
    
            String[] parts = routeInfo.split("-");
            String destinationIP = parts[0];
            int receivedMetric = Integer.parseInt(parts[1]) + 1;
    
            receivedDestinations.add(destinationIP);
    
            Route currentRoute = routingTable.get(destinationIP);
    
            if (currentRoute == null || currentRoute.metric > receivedMetric) {
                routingTable.put(destinationIP, new Route(destinationIP, receivedMetric, senderIP));
                tableUpdated = true;
            }
        }
    
        if (routingTable.containsKey(senderIP)) {
            routingTable.get(senderIP).updateTimestamp();
        }
    
        Iterator<Map.Entry<String, Route>> iterator = routingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Route> entry = iterator.next();
            String destinationIP = entry.getKey();
    
            if (!receivedDestinations.contains(destinationIP) && !neighbors.contains(destinationIP)) {
                iterator.remove();
                System.out.println("Rota removida por falta de anuncio: " + destinationIP + "\n");
                tableUpdated = true;
            }
        }
    
        if (tableUpdated) {
            System.out.println("Tabela de rota atualizada. Mandando nova tabela para os vizinhos. \n");
            messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress);
        }
    } 

    public void addNewRouter(String newRouterIP) {
        if (!routingTable.containsKey(newRouterIP)) {
            routingTable.put(newRouterIP, new Route(newRouterIP, 1, newRouterIP));
            System.out.println("Adicionando nova rota do IP: " + newRouterIP + " para a tabela de rotas. \n");
            updateNeighborsFile(newRouterIP);
            sendRoutingUpdateToNeighbors(); 
        }
    }
    
    void sendRoutingUpdateToNeighbors() {
        messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]), ipAddress);
    }

    public void processUserMessage(String message) {
        if (!message.startsWith("!")) {
            System.out.println("Formato de mensagem invalido. Deve comecar com '!' \n");
            return;
        }
    
        String[] parts = message.substring(1).split(";", 3);
        if (parts.length < 3) {
            System.out.println("Formato de mensagem invalido. Deve seguir o formato !<ip_origem>;<ip_destino>;<mensagem> \n");
            return;
        }
    
        String originIP = parts[0];
        String destinationIP = parts[1];
        String text = parts[2];
    
        sendTextMessage(originIP, destinationIP, text);
    }


    public void handleTextMessage(String message) {
        String[] parts = message.substring(1).split(";", 3); // Limita o split para evitar problemas com ';' na mensagem
        String originIP = parts[0];
        String destinationIP = parts[1];
        String text = parts[2];
    
        if (destinationIP.equals(ipAddress)) {
            System.out.println("Mensagem recebida: \n");
            System.out.println("Origem: " + originIP + "\n");
            System.out.println("Destino: " + destinationIP + "\n");
            System.out.println("Texto: " + text + "\n");
            System.out.println("A mensagem chegou ao destino. \n");
        } else {
            System.out.println("Encaminhando mensagem:  \n");
            System.out.println("Origem: " + originIP + "\n");
            System.out.println("Destino: " + destinationIP + "\n");
            System.out.println("Texto: " + text + "\n");
            Route route = routingTable.get(destinationIP);
            if (route != null) {
                messageSender.sendTextMessage(route.outputIP, originIP, destinationIP, text);
            } else {
                System.out.println("Nenhuma rota encontrada para " + destinationIP + ". Nao foi possível encaminhar a mensagem. \n");
            }
        }
    }

    private void checkInactiveRoutes() {
        Iterator<Map.Entry<String, Route>> iterator = routingTable.entrySet().iterator();
    
        while (iterator.hasNext()) {
            Map.Entry<String, Route> entry = iterator.next();
            String destinationIP = entry.getKey();
            Route route = entry.getValue();
    
            long timeElapsed = route.timeElapsed();
            long timeRemaining = 35 - timeElapsed;
    
            if (timeElapsed > 35) {
                System.out.println("Rota para " + destinationIP + " removida devido inatividade: " + route.outputIP);
                iterator.remove();
            }
        }
    }
    
    public void sendTextMessage(String originIP, String destinationIP, String text) {
        Route route = routingTable.get(destinationIP);
        if (route != null) {
            messageSender.sendTextMessage(route.outputIP, originIP, destinationIP, text);
            System.out.println("Mensagem enviada de " + originIP + " para " + destinationIP + " via " + route.outputIP + "\n");
        } else {
            System.out.println("Nenhuma rota encontrada para " + destinationIP + ". Nao foi possível enviar a mensagem. \n");
        }
    }
    
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Uso: java Router <seu_ip>" + "\n");
                return;
            }
    
            String ownIP = args[0];
            Router router = new Router(ownIP, "roteadores.txt");
            router.start();
    
            new Thread(new UserInputHandler(router)).start();
    
        } catch (IOException e) {
            System.out.println("Falha ao inicializar o roteador. \n");
        }
    }
}

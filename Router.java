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

    public void start() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(() -> messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0])), 0, 15, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkInactiveRoutes, 0, 1, TimeUnit.SECONDS);

        new Thread(new MessageReceiver(socket, this)).start();
    }

    public void updateRoutingTable(String message, String senderIP) {
        String[] routes = message.split("@");
        boolean tableUpdated = false;
        
        for (String routeInfo : routes) {
            if (routeInfo.isEmpty()) continue;
            
            String[] parts = routeInfo.split("-");
            String destinationIP = parts[0];
            int receivedMetric = Integer.parseInt(parts[1]) + 1;

            Route currentRoute = routingTable.get(destinationIP);
            
            if (currentRoute == null || currentRoute.metric > receivedMetric) {
                routingTable.put(destinationIP, new Route(destinationIP, receivedMetric, senderIP));
                tableUpdated = true;
            }
        }

        if (tableUpdated) {
            messageSender.sendRoutingTable(routingTable, neighbors.toArray(new String[0]));
        }
    }

    public void addNewRouter(String newRouterIP) {
        routingTable.put(newRouterIP, new Route(newRouterIP, 1, newRouterIP));
        System.out.println("New router added: " + newRouterIP);
    }

    public void handleTextMessage(String message) {
        String[] parts = message.substring(1).split(";");
        String originIP = parts[0];
        String destinationIP = parts[1];
        String text = parts[2];

        if (destinationIP.equals(ipAddress)) {
            System.out.println("Message received from " + originIP + ": " + text);
        } else {
            System.out.println("Forwarding message from " + originIP + " to " + destinationIP);
            messageSender.sendTextMessage(routingTable.get(destinationIP).outputIP, text, destinationIP);
        }
    }

    private void checkInactiveRoutes() {
        Iterator<Map.Entry<String, Route>> iterator = routingTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Route> entry = iterator.next();
            if (!neighbors.contains(entry.getKey()) && entry.getValue().timeElapsed() > 35) {
                iterator.remove();
                System.out.println("Route removed: " + entry.getKey());
            }
        }
    }

    public static void main(String[] args) {
        try {
            Router router = new Router("192.168.1.1", "roteadores.txt");
            router.start();

            // Exemplo de envio de mensagem de texto
            router.messageSender.sendTextMessage("192.168.1.2", "Oi tudo bem?", "192.168.1.3");
        } catch (IOException e) {
            System.out.println("Failed to initialize router.");
        }
    }
}

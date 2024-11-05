import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.Map;

class MessageSender {
    private DatagramSocket socket;
    private String ipAddress;

    public MessageSender(DatagramSocket socket, String ipAddress) {
        this.socket = socket;
        this.ipAddress = ipAddress;
    }

    // Envia uma mensagem de tabela de roteamento para todos os vizinhos
    public void sendRoutingTable(Map<String, Route> routingTable, String[] neighbors) {
        StringBuilder message = new StringBuilder();
        for (Route route : routingTable.values()) {
            message.append("@").append(route.destinationIP).append("-").append(route.metric);
        }
        
        for (String neighbor : neighbors) {
            sendMessage(message.toString(), neighbor);
        }
    }

    // Envia uma mensagem de texto para o destino específico
    public void sendTextMessage(String destinationIP, String text, String targetIP) {
        String message = "!" + ipAddress + ";" + targetIP + ";" + text;
        sendMessage(message, destinationIP);
    }

    private void sendMessage(String message, String destinationIP) {
        try {
            byte[] buffer = message.getBytes();
            InetAddress address = InetAddress.getByName(destinationIP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 9000);
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("Failed to send message to " + destinationIP);
        }
    }
}
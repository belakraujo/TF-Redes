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

    public void sendRoutingTable(Map<String, Route> routingTable, String[] neighbors, String ownIpAddress) {
        StringBuilder message = new StringBuilder();
    
        // Construindo a mensagem com o formato @IP-Destino-Métrica para cada rota, excluindo o próprio IP
        for (Route route : routingTable.values()) {
            if (!route.destinationIP.equals(ownIpAddress)) { // Exclui o próprio IP do roteador
                message.append("@").append(route.destinationIP).append("-").append(route.metric);
            }
        }
    
        // Log da mensagem para verificar o formato antes do envio
        System.out.println("Routing table message to send (excluding own IP): " + message.toString());
    
        // Envia a mensagem completa para cada vizinho
        for (String neighbor : neighbors) {
            sendMessage(message.toString(), neighbor);
        }
    }
    
    

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
            System.out.println("Enviado o Pacote");
        } catch (IOException e) {
            System.out.println("Failed to send message to " + destinationIP);
        }
    }
}
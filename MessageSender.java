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
    
        for (Route route : routingTable.values()) {
            if (!route.destinationIP.equals(ownIpAddress)) { 
                message.append("@").append(route.destinationIP).append("-").append(route.metric);
            }
        }
    
        System.out.println("Tabela sendo enviada: " + message.toString() + "\n");
    
        for (String neighbor : neighbors) {
            sendMessage(message.toString(), neighbor);
        }
    }

    public void sendTextMessage(String nextHopIP, String originIP, String destinationIP, String text) {
        String message = "!" + originIP + ";" + destinationIP + ";" + text;
        sendMessage(message, nextHopIP);
    }
       

    private void sendMessage(String message, String destinationIP) {
        try {
            byte[] buffer = message.getBytes();
            InetAddress address = InetAddress.getByName(destinationIP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 9000);
            socket.send(packet);
            System.out.println("Enviado o Pacote \n");
        } catch (IOException e) {
            System.out.println("Falha ao enviar a mensagem para " + destinationIP + " \n");
        }
    }

    public void sendRouterAnnouncement(String[] neighbors) {
        String announcementMessage = "*" + ipAddress; 
    
        for (String neighbor : neighbors) {
            sendMessage(announcementMessage, neighbor);
        }
    
        System.out.println("Anuncio da rota enviada pra os vizinhos: " + announcementMessage + "\n");
    }    
}
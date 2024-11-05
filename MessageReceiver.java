import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.util.Map;

class MessageReceiver implements Runnable {
    private DatagramSocket socket;
    private Router router;

    public MessageReceiver(DatagramSocket socket, Router router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIP = packet.getAddress().getHostAddress();
                handleReceivedMessage(message, senderIP);
            } catch (IOException e) {
                System.out.println("Failed to receive message.");
            }
        }
    }

    private void handleReceivedMessage(String message, String senderIP) {
        if (message.startsWith("@")) {
            router.updateRoutingTable(message, senderIP);
        } else if (message.startsWith("*")) {
            router.addNewRouter(senderIP);
        } else if (message.startsWith("!")) {
            router.handleTextMessage(message);
        }
    }
}
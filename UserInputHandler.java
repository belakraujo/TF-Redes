import java.util.Scanner;

public class UserInputHandler implements Runnable {
    private Router router;

    public UserInputHandler(Router router) {
        this.router = router;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Digite a mensagem no formato !<ip_origem>;<ip_destino>;<mensagem> (ou 'sair' para encerrar): \n");
            String inputMessage = scanner.nextLine();
            if (inputMessage.equalsIgnoreCase("sair")) {
                System.out.println("Encerrando o roteador. \n");
                System.exit(0);
            }

            router.processUserMessage(inputMessage);
        }
    }
}
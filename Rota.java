public class Rota {
    int metrica;
    String saida;
    long ultimoContato;

    public Rota(int metrica, String saida) {
        this.metrica = metrica;
        this.saida = saida;
        this.ultimoContato = System.currentTimeMillis(); // Marca o tempo atual
    }

    public void atualizarUltimoContato() {
        this.ultimoContato = System.currentTimeMillis();
    }
}
class Route {
    String destinationIP;
    int metric;
    String outputIP;
    private long lastUpdated;

    public Route(String destinationIP, int metric, String outputIP) {
        this.destinationIP = destinationIP;
        this.metric = metric;
        this.outputIP = outputIP;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public long timeElapsed() {
        return (System.currentTimeMillis() - lastUpdated) / 1000;
    }

    @Override
    public String toString() {
        return "Route [destinationIP=" + destinationIP + ", metric=" + metric + ", outputIP=" + outputIP + "]";
    }
}

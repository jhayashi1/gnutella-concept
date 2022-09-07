public class Servent {
    private String ip;
    private int port;
    private long lastResponse;

    public Servent(String ip, int port, long lastResponse) {
        this.ip = ip;
        this.port = port;
        this.lastResponse = lastResponse;
    }

    public String getIP() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public long getLastResponse() {
        return lastResponse;
    }

    public void setIP(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setLastResponse(long lastResponse) {
        this.lastResponse = lastResponse;
    }
}

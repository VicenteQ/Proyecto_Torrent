package common;

import java.io.Serializable;

public class NodeInfo implements Serializable {
    private int id;
    private String ip;
    private int port;

    public NodeInfo(int id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public int getId() { return id; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    
    @Override
    public String toString() {
        return "Nodo " + id + " [" + ip + ":" + port + "]";
    }
}
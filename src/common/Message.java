package common;

import java.io.Serializable;
import java.util.Set;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;         
    private String fileName;     
    private String peerAddress;  
    private Set<String> peerList; 
    private byte[] content;      // NUEVO: Para transportar los bytes del archivo

    public Message(String type, String fileName, String peerAddress, Set<String> peerList, byte[] content) {
        this.type = type;
        this.fileName = fileName;
        this.peerAddress = peerAddress;
        this.peerList = peerList;
        this.content = content;
    }

    public String getType() { return type; }
    public String getFileName() { return fileName; }
    public String getPeerAddress() { return peerAddress; }
    public Set<String> getPeerList() { return peerList; }
    public byte[] getContent() { return content; } // NUEVO
}
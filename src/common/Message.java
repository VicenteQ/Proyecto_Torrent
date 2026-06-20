package common;

import java.io.Serializable;
import java.util.Set;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;         
    private String fileName;     
    private String peerAddress;  
    private Set<String> peerList; 
    private byte[] content;      
    private int senderId;        // ID del nodo para el Algoritmo Bully
    private int lamportTime;        //Contador de Lamport

    public Message(String type, String fileName, String peerAddress, Set<String> peerList, byte[] content, int lamportTime) {
        this.type = type;
        this.fileName = fileName;
        this.peerAddress = peerAddress;
        this.peerList = peerList;
        this.content = content;
        this.senderId = -1;      // Un -1 indica que es un mensaje normal, no de elección
        this.lamportTime = lamportTime;
    }

    public Message(String type, int senderId, String peerAddress, int lamportTime) {
        this.type = type;
        this.senderId = senderId;
        this.peerAddress = peerAddress;
        this.lamportTime = lamportTime;
    }

    public String getType() { return type; }
    public String getFileName() { return fileName; }
    public String getPeerAddress() { return peerAddress; }
    public Set<String> getPeerList() { return peerList; }
    public byte[] getContent() { return content; } 
    public int getSenderId() { return senderId; } // NUEVO
    public int getLamportTime() { return lamportTime; }
}
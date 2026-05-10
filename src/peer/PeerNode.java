package peer;

import common.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;

public class PeerNode {
    private static final String TRACKER_IP = "25.33.48.72"; 
    private static final int TRACKER_PORT = 8080;

    private static final int MY_P2P_PORT = 5001; 
    private static final String MY_ADDRESS = "25.33.48.72:" + MY_P2P_PORT;

    public static void main(String[] args) {
        Thread serverThread = new Thread(new PeerServer(MY_P2P_PORT));
        serverThread.start();

        // TÚ ANUNCIAS TODOS LOS ARCHIVOS QUE QUIERAS COMPARTIR
        System.out.println("Anunciando archivos al Tracker...");
        announceToTracker("archivo.txt");
        announceToTracker("al lalo se lo cagaron - copia.png");
    }

    private static void announceToTracker(String fileName) {
        try (Socket socket = new Socket(TRACKER_IP, TRACKER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            Message msg = new Message("ANNOUNCE", fileName, MY_ADDRESS, null, null);
            out.writeObject(msg);
            out.flush();
            
            Message response = (Message) in.readObject();
            System.out.println("Respuesta del tracker (ANNOUNCE): " + response.getType());
            
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error anunciando al tracker: " + e.getMessage());
        }
    }

    private static Set<String> requestFromTracker(String fileName) {
        try (Socket socket = new Socket(TRACKER_IP, TRACKER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            Message msg = new Message("REQUEST", fileName, null, null, null);
            out.writeObject(msg);
            out.flush();
            
            Message response = (Message) in.readObject();
            return response.getPeerList();
            
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
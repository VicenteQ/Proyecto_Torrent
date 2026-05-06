package peer;

import common.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;

public class PeerNode {
    private static final String TRACKER_IP = "127.0.0.1";
    private static final int TRACKER_PORT = 8080;
    
    // Puerto donde ESTE peer escuchará a otros peers
    private static final int MY_P2P_PORT = 5001; 
    private static final String MY_ADDRESS = "127.0.0.1:" + MY_P2P_PORT;

    public static void main(String[] args) {
        // 1. Levantar el servidor local P2P en un hilo separado
        Thread serverThread = new Thread(new PeerServer(MY_P2P_PORT));
        serverThread.start();

        // 2. Anunciarse al Tracker (Simulando que tenemos "archivo.txt")
        announceToTracker("archivo.txt");

        // 3. Pedir un archivo al Tracker
        Set<String> peersConElArchivo = requestFromTracker("video.mp4");
        
        // 4. Si alguien lo tiene, conectarse al primero para descargarlo
        if (peersConElArchivo != null && !peersConElArchivo.isEmpty()) {
            String targetPeer = peersConElArchivo.iterator().next();
            String[] parts = targetPeer.split(":");
            String targetIp = parts[0];
            int targetPort = Integer.parseInt(parts[1]);

            Thread downloader = new Thread(new PeerDownloader(targetIp, targetPort, "video.mp4"));
            downloader.start();
        } else {
            System.out.println("Ningun peer tiene el archivo solicitado.");
        }
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
            System.err.println("Error solicitando al tracker: " + e.getMessage());
            return null;
        }
    }
}
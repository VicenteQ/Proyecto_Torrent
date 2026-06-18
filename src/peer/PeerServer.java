package peer;

import common.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerServer implements Runnable {
    private final int listenPort;
    private final ExecutorService threadPool;

    public PeerServer(int listenPort) {
        this.listenPort = listenPort;
        this.threadPool = Executors.newFixedThreadPool(10); 
    }

    @Override
    public void run() {
        System.out.println("Iniciando PeerServer local en el puerto: " + listenPort);
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            while (true) {
                Socket p2pSocket = serverSocket.accept();
                threadPool.execute(() -> handleP2PRequest(p2pSocket));
            }
        } catch (IOException e) {
            System.err.println("Error en el PeerServer: " + e.getMessage());
        }
    }

    private void handleP2PRequest(Socket socket) {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            Message request = (Message) in.readObject();
            String type = request.getType();
            
            if ("DOWNLOAD".equals(type)) {
                String fileName = request.getFileName();
                System.out.println("Me estan pidiendo el archivo: " + fileName);
                
                Path filePath = Paths.get(fileName);
                
                if (Files.exists(filePath)) {
                    byte[] fileData = Files.readAllBytes(filePath);
                    Message response = new Message("FILE_DATA", fileName, null, null, fileData);
                    out.writeObject(response);
                    out.flush();
                    System.out.println("Archivo fisico enviado exitosamente.");
                } else {
                    System.err.println("Error: El archivo " + fileName + " no se encontro en el disco local.");
                }
            } 
            // NUEVO: LÓGICA DEL ALGORITMO BULLY
            else if ("ELECTION".equals(type)) {
                System.out.println("[BULLY] Recibida ELECCION de ID: " + request.getSenderId());
                // Responder "ANSWER" para indicar que asumimos la responsabilidad (ya que tenemos ID mayor)
                Message answer = new Message("ANSWER", PeerNode.miId, PeerNode.MY_IP);
                out.writeObject(answer);
                out.flush();
                
                // Iniciar nuestra propia elección para asegurar que no haya alguien aún mayor
                PeerNode.iniciarEleccion();
            } 
            else if ("ANSWER".equals(type)) {
                System.out.println("[BULLY] Un nodo mayor ha respondido (ANSWER). Me retiro de la contienda.");
                PeerNode.recibiRespuesta = true; // Detiene la proclamación
            } 
            else if ("COORDINATOR".equals(type)) {
                PeerNode.currentTrackerIp = request.getPeerAddress();
                System.out.println("\n*** [NUEVO LÍDER] Tracker establecido en: " + PeerNode.currentTrackerIp + " ***\n");
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error manejando peticion P2P: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        }
    }
}
package peer;

import common.Message;
import common.LogManager;
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

            PeerNode.actualizarReloj(request.getLamportTime());
            
            // No logueamos los HEARTBEATS en el txt para no saturar el archivo Log, pero si los ELECTION/DOWNLOAD
            if (!"HEARTBEAT".equals(request.getType())) {
                LogManager.registrar("peer.log", request.getType() + " recibido", PeerNode.getRelojLamport());
            }

            String type = request.getType();
            
            if ("DOWNLOAD".equals(type)) {
                String fileName = request.getFileName();
                System.out.println("Me estan pidiendo el archivo: " + fileName);
                
                Path filePath = Paths.get(fileName);
                
                if (Files.exists(filePath)) {
                    byte[] fileData = Files.readAllBytes(filePath);

                    int tiempoEnvio = PeerNode.incrementarReloj();
                    LogManager.registrar("peer.log", "FILE_DATA enviado", tiempoEnvio);

                    Message response = new Message("FILE_DATA", fileName, null, null, fileData, tiempoEnvio);
                    out.writeObject(response);
                    out.flush();
                    System.out.println("Archivo fisico enviado exitosamente.");
                } else {
                    System.err.println("Error: El archivo " + fileName + " no se encontro en el disco local.");
                }
            } 
            // LÓGICA DEL ALGORITMO BULLY
            else if ("ELECTION".equals(type)) {
                System.out.println("[BULLY] Recibida ELECCION de ID: " + request.getSenderId());

                int tiempoEnvio = PeerNode.incrementarReloj();
                LogManager.registrar("peer.log", "ANSWER enviado", tiempoEnvio);

                // Responder "ANSWER" para indicar que asumimos la responsabilidad (ya que tenemos ID mayor)
                Message answer = new Message("ANSWER", PeerNode.miId, PeerNode.MY_IP, tiempoEnvio);
                out.writeObject(answer);
                out.flush();
                
                // Iniciar nuestra propia elección para asegurar que no haya alguien aún mayor
                PeerNode.iniciarEleccion();
            } 
            else if ("ANSWER".equals(type)) {
                System.out.println("[BULLY] Un nodo mayor ha respondido (ANSWER). Me retiro de la contienda.");
                PeerNode.recibiRespuesta = true; // Detiene la proclamación

                int tiempoAnswer = PeerNode.incrementarReloj();
                LogManager.registrar("peer.log", "ANSWER procesado", tiempoAnswer);
            } 
            else if ("COORDINATOR".equals(type)) {
                PeerNode.currentTrackerIp = request.getPeerAddress();
                System.out.println("\n*** [NUEVO LÍDER] Tracker establecido en: " + PeerNode.currentTrackerIp + " ***\n");

                int tiempoCoordinator = PeerNode.incrementarReloj();
                LogManager.registrar("peer.log", "COORDINATOR procesado", tiempoCoordinator);
            }
            // RESPUESTA PARA MANTENER LA RED P2P VIVA
            else if ("HEARTBEAT".equals(type)) {
                int tiempoHeartbeat = PeerNode.incrementarReloj();
                Message ack = new Message("HEARTBEAT_ACK", PeerNode.miId, PeerNode.MY_IP, tiempoHeartbeat);
                out.writeObject(ack);
                out.flush();
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error manejando peticion P2P: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        }
    }
}
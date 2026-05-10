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
        this.threadPool = Executors.newFixedThreadPool(10); // Pool para atender a varios peers a la vez
    }

    @Override
    public void run() {
        System.out.println("Iniciando PeerServer local en el puerto: " + listenPort);
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            while (true) {
                Socket p2pSocket = serverSocket.accept();
                // Delegar la subida del archivo a un hilo independiente
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
            
            if ("DOWNLOAD".equals(request.getType())) {
                String fileName = request.getFileName();
                System.out.println("Me estan pidiendo el archivo: " + fileName);
                
                Path filePath = Paths.get(fileName);
                
                // Verificamos si el archivo físico realmente existe en la carpeta
                if (Files.exists(filePath)) {
                    // LEER EL ARCHIVO REAL DEL DISCO DURO (Bytes binarios)
                    byte[] fileData = Files.readAllBytes(filePath);
                    
                    // Enviar el archivo de vuelta encapsulado en el Message
                    Message response = new Message("FILE_DATA", fileName, null, null, fileData);
                    out.writeObject(response);
                    out.flush();
                    System.out.println("Archivo fisico enviado exitosamente.");
                } else {
                    System.err.println("Error: El archivo " + fileName + " no se encontro en el disco local.");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error enviando datos P2P: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        }
    }
}
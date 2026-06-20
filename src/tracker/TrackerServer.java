package tracker;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackerServer {
    private static final int PORT = 8081; //8080;
   
    private static final ConcurrentHashMap<String, Set<String>> fileRegistry = new ConcurrentHashMap<>();
    
    // Pool de hilos para manejar clientes concurrentemente
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(50);

    public static void iniciarServidor() {
        System.out.println("\n[TRACKER ACTIVO] Iniciando Tracker Central en esta maquina (Puerto " + PORT + ")...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
            
                System.out.println("Nueva conexion desde: " + clientSocket.getInetAddress());
                
                // Derivar la conexion a un hilo independiente
                threadPool.execute(new PeerHandler(clientSocket, fileRegistry));
            }
        } catch (IOException e) {
            System.err.println("Error critico en el servidor Tracker: " + e.getMessage());
        } 
        // Eliminado el finally con shutdown() ya que el servidor debe correr indefinidamente 
        // mientras sea el líder.
    }
}
package peer;

import common.Message;
import common.NodeInfo;
import common.LogManager;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PeerNode {
    
    // LISTA GLOBAL DE MEMBRESÍA
    public static List<NodeInfo> membresia = new ArrayList<>();

    public static String currentTrackerIp = "25.42.159.175"; 
    private static final int TRACKER_PORT = 8081; //8080;

    // DATOS LOCALES Y VARIABLES BULLY
    private static final int MY_P2P_PORT = 5001; 
    public static final String MY_ADDRESS = "25.33.48.72:" + MY_P2P_PORT;
    public static final String MY_IP = "25.33.48.72"; // Hecha public para acceso desde PeerServer
    
    // NUEVO: Variables de estado para el Algoritmo Bully
    public static int miId = 1; // IMPORTANTE: Cambiar este ID según el PC (ej. 1, 2, o 3)
    public static volatile boolean recibiRespuesta = false;
    private static volatile int relojLamport = 0;

    public static void main(String[] args) {
        
        // LLENAMOS LA LISTA DE MEMBRESÍA AL INICIAR EL PROGRAMA
        membresia.add(new NodeInfo(1, "25.33.48.72", 5001));       // Tu PC
        membresia.add(new NodeInfo(2, "25.1.1.1", 5001));          // IP falsa para evitar el "eco" local
        membresia.add(new NodeInfo(3, "25.42.159.175", 5001));     // Tu amigo (Tracker inicial caído)

        Thread serverThread = new Thread(new PeerServer(MY_P2P_PORT));
        serverThread.start();

        // Iniciamos el monitor de caídas en segundo plano
        startTrackerMonitor();

        System.out.println("Anunciando archivos al Tracker...");
        announceToTracker("archivo.txt");
        announceToTracker("al lalo se lo cagaron - copia.png");
    }

    public static void startTrackerMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); 
                    
                    if (MY_IP.equals(currentTrackerIp)) {
                        continue; 
                    }

                    try (Socket s = new Socket(currentTrackerIp, TRACKER_PORT)) {
                        // Conexión exitosa, Tracker vivo.
                    } catch (IOException e) {
                        System.out.println("\n[MONITOR] ¡Alerta! El Tracker (" + currentTrackerIp + ") se ha caído.");
                        iniciarEleccion();
                        Thread.sleep(10000); 
                    }
                } catch (InterruptedException e) {
                    System.err.println("Error en el monitor: " + e.getMessage());
                }
            }
        }).start();
    }

    // PASO 4: Lógica completa del Algoritmo Bully (AHORA SÚPER RÁPIDO)
    public static void iniciarEleccion() {
        new Thread(() -> {

            int tiempoEleccion = incrementarReloj();
            LogManager.registrar("peer.log", "Inicio de elección", tiempoEleccion);

            System.out.println("[ELECCION] Iniciando Algoritmo Bully...");
            recibiRespuesta = false;

            // 1. Enviar "ELECTION" a nodos con ID mayor (Con Timeout de 2 segundos)
            for (NodeInfo nodo : membresia) {
                if (nodo.getId() > miId) {
                    try (Socket s = new Socket()) {
                        // AQUÍ ESTÁ EL CAMBIO: Timeout estricto de 2000 milisegundos
                        s.connect(new java.net.InetSocketAddress(nodo.getIp(), nodo.getPort()), 2000);
                        ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                        
                        int tiempoEnvio = incrementarReloj();
                        LogManager.registrar("peer.log", "ELECTION enviado", tiempoEnvio);

                        Message msg = new Message("ELECTION", miId, MY_IP, tiempoEnvio);
                        out.writeObject(msg);
                        out.flush();
                        System.out.println("  -> ELECTION enviado al ID: " + nodo.getId());
                    } catch (IOException e) {
                        System.out.println("  -> (Nodo ID: " + nodo.getId() + " no respondió a la elección)");
                    }
                }
            }

            // 2. Esperar respuestas ("ANSWER")
            try { Thread.sleep(3000); } catch (InterruptedException e) { }

            // 3. Evaluar resultado y proclamarse si nadie mayor respondió
            if (!recibiRespuesta) {
                int tiempoLider = incrementarReloj();
                LogManager.registrar("peer.log", "Me proclamo líder", tiempoLider);
                
                System.out.println("\n[ELECCION] ¡Ningún nodo mayor respondió. YO SOY EL NUEVO LÍDER!");
                currentTrackerIp = MY_IP;
                
                // Levantar el Tracker internamente
                new Thread(() -> tracker.TrackerServer.iniciarServidor()).start();

                // 4. Enviar "COORDINATOR" a todos los demás nodos (También con Timeout de 2 segundos)
                for (NodeInfo nodo : membresia) {
                    if (nodo.getId() != miId) { 
                        try (Socket s = new Socket()) {
                            s.connect(new java.net.InetSocketAddress(nodo.getIp(), nodo.getPort()), 2000);
                            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                            
                            int tiempoCoordinator = incrementarReloj();
                            LogManager.registrar("peer.log", "COORDINATOR enviado", tiempoCoordinator);

                            Message msg = new Message("COORDINATOR", miId, MY_IP, tiempoCoordinator);
                            out.writeObject(msg);
                            out.flush();
                        } catch (IOException e) { 
                            // Nodo caído, ignoramos silenciosamente
                        }
                    }
                }
            }
        }).start();
    }

    private static void announceToTracker(String fileName) {
        try (Socket socket = new Socket(currentTrackerIp, TRACKER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            int tiempoEnvio = incrementarReloj();
            LogManager.registrar("peer.log", "ANNOUNCE enviado", tiempoEnvio);

            Message msg = new Message("ANNOUNCE", fileName, MY_ADDRESS, null, null, tiempoEnvio);
            out.writeObject(msg);
            out.flush();
            
            Message response = (Message) in.readObject();

            actualizarReloj(response.getLamportTime());
            LogManager.registrar("peer.log", response.getType() + " recibido", getRelojLamport());

            System.out.println("Respuesta del tracker (ANNOUNCE): " + response.getType());
            
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error anunciando al tracker: " + e.getMessage());
        }
    }

    private static Set<String> requestFromTracker(String fileName) {
        try (Socket socket = new Socket(currentTrackerIp, TRACKER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); 
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            int tiempoEnvio = incrementarReloj();
            LogManager.registrar("peer.log", "REQUEST enviado", tiempoEnvio);

            Message msg = new Message("REQUEST", fileName, null, null, null, tiempoEnvio);
            out.writeObject(msg);
            out.flush();
            
            Message response = (Message) in.readObject();

            actualizarReloj(response.getLamportTime());
            LogManager.registrar("peer.log", response.getType() + " recibido", getRelojLamport());

            return response.getPeerList();
            
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    //Incremento del reloj Lamport
    public static synchronized int incrementarReloj() {
        relojLamport++;
        return relojLamport;
    }

    //Incremento del reloj Lamport cuando se recibe un mensaje
    public static synchronized void actualizarReloj(int tiempoRecibido) {
        relojLamport = Math.max(relojLamport, tiempoRecibido) + 1;
    }

    public static synchronized int getRelojLamport() {
        return relojLamport;
    }
}
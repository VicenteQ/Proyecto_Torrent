package peer;

import common.Config;
import common.LogManager;
import common.Message;
import common.NodeInfo;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PeerNode {
    
    // LISTA GLOBAL DE MEMBRESÍA
    public static List<NodeInfo> membresia = new CopyOnWriteArrayList<>();

  public static String currentTrackerIp = Config.INITIAL_TRACKER_IP; 
    private static final int TRACKER_PORT = Config.TRACKER_PORT;

    // DATOS LOCALES Y VARIABLES BULLY
 private static final int MY_P2P_PORT = Config.MY_P2P_PORT; 
    public static final String MY_ADDRESS = Config.MY_IP + ":" + MY_P2P_PORT;
    public static final String MY_IP = Config.MY_IP;
    
    // Variables de estado para el Algoritmo Bully
    public static int miId = 2; 
    public static volatile boolean recibiRespuesta = false;
    private static volatile int relojLamport = 0;

    public static void main(String[] args) {
        
        // LLENAMOS LA LISTA DE MEMBRESÍA AL INICIAR EL PROGRAMA
        membresia.add(new NodeInfo(1, Config.IP1, Config.MY_P2P_PORT));
        membresia.add(new NodeInfo(2, Config.IP2, Config.MY_P2P_PORT));
        membresia.add(new NodeInfo(3, Config.IP3, Config.MY_P2P_PORT));

        Thread serverThread = new Thread(new PeerServer(MY_P2P_PORT));
        serverThread.start();

        // Iniciamos el monitor de caídas en segundo plano
        startTrackerMonitor();
        // Iniciamos el monitor de latidos P2P
        startPeerHeartbeatMonitor();

        System.out.println("Anunciando archivos al Tracker...");
        announceToTracker("archivo.txt");
        announceToTracker("al lalo se lo cagaron - copia.png");

        iniciarMenuInteractivo();
    }

    // LATIDOS P2P CONSTANTES
    public static void startPeerHeartbeatMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(4000); // Enviar latidos cada 4 segundos
                    
                    for (NodeInfo nodo : membresia) {
                        // No nos enviamos latidos a nosotros mismos ni al tracker central (de eso se encarga startTrackerMonitor)
                        if (nodo.getId() == miId || nodo.getIp().equals(currentTrackerIp)) {
                            continue;
                        }

                        try (Socket s = new Socket()) {
                            s.connect(new java.net.InetSocketAddress(nodo.getIp(), nodo.getPort()), 2000);
                            s.setSoTimeout(2000); // Esperar máximo 2 segundos por respuesta
                            
                            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                            ObjectInputStream in = new ObjectInputStream(s.getInputStream());

                            int tiempoEnvio = incrementarReloj();
                            // Reusamos el constructor del Bully para enviar ID y IP
                            Message msg = new Message("HEARTBEAT", miId, MY_IP, tiempoEnvio);
                            out.writeObject(msg);
                            out.flush();

                            Message response = (Message) in.readObject();
                            if ("HEARTBEAT_ACK".equals(response.getType())) {
                                actualizarReloj(response.getLamportTime());
                            }
                        } catch (IOException | ClassNotFoundException e) {
                            System.out.println("\n[HEARTBEAT] Nodo " + nodo.getId() + " (" + nodo.getIp() + ") CAIDO o no responde. Eliminando de membresia activa.");
                            membresia.remove(nodo); // Eliminación segura gracias al CopyOnWriteArrayList
                        }
                    }
                } catch (InterruptedException e) {
                    System.err.println("Error en el monitor de latidos P2P: " + e.getMessage());
                }
            }
        }).start();
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

    // Lógica completa del Algoritmo Bully
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

    public static void iniciarMenuInteractivo() {
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            while (true) {
                System.out.println("\n=================================");
                System.out.println("          MENÚ P2P TIPO TORRENT    ");
                System.out.println("=================================");
                System.out.println("1. Descargar archivo de un compañero");
                System.out.println("2. Salir");
                System.out.print("Elige una opción: ");
                
                String opcion = scanner.nextLine();
                
                if ("1".equals(opcion)) {
                    System.out.print("Ingresa el nombre exacto del archivo a descargar: ");
                    String fileName = scanner.nextLine();
                    
                    System.out.println("Consultando al Tracker por el archivo...");
                    Set<String> peersConArchivo = requestFromTracker(fileName);
                    
                    if (peersConArchivo != null && !peersConArchivo.isEmpty()) {
                        System.out.println("¡Archivo encontrado! Disponible en: " + peersConArchivo);
                        
                        String peerTarget = peersConArchivo.iterator().next(); 
                        
                        String[] partes = peerTarget.split(":");
                        String ip = partes[0];
                        int puerto = Integer.parseInt(partes[1]);
                        
                        System.out.println("Iniciando descarga desde " + ip + " por el puerto " + puerto + "...");
                        
                        Thread hiloDescarga = new Thread(new PeerDownloader(ip, puerto, fileName));
                        hiloDescarga.start();
                        
                        try { hiloDescarga.join(); } catch (InterruptedException e) {}
                        
                    } else {
                        System.out.println("Ningún peer tiene el archivo o el Tracker no está disponible.");
                    }
                } else if ("2".equals(opcion)) {
                    System.out.println("Desconectando nodo...");
                    System.exit(0);
                } else {
                    System.out.println("Opción no válida.");
                }
            }
        }
    }

}
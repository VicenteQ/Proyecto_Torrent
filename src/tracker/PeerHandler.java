package tracker;

import common.Message; // Importación obligatoria
import common.LogManager;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PeerHandler implements Runnable {
    private final Socket socket;
    private final ConcurrentHashMap<String, Set<String>> fileRegistry;

    public PeerHandler(Socket socket, ConcurrentHashMap<String, Set<String>> fileRegistry) {
        this.socket = socket;
        this.fileRegistry = fileRegistry;
    }

    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // Leer el objeto serializado enviado por el Peer
            Message request = (Message) in.readObject();

            TrackerClock.actualizarReloj(request.getLamportTime());
            LogManager.registrar("tracker.log", request.getType() +" recibido", TrackerClock.getRelojLamport());
            
            String messageType = request.getType();
            String fileName = request.getFileName();

            if ("ANNOUNCE".equals(messageType)) {
                String peerAddress = request.getPeerAddress(); // Ejemplo: "192.168.1.5:5000"

                // Agregar el peer al registro de forma segura (Thread-safe)
                fileRegistry.computeIfAbsent(fileName, k -> ConcurrentHashMap.newKeySet()).add(peerAddress);
                
                int tiempoRegistro  = TrackerClock.incrementarReloj();
                LogManager.registrar("tracker.log", "Archivo registrado: " + fileName, tiempoRegistro);

                int tiempoEnvio  = TrackerClock.incrementarReloj();
                LogManager.registrar("tracker.log", "SUCCESS enviado", tiempoEnvio);

                // Confirmar recepción al Peer
                Message response = new Message("SUCCESS", fileName, null, null, null, tiempoEnvio);
                out.writeObject(response);
                out.flush();

            } else if ("REQUEST".equals(messageType)) {
                // Obtener la lista de peers o un set vacío si nadie lo tiene
                Set<String> peers = fileRegistry.getOrDefault(fileName, new HashSet<>());
                
                int tiempo = TrackerClock.incrementarReloj();
                LogManager.registrar("tracker.log", "PEER_LIST enviado", tiempo);

                // Enviar la estructura de datos compleja al solicitante
                Message response = new Message("PEER_LIST", fileName, null, peers, null, tiempo);
                out.writeObject(response);
                out.flush();
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error con el peer " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar el socket: " + e.getMessage());
            }
        }
    }
}
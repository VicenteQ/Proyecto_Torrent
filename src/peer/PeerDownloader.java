package peer;

import common.LogManager;
import common.Message;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class PeerDownloader implements Runnable {
    private final String targetIp;
    private final int targetPort;
    private final String fileName;
    
    // Candado estático para sincronizar la escritura en disco entre múltiples hilos
    private static final Object fileLock = new Object();

    public PeerDownloader(String targetIp, int targetPort, String fileName) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        int intentos = 0;
        final int MAX_INTENTOS = 3;
        boolean exito = false;

        // BUCLE DE REINTENTOS PARA MITIGAR FALLOS DE OMISIÓN:
        while (intentos < MAX_INTENTOS && !exito) {
            intentos++;
            System.out.println("Intentando descargar " + fileName + " desde " + targetIp + ":" + targetPort + " (Intento " + intentos + "/" + MAX_INTENTOS + ")");
            
            try (Socket socket = new Socket()) {
                // 1. Timeout de conexión (Por si el nodo se cayó / Crash)
                socket.connect(new java.net.InetSocketAddress(targetIp, targetPort), 3000);
                
                // 2. Timeout de lectura (Por si el paquete se pierde en la red / Omisión)
                socket.setSoTimeout(3000); 

                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    int tiempo = PeerNode.incrementarReloj();
                    LogManager.registrar("peer.log", "DOWNLOAD enviado (Intento " + intentos + ")", tiempo);

                    // Solicitar el archivo al otro Peer
                    Message request = new Message("DOWNLOAD", fileName, null, null, null, tiempo);
                    out.writeObject(request);
                    out.flush();

                    // Leer la respuesta con los datos
                    Message response = (Message) in.readObject();

                    PeerNode.actualizarReloj(response.getLamportTime());
                    LogManager.registrar("peer.log", response.getType() + " recibido", PeerNode.getRelojLamport());
                    
                    if ("FILE_DATA".equals(response.getType()) && response.getContent() != null) {
                        guardarArchivo(response.getFileName(), response.getContent());
                        exito = true; // Salimos del bucle si fue exitoso
                    } else {
                        System.out.println("El peer no tenia el archivo o hubo un error.");
                        break; // Si responde bien pero no tiene el archivo, no vale la pena reintentar
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("OMISION:Se supero el tiempo de espera recibiendo datos del peer " + targetIp + ". Reintentando...");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("FALLO RED: Error conectando con " + targetIp + ": " + e.getMessage() + ". Reintentando...");
            }
            
            if (!exito && intentos < MAX_INTENTOS) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) {} // Pausa antes de reintentar
            }
        }

        if (!exito) {
            System.err.println("ABORTADO: Se agotaron los " + MAX_INTENTOS + " intentos. No se pudo descargar " + fileName + " desde " + targetIp);
        }
    }

    private void guardarArchivo(String name, byte[] data) {
        // Región crítica protegida por synchronized
        synchronized (fileLock) {
            try (FileOutputStream fos = new FileOutputStream("descargas_" + name)) {
                fos.write(data);
                System.out.println("Archivo " + name + " guardado exitosamente en disco.");
            } catch (IOException e) {
                System.err.println("Error de I/O al guardar el archivo: " + e.getMessage());
            }
        }
    }
}
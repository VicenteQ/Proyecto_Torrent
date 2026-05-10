package peer;

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
        System.out.println("Intentando descargar " + fileName + " desde " + targetIp + ":" + targetPort);
        
        try (Socket socket = new Socket(targetIp, targetPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Solicitar el archivo al otro Peer (ahora pasamos null al nuevo parámetro content)
            Message request = new Message("DOWNLOAD", fileName, null, null, null);
            out.writeObject(request);
            out.flush();

            // Leer la respuesta con los datos
            Message response = (Message) in.readObject();
            
            if ("FILE_DATA".equals(response.getType()) && response.getContent() != null) {
                guardarArchivo(response.getFileName(), response.getContent());
            } else {
                System.out.println("El peer no tenia el archivo o hubo un error.");
            }

        } catch (IOException | ClassNotFoundException e) {
            // Manejo de excepciones para prevenir caída del sistema ante fallos parciales de red
            System.err.println("Fallo al conectar con el peer " + targetIp + ": " + e.getMessage());
        }
    }

    private void guardarArchivo(String name, byte[] data) {
        // Región crítica protegida por synchronized
        synchronized (fileLock) {
            // ELIMINAMOS EL 'true' PARA QUE NO CORROMPA LA IMAGEN
            try (FileOutputStream fos = new FileOutputStream("descargas_" + name)) {
                fos.write(data);
                System.out.println("Archivo " + name + " guardado exitosamente en disco.");
            } catch (IOException e) {
                System.err.println("Error de I/O al guardar el archivo: " + e.getMessage());
            }
        }
    }
}
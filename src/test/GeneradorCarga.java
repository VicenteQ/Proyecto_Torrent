package test;

import common.Message;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneradorCarga {
    private static final int NUM_HILOS = 50; 
    private static final int DURACION_SEGUNDOS = 60;
    
    private static final String TRACKER_IP = "localhost"; //"25.42.159.175"; 
    private static final int TRACKER_PORT = 8081; //8080;

    private static AtomicInteger peticionesExitosas = new AtomicInteger(0);
    private static AtomicInteger peticionesFallidas = new AtomicInteger(0);
    private static List<Long> latencias = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INICIANDO PRUEBA DE TRÁFICO ===");
        System.out.println("Configuración: " + NUM_HILOS + " hilos concurrentes durante " + DURACION_SEGUNDOS + " segundos.");
        
        ExecutorService pool = Executors.newFixedThreadPool(NUM_HILOS);
        CountDownLatch latch = new CountDownLatch(NUM_HILOS);
        
        long startTimeGlobal = System.currentTimeMillis();
        long endTimeGlobal = startTimeGlobal + (DURACION_SEGUNDOS * 1000);

        // Lanza los 50 hilos
        for (int i = 0; i < NUM_HILOS; i++) {
            final int idHilo = i;
            pool.execute(() -> {
                Random rand = new Random();
                
                // Ejecución del hilo en bucle hasta que pasen los 60 segundos
                while (System.currentTimeMillis() < endTimeGlobal) {
                    long startReq = System.currentTimeMillis();
                    
                    // Elige aleatoriamente entre las dos funciones principales del sistema
                    boolean isAnnounce = rand.nextBoolean(); 
                    String fileName = "archivo_falso_" + rand.nextInt(100) + ".txt";
                    String mockPeerAddress = "10.0.0." + idHilo + ":" + (5000 + rand.nextInt(100));

                    try (Socket socket = new Socket(TRACKER_IP, TRACKER_PORT);
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        Message msg;
                        if (isAnnounce) {
                            msg = new Message("ANNOUNCE", fileName, mockPeerAddress, null, null, 0);
                        } else {
                            msg = new Message("REQUEST", fileName, null, null, null, 0);
                        }

                        // Envia petición
                        out.writeObject(msg);
                        out.flush();

                        Message response = (Message) in.readObject();
                        if (response != null) {
                            peticionesExitosas.incrementAndGet();
                            long latencia = System.currentTimeMillis() - startReq;
                            latencias.add(latencia);
                        }

                    } catch (Exception e) {
                        peticionesFallidas.incrementAndGet();
                    }
                    
                    // Pequeña pausa de 10ms para evitar saturación extrema de puertos locales
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
                latch.countDown();
            });
        }

        // El hilo principal espera a que todos los hilos terminen sus 60 segundos
        latch.await(); 
        pool.shutdown();
        long tiempoTotalReal = System.currentTimeMillis() - startTimeGlobal;

        imprimirMetricas(tiempoTotalReal);
    }

    private static void imprimirMetricas(long tiempoTotalMilisegundos) {
        int exitosas = peticionesExitosas.get();
        int fallidas = peticionesFallidas.get();
        int totalPeticiones = exitosas + fallidas;
        
        double tiempoTotalSegundos = tiempoTotalMilisegundos / 1000.0;
        double throughput = exitosas / tiempoTotalSegundos;
        double tasaError = (totalPeticiones == 0) ? 0 : ((double) fallidas / totalPeticiones) * 100;

        long latenciaPromedio = 0;
        long latenciaP95 = 0;

        if (!latencias.isEmpty()) {
            long sum = 0;
            synchronized (latencias) {
                for (Long l : latencias) {
                    sum += l;
                }
                Collections.sort(latencias);
                int indexP95 = (int) Math.ceil(95 / 100.0 * latencias.size()) - 1;
                latenciaP95 = latencias.get(Math.max(0, indexP95));
            }
            latenciaPromedio = sum / latencias.size();
        }

        System.out.println("\n=== RESULTADOS ===");
        System.out.println("Tiempo total de ejecución: " + tiempoTotalSegundos + " segundos");
        System.out.println("Total de peticiones procesadas: " + totalPeticiones);
        System.out.println("Peticiones Exitosas: " + exitosas);
        System.out.println("Peticiones Fallidas (Omisión/Crash): " + fallidas);
        System.out.println("----------------------------------------");
        System.out.println("Cálculos tras la prueba:");
        System.out.println("1. Throughput: " + String.format("%.2f", throughput) + " peticiones/segundo");
        System.out.println("2. Latencia Promedio: " + latenciaPromedio + " ms");
        System.out.println("3. Latencia p95: " + latenciaP95 + " ms");
        System.out.println("4. Tasa de Error: " + String.format("%.2f", tasaError) + " %");
        System.out.println("========================================");
    }
}
package tracker;

public class TrackerClock {

    private static int relojLamport = 0;

    public static synchronized int incrementarReloj() {
        relojLamport++;
        return relojLamport;
    }

    public static synchronized void actualizarReloj(int tiempoRecibido) {
        relojLamport = Math.max(relojLamport, tiempoRecibido) + 1;
    }

    public static synchronized int getRelojLamport() {
        return relojLamport;
    }
}
package common;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class LogManager {

    public static synchronized void registrar(String archivo, String evento, int reloj)
    {
        try(FileWriter fw = new FileWriter(archivo, true))
        {
            fw.write(LocalDateTime.now() + " | " + evento + " | Lamport=" + reloj + "\n");
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}
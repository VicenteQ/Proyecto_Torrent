## Instrucciones de uso
En el archivo Config.java que se encuentra en /common se configuran las IPs de los participantes.

El archivo debe coincidir con el de todos los participantes, el tracker ip debe ser de quién se encargue del tracker y my ip debe ser el de cada uno según compilen.

Dentro de PeerNode.java la variable:
- public static int miId = 1;
Deben asignarle el número que se asignaron en el config, por ejemplo:

  // INSERTE LAS IP DE LOS PARTICIPANTES DE LA COMPILACIÓN, TODOS DEBEN TENER LA IP ASOCIADA A LA MISMA VARIABLE
    public static final String IP1 = "25.33.83.56"; // Si anotas tu IP aquí, los demás deben poner TÚ IP acá
    public static final String IP2 = "25.33.48.72"; // Si anotas tu IP aquí, los demás deben poner TÚ IP acá
    public static final String IP3 = "25.1.1.1"; // 

Si tu ip está en IP1, en miId pones 1, si tu ip está en IP2, pones 2...

El que haga de tracker, debe compilar:
- java tracker.TrackerServer
- java peer.PeerNode

Los demás participantes compilan:
- java peer.PeerNode

Ya después podrán enviar el archivo.

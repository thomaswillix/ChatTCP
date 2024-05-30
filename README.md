## TCP Chat
**Servidor TCP (`Server.java`)**:

```java
    public class Server implements Runnable {
        private ArrayList<ConnectionHandler> connections;
        private File f = new File("messages.txt");
        private ServerSocket server;
        private boolean done;
        private ExecutorService pool;
        private List<String> users = new ArrayList<>();

        public Server() {
            connections = new ArrayList<>();
            done = false;
        }
```

- **Atributos**:
  - `connections`: Lista de manejadores de conexiones activas.
  - `f`: Archivo donde se guardan los mensajes.
  - `server`: Socket del servidor.
  - `done`: Flag para controlar si el servidor está en funcionamiento.
  - `pool`: Pool de hilos para manejar múltiples conexiones.
  - `users`: Lista de nicknames de los usuarios conectados.

- **Constructor**: Inicializa la lista de conexiones y el flag `done`.

    #### Método `run`

    ```java
    @Override
        public void run() {
            try {
                server = new ServerSocket(9999);
                pool = Executors.newCachedThreadPool();
                while (!done) {
                    Socket client = server.accept();
                    ConnectionHandler handler = new ConnectionHandler(client);
                    connections.add(handler);
                    pool.execute(handler);
                }
            } catch (Exception e) {
                shutdown();
            }
        }
    ```

- **`run()`**: Inicia el servidor en el puerto 9999, crea un pool de hilos y acepta conexiones de clientes en un bucle. Cada conexión se maneja en un nuevo hilo usando `ConnectionHandler`.

#### Método `broadcast`

```java
    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
        saveMessage(message);
    }
```

- **`broadcast(String message)`**: Envía un mensaje a todos los clientes conectados y lo guarda en un archivo.

#### Método `saveMessage`

```java
    private void saveMessage(String message) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
            bw.write(message);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            //ignore
        }
    }
```

- **`saveMessage(String message)`**: Guarda el mensaje en el archivo `messages.txt`.

#### Método `shutdown`

```java
    public void shutdown() {
        try {
            done = true;
            pool.shutdown();
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            // ignore
        }
    }
```

- **`shutdown()`**: Apaga el servidor, cierra el socket del servidor y todas las conexiones activas.

### Clase `ConnectionHandler`

```java
    class ConnectionHandler implements Runnable {
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;

        public ConnectionHandler(Socket client) {
            this.client = client;
        }
```

- **Atributos**:
  - `client`: Socket del cliente.
  - `in`: Lector de entradas del cliente.
  - `out`: Escritor de salidas hacia el cliente.
  - `nickname`: Nickname del usuario.

- **Constructor**: Inicializa el socket del cliente.

#### Método `run` en `ConnectionHandler`

```java
@Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Hello!...");
                out.println("Please enter a nickname : ");
                validateUser(out, in);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            validateRename(messageSplit[1], out);
                        } else {
                            out.println("No nickname provided!");
                        }
                    } else if (message.startsWith("/quit")) {
                        broadcast(nickname + " left the chat!");
                        System.out.println(nickname + " left the chat");
                        shutdown();
                    } else {
                        broadcast(nickname + ": " + message);
                    }
                }
            } catch (SocketException e) {
                System.out.println("There was an Exception with a client");
            } catch (IOException e) {
                shutdown();
            }
        }
```

- **`run()`**: Maneja la interacción con el cliente. Solicita y valida el nickname del usuario. Procesa los comandos `/nick` para cambiar el nickname y `/quit` para desconectarse. Reenvía los mensajes al resto de usuarios.

#### Métodos Auxiliares en `ConnectionHandler`

- **`validateRename`**: Valida y cambia el nickname del usuario.

```java
        private void validateRename(String s, PrintWriter out) {
            if (users.contains(s)) {
                out.println("User not available, it's already in use");
            } else {
                broadcast(nickname + " renamed themselves to " + s);
                System.out.println(nickname + " renamed themselves to " + s);
                nickname = s;
                out.println("Successfully changed nickname to " + nickname);
            }
        }
```

- **`validateUser`**: Valida el nickname del usuario al conectarse. Si el usuario solicita ver mensajes anteriores, los muestra.

```java
        private void validateUser(PrintWriter out, BufferedReader in) {
            try {
                do {
                    nickname = in.readLine();
                    String watchMessages[] = nickname.split(" ");
                    if (watchMessages.length > 1) {
                        nickname = watchMessages[0];
                        String messages = watchMessages[1];
                        if (messages.equalsIgnoreCase("Y")) {
                            showPrevoiusMesssages(out);
                        }
                    }
                    if (users.contains(nickname)) {
                        out.println(nickname + " is not an available username as it is already in use!");
                    }
                } while (users.contains(nickname));
                users.add(nickname);
                System.out.println(nickname + " connected");
                broadcast(nickname + " joined the chat!");
            } catch (SocketException e) {
                System.out.println("No nickname provided");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
```

- **`showPrevoiusMesssages`**: Muestra los mensajes anteriores al usuario si así lo solicita.

```java
        private void showPrevoiusMesssages(PrintWriter out) {
            try {
                out.println("------------------ These are the previous messages ------------------");
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        out.println(line);
                    }
                } catch (EOFException e) {
                    br.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
```

- **`sendMessage`**: Envía un mensaje al cliente.

```java
        public void sendMessage(String message) {
            out.println(message);
        }
```

- **`shutdown`**: Cierra la conexión del cliente y elimina su nickname de la lista de usuarios.

```java
        public void shutdown() {
            try {
                in.close();
                out.close();
                users.remove(nickname);
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
```

- **`main`**: Inicia el servidor ejecutando el método `run`.

```java
    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
```

### Resumen
Este código implementa un servidor de chat multiusuario en Java utilizando sockets TCP. El servidor:
1. Acepta conexiones de múltiples clientes.
2. Valida los nicknames para asegurar que sean únicos.
3. Permite a los usuarios cambiar su nickname y desconectarse con comandos específicos.
4. Difunde los mensajes a todos los clientes conectados.
5. Guarda los mensajes en un archivo para que los nuevos usuarios puedan ver el historial si así lo desean.

Este servidor maneja las conexiones concurrentemente utilizando un pool de hilos, lo que le permite escalar a múltiples usuarios simultáneamente.
Claro, a continuación se explica el código del cliente en Java para un chat TCP.


**Cliente TCP (`Client.java`)**:

```java
public class Client implements Runnable {
    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;
```
- **Atributos**:
    - `client`: El socket que se conecta al servidor.
    - `in`: Un `BufferedReader` para leer mensajes del servidor.
    - `out`: Un `PrintWriter` para enviar mensajes al servidor.
    - `done`: Un flag booleano para indicar cuándo cerrar la conexión.

#### Método `run`

```java
@Override
public void run() {
    try {
        client = new Socket("127.0.0.1", 9999);
        out = new PrintWriter(client.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        InputHandler inHandler = new InputHandler();
        Thread t = new Thread(inHandler);
        t.start();

        String inMessage;
        while((inMessage = in.readLine()) != null){
            System.out.println(inMessage);
        }

    } catch (Exception e){
        shutdown();
    }
}
```

- **`run()`**:
    1. **Conexión al Servidor**: Crea un socket que se conecta al servidor en la dirección 127.0.0.1 y el puerto 9999.
    2. **Inicialización de `out` e `in`**: Configura los flujos de entrada y salida para comunicarse con el servidor.
    3. **Manejo de Entradas del Usuario**: Crea y arranca un nuevo hilo `InputHandler` para gestionar las entradas del usuario desde la consola.
    4. **Recepción de Mensajes**: Lee mensajes del servidor en un bucle y los imprime en la consola.
    5. **Manejo de Excepciones**: Si ocurre una excepción, se llama al método `shutdown`.


#### Método `shutdown`

```java
public void shutdown(){
    out.println("/quit");
    done = true;
    try{
        in.close();
        out.close();
        if (!client.isClosed()){
            client.close();
        }
    }catch (IOException e){
        // ignore
    }
}
```
- **`shutdown()`**: 
    1. **Envía el Comando de Desconexión**: Envía el mensaje `/quit` al servidor para indicar la desconexión.
    2. **Cierra Recursos**: Cierra los flujos de entrada y salida y el socket del cliente.
    3. **Manejo de Excepciones**: Ignora cualquier excepción de E/S que ocurra durante el cierre de recursos.

### Clase `InputHandler`

```java
class InputHandler implements Runnable {
    @Override
    public void run() {
        try {
            BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
            while(!done){
                String message = inReader.readLine();
                if (message.equals("/quit")){
                    out.println(message);
                    inReader.close();
                    shutdown();
                } else {
                    out.println(message);
                }
            }
        } catch (IOException e){
            shutdown();
        }
    }
}
```
La subclase `InputHandler` implementa `Runnable` y gestiona las entradas del usuario:
1. **Lectura de la Consola**: Lee mensajes desde la entrada estándar (teclado).
2. **Envío de Mensajes**: Envía los mensajes al servidor.
3. **Comando de Salida**: Si el usuario escribe `/quit`, cierra los recursos y llama al método `shutdown`.

#### Método `main`
 
```java
public static void main(String[] args) {
    Client client = new Client();
    client.run();
}
```
- **`main`**: Crea una instancia de `Client` y llama a su método `run` para iniciar la ejecución del cliente.

#### Resumen
Este código implementa un cliente de chat que se conecta a un servidor en una dirección y puerto específicos. Maneja la comunicación bidireccional con el servidor, permitiendo al usuario enviar mensajes desde la consola y recibir mensajes del servidor. Además, gestiona la desconexión limpia del cliente mediante el comando `/quit`.

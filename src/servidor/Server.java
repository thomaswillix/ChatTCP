package servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable{
    private ArrayList<ConnectionHandler> connections;
    private File f = new File("messages.txt");
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;
    private List<String> users = new ArrayList<>();
    public Server (){
        connections = new ArrayList<>();
        done = false;
    }
    @Override
    public void run(){
        try {
            server = new ServerSocket(9999);
            pool = Executors.newCachedThreadPool();
            while(!done) {
                Socket client = server.accept();
                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                pool.execute(handler);
            }
        } catch (Exception e) {
            shutdown();
        }

    }

    public void broadcast(String message){
        for (ConnectionHandler ch: connections) {
            if (ch != null){
                ch.sendMessage(message);
            }
        }
        saveMessage(message);
    }

    private void saveMessage(String message) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
            bw.write(message);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            System.err.println("Sum' went wrong persisting");
        }
    }


    public void shutdown(){
        try {
            done = true;
            pool.shutdown();
            if (!server.isClosed()) {
                server.close();
            }
            for(ConnectionHandler ch : connections){
                ch.shutdown();
            }
        }catch (IOException e){
            // ignore
        }
    }

    class ConnectionHandler implements Runnable{
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        public ConnectionHandler(Socket client){
            this.client = client;
        }
        @Override
        public void run(){
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Hello!\nWelcome to the best chat ever!\n------------------------ INFO ------------------------\nWhenever you enter the chat you have to put your username and\n" +
                        "optionally a Y indicating you want to read previous messages\nCommands:" +
                        "\n   /nick 'newUsername':  to change your username\n   /quit:  to exit the chat\n");
                out.println("Please enter a nickname : ");
                validateUser(out, in);
                String message;
                while ((message = in.readLine()) != null){
                    if (message.startsWith("/nick ")){
                        String[] messageSplit  =  message.split(" ", 2);
                        if(messageSplit.length == 2){
                            validateRename(messageSplit[1], out);
                        } else{
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
            } catch (SocketException e){
                System.out.println("There was an Exception with a client");
            } catch (IOException e) {
                shutdown();
            }

        }

        private void validateRename(String s, PrintWriter out) {
            if(users.contains(s)){
                out.println("User not available, it's already in use");
            } else{
                broadcast(nickname + " renamed themselves to " + s);
                System.out.println(nickname + " renamed themselves to " + s);
                nickname = s;
                out.println("Succesfully changed nickname to " + nickname);
            }
        }

        private void validateUser(PrintWriter out, BufferedReader in) {
            try {
                do {
                    nickname = in.readLine();
                    String watchMessages[] = nickname.split(" ");
                    if (watchMessages.length > 1){
                        nickname = watchMessages[0];
                        String messages = watchMessages[1];
                        if(messages.equalsIgnoreCase("Y")){
                            showPrevoiusMesssages(out);
                        }
                    }
                    if(users.contains(nickname)){
                        out.println(nickname + " is not an available username as it is already in use!");
                    }
                }while (users.contains(nickname));
                users.add(nickname);
                System.out.println(nickname + " connected");
                broadcast(nickname + " joined the chat!");
            } catch (SocketException e){
                System.out.println("No nickname provided");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void showPrevoiusMesssages(PrintWriter out) {
            try {
                out.println("------------------ These are the previous messages ------------------");
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                try {
                    while((line = br.readLine())!=null){
                        out.println(line);
                    }
                }catch (EOFException e){
                    br.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        public void sendMessage(String message){
            out.println(message);
        }

        public void shutdown(){
            try {
                in.close();
                out.close();
                users.remove(nickname);
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e){
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        Server server =  new Server();
        server.run();
    }
}

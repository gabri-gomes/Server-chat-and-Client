import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

// Classe para armazenar informações de cada cliente
class Client {
    String name;
    String room_name;
    String state; // "init", "outside", "inside"

    public Client() {
        this.name = null;
        this.room_name = null;
        this.state = "init";
    }
}

public class ChatServer {

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    // HashMap for all important informations
    private static final Map<SocketChannel, Client> infos = new HashMap<>();

    public static void main(String args[]) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if (key.isAcceptable()) {
                        
                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);

                        // Register it with the selector, for reading
                        sc.register(selector, SelectionKey.OP_READ);

                    } else if (key.isReadable()) {
                        
                        SocketChannel sc = null;
                        try {
                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc);

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();
                                closeConnection(sc);
                            }
                        } catch (IOException ie) {
                            key.cancel();
                            closeConnection(sc);
                        }
                    }
                }

                
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }

    
    private static void closeConnection(SocketChannel sc) {
        try {
            if (sc != null) {
                Socket s = sc.socket();
                System.out.println("Closing connection to " + s);
                infos.remove(sc);
                sc.close();
            }
        } catch (IOException ie) {
            System.err.println("Error closing socket: " + ie);
        }
    }

    
    static private boolean processInput(SocketChannel sc) throws IOException {
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        if (buffer.limit() == 0) {
            return false;
        }

        String message = decoder.decode(buffer).toString().trim();
        System.out.println(message);

        
        if (message.startsWith("/nick")) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                nickCommand(sc, parts[1].trim());
            } else {
                sendMessage(sc, "ERROR");
            }
        } else if (message.startsWith("/join")) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                joinCommand(sc, parts[1].trim());
            } else {
                sendMessage(sc, "ERROR");
            }
        } else if (message.startsWith("/leave")) {
            leaveCommand(sc);
        } else if (message.startsWith("/bye")) {
            byeCommand(sc);
        } else if (message.startsWith("/priv")) {
            privCommand(sc,message);
        }
        else if(message.startsWith("/")){
            messages(sc, message.substring(1));
        }
         else {
            messages(sc, message);
        }

        return true;
    }

    
    private static void messages(SocketChannel sc, String message) throws IOException {
        Client client = infos.get(sc);

        if (client == null || client.room_name == null) {
            sendMessage(sc, "ERROR"); 
            return;
        }

        String room_name = client.room_name;
        String formattedMessage = client.name + ": " + message;

        
        broadcastConnection(room_name, formattedMessage, null);
        
    }

    
    private static void nickCommand(SocketChannel sc, String newName) throws IOException {
        if (newName.isEmpty() || nameNotPermited(newName)) {
            sendMessage(sc, "ERROR"); 
            return;
        }

        Client client = infos.get(sc);
        if (client == null) {
            client = new Client();
            infos.put(sc, client);
        }
        
        if (client.state.equals("init")) {
            client.state = "outside";
        }


        String oldName = client.name; 
        client.name = newName;

       
        sendMessage(sc, "OK");

        
        if (oldName != null) {
            String notification = oldName + " mudou de nome para " + newName;

            
            if (client.room_name != null) {
                broadcastConnection(client.room_name, notification, null);
            } else {
                
                sendMessage(sc, notification);
            }
        }

        
    }

    
    private static void joinCommand(SocketChannel sc, String room_name) throws IOException {
        if (room_name.isEmpty()) {
            sendMessage(sc, "ERROR");
            return;
        }

        Client client = infos.get(sc);
        if (client == null || client.name == null) {
            sendMessage(sc, "ERROR"); 
            return;
        }

        
        if (client.room_name != null) {
            leaveRoom(sc, client.room_name);
        }

        
        client.room_name = room_name;
        client.state = "inside";
        sendMessage(sc, "OK");

        
        broadcastConnection(room_name, "JOINED " + client.name, sc);
        
    }

    
    private static void leaveCommand(SocketChannel sc) throws IOException {
        Client client = infos.get(sc);

        if (client == null || client.room_name == null) {
            sendMessage(sc, "ERROR"); 
            return;
        }

        String room_name = client.room_name;

        
        client.room_name = null;
        client.state = "outside";

        
        broadcastConnection(room_name, "LEFT " + client.name, sc);

        sendMessage(sc, "OK");

        
        
    }

    private static void privCommand(SocketChannel sc, String originalMessage) throws IOException {
        Client sender = infos.get(sc);
    
        
        if (sender == null || sender.name == null) {
            sendMessage(sc, "ERROR"); 
            return;
        }
    
        
        String[] parts = originalMessage.split(" ", 3);
    
        
        if (parts.length < 3) {
            sendMessage(sc, "ERROR");
            return;
        }
    
        String recipientName = parts[1];
        String privateMessage = parts[2];
    
        
        SocketChannel recipientChannel = null;
        for (Map.Entry<SocketChannel, Client> entry : infos.entrySet()) {
            if (entry.getValue().name != null && entry.getValue().name.equals(recipientName)) {
                recipientChannel = entry.getKey();
                break;
            }
        }
    
        
        if (recipientChannel == null) {
            sendMessage(sc, "ERROR");
            return;
        }
    
        
        String formattedMessage = "PRIVATE " + sender.name + " " + privateMessage;
    
        
        sendMessage(recipientChannel, formattedMessage);
    
        
        sendMessage(sc, formattedMessage);
    
        
        sendMessage(sc, "OK");
    
        
        
    }
    

    
    private static void byeCommand(SocketChannel sc) throws IOException {
        Client client = infos.get(sc);

        if (client == null) {
            sendMessage(sc, "ERROR"); 
            return;
        }

        if (client.room_name != null) {
            
            broadcastConnection(client.room_name, "LEFT " + client.name, sc);
        }

        
        sendMessage(sc, "BYE");

        
        closeConnection(sc);
        
    }

    
    private static void leaveRoom(SocketChannel sc, String room_name) throws IOException {
        Client client = infos.get(sc);
        if (client != null) {
            broadcastConnection(room_name, "LEFT " + client.name, sc);
        }
    }

    
    private static void broadcastConnection(String room_name, String message, SocketChannel exclude) throws IOException {
        
        for (Map.Entry<SocketChannel, Client> entry : infos.entrySet()) {
            Client c = entry.getValue();
            SocketChannel ch = entry.getKey();

            if (c.room_name != null && c.room_name.equals(room_name)) {
                if (exclude == null || !ch.equals(exclude)) {
                    sendMessage(ch, message);
                }
            }
        }
    }

    
    private static boolean nameNotPermited(String name) {
        for (Client client : infos.values()) {
            if (name.equals(client.name)) {
                return true;
            }
        }
        return false;
    }

    
    private static void sendMessage(SocketChannel sc, String message) throws IOException {
        sc.write(ByteBuffer.wrap((message + "\n").getBytes("UTF-8")));
    }
}

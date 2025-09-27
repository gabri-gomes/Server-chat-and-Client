import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas com a interface gráfica

    // Variáveis para conexão com o servidor
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Inicializa a conexão com o servidor
        socket = new Socket(server, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // Envia a mensagem ao servidor
        if(notcommnad(message)) {
            out.println("/" + message);
        }
        else{
            out.println(message);
        }
    }

    public boolean notcommnad (String message) {
        
        String firstPart = message.split(" ")[0];
        if(firstPart.equals("/nick")){
            return false;
        }
        else if(firstPart.equals("/join")){
            return false;
        }
        else if(firstPart.equals("/leave")){
            return false;
        }
        else if(firstPart.equals("/bye")){
            return false;
        }
        else if(firstPart.equals("/priv")){
            return false;
        }
        else if(message.charAt(0) != '/'){
            return false;
        }
        else{return true;}
    } 

    // Método principal do objeto
    public void run() {
        // Thread para receber mensagens do servidor
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    printMessage(message + "\n");
                }
            } catch (IOException e) {
                System.err.println("Connection to server lost.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignora exceção ao fechar o socket
                }
            }
        }).start();
    }

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}

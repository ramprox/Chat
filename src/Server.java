import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Scanner;

public class Server {

    private static final String END_SESSION = "/finish";
    private static final String END_MESSAGE = "/eom";           // end of message

    private static ServerSocket serverSocket;
    private static Socket socket;
    private static DataInputStream dis;
    private static DataOutputStream dos;
    private static Scanner scanner;

    // В аргументе можно передать кодировку командной строки (по умолчанию используется "IBM866", т.к.
    // эта кодировка используется по умолчанию в ОС Windows (русская версия)).
    // В консоли Intellij IDEA используется "UTF-8". Если не задать корректную кодировку
    // при отправлении сообщения на русском языке с консоли сервера
    // на стороне клиента будут "кракозябры".
    public static void main(String[] args) {
        String codePage = args.length > 0 ? args[0] : "IBM866";
        try {
            scanner = new Scanner(System.in, codePage);
            serverSocket = new ServerSocket(8189);
            System.out.println("Сервер стартанул. Ожидание клиента...");
            socket = serverSocket.accept();
            System.out.println("Клиент подключился.");
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            boolean isEndSession = false;
            while(true) {
                String messageFromClient;
                while(true) {
                    messageFromClient = dis.readUTF();
                    if (messageFromClient.equalsIgnoreCase(END_SESSION)) {
                        dos.writeUTF(messageFromClient);
                        isEndSession = true;
                        System.out.println("Клиент отключился");
                        break;
                    }
                    if(messageFromClient.equalsIgnoreCase(END_MESSAGE)) {
                        break;
                    }
                    dos.writeUTF("Echo " + messageFromClient);
                    System.out.println("Client: " + messageFromClient);
                }
                if(isEndSession) {
                    break;
                }
                System.out.print("Server: ");
                String messageToClient;
                do {
                    messageToClient = scanner.nextLine();
                } while(messageToClient.trim().isEmpty());
                dos.writeUTF(messageToClient);
            }
        } catch (IOException ignored) {
            System.out.println("Соединение разорвано.");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        finally {
            close();
        }
    }

    private static void close() {
        if(scanner != null) {
            scanner.close();
        }
        if(serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(dis != null) {
            try {
                dis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(dos != null) {
            try {
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

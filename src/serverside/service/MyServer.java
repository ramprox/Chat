package serverside.service;

import serverside.interfaces.AuthService;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MyServer {

    private final int PORT = 8081;
    private List<ClientHandler> clients;
    private AuthService authService;

    public AuthService getAuthService() {
        return authService;
    }

    public MyServer() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            authService = new BaseAuthService();
            authService.start();
            clients = new ArrayList<>();
            while(true) {
                System.out.println("Сервер ожидает подключения");
                Socket socket = server.accept();
                System.out.println("Клиент подключился");
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            System.out.println("Сервер грохнулся");
        } finally {
            if(authService != null) {
                authService.stop();
            }
        }
    }

    public synchronized void broadcastMessage(String message) {
        for(ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public synchronized void sendPrivateMessage(ClientHandler sender, String nick, String message) {
        ClientHandler client = null;
        for(ClientHandler c : clients) {
            if(c.getName().equals(nick)) {
                client = c;
                break;
            }
        }
        if(client != null) {
            client.sendMessage("[Личное сообщение от " + sender.getName() + "]: " + message);
            sender.sendMessage("[Личное сообщение к " + nick + "]: " + message);
        } else {
            sender.sendMessage("/errorSPM Пользователя " + nick + " нет в чате");
        }
    }

    public synchronized void subscribe(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void unsubscribe(ClientHandler client) {
        clients.remove(client);
    }

    public boolean isNickBusy(String nick) {
        for(ClientHandler client : clients) {
            if(client.getName().equals(nick)) {
                return true;
            }
        }
        return false;
    }
}

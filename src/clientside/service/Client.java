package clientside.service;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class Client extends JFrame {
    private static final Dimension MINIMUM_SIZE = new Dimension(400, 400);
    private final Integer SERVER_PORT = 8081;
    private final String SERVER_ADDRESS = "localhost";
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private boolean isAuthorized = false;

    private JTextField msgInputField;
    private JTextArea chatArea;

    private AuthenticationDialog authDialog;
    private JMenuItem menuItemConnect;
    private JMenuItem menuItemDisconnect;

    private final Object lock = new Object();

    private static final String errSPM = "/errorSPM";   // error send private message

    public Client() {
        prepareGUI();
        setAuthorized(false);
        tryConnection();
    }

    private void tryConnection() {
        try {
            connection();
        } catch (IOException e) {
            showErrorMessage("Сервер не отвечает");
        }
    }

    private void connection() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                /*while (true) {
                    String messageFromServer = dis.readUTF();
                    if (messageFromServer.startsWith("/authok")) {
                        setAuthorized(true);
                        showInfoMessage("Вы вошли в чат");
                        break;
                    }
                    showErrorMessage(messageFromServer);
                    notify();
                }*/
                readAuthFromServer();
                while (true) {
                    String messageFromServer = dis.readUTF();
                    if(messageFromServer.startsWith(errSPM)) {
                        String errMsg = messageFromServer.substring(errSPM.length());
                        showErrorMessage(errMsg);
                        continue;
                    }
                    chatArea.append(messageFromServer + "\n");
                }
            } catch (IOException ignored) {

            }
        }).start();
        authentication();
    }

    private void setAuthorized(boolean authorized) {
        if(authorized) {
            menuItemConnect.setEnabled(false);
            menuItemDisconnect.setEnabled(true);
            isAuthorized = true;
        } else {
            menuItemConnect.setEnabled(true);
            menuItemDisconnect.setEnabled(false);
            isAuthorized = false;
        }
    }

    private void readAuthFromServer() throws IOException {
        while (true) {
            String messageFromServer = dis.readUTF();
            synchronized (lock) {
                if (messageFromServer.startsWith("/authok")) {
                    setAuthorized(true);
                    lock.notify();
                    break;
                }
                lock.notify();
            }
            showErrorMessage(messageFromServer);
        }
    }

    private void authentication() {
        while(!isAuthorized) {
            synchronized (lock) {
                if (authDialog.showDialog()) {
                    sendMessageToServer("/auth " + authDialog.getLogin() + " " + authDialog.getPassword());
                    authDialog.clearLoginAndPassword();
                } else {
                    sendMessageToServer("/end");
                    return;
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        showInfoMessage("Вы вошли в чат!");
    }

    private void send() {
        String messageToServer = msgInputField.getText();
        if(!messageToServer.trim().isEmpty()) {
            if(isAuthorized) {
                sendMessageToServer(messageToServer);
                msgInputField.setText("");
                msgInputField.grabFocus();
            } else {
                showErrorMessage("Вы не в чате");
            }
        }
    }

    private void sendMessageToServer(String message) {
        try{
            dos.writeUTF(message);
            if(message.equals("/end")) {
                closeConnection();
                showInfoMessage("Соединение разорвано");
            }
        } catch (IOException ignored) {
            showInfoMessage("Соединение разорвано");
        }
    }

    private void closeConnection() {
        setAuthorized(false);
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
        if(socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void showInfoMessage(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Информация", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private void prepareGUI() {
        setMinimumSize(MINIMUM_SIZE);
        setSize(MINIMUM_SIZE);
        setLocationRelativeTo(null);
        setTitle("Клиент");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JButton btnSendMsg = new JButton("Отправить");
        bottomPanel.add(btnSendMsg, BorderLayout.EAST);
        msgInputField = new JTextField();
        bottomPanel.add(msgInputField, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        btnSendMsg.addActionListener(e -> send());
        msgInputField.addActionListener(e -> send());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                closeConnection();
            }
        });

        authDialog = new AuthenticationDialog(this);

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        JMenu menuServer = new JMenu("Сервер");
        menuBar.add(menuServer);
        menuItemConnect = new JMenuItem("Подключиться");
        menuItemConnect.addActionListener(e -> tryConnection());
        menuServer.add(menuItemConnect);

        menuItemDisconnect = new JMenuItem("Отключиться");
        menuItemDisconnect.addActionListener(e -> sendMessageToServer("/end"));
        menuServer.add(menuItemDisconnect);
        setVisible(true);
        msgInputField.grabFocus();
    }
}

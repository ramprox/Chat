package clientside.service;

import clientside.model.ConnectionInfo;
import clientside.model.HistoryWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

public class Client extends JFrame {
    private static final Dimension MINIMUM_SIZE = new Dimension(400, 400);
    private final Integer SERVER_PORT = 8081;
    private final String SERVER_ADDRESS = "localhost";
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final ConnectionInfo connectionInfo = new ConnectionInfo();

    private JTextField msgInputField;
    private JTextArea chatArea;
    private JMenuItem menuItemConnect;
    private JMenuItem menuItemDisconnect;

    // команды
    private static final String END = "/end";                           // отключить соединение

    // ответы от сервера
    private static final String AUTH_OK = "/authok ";                   // успешная авторизация
    private static final String TIMEOUT_AUTH = "/timeoutauth";          // время для авторизации истекло
    private static final String TIMEOUT_ACTIVITY = "/timeoutactivity";  // длительный простой
    private static final String CHANGE_NICK_OK = "/chnickok ";          // успешная смена ника
    private static final String ERR_SPM = "/errorSPM ";                 // ошибка при отправке личного сообщения
    private static final String CLIENTS = "/clients ";                  // список онлайн клиентов
    private static final String ERR_CHANGE_NICK = "/errchnick ";        // ошибка при смене ника
    private static final String NOTIFY = "/notify ";                    // уведомление

    private HistoryWriter historyWriter;                                // писатель истории сообщений

    public Client() {
        prepareGUI();
        setConnected(false);
        tryConnection();
    }

    /**
     * Попытка установить соединение
     */
    private void tryConnection() {
        try {
            connection();
        } catch (IOException e) {
            showErrorMessage("Сервер не отвечает");
        }
    }

    /**
     * Устанавливает соединение
     * @throws IOException - если возникли неполадки во время установления соединения
     */
    private void connection() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        setConnected(true);

        new Thread(() -> {
            try {
                authentication();
                readMessageFromServer();
            } catch (IOException ignored) {
                closeConnection(true, "Соединение разорвано");
            } catch (TimeoutException e) {
                closeConnection(false, e.getMessage());
            } finally {
                setTitle("Клиент");
            }
        }).start();
    }

    /**
     * Цикл аутентификации
     * @throws IOException, если какие то неполадки во время чтения сообщения от сервера
     */
    private void authentication() throws IOException, TimeoutException {
        while (true) {
            String messageFromServer = dis.readUTF();
            if (messageFromServer.startsWith(AUTH_OK)) {
                String[] arr = messageFromServer.split("\\s");
                connectionInfo.setAuthorized(true);
                historyWriter = new HistoryWriter(arr[2], 100);
                String lastMessages = historyWriter.getLastMessages();
                chatArea.setText("");
                chatArea.append(lastMessages);
                setTitle(arr[1]);
                EventQueue.invokeLater(() -> showInfoMessage("Вы вошли в чат. Ваш ник " + arr[1]));
                break;
            }
            if(messageFromServer.startsWith(TIMEOUT_AUTH)) {
                throw new TimeoutException("Время для авторизации истекло");
            }
            EventQueue.invokeLater(() -> showErrorMessage(messageFromServer));
        }
    }

    /**
     * Цикл чтения сообщений от сервера после успешной аутентификации
     * @throws IOException, если какие то неполадки во время чтения сообщения от сервера
     */
    private void readMessageFromServer() throws IOException, TimeoutException {
        while (true) {
            String messageFromServer = dis.readUTF();
            if(isServiceMessage(messageFromServer)) {
                handleServiceMessage(messageFromServer);
                continue;
            }
            historyWriter.write(messageFromServer + "\n");
            chatArea.append(messageFromServer + "\n");
        }
    }

    /**
     * Метод, определяющий является ли сообщение от сервера служебным сообщением
     * @param message - сообщение от сервера
     * @return true - если сообщение является служебным сообщением, false - в противном случае
     */
    private boolean isServiceMessage(String message) {
        return message.trim().startsWith("/");
    }

    /**
     * Метод обработки служебных сообщений от сервера
     * @param message - служебное сообщение от сервера
     */
    private void handleServiceMessage(String message) throws TimeoutException {
        if(message.startsWith(ERR_SPM)) {
            String errMsg = message.substring(ERR_SPM.length());
            showErrorMessage(errMsg);
        }
        if(message.startsWith(CLIENTS)) {
            String[] arr = message.split("\\s", 2);
            message = "[Список онлайн пользователей]: " + arr[1];
            chatArea.append(message + "\n");
        }
        if(message.startsWith(CHANGE_NICK_OK)) {
            String newNick = message.substring(CHANGE_NICK_OK.length());
            showInfoMessage("Вы успешно изменили nick на " + newNick);
            setTitle(newNick);
        }
        if(message.startsWith(NOTIFY)) {
            String[] arr = message.split("\\s", 2);
            chatArea.append(arr[1] + "\n");
        }
        if(message.startsWith(ERR_CHANGE_NICK)) {
            String errMsg = message.substring(ERR_CHANGE_NICK.length());
            showErrorMessage(errMsg);
        }
        if(message.startsWith(TIMEOUT_ACTIVITY)) {
            throw new TimeoutException("Соединение разорвано по причине длительного простоя");
        }
    }

    /**
     * Устанавливает состояние соединения клиента
     * @param connected - значение типа boolean. true - если соединение установлено, false - в противном случае
     */
    private void setConnected(boolean connected) {
        connectionInfo.setConnected(connected);
        if(connected) {
            menuItemConnect.setEnabled(false);
            menuItemDisconnect.setEnabled(true);
        } else {
            menuItemConnect.setEnabled(true);
            menuItemDisconnect.setEnabled(false);
        }
    }

    /**
     * Отправляет сообщение набранное в поле msgInputField
     */
    private void send() {
        String messageToServer = msgInputField.getText();
        if(!messageToServer.trim().isEmpty()) {
            if(connectionInfo.isConnected()) {
                sendMessageToServer(messageToServer);
                msgInputField.setText("");
                msgInputField.grabFocus();
            } else {
                showErrorMessage("Вы не в сети");
            }
        }
    }

    /**
     * Отправляет сообщение на сервер
     * @param message - сообщение
     */
    private void sendMessageToServer(String message) {
        try {
            dos.writeUTF(message);
            if(message.equals(END)) {
                closeConnection(false, "Соединение разорвано");
            }
        } catch (IOException ignored) {
            closeConnection(true, "Соединение разорвано");
        }
    }

    /**
     * Закрывает соединение и показывает сообщение в окне
     * @param isError указывает, закрывается ли соединение в результате ошибки (true) или по команде клиента (false)
     * @param message - выводимое в окно сообщение
     */
    private void closeConnection(boolean isError, String message) {
        if(closeConnection()) {
            if(isError) {
                showErrorMessage(message);
            } else {
                showInfoMessage(message);
            }
        }
    }

    /**
     * Закрывает соединение
     * @return true - если соединение не было закрыто до вызова этого метода, false - если соединение уже закрыто
     */
    private boolean closeConnection() {
        if(connectionInfo.isConnected()) {
            setConnected(false);
            closeDataInputStream();
            closeDataOutputStream();
            closeSocket();
            closeHistoryWriterService();
            return true;
        }
        return false;
    }

    private void closeDataInputStream() {
        if(dis != null) {
            try {
                dis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeDataOutputStream() {
        if(dos != null) {
            try {
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeSocket() {
        if(socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeHistoryWriterService() {
        if(historyWriter != null) {
            historyWriter.close();
        }
    }

    /**
     * Показывает в окне с пометкой "Информация" сообщение
     * @param message - выводимое сообщение
     */
    private void showInfoMessage(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Информация", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Показывает в окне с пометкой "Ошибка" сообщение
     * @param message - выводимое сообщение
     */
    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Подготовка GUI
     */
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

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        JMenu menuServer = new JMenu("Сервер");
        menuBar.add(menuServer);
        menuItemConnect = new JMenuItem("Подключиться");
        menuItemConnect.addActionListener(e -> tryConnection());
        menuServer.add(menuItemConnect);

        menuItemDisconnect = new JMenuItem("Отключиться");
        menuItemDisconnect.addActionListener(e -> sendMessageToServer(END));
        menuServer.add(menuItemDisconnect);
        setVisible(true);
        msgInputField.grabFocus();
    }
}

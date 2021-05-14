import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client extends JFrame {
    private static final Dimension MINIMUM_SIZE = new Dimension(400, 400);
    private static final String END_SESSION = "/finish";
    private static final String END_MESSAGE = "/eom";    // end of message

    private JTextArea messagesArea;
    private JTextField sendTxtField;
    private JLabel statusLabel;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;

    public Client() {
        initializeGUI();
        connect();
    }

    private void connect() {
        try {
            socket = new Socket("localhost", 8189);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            statusLabel.setText("Соединение с сервером установлено.");
            new Thread(this::ReadMessagesFromServer).start();
        } catch (IOException ex) {
            statusLabel.setText("Не удалось установить соединение.");
        }
    }

    private void ReadMessagesFromServer() {
        try {
            while(true) {
                String messageFromServer = dis.readUTF();
                if(messageFromServer.equalsIgnoreCase(END_SESSION)) {
                    dos.writeUTF(messageFromServer);
                    break;
                }
                messagesArea.append("Server: " + messageFromServer + "\n\n");
            }
        } catch (IOException ignored) {

        } finally {
            statusLabel.setText("Соединение разорвано");
            close();
        }
    }

    private void close() {
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

    private void initializeGUI() {
        setTitle("Чат");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(MINIMUM_SIZE);
        setSize(MINIMUM_SIZE);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                close();
            }
        });
        addStatusText();
        addCenterPanel();
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(e -> {
            if(e.getKeyChar() == KeyEvent.VK_ENTER) {
                sendMessage();
            }
            return false;
        });
        setVisible(true);
        sendTxtField.requestFocusInWindow();
    }

    private void addStatusText() {
        statusLabel = new JLabel();
        statusLabel.setText("Готово");
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void addCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        addSendMessagePanel(panel);
        addMessagesHystoryPanel(panel);
        add(panel, BorderLayout.CENTER);
    }

    private void addSendMessagePanel(JPanel parentPanel) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        sendTxtField = new JTextField();
        sendTxtField.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        panel.add(sendTxtField, BorderLayout.CENTER);
        JButton button = new JButton("Отправить");
        button.addActionListener(e -> sendMessage());
        panel.add(button, BorderLayout.EAST);
        parentPanel.add(panel, BorderLayout.SOUTH);
    }

    private void addMessagesHystoryPanel(JPanel parentPanel) {
        JPanel hystoryPanel = new JPanel();
        hystoryPanel.setLayout(new BorderLayout());
        hystoryPanel.setAutoscrolls(true);
        messagesArea = new JTextArea();
        messagesArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messagesArea);
        hystoryPanel.add(scrollPane);
        parentPanel.add(hystoryPanel, BorderLayout.CENTER);
    }

    private void sendMessage() {
        String text = sendTxtField.getText();
        if(!text.trim().isEmpty()) {
            try {
                dos.writeUTF(text);
                messagesArea.append("Client: " + text + "\n\n");
                sendTxtField.setText("");
            } catch (IOException ex) {
                statusLabel.setText("Соединение разорвано");
            }
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(Client::new);
    }
}

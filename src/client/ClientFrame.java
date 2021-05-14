package client;

import javax.swing.*;
import javax.swing.border.StrokeBorder;
import java.awt.*;
import java.awt.event.*;

public class ClientFrame extends JFrame {

    private static final Dimension MINIMUM_SIZE = new Dimension(400, 400);

    private JTextArea messages;
    private JTextField txt;

    public ClientFrame() {
        setTitle("Чат");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(MINIMUM_SIZE);
        setSize(MINIMUM_SIZE);
        setLocationRelativeTo(null);
        addEditMessagePanel();
        addMessagesHystoryPanel();
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(e -> {
            if(e.getKeyChar() == KeyEvent.VK_ENTER) {
                sendMessage();
            }
            return false;
        });
        setVisible(true);
        txt.requestFocusInWindow();
    }

    private void addEditMessagePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        txt = new JTextField();
        txt.setBorder(new StrokeBorder(new BasicStroke(1f)));
        txt.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if(e.getKeyChar() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });
        panel.add(txt, BorderLayout.CENTER);
        JButton button = new JButton("Отправить");
        button.addActionListener(e -> sendMessage());
        panel.add(button, BorderLayout.EAST);
        add(panel, BorderLayout.SOUTH);
    }

    private void addMessagesHystoryPanel() {
        JPanel hystoryPanel = new JPanel();
        hystoryPanel.setLayout(new BorderLayout());
        messages = new JTextArea();
        messages.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messages);
        hystoryPanel.add(scrollPane);
        int i = hystoryPanel.getWidth();
        add(hystoryPanel, BorderLayout.CENTER);
    }

    private void sendMessage() {
        String text = txt.getText();
        if(!text.isEmpty()) {
            txt.setText("");
            messages.append(text + "\n\n");
        }
    }
}

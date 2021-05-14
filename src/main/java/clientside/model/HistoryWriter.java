package clientside.model;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class HistoryWriter {
    private RandomAccessFile raf;
    private final int lastMessagesCount;
    private File historyFile;
    private long currentMessagesCount;    // текущее количество сообщений
    private long pointerLastMessages;     // указатель на место в файле истории,
                                          // откуда начинаются последние 100 сообщений

    /**
     * Конструирует новый объект HistoryWriter
     * Создает файл истории сообщений если он не существует и загружает последние 100 сообщений
     * Структура файла истории:
     * Первая строка - указатель типа long на место в файле, откуда начинаются последние 100 сообщений
     * Следующие строки - сами сообщения
     * @param login логин
     * @param lastMessagesCount количество последних сообщений, доступных для загрузки
     */
    public HistoryWriter(String login, int lastMessagesCount) throws IOException {
        this.lastMessagesCount = lastMessagesCount;
        File historiesDir = new File("Clients Histories");
        historiesDir.mkdir();
        historyFile = new File(historiesDir.getPath() + "//history_" + login + ".txt");
        raf = new RandomAccessFile(historyFile, "rw");
        if(historyFile.length() == 0) {
            pointerLastMessages = 10;
            raf.writeLong(pointerLastMessages);
            raf.writeChar('\n');
        } else {
            pointerLastMessages = raf.readLong();
            raf.seek(pointerLastMessages);
        }
    }

    public void close() {
        if(raf != null) {
            try {
                raf.seek(0);
                raf.writeLong(pointerLastMessages);
                raf.close();
                raf = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(String message) throws IOException {
        raf.writeUTF(message);
        currentMessagesCount++;

        if(currentMessagesCount > lastMessagesCount) {
            raf.seek(pointerLastMessages);
            raf.readUTF();
            pointerLastMessages = raf.getFilePointer();
            raf.seek(historyFile.length());
        }
    }

    public String getLastMessages() throws IOException {
        StringBuilder builder = new StringBuilder();
        raf.seek(pointerLastMessages);
        while(raf.getFilePointer() != historyFile.length()) {
            builder.append(raf.readUTF());
            currentMessagesCount++;
        }
        return builder.toString();
    }
}

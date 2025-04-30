package ru.nsu.server;

//import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import ru.nsu.server.worker.MessageWorkerPool;
import ru.nsu.server.database.DataBaseManager;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Scanner;

public class Server extends WebSocketServer {

    private final Map<WebSocket, String> userSessions = new ConcurrentHashMap<>();
    private final MessageWorkerPool workerPool = new MessageWorkerPool(4);
    private final DataBaseManager storage = new DataBaseManager();
    private final ObjectMapper mapper = new ObjectMapper(); // для работы с JSON

    public Server(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection from " + conn.getRemoteSocketAddress());
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "INIT");
        node.put("message", "");
        node.put("username", "");
        node.put("room", "");

        try {
            mapper.writeValueAsString(node);
        } catch (Exception e) {
            System.out.println("Ошибка при сериализации json "+ e.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection: " + reason);
        userSessions.remove(conn);
        if (conn != null && conn.isOpen()) {
            conn.close(1000);
        }
        workerPool.submit(() -> {
            try {
                String[] parts = userSessions.get(conn).split("@");
                MessageHandler.handleDisconnect(userSessions, storage, userSessions.get(conn), parts[1]);
                userSessions.remove(conn);
            } catch (Exception e) {
                System.err.println("Ошибка при обработке отключения:" + e.getMessage());
            }
        });
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Сообщение:" + message);
        workerPool.submit(() -> {
            try {
                MessageHandler.handleMessage(conn, message, userSessions, storage);
            } catch (Exception e) {
                System.err.println("Ошибка при обработке сообщения:" + message);
            }
        });
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (ex != null) {
            System.err.println("Ошибка: " + ex.getMessage());
        } else {
            System.err.println("Неизвестная ошибка");
        }

        // Если соединение открыто, закрываем его
        if (conn != null && conn.isOpen()) {
            conn.close(1011, ex != null ? ex.getMessage() : "Неизвестная ошибка");
        }
    }

    @Override
    public void onStart() {
        System.out.println("Сервер начал свою работу!");
    }

    public void stopServer() {
        workerPool.shutdown();
        for (WebSocket conn : userSessions.keySet()) {
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close(1000);
                    System.out.println("Соединение с клиентом закрыто");
                }
            } catch (Exception e) {
                System.err.println("Ошибка при закрытии соединения: " + e.getMessage());
            }
        }
        try {
            this.stop();
            System.out.println("Сервер остановлен!");
        } catch (InterruptedException e) {
            System.err.println("Ошибка при остановке сервера: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

    }

    public void listenForExitCommand() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input)) {
                    stopServer();
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        int port = 8888; // Указываем порт для сервера
        Server server = new Server(port);

        server.start();
        // Ожидаем команду от пользователя для завершения работы сервера
        server.listenForExitCommand();
    }
}

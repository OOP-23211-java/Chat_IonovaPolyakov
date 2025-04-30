package ru.nsu.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import ru.nsu.server.database.DataBaseManager;

import java.util.Map;

public class MessageHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void handleMessage(WebSocket conn, String message,
                                     Map<WebSocket, String> usernames,
                                     DataBaseManager storage) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Задача прервана до обработки");
                return;
            }
            JsonNode json = mapper.readTree(message);
            String type = json.get("type").asText();
            String room = json.get("room").asText();
            String username = json.get("username").asText();
            String content = json.get("content").asText();
            String jsonString;

            switch (type) {
                case "JOIN":
                    handleJoin(conn, username, room, usernames, storage);
                    break;

                case "MESSAGE":
                    if (!usernames.containsKey(conn)) {
                        jsonString = buildJsonString("ERROR", "Join a room first " + room, username, room);
                        if (jsonString != null) {
                            conn.send(jsonString);
                        }
                        return;
                    }

                    for (Map.Entry<WebSocket, String> entry : usernames.entrySet()) {
                        String[] parts = entry.getValue().split("@");
                        if (parts[1].equals(room)) {
                            jsonString = buildJsonString("MESSAGE", content, username, room);
                            if (jsonString != null && !username.equals(parts[0])) {
                                entry.getKey().send(jsonString);
                            }
                            if(parts[0].equals(username)){
                                storage.addMessage(room, jsonString);
                            }
                        }
                    }
                    break;

                default:
                    jsonString = buildJsonString("ERROR", "Unknown message type: " + type, username, room);
                    if (jsonString != null) {
                        conn.send(jsonString);
                    }
                    break;
            }
        } catch (Exception e) {
            String errorJson = buildJsonString("ERROR", e.getMessage(), "", "");
            if (errorJson != null) {
                conn.send(errorJson);
            }
        }
    }
   // Новый метод для обработки присоединения пользователя
    private static void handleJoin(WebSocket conn, String username, String room,
                                   Map<WebSocket, String> usernames, DataBaseManager storage) {
        String jsonString;

        // Попытка присоединиться к комнате
        boolean joined = storage.joinRoom(room, username);
        if (joined) {
            usernames.put(conn, username + "@" + room);

            jsonString = buildJsonString("USER_JOINED", "", username, room);
            sendHistory(room, username, conn, storage);
            //storage.addMessage(room, jsonString);
            //System.out.println("Сообщение добавлено " + jsonString);

            // Уведомляем остальных пользователей в комнате о новом присоединившемся
            for (Map.Entry<WebSocket, String> entry : usernames.entrySet()) {
                String[] parts = entry.getValue().split("@");
                if (parts[1].equals(room)) {
                    jsonString = buildJsonString("USER_JOINED", "",username, room);
                    if (jsonString != null) {
                        entry.getKey().send(jsonString);
                    }
                }
            }
        } else {
            // Если имя пользователя уже занято в комнате
            jsonString = buildJsonString("ERROR", "Username already taken in this room " + room, username, room);
            if (jsonString != null) {
                conn.send(jsonString);
            }
        }
    }

    // Метод для отправки истории сообщений
    public static void sendHistory(String room, String username, WebSocket conn, DataBaseManager storage) {
      //  System.out.println("Отправляю историю для пользователя " + username + " в комнате " + room);
        // Получаем историю сообщений для этой комнаты
        String history = storage.getHistory(room);
        // Создаем JSON для истории
        String jsonHistory = buildJsonString("HISTORY", history, username, room);
        System.out.println(jsonHistory);
        if (jsonHistory != null) {
            conn.send(jsonHistory); // Отправляем историю клиенту
        }
    }

    // Метод для построения строки JSON
    private static String buildJsonString(String type, String message, String username, String room) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", type);
            node.put("content", message);
            node.put("username", username);
            node.put("room", room);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            System.out.println("Ошибка при сериализации json: " + e.getMessage());
            return null;
        }
    }

    }


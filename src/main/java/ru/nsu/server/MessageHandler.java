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
            JsonNode json = mapper.readTree(message);
            //System.out.println("Обработал json");
            String type = json.get("type").asText();
            String room = json.get("room").asText();
            String username = json.get("username").asText();
            String jsonString;

            switch (type) {
                case "JOIN":
                    boolean joined = true; // заглушка
                    if (joined) {
                        usernames.put(conn, username + "@" + room);
                        for (Map.Entry<WebSocket, String> entry : usernames.entrySet()) {
                            if (entry.getValue().endsWith("@" + room)) {
                                jsonString = buildJsonString("USER_JOINED", "", username, room);
                                if (jsonString != null) {
                                    entry.getKey().send(jsonString);
                                }
                            }
                        }
                    } else {
                        jsonString = buildJsonString("ERROR", "Username already taken in this room " + room, username, room);
                        if (jsonString != null) {
                            conn.send(jsonString);
                        }
                    }
                    break;

                case "MESSAGE":
                    if (!usernames.containsKey(conn)) {
                        jsonString = buildJsonString("ERROR", "Join a room first " + room, username, room);
                        if (jsonString != null) {
                            conn.send(jsonString);
                        }
                        return;
                    }

                    String msgText = json.get("MESSAGE").asText();
                    //storage.addMessage(room, username, msgText);

                    for (Map.Entry<WebSocket, String> entry : usernames.entrySet()) {
                        if (entry.getValue().endsWith("@" + room)) {
                            jsonString = buildJsonString("MESSAGE", msgText, username, room);
                            if (jsonString != null && entry.getValue()!= (username + "@" + room )) {
                                entry.getKey().send(jsonString);
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

            errorJson = buildJsonString("ERROR", "Invalid format message", "", "");
            if (errorJson != null) {
                conn.send(errorJson);
            }
        }
    }

    private static String buildJsonString(String type, String message, String username, String room) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", type);
            node.put("message", message);
            node.put("username", username);
            node.put("room", room);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            System.out.println("Ошибка при сериализации json: " + e.getMessage());
            return null;
        }
    }
}

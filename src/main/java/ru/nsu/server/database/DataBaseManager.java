package ru.nsu.server.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataBaseManager {
    private static final int MAX_MESSAGES = 4;
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> messages = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper для парсинга и сериализации

    public synchronized boolean joinRoom(String room, String username) {
        rooms.putIfAbsent(room, new HashSet<>());
        Set<String> users = rooms.get(room);
        if (users.contains(username)) {
            return false;
        }
        users.add(username);
        return true;
    }

    // сюда приходит сериализованный JSON
    public synchronized void addMessage(String room, String serializedJsonMessage) {
        messages.putIfAbsent(room, new LinkedList<>());
        Deque<String> history = messages.get(room);

        try {
            JsonNode node = objectMapper.readTree(serializedJsonMessage);
            String type = node.get("type").asText();
            String username = node.get("username").asText();
            String message = node.get("content").asText();
            if(message.isEmpty()){
                message = " ";
            }

            String formatted = type + ":" + username + ":" + message;

            if (history.size() >= MAX_MESSAGES) {
                history.pollFirst();
            }
            history.addLast(formatted);
        } catch (Exception e) {
            System.out.println("Ошибка при добавлении сообщения: " + e.getMessage());
        }
    }


    // возвращает один JSON, в котором content — строка с сообщениями, разделёнными \n
    public synchronized String getHistory(String room) {
        Deque<String> history = messages.getOrDefault(room, new LinkedList<>());
        return String.join("\n", history);  // Соединяем сообщения с разделителем \n
    }

    public synchronized boolean isInRoom(String room, String username) {
        return rooms.containsKey(room) && rooms.get(room).contains(username);
    }

    public synchronized void leaveRoom(String room, String username) {
        if (rooms.containsKey(room)) {
            rooms.get(room).remove(username);
        }
    }

    public synchronized String debugInfo() {
        StringBuilder debugOutput = new StringBuilder();

        debugOutput.append("=== Debug Info ===\n");

        debugOutput.append("\n--- Rooms ---\n");
        if (rooms.isEmpty()) {
            debugOutput.append("No rooms available.\n");
        } else {
            for (Map.Entry<String, Set<String>> entry : rooms.entrySet()) {
                debugOutput.append(String.format("Room: %-20s | Users: %s\n", entry.getKey(), entry.getValue()));
            }
        }

        debugOutput.append("\n--- Messages ---\n");
        if (messages.isEmpty()) {
            debugOutput.append("No messages available.\n");
        } else {
            for (Map.Entry<String, Deque<String>> entry : messages.entrySet()) {
                debugOutput.append(String.format("Room: %-20s | Messages: %s\n", entry.getKey(), entry.getValue()));
            }
        }

        debugOutput.append("\n===================\n");
        return debugOutput.toString();
    }
}

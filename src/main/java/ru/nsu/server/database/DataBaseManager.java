package ru.nsu.server.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataBaseManager {

    private static final int MAX_MESSAGES = 4;

    private final Map<String, Set<String>> rooms     = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> history = new ConcurrentHashMap<>();

    private final DataBase db;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataBaseManager(DataBase db) {
        this.db = db;
    }

    public synchronized boolean joinRoom(String room, String user) {
        rooms.putIfAbsent(room, new HashSet<>());
        return rooms.get(room).add(user);
    }

    public synchronized boolean isInRoom(String room, String user) {
        return rooms.containsKey(room) && rooms.get(room).contains(user);
    }

    public synchronized void leaveRoom(String room, String user) {
        if (rooms.containsKey(room)) rooms.get(room).remove(user);
    }

    public synchronized void addMessage(String room, String jsonMessage) {
        try {
            JsonNode node     = mapper.readTree(jsonMessage);
            String user       = node.get("username").asText();
            String text       = node.get("content").asText("");
            if (text.isEmpty()) text = " ";

            // 1) сохраняем в SQLite
            db.insertMessage(user, room, text);

            // 2) обновляем in-memory кэш
            history.putIfAbsent(room, new ArrayDeque<>());
            Deque<String> deque = history.get(room);
            if (deque.size() >= MAX_MESSAGES) deque.pollFirst();
            deque.addLast(user + ':' + text);

        } catch (Exception e) {
            System.err.println("addMessage: " + e.getMessage());
        }
    }

    public synchronized String getHistory(String room) {
        this.warmUp(room);
        Deque<String> deque = history.getOrDefault(room, new ArrayDeque<>());
        return String.join("\n", deque);
    }

    public synchronized void warmUp(String room) {
        try {
            List<String> last = db.loadLast(room, MAX_MESSAGES);
            history.put(room, new ArrayDeque<>(last));
        } catch (Exception e) {
            System.err.println("warmUp: " + e.getMessage());
        }
    }
}

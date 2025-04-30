package ru.nsu.server.database;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataBaseManager{
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>(); // roomName -> usernames
    private final Map<String, List<String>> messages = new ConcurrentHashMap<>(); // roomName -> messages

    public synchronized boolean joinRoom(String room, String username) {
        rooms.putIfAbsent(room, new HashSet<>());
        Set<String> users = rooms.get(room);
        if (users.contains(username)) return false;
        users.add(username);
        return true;
    }

    public synchronized void addMessage(String room, String username, String message) {
        messages.putIfAbsent(room, new ArrayList<>());
        messages.get(room).add(username + ": " + message);
    }

    public List<String> getMessages(String room) {
        return messages.getOrDefault(room, List.of());
    }

    public boolean isInRoom(String room, String username) {
        return rooms.containsKey(room) && rooms.get(room).contains(username);
    }

    public void leaveRoom(String room, String username) {
        if (rooms.containsKey(room)) {
            rooms.get(room).remove(username);
        }
    }
}

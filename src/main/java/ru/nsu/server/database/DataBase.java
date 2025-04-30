package ru.nsu.server.database;

import java.sql.*;

public class DataBase {
    private final String url;
    private Connection conn;

    public DataBase(String filePath) {
        this.url = "jdbc:sqlite:" + filePath;
    }

    public void connect() throws SQLException {
        conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                  id         INTEGER PRIMARY KEY AUTOINCREMENT,
                  name       TEXT NOT NULL,
                  roomNumber TEXT NOT NULL,
                  message    TEXT NOT NULL,
                  ts         DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);
        }
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public void insertMessage(String name, String roomNumber, String message)
            throws SQLException
    {
        String sql = "INSERT INTO messages(name, roomNumber, message) VALUES(?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, roomNumber);
            ps.setString(3, message);
            ps.executeUpdate();
        }
    }

    public java.util.List<String> loadLast(String roomNumber, int limit)
            throws SQLException
    {
        String sql = """
            SELECT name || ':' || message AS line
              FROM messages
             WHERE roomNumber = ?
             ORDER BY id DESC
             LIMIT ?;
        """;
        java.util.List<String> list = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomNumber);
            ps.setInt   (2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("line"));
        }
        java.util.Collections.reverse(list);        // по возрастанию id
        return list;
    }

    public Connection getConnection() { return conn; }
}

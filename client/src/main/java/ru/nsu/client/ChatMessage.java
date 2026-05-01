/**
 * <h2>ChatMessage</h2>
 * <p>
 * DTO‑класс, описывающий структуру JSON‑сообщений, которыми обмениваются
 * клиент и сервер чата. Содержит четыре поля:
 * <ul>
 *   <li><b>type</b> &nbsp;— тип сообщения (<code>MESSAGE</code>, <code>JOIN</code>, <code>ERROR</code> и&nbsp;т.&nbsp;д.);</li>
 *   <li><b>username</b> &nbsp;— имя отправителя;</li>
 *   <li><b>room</b> &nbsp;— идентификатор комнаты;</li>
 *   <li><b>content</b> &nbsp;— текст сообщения / произвольный payload.</li>
 * </ul>
 * Для (де)сериализации используется {@link com.google.gson.Gson}.
 * </p>
 */
package ru.nsu.client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ChatMessage {
    /** Singleton‑экземпляр {@link Gson} для преобразования объектов в JSON и обратно. */
    private static final Gson GSON = new Gson();

    private String type;
    private String username;
    private String room;
    private String content;

    /**
     * Пустой конструктор необходим Gson для рефлексивной десериализации.
     */
    public ChatMessage() {}

    /**
     * Полный конструктор для ручного создания сообщения.
     *
     * @param type     тип сообщения (<em>MESSAGE</em>, <em>JOIN</em>, <em>ERROR</em> …)
     * @param content  текст / тело сообщения
     * @param username имя пользователя‑отправителя
     * @param room     комната/канал, к которому относится сообщение
     */
    public ChatMessage(String type, String content, String username, String room) {
        this.type     = type;
        this.content  = content;
        this.username = username;
        this.room     = room;
    }

    /**
     * Сериализует текущий объект в JSON‑строку.
     * @return валидный JSON‐текст
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Десериализует JSON‑строку в {@link ChatMessage}.
     *
     * @param json исходный JSON‑текст
     * @return объект {@link ChatMessage}
     * @throws JsonSyntaxException если строка не соответствует ожидаемому формату
     */
    public static ChatMessage fromJson(String json) throws JsonSyntaxException {
        return GSON.fromJson(json, ChatMessage.class);
    }

    /* ----------------- простые геттеры ----------------- */

    /** @return значение поля {@code type}. */
    public String getType()     { return type; }
    /** @return значение поля {@code username}. */
    public String getUsername() { return username; }
    /** @return значение поля {@code room}. */
    public String getRoom()     { return room; }
    /** @return значение поля {@code content}. */
    public String getContent()  { return content; }
}

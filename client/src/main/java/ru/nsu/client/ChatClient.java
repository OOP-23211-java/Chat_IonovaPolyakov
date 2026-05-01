/**
 * <h2>ChatClient</h2>
 * <p>
 * Клиент WebSocket, отвечающий за подключение к&nbsp;серверу, отправку и приём
 * JSON-сообщений формата <code>{type, username, room, message}</code>.
 * Реализован через API JSR‑356 при помощи аннотаций {@link javax.websocket.OnMessage},
 * {@link javax.websocket.OnOpen}, {@link javax.websocket.OnClose} и {@link javax.websocket.OnError}.
 * Все входящие сообщения обрабатываются асинхронно в пуле потоков, чтобы не
 * блокировать контейнерные IO‑потоки.
 * </p>
 */
package ru.nsu.client;

import javax.websocket.*;
import java.net.URI;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@ClientEndpoint
public class ChatClient {
    /* ------------------------- статические поля ------------------------- */

    /** Логгер текущего класса. */
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());

    /** Экземпляр Gson для (де)сериализации сообщений. */
    private static final Gson GSON = new Gson();

    /* --------------------------- состояние ------------------------------ */

    private Session session;                 // активная WebSocket‑сессия
    private final String username;           // имя пользователя
    private String room;                     // номер комнаты
    private final String serverUri;          // полный ws:// URI
    private final ExecutorService executor;  // пул для async‑обработки сообщений
    private CompletableFuture<String> serverStateFuture; // ответ на list

    /** Флаг, выставляемый в {@link #onClose} / {@link #onError}. */
    private volatile boolean closed = false;

    /* ------------------------- конструктор ------------------------------ */

    /**
     * Создаёт клиента и сразу пытается подключиться к&nbsp;серверу.
     *
     * @param serverUri URI вида <code>ws://host:port/chat</code>
     * @param username  имя пользователя, отображаемое в чате
     * @param room      номер комнаты / канала
     * @throws RuntimeException если соединение установить не удалось
     */
    public ChatClient(String serverUri, String username, String room) {
        this.serverUri = serverUri;
        this.username  = username;
        this.room      = room;
        this.executor  = Executors.newFixedThreadPool(4);
        LOGGER.info("Подключение к серверу: " + serverUri);
        if (!connect()) {
            LOGGER.severe("Не удалось подключиться к серверу: " + serverUri);
            throw new RuntimeException("Не удалось подключиться к серверу: " + serverUri);
        }
    }

    /* ----------------------- геттеры/сеттеры ---------------------------- */

    /** @return <code>true</code>, если сессия была закрыта сервером или из‑за ошибки. */
    public boolean isClosed() { return closed; }

    /** @return имя пользователя. */
    public String getUsername() { return username; }

    /**
     * Изменить текущую комнату (канал).
     * @param newRoom новый идентификатор комнаты
     */
    public void setRoom(String newRoom) {
        this.room = newRoom;
        LOGGER.info("Комната изменена на: " + newRoom);
    }

    /* ----------------------- приватная логика --------------------------- */

    /**
     * Подключается к&nbsp;серверу и инициализирует поле {@link #session}.
     * @return <code>true</code>, если соединение установлено успешно
     */
    private boolean connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            this.session = container.connectToServer(this, new URI(serverUri));
            LOGGER.info("Успешно подключено к серверу: " + serverUri);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка подключения к серверу: " + serverUri, e);
            return false;
        }
    }

    /* -------------------- публичные служебные API ----------------------- */

    /**
     * Отправляет на сервер сообщение «JOIN» и тем самым регистрирует пользователя
     * в указанной комнате.
     */
    public void registrationUser() {
        if (session != null && session.isOpen()) {
            ChatMessage msg = new ChatMessage("JOIN", "pop", username, room);
            session.getAsyncRemote().sendText(msg.toJson());
            LOGGER.fine("Отправлено сообщение: " + msg.toJson());
        } else {
            LOGGER.warning("Не удалось отправить: соединение не установлено");
        }
    }

    /**
     * Делает запрос состояния сервера (команда <code>GET_SERVER_STATE</code>) и
     * дожидается ответа не более 10&nbsp;секунд.
     *
     * @return JSON‑строка с состоянием сервера
     * @throws IllegalStateException если соединение не установлено
     * @throws RuntimeException      при таймауте или другой ошибке ожидания
     */
    public String waitForServerState() {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("Соединение не установлено");
        }
        serverStateFuture = new CompletableFuture<>();
        ChatMessage req = new ChatMessage("GET_SERVER_STATE", "", username, room);
        session.getAsyncRemote().sendText(req.toJson());
        try {
            return serverStateFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка при получении состояния сервера", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Отправляет текстовое сообщение в комнату.
     * @param content сам текст
     */
    public void sendMessage(String content) {
        if (session != null && session.isOpen()) {
            ChatMessage msg = new ChatMessage("MESSAGE", content, username, room);
            session.getAsyncRemote().sendText(msg.toJson());
            LOGGER.info("Отправлено сообщение: " + msg.toJson());
        } else {
            LOGGER.warning("Не удалось отправить: соединение не установлено");
        }
    }

    /** Закрывает сессию и аварийно останавливает пул потоков. */
    public void close() {
        executor.shutdownNow();
        if (session != null && session.isOpen()) {
            try {
                session.close();
                LOGGER.info("Сессия закрыта");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Ошибка при закрытии сессии", e);
            }
        }
    }

    /* ------------------ WebSocket callback‑методы ----------------------- */

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        LOGGER.info("WebSocket сессия открыта");
    }

    @OnMessage
    public void onMessage(String message) {
        String trimmed = message.trim();
        LOGGER.fine("[RECV] " + trimmed);

        // ответ на GET_SERVER_STATE
        if (trimmed.startsWith("{") && serverStateFuture != null && !serverStateFuture.isDone()) {
            serverStateFuture.complete(trimmed);
            return;
        }

        executor.submit(() -> {
            try {
                ChatMessage msg = GSON.fromJson(trimmed, ChatMessage.class);
                switch (msg.getType()) {
                    case "MESSAGE"      -> System.out.println(msg.getUsername() + ": " + msg.getContent());
                    case "USER_JOINED"  -> System.out.println(msg.getUsername() + " зашёл на сервер");
                    case "HISTORY"      -> msg.getContent().lines().forEach(System.out::println);
                    case "ERROR"        -> System.err.println("Ошибка: " + msg.getContent());
                    case "INIT"         -> System.out.println("Получен ответ от сервера");
                    default              -> System.out.println("[UNDEF] " + trimmed);
                }
                LOGGER.info("Получено сообщение от " + msg.getUsername() + ": " + msg.getContent());
            } catch (JsonSyntaxException ex) {
                LOGGER.warning("Невалидный JSON: " + trimmed);
            }
        });
    }

    @OnError
    public void onError(Session s, Throwable t) {
        LOGGER.log(Level.SEVERE, "Ошибка WebSocket", t);
        closed = true;
    }

    @OnClose
    public void onClose(CloseReason reason) {
        LOGGER.info("Сессия WebSocket закрыта: " + reason.getReasonPhrase());
        closed = true;
    }
}
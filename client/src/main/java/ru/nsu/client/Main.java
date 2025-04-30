/**
 * <h2>Консольный WebSocket‑клиент</h2>
 * <p>
 * Запускает интерактивную CLI‑сессию: запрашивает логин и номер комнаты,
 * подключается к серверу по указанному URI, регистрируется и позволяет
 * обмениваться сообщениями. Управление:
 * </p>
 * <ul>
 *   <li><b>list</b> &nbsp;— запросить состояние сервера (список пользователей / статистику);</li>
 *   <li><b>exit</b> &nbsp;— корректно закрыть соединение и завершить программу;</li>
 *   <li>любая другая строка — отправляется как текстовое сообщение.</li>
 * </ul>
 * <p>
 * Логи пишутся через {@link LoggerConfig} исключительно в&nbsp;<code>app.log</code>.
 * </p>
 */
package ru.nsu.client;

import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Main {
    /** Порт, на котором запущен WebSocket‑сервер. */
    private static final int PORT = 8889;
    /** IP‑адрес или DNS‑имя сервера. */
    private static final String SERVER_IP = "192.168.0.240";
    /** Шаблон для построения URI WebSocket‑соединения. */
    private static final String SERVER_URI_TEMPLATE = "ws://%s:%d/chat";

    /** Логгер приложения, настроенный в {@link LoggerConfig}. */
    private static final Logger LOGGER = LoggerConfig.getLogger(Main.class.getName());

    /**
     * Точка входа. Считывает учётные данные, устанавливает соединение и запускает
     * главный цикл чата.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        String serverUri = String.format(SERVER_URI_TEMPLATE, SERVER_IP, PORT);
        LOGGER.info("Сервер: " + serverUri);
        System.out.println("====== Chat Client ======");

        try (Scanner scanner = new Scanner(System.in)) {
            /* --- ввод имени пользователя --- */
            System.out.print("Введите имя: ");
            String username = scanner.nextLine().trim();
            LOGGER.info("Пользователь: " + username);

            /* --- ввод номера комнаты --- */
            System.out.print("Введите номер комнаты : ");
            String room = scanner.nextLine().trim();
            LOGGER.info("Комната: " + room);

            /* --- попытки подключения --- */
            ChatClient client = null;
            while (client == null) {
                LOGGER.info("Пытаемся подключиться к " + serverUri);
                System.out.println("Подключение к серверу: " + serverUri);
                try {
                    client = new ChatClient(serverUri, username, room);
                    LOGGER.info("Подключение успешно");
                } catch (RuntimeException e) {
                    LOGGER.severe("Не удалось подключиться: " + e.getMessage());
                    System.out.print("Повторить подключение? (y/N): ");
                    String answer = scanner.nextLine().trim();
                    if (!answer.equalsIgnoreCase("y")) {
                        LOGGER.info("Выход по запросу пользователя");
                        return;
                    }
                }
            }

            /* --- подтверждение/смена комнаты --- */
            room = confirmRoom(scanner, client, room);

            /* --- основной цикл --- */
            LOGGER.info("Запускаем основной цикл чата");
            client.registrationUser();
            chatLoop(scanner, client);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка в main", e);
        }
    }

    /**
     * Уточняет комнату перед вступлением в чат.
     *
     * @param scanner      {@link Scanner} для чтения ввода пользователя
     * @param client       клиент, в который надо передать новый номер комнаты
     * @param currentRoom  номер комнаты, введённый изначально
     * @return окончательный идентификатор комнаты
     */
    private static String confirmRoom(Scanner scanner, ChatClient client, String currentRoom) {
        LOGGER.info("Проверка текущей комнаты: " + currentRoom);
        System.out.printf("Текущая комната: %s. Оставить? (y/n): ", currentRoom);
        String answer = scanner.nextLine().trim();
        if (answer.equalsIgnoreCase("y")) {
            return currentRoom;
        } else {
            System.out.print("Введите новый номер комнаты (число): ");
            String newRoom = scanner.nextLine().trim();
            client.setRoom(newRoom);
            LOGGER.info("Комната изменена на: " + newRoom);
            return newRoom;
        }
    }

    /**
     * Главный цикл чтения ввода/вывода. Следит за состоянием соединения:
     * если сервер закроет WebSocket, цикл завершится.
     *
     * @param scanner {@link Scanner} для ввода пользователя
     * @param client  подключённый {@link ChatClient}
     */
    private static void chatLoop(Scanner scanner, ChatClient client) {
        System.out.println("Введите сообщение ('exit' - выход, 'list' - состояние сервера):");
        while (true) {
            if (client.isClosed()) {                 // сервер отключился
                System.out.println("Соединение прервано сервером.");
                break;
            }
            String message = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(message)) {
                break;

            } else if ("list".equalsIgnoreCase(message)) {
                try {
                    String state = client.waitForServerState();
                    System.out.println("Состояние сервера: " + state);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Ошибка при запросе состояния", e);
                }

            } else {
                System.out.println(client.getUsername() + ": " + message);
                client.sendMessage(message);
            }
        }

        client.close();
        LOGGER.info("Отключено от сервера");
        System.out.println("Отключено от сервера.");
    }
}

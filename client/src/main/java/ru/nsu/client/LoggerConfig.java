package ru.nsu.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.*;
/**
 * <h2>LoggerConfig</h2>
 * <p>
 * Утилитный класс
 * После первой загрузки класса выполняется статический блок, который:
 * </p>
 * <ol>
 *   <li>создаёт файл <code>app.log</code>, если он отсутствует;</li>
 *   <li>удаляет все предустановленные {@link java.util.logging.Handler Handler'ы}
 *       (включая консольный) из корневого логгера;</li>
 *   <li>добавляет {@link java.util.logging.FileHandler FileHandler},
 *       пишущий в <code>app.log</code> с форматтером {@link java.util.logging.SimpleFormatter};</li>
 *   <li>задаёт уровень логирования {@link java.util.logging.Level#INFO INFO}</li>
 *   <li>(при необходимости можно изменить на {@code FINE} / {@code FINEST}).</li>
 * </ol>
 * <p>
 * После инициализации любой вызов {@code LoggerConfig.getLogger(...)} возвращает
 * настроенный {@link java.util.logging.Logger}, уже связанный с файлом <code>app.log</code>.
 * </p>
 */
public final class LoggerConfig {

    /** Имя лог‑файла (создаётся в рабочем каталоге приложения). */
    private static final String LOG_PATH = "app.log";

    /*
     * Статический инициализатор выполняется однократно при первом обращении к классу.
     * Его задача — сконфигурировать корневой логгер: оставить только файловый хендлер
     * и настроить уровни.
     */
    static {
        try {
            // Создаём файл, если его нет (чтобы FileHandler не упал на FileNotFoundException)
            Path p = Path.of(LOG_PATH);
            if (Files.notExists(p)) {
                Files.createFile(p);
            }

            LogManager lm = LogManager.getLogManager();
            Logger root = lm.getLogger("");

            // Удаляем все существующие хендлеры (в т.ч. консольный по умолчанию)
            for (Handler h : root.getHandlers()) {
                root.removeHandler(h);
            }

            // Файловый хендлер: дописываем в конец, формат — простая одна строка
            FileHandler fileHandler = new FileHandler(LOG_PATH, /* append = */ true);
            fileHandler.setLevel(Level.ALL);                    // пишем ВСЕ уровни
            fileHandler.setFormatter(new SimpleFormatter());
            root.addHandler(fileHandler);

            // Показываем сообщения от INFO и выше
            root.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Не удалось инициализировать логгер: " + e.getMessage());
        }
    }

    /**
     * Приватный конструктор предотвращает создание экземпляров.
     * Этот класс предназначен только для статического доступа.
     */
    private LoggerConfig() {}

    /**
     * Возвращает {@link java.util.logging.Logger} с именем переданного класса.
     * <p>Эквивалентно {@code Logger.getLogger(clazz.getName())}, но гарантирует,
     * что корневой логгер уже настроен (файловый хендлер и уровни).</p>
     *
     * @param clazz класс, для которого нужен логгер
     * @return настроенный логгер
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /**
     * Возвращает {@link java.util.logging.Logger} по произвольному строковому имени.
     * Удобно, если логгеру нужно задать имя, не совпадающее с FQN класса.
     *
     * @param name уникальное название логгера
     * @return настроенный логгер
     */
    public static Logger getLogger(String name) {
        return Logger.getLogger(name);
    }
}
package ru.nsu.server.worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageWorkerPool {
    private final BlockingQueue<Runnable> taskQueue;
    private final Thread[] workers;
    private volatile boolean isShutdown = false;

    public MessageWorkerPool(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("poolSize должен быть > 0, передано: " + size);
        }

        this.taskQueue = new LinkedBlockingQueue<>();
        this.workers = new Thread[size];

        for (int i = 0; i < size; i++) {
            workers[i] = new Thread(new Worker(), "Worker-" + i);
            workers[i].setDaemon(true); // это позволяет JVM завершиться, если все, кроме этих потоков, завершились
            workers[i].start();
        }
    }

    public void submit(Runnable task) {
        if (isShutdown) {
            throw new IllegalStateException("Пул остановлен, нельзя добавить задачу");
        }
        if (!taskQueue.offer(task)) {
            System.out.println("MessageWorkerPool.submit: Ошибка при записи в очередь задач");
        }
    }

    public void shutdown() {
        isShutdown = true;
        for (Thread worker : workers) {
            worker.interrupt(); // прерываем потоки
        }
        System.out.println("Воркеры остановлены");
    }

    private class Worker implements Runnable {
        public void run() {
            try {
                while (!isShutdown || !taskQueue.isEmpty()) {
                    Runnable task = taskQueue.take();
                    try {
                        task.run();
                    } catch (Exception e) {
                        System.err.println("Ошибка при выполнении задачи: " + e.getMessage());
                    }
                }
            } catch (InterruptedException ignored) {
                // поток завершён — нормально
            }
        }
    }
}

package com.epam.rd.autotasks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadUnionImpl implements ThreadUnion {
    private final String name;
    private final AtomicInteger threadCounter;
    private final List<Thread> threads;
    private final List<FinishedThreadResult> threadResults;
    private volatile boolean isShutdown;

    private ThreadUnionImpl(String name) {
        this.name = name;
        this.threadCounter = new AtomicInteger(0);
        this.threads = new CopyOnWriteArrayList<>();
        this.threadResults = new CopyOnWriteArrayList<>();
        this.isShutdown = false;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        if (isShutdown) {
            throw new IllegalStateException("ThreadUnion is already shutdown");
        }

        int threadNumber = threadCounter.getAndIncrement();
        Thread thread = new Thread(() -> {
            String threadName = name + "-worker-" + threadNumber;
            try {
                runnable.run();
                threadResults.add(new FinishedThreadResult(threadName));
            } catch (Throwable throwable) {
                threadResults.add(new FinishedThreadResult(threadName, throwable));
                throw throwable;
            }
        });

        thread.setName(name + "-worker-" + threadNumber);
        threads.add(thread);
        return thread;
    }

    @Override
    public synchronized int totalSize() {
        return threads.size();
    }

    @Override
    public synchronized int activeSize() {
        return (int) threads.stream().filter(Thread::isAlive).count();
    }

    @Override
    public synchronized void shutdown() {
        if (!isShutdown) {
            isShutdown = true;
            threads.forEach(Thread::interrupt);
        }
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public void awaitTermination() {
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isFinished() {
        return isShutdown && threads.stream().noneMatch(Thread::isAlive);
    }

    @Override
    public List<FinishedThreadResult> results() {
        return new ArrayList<>(threadResults);
    }

    // Important: This method should be in the main interface, not in this implementation
    public static ThreadUnion newInstance(String name) {
        return new ThreadUnionImpl(name);
    }
}
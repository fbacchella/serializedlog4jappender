package fr.loghub.log4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import fr.loghub.log4j.JavaSerializer;
import fr.loghub.log4j.ZMQAppender;
import zmq.socket.Sockets;

public class ZMQAppenderTest {

    @Test(timeout=10000)
    public void testParallel() throws InterruptedException, ExecutionException, TimeoutException {
        final int count = 1000;
        final ZContext ctx = new ZContext(1);
        ctx.getContext();
        ctx.setLinger(0);
        final AtomicInteger received = new AtomicInteger();
        final AtomicInteger port = new AtomicInteger();
        final Lock mutex = new  ReentrantLock();
        final FutureTask<ZMQ.Socket> bindToPort = new FutureTask<>(new Callable<ZMQ.Socket>() {
            @Override
            public ZMQ.Socket call() throws Exception {
                ZMQ.Socket socket = ctx.createSocket(Sockets.PULL.ordinal());
                port.set(socket.bindToRandomPort("tcp://localhost"));
                return socket;
            }});
        final Thread testThread = Thread.currentThread();
        Thread receiver = new Thread() {
            @Override
            public void run() {
                try {
                    mutex.tryLock(10, TimeUnit.MILLISECONDS);
                    bindToPort.run();
                    ZMQ.Socket socket = bindToPort.get();
                    socket.setHWM(count + 1);
                    while(received.get() < count) {
                        byte[] buffer = socket.recv();
                        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(buffer))) {
                            @SuppressWarnings("unused")
                            LoggingEvent o = (LoggingEvent) ois.readObject();
                        }
                        received.incrementAndGet();
                        Thread.yield();
                    }
                } catch (ClassNotFoundException | IOException | InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                } finally {
                    mutex.unlock();
                }
            }
        };
        receiver.setDaemon(true);
        receiver.start();
        final ZMQAppender appender = new ZMQAppender(ctx);
        appender.setErrorHandler(new ErrorHandler() {

            @Override
            public void activateOptions() {
            }

            @Override
            public void setLogger(Logger logger) {
            }

            @Override
            public void error(String message, Exception e, int errorCode) {
                e.printStackTrace();
                try {
                    Assert.fail(message);
                } finally {
                    testThread.interrupt();
                }
            }

            @Override
            public void error(String message) {
                try {
                    Assert.fail(message);
                } finally {
                    testThread.interrupt();
                }
            }

            @Override
            public void error(String message, Exception e, int errorCode, LoggingEvent event) {
                e.printStackTrace();
                try {
                    Assert.fail(message);
                } finally {
                    testThread.interrupt();
                }
            }

            @Override
            public void setAppender(Appender appender) {
            }

            @Override
            public void setBackupAppender(Appender appender) {
            }

        });
        bindToPort.get(1200, TimeUnit.MILLISECONDS);
        appender.setEndpoint("tcp://localhost:" + port.get());
        appender.setMethod("connEct");
        appender.setType("PUSH");
        appender.activateOptions();
        appender.setSerializer(JavaSerializer.class.getName());
        appender.setHwm(count * 11);
        final Thread[] threads = new Thread[10];
        final AtomicInteger sends = new AtomicInteger();
        for (int i = 0; i < 10 ; i++) {
            final int subi = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    for (int i = 0 ; i < count / 10 ; i++) {
                        appender.append(new LoggingEvent(ZMQAppenderTest.class.getName(), Logger.getLogger(ZMQAppenderTest.class), Level.FATAL, subi + "/" + i, null));
                        sends.incrementAndGet();
                        Thread.yield();
                    }
                }
            };
            threads[i].setName("Injector" + i);
            threads[i].setDaemon(true);
        }
        for (int i = 0; i < 10 ; i++) {
            threads[i].start();
        }
        try {
            if (mutex.tryLock(10000, TimeUnit.MILLISECONDS)) {
                mutex.unlock();
            } else {
                Assert.fail("tryLock failed");
            };
        } catch (InterruptedException  e) {
            for (int i = 0; i < 10 ; i++) {
                threads[i].interrupt();
                Assert.fail("Was interrupted");
            }
        } finally {
            System.out.println("" + sends.get() + " was sent");
        }
        appender.close();
        Assert.assertTrue(received.get() == count);
        ctx.close();
    }

    @Test
    public void testClosed() {
        try (ZContext ctx = new ZContext(1)) {
            Assert.assertTrue(ctx.isClosed());
        };
    }
}

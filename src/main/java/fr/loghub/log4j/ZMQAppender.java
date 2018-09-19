package fr.loghub.log4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import zmq.socket.Sockets;

/**
 * Sends {@link LoggingEvent} objects to a remote a ØMQ socket,.
 * 
 * <p>Remote logging is non-intrusive as far as the log event is concerned. In other
 *    words, the event will be logged with the same time stamp, {@link
 *    org.apache.log4j.NDC}, location info as if it were logged locally by
 *    the client.
 * 
 *  <p>SocketAppenders do not use a layout. They ship a
 *     serialized {@link LoggingEvent} object to the server side.
 * 
 * @author Fabrice Bacchella
 *
 */
public class ZMQAppender extends SerializerAppender {

    public enum Method {
        CONNECT {
            @Override
            public void act(ZMQ.Socket socket, String address) { socket.connect(address); }

            @Override
            public char getSymbol() {
                return '-';
            }
        },
        BIND {
            @Override
            public void act(ZMQ.Socket socket, String address) { socket.bind(address); }

            @Override
            public char getSymbol() {
                return 'O';
            }
        };
        public abstract void act(ZMQ.Socket socket, String address);
        public abstract char getSymbol();
    }

    private ZMQ.Socket socket;
    // If the appender uses it's own context, it must terminate it itself
    private final boolean localCtx;
    private final ZContext ctx;
    private Sockets type = Sockets.PUB;
    private Method method = Method.CONNECT;
    private String endpoint = null;
    private int hwm = 1000;
    private BlockingQueue<byte[]> logQueue;

    public ZMQAppender() {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        localCtx = true;
    }

    public ZMQAppender(ZContext ctx) {
        this.ctx = ctx;
        localCtx = false;
    }

    @Override
    public void subOptions() {
        if (endpoint == null) {
            errorHandler.error("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }

        logQueue = new ArrayBlockingQueue<>(hwm);
        Thread publishingThread = new Thread() {

            @Override
            public void run() {
                publishingRun();
            }

            /* Don't interrupt a ZMQ thread, just finished it
             * @see java.lang.Thread#interrupt()
             */
            @Override
            public void interrupt() {
                synchronized(this) {
                    closed = true;
                }
            }
        };
        publishingThread.setName("Log4JZMQPublishingThread");
        publishingThread.setDaemon(true);
        synchronized(this) {
            closed = false;
        }
        // Workaround https://github.com/zeromq/jeromq/issues/545
        ctx.getContext();
        publishingThread.start();
    }

    private void publishingRun() {
        try {
            while (! isClosed()) {
                // First check if socket is null.
                // It might be the first iteration, or the previous socket badly failed and was dropped
                if (socket == null) {
                    socket = newSocket(method, type, endpoint, hwm, -1);
                    if (socket == null) {
                        // No socket returned, appender was closed
                        break;
                    }
                    socket.setLinger(100);
                }
                // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        synchronized (ctx) {
                            if (!isClosed()) {
                                boolean sended = socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
                                // An assert to failed during tests but not during run
                                assert sended : "failed sending";
                            }
                        }
                    } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                        // Using hash code if OnlyOnceErrorHandler is used
                        errorHandler.error("Failed ZMQ socket", e, socket.hashCode());
                        synchronized (ctx) {
                            // If it's not closed, drop the socket, to recreate a new one
                            if (!isClosed()) {
                                ctx.destroySocket(socket);
                                socket = null;
                            }
                        }
                    } 
                }
            }
        } catch (InterruptedException e) {
            // Interrupt deactivated, so never happens
        }
        close();
    }

    /**
     * A synchonized method, to ensure write barrier for closed, which is not volatile
     * @return
     */
    private synchronized boolean isClosed() {
        return closed;
    }

    public void close() {
        if (isClosed()) {
            return;
        }
        synchronized (ctx) {
            synchronized(this) {
                closed = true;
            }
            ctx.destroySocket(socket);
            socket = null;
            if(localCtx) {
                ctx.destroy();
            }
        }
    }

    public boolean requiresLayout() {
        return false;
    }

    @Override
    protected  void send(byte[] content) {
        if (!logQueue.offer(content)) {
            errorHandler.error("Log event lost");
        }
    }

    /**
     * Define the ØMQ socket type. Current allowed value are PUB or PUSH.
     * 
     * @param type
     */
    public void setType(String type) {
        try {
            this.type = Sockets.valueOf(type.toUpperCase());
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [PUSH, PUB]" + ", using default ZeroMQ socket type, PUSH by default.";
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
        }
    }

    /**
     * @return the ØMQ socket type.
     */
    public String getType() {
        return type.toString();
    }

    /**
     * The <b>method</b> define the connection method for the ØMQ socket. It can take the value
     * connect or bind, it's case insensitive.
     * @param method
     */
    public void setMethod(String method) {
        try {
            this.method = Method.valueOf(method.toUpperCase());
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [connect, bind]" + ", using default ZeroMQ socket type, connect by default.";
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
        }
    }


    /**
     * @return the 0MQ socket connection method.
     */
    public String getMethod() {
        return method.name();
    }

    /**
     * The <b>endpoint</b> take a string value. It's the ØMQ socket endpoint.
     * @param endpoint
     */
    public void setEndpoint(final String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * @return the ØMQ socket endpoint.
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * The <b>hwm</b> option define the ØMQ socket HWM (high water mark).
     */
    public void setHwm(int hwm) {
        this.hwm = hwm;
    }

    /**
     * @return the ØMQ socket HWM.
     */
    public int getHwm() {
        return hwm;
    }

    private Socket newSocket(Method method, Sockets type, String endpoint, int hwm, int timeout) {
        synchronized (ctx) {
            if (isClosed() || ctx.isClosed()) {
                return null;
            } else {
                Socket newsocket = ctx.createSocket(type.ordinal());
                newsocket.setRcvHWM(hwm);
                newsocket.setSndHWM(hwm);
                newsocket.setSendTimeOut(timeout);
                newsocket.setReceiveTimeOut(timeout);

                method.act(newsocket, endpoint);
                String url = endpoint + ":" + type.toString() + ":" + method.getSymbol();
                newsocket.setIdentity(url.getBytes());
                return newsocket;
            }
        }
    }

}

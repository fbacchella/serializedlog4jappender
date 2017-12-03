package loghub.log4j;

import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.zeromq.ZMQ;

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

    private enum Method {
        CONNECT {
            @Override
            void act(ZMQ.Socket socket, String address) { socket.connect(address); }
        },
        BIND {
            @Override
            void act(ZMQ.Socket socket, String address) { socket.bind(address); }
        };
        abstract void act(ZMQ.Socket socket, String address);
    }

    private enum ZMQSocketType {
        PUSH(ZMQ.PUSH),
        PUB(ZMQ.PUB);
        public final int type;
        ZMQSocketType(int type) {
            this.type = type;
        }
    }

    ZMQ.Socket socket;
    // If the appender uses it's own context, it must terminate it itself
    private final boolean localCtx;
    private final ZMQ.Context ctx;
    private ZMQSocketType type = ZMQSocketType.PUB;
    private Method method = Method.CONNECT;
    private String endpoint = null;
    private int hwm = 1000;

    public ZMQAppender() {
        ctx = ZMQ.context(1);
        localCtx = true;
    }

    public ZMQAppender(ZMQ.Context ctx) {
        this.ctx = ctx;
        localCtx = false;
    }

    @Override
    public void subOptions() {
        if (endpoint == null) {
            errorHandler.error("Unconfigured endpoint, the ZMQ append can't log");
            closed = true;
            return;
        }
        socket = ctx.socket(type.type);
        socket.setLinger(1);
        socket.setHWM(hwm);
        method.act(socket, endpoint);
    }

    public void close() {
        if (closed) {
            return;
        }
        try {
            socket.close();
        } catch (Exception e) {
        }
        if(localCtx) {
            ctx.term();
        }
    }

    public boolean requiresLayout() {
        return false;
    }

    @Override
    protected  void send(byte[] content) {
        try {
            socket.send(content);
        } catch (zmq.ZError.IOException e ) {
            try {
                socket.close();
                closed = true;
            } catch (Exception e1) {
            }
        } catch (java.nio.channels.ClosedSelectorException e ) {
            try {
                socket.close();
                closed = true;
            } catch (Exception e1) {
            }
        } catch (org.zeromq.ZMQException e ) {
            try {
                socket.close();
                closed = true;
            } catch (Exception e1) {
            }
        }
    }

    /**
     * Define the ØMQ socket type. Current allowed value are PUB or PUSH.
     * 
     * @param type
     */
    public void setType(String type) {
        try {
            this.type = ZMQSocketType.valueOf(type.toUpperCase());
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

}

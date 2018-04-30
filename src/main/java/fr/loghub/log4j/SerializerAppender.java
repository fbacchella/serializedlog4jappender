package fr.loghub.log4j;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;

public abstract class SerializerAppender extends AppenderSkeleton {

    private String serializerName = JavaSerializer.class.getName();
    private Serializer serializer;

    private String hostname =  null;
    private String application;
    private boolean locationInfo = false;

    @Override
    public final void activateOptions() {

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Serializer> c = (Class<? extends Serializer>) getClass().getClassLoader().loadClass(serializerName);
            serializer = c.getConstructor().newInstance();
        } catch (ClassCastException | ClassNotFoundException | IllegalArgumentException | SecurityException | InstantiationException | IllegalAccessException 
                | NoSuchMethodException e) {
            errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                errorHandler.error("failed to create serializer", (Exception) e.getCause(), ErrorCode.GENERIC_FAILURE);
            } else {
                errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
            }
        }
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                errorHandler.error(e.getMessage(), e, ErrorCode.GENERIC_FAILURE);
            } 
        }
        subOptions();
    }

    protected abstract void subOptions();

    @Override
    protected final void append(LoggingEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, String> eventProps = event.getProperties();

        // The event is copied, because a host field is added in the properties
        LoggingEvent modifiedEvent = new LoggingEvent(event.getFQNOfLoggerClass(), event.getLogger(), event.getTimeStamp(), event.getLevel(), event.getMessage(),
                event.getThreadName(), event.getThrowableInformation(), event.getNDC(), locationInfo ? event.getLocationInformation() : null,
                        new HashMap<String,String>(eventProps));

        if (application != null) {
            modifiedEvent.setProperty("application", application);
        }
        modifiedEvent.setProperty("hostname", hostname);
        try {
            send(serializer.objectToBytes(modifiedEvent));
        } catch (IOException e) {
            errorHandler.error("failed to serialize event", e, ErrorCode.GENERIC_FAILURE);
        }
    }

    protected abstract void send(byte[] content);

    public String getSerializer() {
        return serializerName;
    }

    /**
     * The class name of a implementation of a {@link Serializer} interface.
     * @param serializer
     */
    public void setSerializer(String serializer) {
        this.serializerName = serializer;
    }

    /**
     * The <b>Hostname</b> take a string value. The default value is resolved using InetAddress.getLocalHost().getHostName().
     * It will be send in a custom hostname property.
     * @param hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return value of the <b>Hostname</b> option.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * The <b>LocationInfo</b> option takes a boolean value. If true,
     * the information sent to the remote host will include location
     * information. By default no location information is sent to the server.
     */
    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    /**
     * @return value of the <b>LocationInfo</b> option.
     */
    public boolean getLocationInfo() {
        return locationInfo;
    }

    /**
     * The <b>App</b> option takes a string value which should be the name of the 
     * application getting logged.
     * If property was already set (via system property), don't set here.
     */
    public void setApplication(String application) {
        this.application = application;
    }

    /**
     *  @return value of the <b>Application</b> option.
     */
    public String getApplication() {
        return application;
    }

}

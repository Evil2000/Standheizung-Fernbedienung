package de.evil2000.standheizungfernbedienung;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by dave on 08.12.17.
 */

public class StateItem implements Serializable {
    private Date timestamp;
    private boolean state;
    private String stateString;
    private String defaultDateFormat = "dd.MM.yyyy HH:mm:ss";

    public StateItem() {
        this((new Date()).getTime(), "");
    }

    public StateItem(String stateString) {
        this((new Date()).getTime(), stateString);
    }

    public StateItem(Date timestamp, String stateString) {
        this(timestamp.getTime(), stateString);
    }

    public StateItem(long timestamp, String stateString) {
        this.timestamp = new Date(timestamp);
        this.stateString = stateString;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getFormatedTimestamp() {
        return getFormatedTimestamp(defaultDateFormat);
    }
    public String getFormatedTimestamp(String format) {
        if (format == null || format.isEmpty())
            format = defaultDateFormat;
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(timestamp);
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public String getStateString() {
        return stateString;
    }

    public void setStateString(String stateString) {
        this.stateString = stateString;
    }

    /**
     * Write out the object to stream.
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        // Call even if there is no default serializable fields.
        s.defaultWriteObject();

        // save the dimension
        //s.writeInt();
    }

    /**
     * Read in the object
     */
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        // restore the dimension
        //dimension = s.readInt();
    }
}

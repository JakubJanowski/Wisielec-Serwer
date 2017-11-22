package shared;

import java.io.Serializable;

public class Message implements Serializable {
    public MessageType type;
    public Object data;

    public Message(MessageType type) {
        this.type = type;
        this.data = null;
    }

    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }
}

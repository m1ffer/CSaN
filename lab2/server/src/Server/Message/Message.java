package Server.Message;

import java.io.Serializable;

public sealed interface Message
        extends Serializable
        permits FileMessage, TextMessage {
    String username();
}

package Server.Message;

public record TextMessage(
        String username,
        String text
) implements Message {
    public TextMessage{
        MessageUtil.checkStrings(username, text);
        if (username.length() > MessageUtil.MAX_USERNAME_LENGTH)
            throw new IllegalArgumentException("Превышена длина ника");
        if (text.length() > MessageUtil.MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Превышена длина сообщения");
    }
}

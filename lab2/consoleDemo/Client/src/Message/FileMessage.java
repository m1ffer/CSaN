package Message;

import java.util.Objects;

public record FileMessage(
        String username,
        String fileName,
        byte[] data
) implements Message {
    public FileMessage{
        MessageUtil.checkStrings(username, fileName);
        if (username.length() > MessageUtil.MAX_USERNAME_LENGTH)
            throw new IllegalArgumentException("Превышена длина ника");
        Objects.requireNonNull(data, "Массив данных не может быть null");
        if (data.length > MessageUtil.MAX_FILE_LENGTH)
            throw new IllegalArgumentException("Превышен размер файла");
        data = data.clone();
    }

    @Override
    public byte[] data(){
        return data.clone();
    }
}

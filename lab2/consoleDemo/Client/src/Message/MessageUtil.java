package Message;

import java.util.Arrays;
import java.util.Objects;

public class MessageUtil {
    private MessageUtil(){}

    public static long MAX_MESSAGE_LENGTH = 5000;
    public static long MAX_FILE_LENGTH = 1000000;
    public static int MAX_USERNAME_LENGTH = 25;

    public static void checkString(String data){
        Objects.requireNonNull(data, "Строка не может быть null");
        if (data.isBlank())
            throw new IllegalArgumentException("Строка не может быть пустой");
    }

    public static void checkStrings(String... data){
        Arrays.stream(data).
                forEach(MessageUtil :: checkString);
    }
}

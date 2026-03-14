import Message.*;


import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Main{
    static void main(String[] args){
        IO.print("Введите ip: ");
        String host = IO.readln();
        IO.print("Введите порт: ");
        int port = Integer.parseInt(IO.readln());
        IO.print("Введите ник: ");
        String username = IO.readln();
        try(
                App app = new App(host, port, username);
        ){
            app.start();
        }
        catch(Exception e){
            IO.println("Что-то пошло не так: " + e.getMessage());
        }
    }
}
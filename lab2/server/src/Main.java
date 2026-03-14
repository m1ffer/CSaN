import java.io.IOException;
import java.util.Scanner;

public class Main{

    private static Server server = null;

    static void main(String[] args) {
        IO.print("Введите порт: ");
        final int port = Integer.parseInt(IO.readln());
        Thread serverThread = new Thread(() ->{
            try{
                server = new Server(port);
                server.start();
            }
            catch(Exception e){
                IO.println("упало");
                e.printStackTrace();
            }
            finally{
                try {
                    server.close();
                } catch (Exception _) {
                    IO.println("прям грохнулось");
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        IO.readln();
        try {
            server.shutdown();
        } catch (Exception _) {
            IO.println("прям грохнулось");
        }
    }
}
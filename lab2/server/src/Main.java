import java.util.Scanner;

public class Main{

    private static Server server = null;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Введите порт: ");
        final int port = Integer.parseInt(sc.nextLine().trim());
        try {
            server = new Server(port);
        } catch (Exception e) {
            System.out.println("Не удалось запустить сервер");
            e.printStackTrace();
            return;
        }
        Thread serverThread = new Thread(() ->{
            try{
                server.start();
            }
            catch(Exception e){
                System.out.println("упало");
                e.printStackTrace();
            }
            finally{
                try {
                    server.close();
                } catch (Exception ignored) {
                    System.out.println("прям грохнулось");
                }
            }
        });
        serverThread.start();

        System.out.println("Нажмите Enter, чтобы остановить сервер");
        sc.nextLine();
        try {
            server.shutdown();
        } catch (Exception ignored) {
            System.out.println("прям грохнулось");
        }
        try {
            serverThread.join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

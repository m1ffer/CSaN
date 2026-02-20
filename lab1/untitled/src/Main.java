void main(String[] args){
    try {
        NetworkScanner ns = new NetworkScanner();
        ns.scanNetwork();
    }
    catch(Exception e){
        IO.println("Что-то пошло не так: " + e.getMessage());
        e.printStackTrace();
    }
}
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;

public class Main {

    public final static String LOCAL_IP = "127.0.0.1";
    public final static int DEFAULT_PORT = 1234;
    public final static int DEFAULT_TTL = 5;
    public final static String CONNECT_REQUEST = "GUNTELLA CONNECT/0.4\n\n";
    public final static String CONNECT_RESPONSE = "GNUTELLA OK\n\n";
    public final static int TIME_TO_SLEEP = 60;
    public final static String DIRECTORY = Paths.get("").toAbsolutePath().toString() + "/../files/";
    public static void main(String[] args) {
        Gnutella g = new Gnutella(DEFAULT_PORT);

        if (args.length > 1) {
            for (int i = 0; i < args.length; i++) {
                switch (args[i++]) {
                    //Set port to bind to
                    case "-p":
                        g.setListenPort(Integer.parseInt(args[i]));
                        break;
                    //Connect to node at ip:port (ie 127.0.0.1:1234)
                    case "-connect":
                        g.setIP(args[i]);
                        break;
                    //Request a file from other nodes by name
                    case "-q":
                        g.setQueryString(args[i]);
                        break;
                    default: 
                        System.out.println("Invalid argument");
                        return;
                }
            }
        }
        g.setNumFiles(findFiles(g.getListenPort()));

        Thread t = new Thread(g);
        t.start();
    }

    public static byte[] generateRandomMessageID() {
        String uuid = UUID.randomUUID().toString().substring(0, 16);
        return uuid.getBytes();
    }

    public static byte[] ipToBytes(String ip) {
        byte[] buf = new byte[4];

        String[] split = ip.split("\\.");

        for (int i = 0; i < split.length; i++) {
            buf[i] = (byte) Integer.parseInt(split[i]);
        }

        return buf;
    }

    public static String bytesToIP(byte[] ip) {
        String result = "";

        for (int i = 0; i < ip.length; i++) {
            result += (int) ip[i];

            if (i + 1 != ip.length) {
                result += ".";
            }
        }

        return result;
    }

    private static int findFiles(int port) {
        int numFiles = 0;

        File directory = new File(DIRECTORY + port + "/");
        if (directory.mkdirs()) {
            System.out.println("Made directory at " + directory.getAbsolutePath());
        }
        numFiles = directory.list().length;

        System.out.println("Broadcasting " + numFiles + " file(s)");

        return numFiles;
    }

    public static File searchFiles(String queryString, int port) {
        File found = null;
        File directory = new File(DIRECTORY + port);
        File[] fileList = directory.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile() && file.getName().equals(queryString)) {
                    found = file;
                    break;
                }
            }
        }
        return found;
    }
}

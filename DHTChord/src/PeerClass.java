//The Client sessions package

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

//The Base package for representing JSON-RPC 2.0 messages
//The JSON Smart package for JSON encoding/decoding (optional)
//For creating URLs


public class PeerClass extends Thread {

    URL serverURL = null;
    static String ip;
    static int PORT;
    ArrayList<Key> keys = new ArrayList<>();
    int nodeID;
    // Finger table stored in parts
    ArrayList<Integer> relative = new ArrayList<>();
    ArrayList<Integer> actual = new ArrayList<>();
    ArrayList<Integer> present = new ArrayList<>();
    ArrayList<String> presentIP = new ArrayList<>();


    public void activity(int port) {

        try {
            serverURL = new URL("http://127.0.0.1:" + port);

        } catch (MalformedURLException e) {
            // handle exception...
        }


        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "passKey";
        int requestID = 2;
        String params = "127.0.0.1";
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);
        ArrayList<String> list = new ArrayList<>();
        list.add(ip);
        request.setParams(list);

        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);

        } catch (JSONRPC2SessionException e) {

            System.err.println(e.getMessage());
            // handle exception...
        }

        // Print response result / error
        if (response.indicatesSuccess()) {
            System.out.println(response.getResult());
        } else
            System.out.println(response.getError().getMessage());


    }


    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for a dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private Dispatcher dispatcher;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
//            System.out.println("Hello3");
            this.socket = socket;
//            System.out.println("Here 1");
//            ip = socket.getLocalAddress();
            // Create a new JSON-RPC 2.0 request dispatcher
            this.dispatcher = new Dispatcher();

            // Register the "echo", "getDate" and "getTime" handlers with it
            dispatcher.register(new JsonHandlerForClient1.PassKey());
//            dispatcher.register(new JsonHandler.DateTimeHandler());
//            dispatcher.register(new JsonHandler.Trial());


        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                System.out.println("Hello2" + ip);
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // read request
                String line;
                line = in.readLine();
//                System.out.println(line);
                StringBuilder raw = new StringBuilder();
                raw.append("" + line);
                boolean isPost = line.startsWith("POST");
                int contentLength = 0;
                while (!(line = in.readLine()).equals("")) {
//                    System.out.println(line);
                    raw.append('\n' + line);
                    if (isPost) {
                        final String contentHeader = "Content-Length: ";
//                        System.out.println("Hello");
//                        System.out.println(line);
                        if (line.startsWith(contentHeader)) {
                            contentLength = Integer.parseInt(line.substring(contentHeader.length()));
//                            System.out.println("Size of content length: " + contentLength);
                        }
                    }
                }
                StringBuilder body = new StringBuilder();
                if (isPost) {
                    int c = 0;
                    for (int i = 0; i < contentLength; i++) {
                        c = in.read();
                        body.append((char) c);
                    }
                }

                System.out.println( "body: " + body);
                JSONRPC2Request request = JSONRPC2Request.parse(body.toString());
                JSONRPC2Response resp = dispatcher.process(request, null);

                System.out.println("result: " + resp.getResult().toString().split(",")[1]);

                // send response
                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("\r\n");
                System.out.println(resp.toJSONString());
                out.write(resp.toJSONString());
                // do not in.close();
                out.flush();
                out.close();
                socket.close();
                System.out.println("result: " + resp.getResult().toString().split(",")[1]);
            } catch (IOException e) {
                System.out.println(e);
            } catch (JSONRPC2ParseException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
            System.out.println("End");
        }
    }

    public void run() {
//        System.out.println("Hello");
        System.out.println("CLient1 is running.");
        ServerSocket listener = null;
        try {
            listener = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (true) {
                System.out.println("Ip " + ip);
                new Handler(listener.accept()).start();
//                System.out.println("Hello1");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                listener.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void getKey() {
        if (keys.size() == 0) {
            System.out.println("No keys present.");
        } else {
            for (Key key : keys) {
                System.out.println(key);
            }
        }
    }

    public void findCorrectNode() {

    }

    public void goOffline() {

    }

    public void initialize(int port) {

        try {
            serverURL = new URL("http://127.0.0.1:" + port);

        } catch (MalformedURLException e) {
            // handle exception...
        }


        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "joinNode";
        int requestID = 0;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);
        ArrayList<String> list = new ArrayList<>();
        list.add(ip);
        request.setParams(list);

        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);

        } catch (JSONRPC2SessionException e) {

            System.err.println(e.getMessage());
            // handle exception...
        }

        // Print response result / error
        if (response.indicatesSuccess()) {
            System.out.println(response.getResult());
            if (response.getResult().toString().equals("First Node")) {
                nodeID = 1;
                initializeFingerTable();
            }

        } else
            System.out.println(response.getError().getMessage());
    }

    public void initializeFingerTable() {
        // TODO: 10/14/18 replace with log(size)
        for (int i = 0; i < 4; i++) {
            relative.add((int) (4 + Math.pow(2, i)));
            actual.add((int) (4 + Math.pow(2, i)));
            present.add(1);
            presentIP.add(ip);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        PeerClass peer = new PeerClass();
        InetAddress localhost = InetAddress.getLocalHost();
        System.out.println("Enter port number");
        Scanner src = new Scanner(System.in);
        PORT = src.nextInt();
        System.out.println(localhost.toString().split("/")[1]);
        ip = localhost.toString().split("/")[1];
        peer.start();
        System.out.println("Welcome Peer to the DHT Chord system!");

        //peer.initialize(8000);

        // TODO: 10/13/18 assign node id functionality
        System.out.println("Choose Node id: Y/N ?");

        while (true) {
            System.out.println("Select the following functionalities \n 1. Display Keys \n 2. Upload Keys \n" +
                    " 3. Go Offline");
            int ch = src.nextInt();
            switch (ch) {
                case 1:
                    peer.getKey();
                    break;
                case 2:
                    peer.findCorrectNode();
                    break;
                case 3:
                    peer.goOffline();
                    break;
                default:
                    System.out.println("Invalid choice");


            }
            System.out.println("Enter peer port number");
            int port = src.nextInt();
            peer.activity(port);
        }


    }
}
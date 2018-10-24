package DHTChord;

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
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This is a lite manager which will store data for some of the online nodes in the system.
 */

public class Manager extends Thread {

    URL serverURL = null;

    // to store identity of max 3 online nodes
    static HashMap<Integer, String> onlineNodes = new HashMap<>();
    // to store the keys if every node went offline
    ArrayList<Key> keys = new ArrayList<>();

    public static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private Dispatcher dispatcher;

        public Handler(Socket socket) {

            this.socket = socket;
            this.dispatcher = new Dispatcher();

            dispatcher.register(new JsonHandlerForManager.NodeJoiner());
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // read request
                String line;
                line = in.readLine();
                System.out.println("line: " + line );
                StringBuilder raw = new StringBuilder();
                raw.append("" + line);

                boolean isPost = line.startsWith("POST");
                int contentLength = 0;

                while (!(line = in.readLine()).equals("")) {
                    raw.append('\n' + line);
                    if (isPost) {
                        final String contentHeader = "Content-Length: ";

                        if(line.startsWith(contentHeader)) {
                            contentLength = Integer.parseInt(line.substring(contentHeader.length()));
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

                System.out.println(body);
                JSONRPC2Request request  = JSONRPC2Request.parse(body.toString());
                JSONRPC2Response resp = dispatcher.process(request, null);

                System.out.println("Result: " + resp.getResult());
                Manager manager = new Manager();

                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("\r\n");
                // checking for the first element in the system
                if (resp.getResult().toString().startsWith("FirstNode")) {
                    onlineNodes.put(1, resp.getResult().toString().split(",")[1]);
                    System.out.println("IP received: " + resp.getResult().toString().split(",")[1]);
                    // send response
                    // TODO: 10/14/18 make peer 1 initialize itself
                    out.write(resp.toJSONString());
//                        out.write(new JSONRPC2Response("FirstNode Edited One", request.getID()).toJSONString());

                    out.flush();
                    out.close();
                    socket.close();

                } else if (resp.getResult().toString().startsWith("NotFirstNode")) {
                    System.out.println("Request received");
                    String newNodeIP = resp.getResult().toString().split(",")[1];
                    out.write(resp.toJSONString()); // closing the connection with the new node
                    out.flush();
                    out.close();
                    out.close();
                    socket.close();

                    // TODO: 10/14/18 function call to anchor node anc check on port number
                    manager.callAnchorNode(Manager.onlineNodes.get(1), 8020, newNodeIP);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONRPC2ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void callAnchorNode(String anchorNodeIP, int port, String newNodeIP) {
        System.out.println("call anchor node");
        try {// connecting with the anchor node
            serverURL = new URL("http://" + anchorNodeIP + ":" + port);

        } catch (MalformedURLException e) {
            // handle exception...
        }


        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "newNode";
        int requestID = 3;
        ArrayList<Object> list = new ArrayList<>();
        list.add(newNodeIP);

        JSONRPC2Request request = new JSONRPC2Request(method, list, requestID);


        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);

        } catch (JSONRPC2SessionException e) {

            System.err.println(e.getMessage());
            // handle exception...
        }

        // Print response result / error
        if (response.indicatesSuccess()) {
            System.out.println("Contacted anchor node");
        } else
            System.out.println(response.getError().getMessage());
    }

    public void run() {
        ServerSocket listener = null;
        try {
            listener = new ServerSocket(8015);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            while (true) {
                new Handler(listener.accept()).start();
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

    public static void main(String[] args) {
        System.out.println("Welcome to the DHT Chord System!");
        Manager manager = new Manager();
        manager.start();
    }
}

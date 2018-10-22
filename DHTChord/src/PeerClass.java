package DHTChord;//The Client sessions package

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
import java.util.*;

//The Base package for representing JSON-RPC 2.0 messages
//The JSON Smart package for JSON encoding/decoding (optional)
//For creating URLs


public class PeerClass extends Thread {

    URL serverURL = null;
    static String ip;
    static int PORT;
    static ArrayList<Key> keys = new ArrayList<>();
    static int nodeID;
    // Finger table stored in parts
    private static ArrayList<Integer> relative = new ArrayList<>();
    private static ArrayList<Integer> actual = new ArrayList<>();
    private static ArrayList<Integer> present = new ArrayList<>();
    private static ArrayList<String> presentIP = new ArrayList<>();

    private static int N = 16;


    public void activity(int port) {

        try {
            serverURL = new URL("http://" + ip + ":" + port);

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
        list.add("parameter2");
        list.add("param3");
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
            dispatcher.register(new JsonHandlerForClient1.NewNodeJoiner());
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

                System.out.println("body: " + body);
                JSONRPC2Request request = JSONRPC2Request.parse(body.toString());

//                ArrayList<Object> list = (ArrayList) request.getParams();
//                String s = (String) list.get(0);
                JSONRPC2Response resp = dispatcher.process(request, null);

//                request.getNamedParams().

                resp.getResult().toString();
                System.out.println("result: " + resp.getResult().toString());

                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("\r\n");

                PeerClass peer = new PeerClass();
// TODO: 10/16/18 make the handler or peer class return similar type of output so that while checking it would not throw the error
                if (resp.getResult().toString().split(",")[0].equals("JoinNewNode")) {
                    // do something
                    out.write(resp.toJSONString()); // closing the connection with the manager
                    out.flush();
                    out.close();
                    socket.close();
                    String newNodeIP = resp.getResult().toString().split(",")[1];
                    // TODO: 10/14/18 call function to get list of all the online nodes
                    peer.startCollectingOnlineNodes(newNodeIP);

                } else if (resp.getResult().toString().equals("CollectOnlineNodes")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    String newNodeIP = (String) paramList.get(0);
                    HashMap<Integer, String> onlineNodes = (HashMap)  paramList.get(1);

                    if (!onlineNodes.get(1).equals(ip)) { // not the anchor node
                        peer.collectOnlineNodes(newNodeIP, onlineNodes);
                    } else {
                        // loop completed, make anchor node contact the new node
                        peer.contactNewNode(newNodeIP, onlineNodes);
                    }

                    out.write("");
                    // do not in.close();
                    out.flush();
                    out.close();
                    socket.close();
                } else if (resp.getResult().toString().equals("initializeNewNode")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    HashMap<String, String> onlineNodes = (HashMap<String, String>) paramList.get(0);
                    Long newNodeID1 = (Long) paramList.get(1);
                    int newNodeID = newNodeID1.intValue();
                    peer.initializeFingerTable(onlineNodes, newNodeID);

                    out.write(resp.toJSONString()); // closing the connection with the manager
                    out.flush();
                    out.close();
                    socket.close();
                    String newNodeIP = (String) paramList.get(2);
                    // TODO: 10/14/18 call function to get list of all the online nodes

                } else {

                    // send response

                    System.out.println(resp.toJSONString());
                    out.write(resp.toJSONString());
                    // do not in.close();
                    out.flush();
                    out.close();
                    socket.close();
                }
//                System.out.println("result: " + resp.getResult().toString().split(",")[1]);
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

        }
    }

    /**
     * contacts new node when the list of all online nodes is available
     * @param newNodeIP
     * @param onlineNodes
     */

    public void contactNewNode(String newNodeIP, HashMap onlineNodes) {

        System.out.println("Contacting new node");

        int newNodeID = updateFingerTable(onlineNodes, newNodeIP);

        try {
            serverURL = new URL("http://" + newNodeIP + ":" + 8002);

        } catch (MalformedURLException e) {
            // handle exception...
        }
        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "initializeNewNode";
        int requestID = 6;

        ArrayList<Object> list = new ArrayList<>();

        list.add(onlineNodes);
        list.add(newNodeID);
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
            System.out.println(response.getResult());
        } else
            System.out.println(response.getError().getMessage());
    }

    public void collectOnlineNodes(String newNodeIP, HashMap onlineNodes) {
        // call the successor node based on the finger table

        try {
            serverURL = new URL("http://" + presentIP.get(0) + ":" + 8001);

        } catch (MalformedURLException e) {
            // handle exception...
        }
        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "collectingOnlineNodes";
        int requestID = 5;

        ArrayList<Object> list = new ArrayList<>();

        list.add(newNodeIP);

        onlineNodes.put(nodeID, ip);
        list.add(onlineNodes);
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
            System.out.println(response.getResult());
        } else
            System.out.println(response.getError().getMessage());
    }

    public void startCollectingOnlineNodes(String newNodeIP) {
        // call the successor node based on the finger table
        System.out.println("Inside start collection");
        // checking if the successor is itself
        System.out.println("Present length: " + present.size());
        if (present.get(0) == nodeID) {
            System.out.println("Successor is me!");
            // send the ip of anchor node to the new node
            // TODO: 10/15/18 send ip to new node
            HashMap<Integer, String> onlineNodes = new HashMap<>();
            onlineNodes.put(nodeID, ip);
            PeerClass peer = new PeerClass();
            peer.contactNewNode(newNodeIP, onlineNodes);

        } else {
            try {
                serverURL = new URL("http://" + presentIP.get(0) + ":" + 8001); // calling the successor

            } catch (MalformedURLException e) {
                // handle exception...
            }
            // Create new JSON-RPC 2.0 client session
            JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

            String method = "collectingOnlineNodes";
            int requestID = 4;

            ArrayList<Object> list = new ArrayList<>();

            list.add(newNodeIP);

            HashMap<Integer, String> onlineNodes = new HashMap<>();

//            ArrayList<String> onlineNodes = new ArrayList<>();
            onlineNodes.put(nodeID, ip);
            list.add(onlineNodes);

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
                System.out.println(response.getResult());
            } else
                System.out.println(response.getError().getMessage());
        }
    }

    boolean flag = true;

    public void display() {
        System.out.println("Hello printing elements");
    }

    public void run() {
//        System.out.println("Hello");
        System.out.println("Peer is running.");
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
            // contacting the manager
            serverURL = new URL("http://127.0.0.1:" + port);

        } catch (MalformedURLException e) {
            // handle exception...
        }


        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "joinNode";
        int requestID = 0;
        ArrayList<Object> list = new ArrayList<>();
        list.add(ip);
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
            System.out.println(response.getResult());
            if (response.getResult().toString().startsWith("FirstNode")) {
                nodeID = 1;
                System.out.println("inside initialize.");
                initializeFingerTable();
            } else {
                System.out.println("Contacted Manager");
            }

        } else
            System.out.println(response.getError().getMessage());
    }

    public void initializeFingerTable() {
        // TODO: 10/14/18 replace with log(size)
        System.out.println("Inside finger table");
//        System.out.println("FT: " + actual.get(0));
        for (int i = 0; i < 4; i++) {
            relative.add((int) (1 + Math.pow(2, i)));
            actual.add((int) (1 + Math.pow(2, i)) % N);
            present.add(1);
            presentIP.add(ip);
        }
        System.out.println(relative);
        System.out.println(actual);
        System.out.println(present + "" +present.size());
        System.out.println(presentIP);
    }

    public void initializeFingerTable(HashMap<String, String> onlineNodes, int newNodeID) {
        // TODO: 10/15/18 initialize finger table based on list of online nodes
        // initialize nodeID too

        ArrayList<Integer> onlineNodeList = new ArrayList<>();
        Iterator it = onlineNodes.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry) it.next();
//            String s = (String) pair.getKey();
//            onlineNodeList.add(Integer.parseInt(s));
//        }

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            onlineNodeList.add(Integer.parseInt(entry.getKey()));
        }

        onlineNodeList.add(newNodeID);
        onlineNodes.put("" + newNodeID, ip);

        Collections.sort(onlineNodeList);
        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add(onlineNodeList.get(i) + N);
        }

        for (int i = 0; i < (int)(Math.log(16) / Math.log(2)); i++) {
            actual.add(i, (newNodeID + (int)Math.pow(2, i)) % N);
            int k = 0;
            while (actual.get(i) > onlineNodeList.get(k)) {
                k++;
            }
            present.add(i, onlineNodeList.get(k) % N);
            String IPadd = (String)onlineNodes.get("" + onlineNodeList.get(k) % N);
            presentIP.add(i, IPadd);
            relative.add(i, actual.get(i));
        }

        System.out.println("Finger Table");
        System.out.println(actual);
        System.out.println(present);
        System.out.println(presentIP);

    }

    public int updateFingerTable(HashMap onlineNodes, String newNodeIP) {
        // here finger table will get updated and a node id will be assigned to the new node and will be sent to each node.

        ArrayList<Integer> onlineNodeList = new ArrayList<>();
        Iterator it = onlineNodes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            onlineNodeList.add((Integer) pair.getKey());
        }

        int hash =  newNodeIP.hashCode() % N;
        int newNodeID = findAptNodeID(onlineNodeList, hash);
        onlineNodeList.add(newNodeID);
        onlineNodes.put(newNodeID, newNodeIP);

        Collections.sort(onlineNodeList);
        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add(onlineNodeList.get(i) + N);
        }

        for (int i = 0; i < (int)(Math.log(16) / Math.log(2)); i++) {
            int k = 0;
            while (actual.get(i) > onlineNodeList.get(k)) {
                k++;
            }
            present.remove(i);
            present.add(i, onlineNodeList.get(k) % N);
            presentIP.remove(i);
            String IPadd = (String)onlineNodes.get(onlineNodeList.get(k) % N);
            presentIP.add(i, IPadd);
        }

        return newNodeID;
    }

    /**
     * to assign node id to the new node based on the vacancy
     *
     * @param onlineNodeList
     * @param hash
     * @return
     */

    public int findAptNodeID (ArrayList<Integer> onlineNodeList, int hash) {

        while (onlineNodeList.contains(hash)) {
            hash = (hash + 3) % N;
        }
        return hash;
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

        peer.initialize(8000);

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
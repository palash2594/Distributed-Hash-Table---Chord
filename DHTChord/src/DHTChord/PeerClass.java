package DHTChord;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;

import javax.management.ObjectName;
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
    private static PeerClass peer = new PeerClass();
    private static String managerIP;

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
        public synchronized void run() {
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

//                PeerClass peer = new PeerClass();
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
                    HashMap<String, String> onlineNodes = (HashMap) paramList.get(1);
                    System.out.println("collecting nodes: " + onlineNodes);

                    out.write(resp.toJSONString());
                    // do not in.close();
                    out.flush();
                    out.close();
                    socket.close();
                    System.out.println("***************");
                    System.out.println("Printing collected nodes: " + onlineNodes);
                    System.out.println("****************");

                    if (!onlineNodes.get("1").equals(ip)) { // not the anchor node
                        peer.collectOnlineNodes(newNodeIP, onlineNodes);
                    } else {
                        // loop completed, make anchor node contact the new node
                        System.out.println("Now here");
                        peer.contactNewNode(newNodeIP, onlineNodes);
                    }

                } else if (resp.getResult().toString().equals("initializeNewNode")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    HashMap<String, String> onlineNodes = (HashMap<String, String>) paramList.get(0);
                    Long newNodeID1 = (Long) paramList.get(1);
                    int newNodeID = newNodeID1.intValue();
                    out.write(resp.toJSONString()); // closing the connection with the manager
                    out.flush();
                    out.close();
                    socket.close();
                    try {
                        peer.initializeFingerTable(onlineNodes, newNodeID);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    String newNodeIP = (String) paramList.get(2);
                    // TODO: 10/14/18 call function to get list of all the online nodes

                } else if (resp.getResult().toString().equals("updateFingerTableNodeAddition")) {

                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    HashMap<String, String> onlineNodes = (HashMap<String, String>) paramList.get(0);
                    Long newNodeID1 = (Long) paramList.get(1);
                    int newNodeID = newNodeID1.intValue();
                    String newNodeIP = (String) paramList.get(2);
                    ArrayList<String> onlineNodeListDone = (ArrayList<String>) paramList.get(3);
                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    peer.updateFingerTable(onlineNodes, newNodeIP, newNodeID);
                    peer.updateFingerTableNodeAddition(onlineNodes, newNodeID, newNodeIP, onlineNodeListDone);
                } else if (resp.getResult().toString().equals("StoreKeyInsert")) {

                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    String k = (String) paramList.get(0);
                    String[] k2 = k.split(" ");
                    Key key = new Key(Integer.parseInt(k2[0]), k2[1], Integer.parseInt(k2[2]));
                    System.out.println(key);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    keys.add(key);
                    System.out.println("Key added successfully: " + key);

                } else if (resp.getResult().toString().equals("FindKeyInsert")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String k = (String) paramList.get(0);
                    String[] k2 = k.split(" ");
                    Key key = new Key(Integer.parseInt(k2[0]), k2[1], Integer.parseInt(k2[2]));
                    System.out.println(key);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    if (nodeID == key.id) {
                        keys.add(key);
                        System.out.println("Key added successfully: " + key);
                    } else {
                        peer.findNodeForKey(key);
                        System.out.println("received key, passing on");
                    }
                } else if (resp.getResult().toString().equals("requestKeys")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String newNodeID = (String) paramList.get(0);
                    String newNodeIP = (String) paramList.get(1);
                    String predecessor = (String) paramList.get(2);

////                    String keys1 = peer.sendingKeysToNewNode(newNodeID, newNodeIP, predecessor);
//                    if (keys1.equals("No keys")) {
//                        System.out.println("sending no keys");
//                        out.write(resp.toJSONString());
//
//                    } else {
//                        JSONRPC2Response res = new JSONRPC2Response(keys1, request.getID());
//                        out.write(res.toJSONString());
//                    }
                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    String keys1 = peer.findKeysforNewNode(newNodeID, newNodeIP, predecessor);
                    peer.sendKeysToNewNode(keys1, newNodeIP);

                } else if (resp.getResult().toString().equals("sendingKeysToNewNode")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String keyString = (String) paramList.get(0);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    System.out.println("Keys received are: " + keyString);

                    if (keyString.equals("No keys")) {
                        System.out.println("No keys to receive");
                    } else {
                        String[] keysReceived = keyString.split("---");

                        System.out.println("Keys received");
                        for (int i = 0; i < keysReceived.length; i++) {
                            String[] k = keysReceived[i].split(",");
                            Key key = new Key(Integer.parseInt(k[0]), k[1], Integer.parseInt(k[2]));
                            System.out.println(key);
                            keys.add(key);
                        }
                    }

                } else if (resp.getResult().toString().equals("sendingKeysToSuccessor")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String keyString = (String) paramList.get(0);
                    String offlineNodeID = (String) paramList.get(1);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    String[] keysReceived = keyString.split("---");

                    System.out.println("Keys received from node: " + offlineNodeID);
                    for (int i = 0; i < keysReceived.length; i++) {
                        String[] k = keysReceived[i].split(",");
                        Key key = new Key(Integer.parseInt(k[0]), k[1], Integer.parseInt(k[2]));
                        System.out.println(key);
                        keys.add(key);
                    }
                } else if (resp.getResult().toString().equals("removeMe")) {

                    System.out.println("inside removeMe");

                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String offlineNode = (String) paramList.get(0);
                    String offlineNodeIP = (String) paramList.get(1);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    HashMap<String, String> onlineNodes = new HashMap<>();
                    String startingNodeID = "" + nodeID;
                    peer.contactOnlineNodesRemove(onlineNodes, startingNodeID, offlineNode, offlineNodeIP);

                } else if (resp.getResult().toString().equals("collectingOnlineNodesRemove")) {

                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String startingNodeID = (String) paramList.get(0);
                    String offlineNodeID = (String) paramList.get(1);
                    String offlineNodeIP = (String) paramList.get(2);
                    HashMap<String, String> onlineNodes = (HashMap<String, String>) paramList.get(3);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    peer.contactOnlineNodesRemove(onlineNodes, startingNodeID, offlineNodeID, offlineNodeIP);
                } else if (resp.getResult().toString().equals("allowNodeGoOffline")) {

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    System.out.println("Going offline!");
                    System.exit(0);
                } else if (resp.getResult().toString().equals("updateFingerTableNodeRemovePassOn")) {

                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    HashMap<String, String> onlineNodes = (HashMap<String, String>) paramList.get(0);
                    String offlineNodeID = (String) paramList.get(1);
                    String offlineNodeIP = (String) paramList.get(2);
                    ArrayList<String> onlineNodeListDone = (ArrayList<String>) paramList.get(3);


                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();
                    peer.updateFingerTableNodeRemove(onlineNodes, offlineNodeID, offlineNodeIP);
                    peer.updateFingerTableNodeRemovePassOn(onlineNodes, offlineNodeID, offlineNodeIP, onlineNodeListDone);
                }

                else {

                    // send response

                    System.out.println(resp.toJSONString());
                    out.write(resp.toJSONString());
                    // do not in.close();

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
     *
     * @param newNodeIP
     * @param onlineNodes
     */

    static int i = 8020;

    public void contactNewNode(String newNodeIP, HashMap onlineNodes) {

        System.out.println("Contacting new node");

        int newNodeID = updateFingerTable(onlineNodes, newNodeIP, -1);

        // making other online nodes update their FT with the information regarding this new node
        if (onlineNodes.size() > 2) {

            ArrayList<String> onlineNodeListDone = new ArrayList<>();
            onlineNodeListDone.add("" + nodeID);
            onlineNodeListDone.add("" + newNodeID);

            updateFingerTableNodeAddition(onlineNodes, newNodeID, newNodeIP, onlineNodeListDone);
        }

        System.out.println("value of i: " + i);


        try {
            serverURL = new URL("http://" + newNodeIP + ":" + 8020);

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

        if (onlineNodes.size() > 2) {
            // call updateAboutNewNode()
        }
    }

    /**
     * update finger table of old nodes, after a new node has been added in the system
     *
     * @param onlineNodes        list of all the online nodes
     * @param newNodeID          id of the new node
     * @param newNodeIP          the ip of the new node
     * @param onlineNodeListDone list of online nodes who's finger table has been updated
     */

    public void updateFingerTableNodeAddition(HashMap<String, String> onlineNodes, int newNodeID, String newNodeIP, ArrayList<String> onlineNodeListDone) {

        onlineNodeListDone.add("" + nodeID);

        System.out.println("online nodes list done: " + onlineNodeListDone);
        System.out.println("online nodes: " + onlineNodes);

        String ipAdd = null;
        System.out.println("ipAdd val inside node addition before" + ipAdd);

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            if (onlineNodeListDone.contains(entry.getKey())) {
                System.out.println("inside contains: " + entry.getKey());
                continue;
            }
            System.out.println("outside contains: " + entry.getKey());
            ipAdd = entry.getValue();
            System.out.println("outside contains: " + entry.getValue());
            break;
        }

        System.out.println("online nodes: " + onlineNodes);

        System.out.println("ipAdd val inside node addition" + ipAdd);

        if (!(ipAdd == null)) {

            try {
                serverURL = new URL("http://" + ipAdd + ":" + 8020);

            } catch (MalformedURLException e) {
                // handle exception...
            }
            // Create new JSON-RPC 2.0 client session
            JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

            String method = "updateFingerTableNodeAddition";
            int requestID = 11;

            ArrayList<Object> list = new ArrayList<>();

            list.add(onlineNodes);
            list.add(newNodeID);
            list.add(newNodeIP);
            list.add(onlineNodeListDone);

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

    public void collectOnlineNodes(String newNodeIP, HashMap onlineNodes) {
        // call the successor node based on the finger table

        try {
            serverURL = new URL("http://" + presentIP.get(0) + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }
        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "collectingOnlineNodes";
        int requestID = 5;

        ArrayList<Object> list = new ArrayList<>();

        list.add(newNodeIP);

        System.out.println("suppose to be 15: " + nodeID + " " + ip);
        onlineNodes.put("" + nodeID, ip);
        System.out.println("checking contents of online nodes: " + onlineNodes);
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
        System.out.println("Present length: " + present.size() + presentIP);
        if (present.get(0) == nodeID) {
            System.out.println("Successor is me!");
            // send the ip of anchor node to the new node
            // TODO: 10/15/18 send ip to new node
            HashMap<String, String> onlineNodes = new HashMap<>();
            onlineNodes.put("" + nodeID, ip);
//            PeerClass peer = new PeerClass();
            peer.contactNewNode(newNodeIP, onlineNodes);

        } else {
            try {
                System.out.println("Inside else");
                System.out.println("present successor: " + presentIP.get(0));
                System.out.println("*******");
                System.out.println("i am the anchor node and pointing to: " + presentIP.get(0));
                serverURL = new URL("http://" + presentIP.get(0) + ":" + 8020); // calling the successor

            } catch (MalformedURLException e) {
                // handle exception...
            }
            // Create new JSON-RPC 2.0 client session
            JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

            String method = "collectingOnlineNodes";
            int requestID = 5;

            ArrayList<Object> list = new ArrayList<>();

            list.add(newNodeIP);

            HashMap<String, String> onlineNodes = new HashMap<>();

//            ArrayList<String> onlineNodes = new ArrayList<>();
            onlineNodes.put("" + nodeID, ip);
            list.add(onlineNodes);

            JSONRPC2Request request = new JSONRPC2Request(method, list, requestID);

            JSONRPC2Response response = null;

            try {
                response = mySession.send(request);

            } catch (JSONRPC2SessionException e) {

                System.err.println(e.getMessage());
                // handle exception...
            }

            System.out.println(presentIP);

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
            serverURL = new URL("http://" + managerIP + ":" + port);

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
        System.out.println(present + "" + present.size());
        System.out.println(presentIP);
    }

    public void initializeFingerTable(HashMap<String, String> onlineNodes, int newNodeID) throws InterruptedException {
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

        System.out.println("I'm the second node: " + newNodeID);
        nodeID = newNodeID;
//
//        onlineNodeList.add(newNodeID);
//        onlineNodes.put("" + newNodeID, ip);

        Collections.sort(onlineNodeList);
        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add(onlineNodeList.get(i) + N);
        }

        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            actual.add(i, (newNodeID + (int) Math.pow(2, i)) % N);
            int k = 0;
            while (actual.get(i) > onlineNodeList.get(k)) {
                k++;
            }
            present.add(i, onlineNodeList.get(k) % N);
            String IPadd = (String) onlineNodes.get("" + onlineNodeList.get(k) % N);
            presentIP.add(i, IPadd);
            relative.add(i, (newNodeID + (int) Math.pow(2, i)));
        }

        System.out.println("Finger Table");
        System.out.println(actual);
        System.out.println(present);
        System.out.println(presentIP);

        // let other threads update their finger table with new node information,
        // and then request for keys from the successor
        Thread.sleep(1000);

        int successorID = 1;
        int predecessorID = 1;

        for (int i = 0; i < onlineNodeList.size(); i++) {
            if (onlineNodeList.get(i) <= newNodeID) {
                continue;
            } else {
                successorID = onlineNodeList.get(i) % N;
                if (i == 1) {
                    predecessorID = onlineNodeList.get(onlineNodeList.size() - 1) % N;
                } else {
                    predecessorID = onlineNodeList.get(i - 2);
                }
                break;
            }
        }

        System.out.println("the successor is: " + onlineNodes.get("" + successorID));
        System.out.println("the predecessor is: " + onlineNodes.get("" + predecessorID));
        peer.requestKeysFromSuccessor(onlineNodes.get("" + successorID), "" + predecessorID);

    }

    public void requestKeysFromSuccessor(String successorIP, String predecessor) {

        System.out.println("Inside requestKeysFromSuccessor");

        try {
            serverURL = new URL("http://" + successorIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }
        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "requestKeys";
        int requestID = 15;

        ArrayList<Object> list = new ArrayList<>();

        list.add("" + nodeID);
        list.add(ip);
        list.add(predecessor);
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
            System.out.println(response.getResult() + "**");
//            if (response.getResult().toString().equals("requestKeys")) {
//                // do nothing
//            } else {
//                String[] keysReceived = response.getResult().toString().split("---");
//
//                System.out.println("Keys received");
//                for (int i = 0; i < keysReceived.length; i++) {
//                    String[] k = keysReceived[i].split(",");
//                    Key key = new Key(Integer.parseInt(k[0]), k[1], Integer.parseInt(k[2]));
//                    System.out.println(key);
//                    keys.add(key);
//                }
//            }
        } else
            System.out.println(response.getError().getMessage());

    }

    public String findKeysforNewNode(String newNodeID, String newNodeIP, String predecessor) {
        System.out.println("Starting");
        int newNodeid = Integer.parseInt(newNodeID);
        int currentID = nodeID;
        int predecessorID = Integer.parseInt(predecessor);
        if (nodeID < newNodeid) {
            currentID += N;
        }

        int k = 1;
        int pos = 0;

        ArrayList<Integer> keyIDs = new ArrayList<>();

        while (true) {
            System.out.println("in while");
            if ((predecessorID + k++) % N != (newNodeid + 1) % N) {
                keyIDs.add(predecessorID + k - 1);
            } else {
                break;
            }
        }

        System.out.println("outside while");

        String keysToSend = "";
        ArrayList<String> keysToRemove = new ArrayList<>();

        if (keys.size() == 0) {
            System.out.println("No keys 1");
            return "No keys";
        }

        System.out.println("over here");

        int size = keys.size();

        for (int i = 0; i < size; i++) {
            if (keyIDs.contains(keys.get(i).id)) {
                keysToSend += keys.get(i).display();
                keysToSend += "---";
                keysToRemove.add(keys.get(i).message);
//                keysToSend.add(keys.get(i).display());
            }
            System.out.println("in for");
        }

        for (int i = 0; i < keys.size(); i++) {
            if (keysToRemove.contains(keys.get(i).message)) {
                keys.remove(i);
                i--;
            }
        }

        System.out.println("Ending");

        if (keysToSend.equals("")) {
            System.out.println("No Keys 2");
            return "No keys";
        }

        return keysToSend;
    }

    public void sendKeysToNewNode(String keyString, String newNodeIP) {
        System.out.println("Inside requestKeysFromSuccessor");

        try {
            serverURL = new URL("http://" + newNodeIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }
        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "sendingKeysToNewNode";
        int requestID = 16;

        ArrayList<Object> list = new ArrayList<>();

        list.add(keyString);
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

    public int updateFingerTable(HashMap<String, String> onlineNodes, String newNodeIP, int newNodeID) {
        // here finger table will get updated and a node id will be assigned to the new node and will be sent to each node.

        ArrayList<Integer> onlineNodeList = new ArrayList<>();
        Iterator it = onlineNodes.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry pair = (Map.Entry) it.next();
//            onlineNodeList.add((Integer) pair.getKey());
//        }

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            onlineNodeList.add(Integer.parseInt(entry.getKey()));
        }

        if (newNodeID == -1) {

            int hash = Math.abs(newNodeIP.hashCode()) % N;
            newNodeID = findAptNodeID(onlineNodeList, hash);
            onlineNodeList.add(newNodeID);
            onlineNodes.put("" + newNodeID, newNodeIP);
        }

        Collections.sort(onlineNodeList);
        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add(onlineNodeList.get(i) + N);
        }

        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            int k = 0;
            while (actual.get(i) > onlineNodeList.get(k)) {
                k++;
            }
            present.remove(i);
            present.add(i, onlineNodeList.get(k) % N);
            presentIP.remove(i);
            String IPadd = (String) onlineNodes.get("" + (onlineNodeList.get(k) % N));
            presentIP.add(i, IPadd);
        }

        System.out.println("updated presentIP: " + presentIP);

        return newNodeID;
    }

    /**
     * to assign node id to the new node based on the vacancy
     *
     * @param onlineNodeList
     * @param hash
     * @return
     */

    public int findAptNodeID(ArrayList<Integer> onlineNodeList, int hash) {

        Random rand = new Random();

        hash = rand.nextInt(N);

        while (onlineNodeList.contains(hash)) {
            hash = (hash + rand.nextInt(N)) % N;
        }
        return hash;
    }

    public void deliverKey(Key key, String operation, String nodeIP) {
        try {
            // contacting the manager
            serverURL = new URL("http://" + nodeIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }


        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = operation;
        int requestID = 12;
        ArrayList<Object> list = new ArrayList<>();
        list.add("" + key.id + " " + key.message + " " + key.size);
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

    /**
     * determines where to send the key to
     *
     * @param key the key to be sent
     */

    public void findNodeForKey(Key key) {
        System.out.println("Inside findnodeforkey");
        int keyID = key.id;
        if (present.contains(keyID)) {
            int id = present.indexOf(keyID);
            peer.deliverKey(key, "StoreKeyInsert", presentIP.get(id));
            System.out.println("StoreKeyInsert1");
        } else {
            if (keyID < relative.get(0)) {
                keyID += N;
            }
            int i = 0;
            for (i = 0; i < 4; i++) {
                if (keyID < relative.get(i)) {
                    break;
                }
            }
            i--;
            if (actual.get(i) <= present.get(i)) {
                if ((keyID) < present.get(i)) {
                    peer.deliverKey(key, "StoreKeyInsert", presentIP.get(i));
                    System.out.println("StoreKeyInsert2");
                } else {
                    peer.deliverKey(key, "FindKeyInsert", presentIP.get(i));
                    System.out.println("FindKeyInsert4");
                }
            } else {
                int presentI = present.get(i) + N;
                if (keyID < presentI) {
                    peer.deliverKey(key, "StoreKeyInsert", presentIP.get(i));
                    System.out.println("StoreKeyInsert3");
                } else {
                    peer.deliverKey(key, "FindKeyInsert", presentIP.get(i));
                    System.out.println("FindKeyInsert5");
                }
            }
        }
    }

    /**
     * creates a key and checks whether it belongs to the current node,
     * if not then route to the appropriate node using finger table
     */

    public void insertNode() {
        Scanner src = new Scanner(System.in);
        System.out.println("Enter file name");
        String fileName = src.next();
        int size = src.nextInt();
        int keyhash = Math.abs((fileName + size).hashCode() % N);
        System.out.println("key hssh: " + keyhash);
        Key key = new Key(keyhash, fileName, size);

        if (nodeID == keyhash) {
            System.out.println("Adding key here.");
            keys.add(key);
        } else {
            System.out.println("In else");
            peer.findNodeForKey(key);
        }
    }

    /**
     * Send keys to the successor before going offline.
     */

    public void sendKeysToSuccessor() {
        String successorIP = presentIP.get(0);

        try {
            // contacting the manager
            serverURL = new URL("http://" + successorIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }

        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "sendingKeysToSuccessor";
        int requestID = 18;
        ArrayList<Object> list = new ArrayList<>();
        String keyString = "";

        if (keys.size() == 0) {
            System.out.println("No keys to send to successor");
        } else {

            // making string of keys
            for (Key key : keys) {
                keyString += "" + key.id + "," + key.message + "," + key.size;
                keyString += "---";
            }

            list.add(keyString);
            list.add("" + nodeID);
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
        peer.contactSuccessorToRemove();
    }

    public void contactSuccessorToRemove() {
        System.out.println("inside contactSuccessor");
        String successorIP = presentIP.get(0);

        try {
            // contacting the manager
            serverURL = new URL("http://" + successorIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }

        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        ArrayList<Object> list = new ArrayList<>();
        list.add("" + nodeID);
        list.add(ip);

        String method = "removeMe";
        int requestID = 19;

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

    public void contactOnlineNodesRemove(HashMap<String, String> onlineNodes, String startingNodeID, String offlineNodeID, String offlineNodeIP) {

        System.out.println("inside contactOnlineNodesRemove");

        System.out.println("NodeID: " + nodeID);
        System.out.println("Starting node: " + startingNodeID);
        // completes the loop
        if (startingNodeID.equals("" + nodeID) && onlineNodes.containsKey((String)"" + nodeID)) {
            peer.allowNodeGoOffline(offlineNodeIP);
            peer.updateFingerTableNodeRemove(onlineNodes, offlineNodeID, offlineNodeIP);
            ArrayList<String> onlineNodeListDone = new ArrayList<>();
            onlineNodeListDone.add(offlineNodeID);
            onlineNodeListDone.add("" + nodeID);
            peer.updateFingerTableNodeRemovePassOn(onlineNodes, offlineNodeID, offlineNodeIP, onlineNodeListDone);
            // contact node to go offline successfully
            return;
        }

        String successorIP = presentIP.get(0);

        try {
            // contacting the manager
            serverURL = new URL("http://" + successorIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }

        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "collectingOnlineNodesRemove";
        int requestID = 20;
        ArrayList<Object> list = new ArrayList<>();

        onlineNodes.put("" + nodeID, ip);

        list.add(startingNodeID);
        list.add(offlineNodeID);
        list.add(offlineNodeIP);
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

        // updatefingertableremove()
    }

    public void allowNodeGoOffline(String offlineNodeIP) {

        try {
            // contacting the manager
            serverURL = new URL("http://" + offlineNodeIP + ":" + 8020);

        } catch (MalformedURLException e) {
            // handle exception...
        }

        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

        String method = "allowNodeGoOffline";
        int requestID = 21;

        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

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

    public void updateFingerTableNodeRemove(HashMap<String, String> onlineNodes, String offlineNodeID, String offlineNodeIP) {
        // here finger table will get updated and a node id will be assigned to the new node and will be sent to each node.

        System.out.println("Inside updateFingerTableNodeRemove");

        ArrayList<String> onlineNodeList = new ArrayList<>();

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            onlineNodeList.add(entry.getKey());
        }
        System.out.println("OfflineNodeID: " + offlineNodeID);
        onlineNodeList.remove(offlineNodeID);

        ArrayList<Integer> temp = new ArrayList<>();
        for (String s : onlineNodeList) {
            temp.add(Integer.parseInt(s));
        }

        Collections.sort(temp);
        System.out.println("temp: " + temp);
        System.out.println("1. before sorting OnlineNodeList: " + onlineNodeList);

        onlineNodeList.clear();
        int pos = 0;
        for (Integer i : temp) {
            onlineNodeList.add(pos++, "" + i);
        }

        System.out.println("before sorting OnlineNodeList: " + onlineNodeList);
        System.out.println("OnlineNodeList: " + onlineNodeList);
        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add("" + (Integer.parseInt(onlineNodeList.get(i)) + N));
        }

        System.out.println("OnlineNodeList: " + onlineNodeList);
        System.out.println("OnlineNodes: " + onlineNodes);

        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            int k = 0;
            while (actual.get(i) > Integer.parseInt(onlineNodeList.get(k))) {
                System.out.println("K: " + k);
                k++;
            }
            present.remove(i);
            present.add(i, Integer.parseInt(onlineNodeList.get(k)) % N);
            presentIP.remove(i);
            String IPadd = (String) onlineNodes.get("" + (Integer.parseInt(onlineNodeList.get(k)) % N));
            presentIP.add(i, IPadd);
        }

        System.out.println("updated presentIP: " + presentIP);
    }

    public void updateFingerTableNodeRemovePassOn(HashMap<String, String> onlineNodes, String oflineNodeID, String offlineNodeIP, ArrayList<String> onlineNodeListDone) {

        onlineNodeListDone.add("" + nodeID);

        System.out.println("online nodes list done: " + onlineNodeListDone);
        System.out.println("online nodes: " + onlineNodes);

        String ipAdd = null;
        System.out.println("ipAdd val inside node addition before" + ipAdd);

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            if (onlineNodeListDone.contains(entry.getKey())) {
                System.out.println("inside contains: " + entry.getKey());
                continue;
            }
            System.out.println("outside contains: " + entry.getKey());
            ipAdd = entry.getValue();
            System.out.println("outside contains: " + entry.getValue());
            break;
        }

        System.out.println("online nodes: " + onlineNodes);

        System.out.println("ipAdd val inside node addition" + ipAdd);

        if (!(ipAdd == null)) {

            try {
                serverURL = new URL("http://" + ipAdd + ":" + 8020);

            } catch (MalformedURLException e) {
                // handle exception...
            }
            // Create new JSON-RPC 2.0 client session
            JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

            String method = "updateFingerTableNodeRemovePassOn";
            int requestID = 20;

            ArrayList<Object> list = new ArrayList<>();

            list.add(onlineNodes);
            list.add(oflineNodeID);
            list.add(offlineNodeIP);
            list.add(onlineNodeListDone);

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

    public static void main(String[] args) throws IOException, InterruptedException {

//        PeerClass peer = new PeerClass();
        InetAddress localhost = InetAddress.getLocalHost();
//        System.out.println("Enter port number");
        Scanner src = new Scanner(System.in);
        PORT = 8020;
        System.out.println(localhost.toString().split("/")[1]);
        ip = localhost.toString().split("/")[1];
        managerIP = args[0];

        peer.start();

        System.out.println("Welcome Peer to the DHT Chord system!");

        peer.initialize(8015);

        // TODO: 10/13/18 assign node id functionality
        System.out.println("Choose Node id: Y/N ?");

        while (true) {
            System.out.println("Select the following functionalities \n 1. Display Keys \n 2. Upload Keys \n" +
                    " 3. Go Offline");
            int ch;

            do {
                ch = src.nextInt();
                switch (ch) {
                    case 1:
                        System.out.println(relative);
                        System.out.println(actual);
                        System.out.println(present + "" + present.size());
                        System.out.println(presentIP);
                        break;
                    case 2:
                        peer.insertNode();
                        break;
                    case 3:
                        peer.getKey();
                        break;
                    case 4:
                        peer.sendKeysToSuccessor();
                        break;
                    default:
                        System.out.println("Invalid choice");
                }

            } while (ch != 4);

        }
    }
}

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

    Random random = new Random();
    private static int N = 16;


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
            this.socket = socket;
            // Create a new JSON-RPC 2.0 request dispatcher
            this.dispatcher = new Dispatcher();

            dispatcher.register(new JsonHandlerForPeer.NewNodeJoiner());
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
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // read request
                String line;
                line = in.readLine();
                StringBuilder raw = new StringBuilder();
                raw.append("" + line);
                boolean isPost = line.startsWith("POST");
                int contentLength = 0;
                while (!(line = in.readLine()).equals("")) {
                    raw.append('\n' + line);
                    if (isPost) {
                        final String contentHeader = "Content-Length: ";
                        if (line.startsWith(contentHeader)) {
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

                JSONRPC2Request request = JSONRPC2Request.parse(body.toString());

                JSONRPC2Response resp = dispatcher.process(request, null);

                resp.getResult().toString();

                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("\r\n");

                if (resp.getResult().toString().split(",")[0].equals("JoinNewNode")) {
                    // do something
                    out.write(resp.toJSONString()); // closing the connection with the manager
                    out.flush();
                    out.close();
                    socket.close();
                    String newNodeIP = resp.getResult().toString().split(",")[1];
                    peer.startCollectingOnlineNodes(newNodeIP);

                } else if (resp.getResult().toString().equals("CollectOnlineNodes")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    String newNodeIP = (String) paramList.get(0);
                    String startingNodeIP = (String) paramList.get(1);
                    HashMap<String, String> onlineNodes = (HashMap) paramList.get(2);

                    out.write(resp.toJSONString());
                    // do not in.close();
                    out.flush();
                    out.close();
                    socket.close();

                    if (!startingNodeIP.equals(ip)) { // not the anchor node
                        peer.collectOnlineNodes(newNodeIP, startingNodeIP, onlineNodes);
                    } else {
                        // loop completed, make anchor node contact the new node
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

                    String function = (String) paramList.get(1);
                    String findNodeIP = (String) paramList.get(2);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    if (function.equals("Store")) {
                        keys.add(key);
                        System.out.println("Key added successfully: " + key);
                    } else {
                        int flag = 0;
                        for (int i = 0; i < keys.size(); i++) {
                            if (keys.get(i).id == key.id) {
                                peer.notifyNodeAboutKey("found", findNodeIP);
                                flag = 1;
                            }
                        }
                        if (flag == 0) {
                            peer.notifyNodeAboutKey("notFound", findNodeIP);
                        }
                    }

                } else if (resp.getResult().toString().equals("FindKeyInsert")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String k = (String) paramList.get(0);
                    String[] k2 = k.split(" ");
                    Key key = new Key(Integer.parseInt(k2[0]), k2[1], Integer.parseInt(k2[2]));

                    String function = (String) paramList.get(1);
                    String findNodeIP = (String) paramList.get(2);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    if (nodeID == key.id) {
                        keys.add(key);
                        System.out.println("Key added successfully: " + key);
                    } else {
                        peer.findNodeForKey(key, function, findNodeIP);
                        System.out.println("received key, passing on");
                    }
                } else if (resp.getResult().toString().equals("requestKeys")) {
                    ArrayList<Object> paramList = (ArrayList) request.getParams();

                    String newNodeID = (String) paramList.get(0);
                    String newNodeIP = (String) paramList.get(1);
                    String predecessor = (String) paramList.get(2);

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
                        keys.add(key);
                    }
                } else if (resp.getResult().toString().equals("removeMe")) {

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

//                    Manager.onlineNodes.remove(nodeID);

                    System.out.println("Going offline!");
                    peer.removeFromAnchorNodeList();
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
                } else if (resp.getResult().toString().equals("notifyAboutKey")) {

                    ArrayList<Object> paramList = (ArrayList) request.getParams();
                    String status = (String) paramList.get(0);
                    String nodeID = (String) paramList.get(1);

                    out.write(resp.toJSONString());
                    out.flush();
                    out.close();
                    socket.close();

                    if (status.equals("found")) {
                        System.out.println("The file is stored on node: " + nodeID);
                    } else {
                        System.out.println("File is not present in the system.");
                    }
                } else {

                    // send response

//                    System.out.println(resp.toJSONString());
                    out.write(resp.toJSONString());
                    // do not in.close();

                }
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

        int newNodeID = updateFingerTable(onlineNodes, newNodeIP, -1);

        // making other online nodes update their FT with the information regarding this new node
        if (onlineNodes.size() > 2) {

            ArrayList<String> onlineNodeListDone = new ArrayList<>();
            onlineNodeListDone.add("" + nodeID);
            onlineNodeListDone.add("" + newNodeID);

            updateFingerTableNodeAddition(onlineNodes, newNodeID, newNodeIP, onlineNodeListDone);
        }

        String method = "initializeNewNode";
        int requestID = 6;

        ArrayList<Object> list = new ArrayList<>();

        list.add(onlineNodes);
        list.add(newNodeID);
        list.add(newNodeIP);

        peer.connection(newNodeIP, 8020, method, requestID, list);

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

        String ipAdd = null;

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            if (onlineNodeListDone.contains(entry.getKey())) {
                continue;
            }
            ipAdd = entry.getValue();
            break;
        }

        if (!(ipAdd == null)) {

            String method = "updateFingerTableNodeAddition";
            int requestID = 11;

            ArrayList<Object> list = new ArrayList<>();

            list.add(onlineNodes);
            list.add(newNodeID);
            list.add(newNodeIP);
            list.add(onlineNodeListDone);

            peer.connection(ipAdd, 8020, method, requestID, list);

        }
    }

    /**
     * to collect the list of all online roles
     *
     * @param newNodeIP   IP address of the new node
     * @param onlineNodes map of online nodes found yet
     */

    public void collectOnlineNodes(String newNodeIP, String startingNodeIP, HashMap onlineNodes) {
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
        list.add(startingNodeIP);

        onlineNodes.put("" + nodeID, ip);
        list.add(onlineNodes);

        peer.connection(presentIP.get(0), 8020, method, requestID, list);

    }

    /**
     * start collecting online nodes, started by the anchor nodes
     *
     * @param newNodeIP IP address of the new node
     */

    public void startCollectingOnlineNodes(String newNodeIP) {
        // call the successor node based on the finger table
        // checking if the successor is itself
        if (present.get(0) == nodeID) {
            // send the ip of anchor node to the new node
            HashMap<String, String> onlineNodes = new HashMap<>();
            onlineNodes.put("" + nodeID, ip);
//            PeerClass peer = new PeerClass();
            peer.contactNewNode(newNodeIP, onlineNodes);

        } else {

            String method = "collectingOnlineNodes";
            int requestID = 5;

            ArrayList<Object> list = new ArrayList<>();

            list.add(newNodeIP);
            list.add(ip); // starting node IP

            HashMap<String, String> onlineNodes = new HashMap<>();

//            ArrayList<String> onlineNodes = new ArrayList<>();
            onlineNodes.put("" + nodeID, ip);
            list.add(onlineNodes);

            peer.connection(presentIP.get(0), 8020, method, requestID, list);

        }
    }

    boolean flag = true;

    /**
     * displaying the finger table
     */

    public void display() {
        System.out.println("Finger table for node: " + nodeID);
        System.out.println(" -----------------------------------------------");
        System.out.println("| \ti \t| k + 2i   \t| successor     |");
        System.out.println("|-----------------------------------------------|");
        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            System.out.println("|\t" + i + " \t|   " + actual.get(i) + "\t\t|" + present.get(i) + " (" + presentIP.get(i) + ") |");
        }
        System.out.println(" -----------------------------------------------");
    }

    /**
     * display the list keys
     */

    public void displayKeys() {
        if (keys.size() == 0) {
            System.out.println("No files present in the system");
        } else {
            System.out.println("Files Present in the node: ");
            System.out.println(" -----------------------");
            System.out.println("|  id \t| Name\t| Size\t|");
            System.out.println("|-----------------------|");
            for (int i = 0; i < keys.size(); i++) {
                System.out.println("|  " + keys.get(i).id + "\t| " + keys.get(i).message + "\t| " + keys.get(i).size + "\t|");
            }
            System.out.println(" -----------------------");
        }
    }

    /**
     * method start the thread for listening requests
     */

    public void run() {
        System.out.println("Peer is running.");
        ServerSocket listener = null;
        try {
            listener = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Ip " + ip);
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

    public void getKey() {
        if (keys.size() == 0) {
            System.out.println("No keys present.");
        } else {
            for (Key key : keys) {
                System.out.println(key);
            }
        }
    }

    /**
     * to contact the manager when a new node joins the system
     *
     * @param port manager's port
     */

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

    /**
     * to initialize a finger when new node comes into the system
     */

    public void initializeFingerTable() {
//        System.out.println("Inside finger table");
        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            relative.add((int) (1 + Math.pow(2, i)));
            actual.add((int) (1 + Math.pow(2, i)) % N);
            present.add(1);
            presentIP.add(ip);
        }
        peer.display();
    }

    /**
     * initialize finger table when a new node joins the system
     *
     * @param onlineNodes list of all the online nodes
     * @param newNodeID   id of the new node
     * @throws InterruptedException
     */

    public void initializeFingerTable(HashMap<String, String> onlineNodes, int newNodeID) throws InterruptedException {

        ArrayList<Integer> onlineNodeList = new ArrayList<>();
        Iterator it = onlineNodes.entrySet().iterator();

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            onlineNodeList.add(Integer.parseInt(entry.getKey()));
        }

        System.out.println("My node ID: " + newNodeID);
        nodeID = newNodeID;

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

        peer.requestKeysFromSuccessor(onlineNodes.get("" + successorID), "" + predecessorID);

    }

    /**
     * to request keys from successor when a new node joins the system
     *
     * @param successorIP IP address of the new ndoe
     * @param predecessor IP address of the new node
     */

    public void requestKeysFromSuccessor(String successorIP, String predecessor) {

        String method = "requestKeys";
        int requestID = 15;

        ArrayList<Object> list = new ArrayList<>();

        list.add("" + nodeID);
        list.add(ip);
        list.add(predecessor);

        peer.connection(successorIP, 8020, method, requestID, list);

    }

    /**
     * to get list of keys for the new node
     *
     * @param newNodeID
     * @param newNodeIP
     * @param predecessor
     * @return
     */

    public String findKeysforNewNode(String newNodeID, String newNodeIP, String predecessor) {
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
            if ((predecessorID + k++) % N != (newNodeid + 1) % N) {
                keyIDs.add(predecessorID + k - 1);
            } else {
                break;
            }
        }


        String keysToSend = "";
        ArrayList<String> keysToRemove = new ArrayList<>();

        if (keys.size() == 0) {
            return "No keys";
        }

        int size = keys.size();

        for (int i = 0; i < size; i++) {
            if (keyIDs.contains(keys.get(i).id)) {
                keysToSend += keys.get(i).display();
                keysToSend += "---";
                keysToRemove.add(keys.get(i).message);
            }
        }

        for (int i = 0; i < keys.size(); i++) {
            if (keysToRemove.contains(keys.get(i).message)) {
                keys.remove(i);
                i--;
            }
        }

        if (keysToSend.equals("")) {
            return "No keys";
        }

        return keysToSend;
    }

    /**
     * send keys to the new node
     *
     * @param keyString the keys
     * @param newNodeIP IP address of the new node
     */

    public void sendKeysToNewNode(String keyString, String newNodeIP) {

        String method = "sendingKeysToNewNode";
        int requestID = 16;

        ArrayList<Object> list = new ArrayList<>();

        list.add(keyString);

        peer.connection(newNodeIP, 8020, method, requestID, list);

    }

    /**
     * to update the finger table
     *
     * @param onlineNodes list of online nodes
     * @param newNodeIP   IP address of the new node
     * @param newNodeID   ID of the new node
     * @return
     */

    public int updateFingerTable(HashMap<String, String> onlineNodes, String newNodeIP, int newNodeID) {
        // here finger table will get updated and a node id will be assigned to the new node and will be sent to each node.

        ArrayList<Integer> onlineNodeList = new ArrayList<>();
        Iterator it = onlineNodes.entrySet().iterator();

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

    /**
     * to send the key to the correct node
     *
     * @param key        the key to be delivered
     * @param operation  tells whether to store of pass key to the next node
     * @param nodeIP     IP address of the node to pass on
     * @param function   tells whether this is search or store operation
     * @param findNodeIP IP address of the node which initiates the search operation
     */

    public void deliverKey(Key key, String operation, String nodeIP, String function, String findNodeIP) {
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
        list.add(function);
        list.add(findNodeIP);
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
//            System.out.println(response.getResult());
        } else
            System.out.println(response.getError().getMessage());
    }

    /**
     * determines where to send the key to
     *
     * @param key the key to be sent
     */

    public void findNodeForKey(Key key, String function, String findNodeIP) {
        int keyID = key.id;
        if (present.contains(keyID)) {
            int id = present.indexOf(keyID);
            peer.deliverKey(key, "StoreKeyInsert", presentIP.get(id), function, findNodeIP);
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
                if (actual.get(i) > (keyID % N)) {
                    if ((keyID) < present.get(i)) {
                        peer.deliverKey(key, "StoreKeyInsert", presentIP.get(i), function, findNodeIP);
                    } else {
                        peer.deliverKey(key, "FindKeyInsert", presentIP.get(i), function, findNodeIP);
                    }
                }
                else if ((keyID % N) < present.get(i)) {
                    peer.deliverKey(key, "StoreKeyInsert", presentIP.get(i), function, findNodeIP);
                }else {
                    peer.deliverKey(key, "FindKeyInsert", presentIP.get(i), function, findNodeIP);
                }
            } else {
                int presentI = present.get(i) + N;
                if (keyID < presentI) {
                    peer.deliverKey(key, "StoreKeyInsert", presentIP.get(i), function, findNodeIP);
                } else {
                    peer.deliverKey(key, "FindKeyInsert", presentIP.get(i), function, findNodeIP);
                }
            }
        }
    }

    /**
     * creates a key and checks whether it belongs to the current node,
     * if not then route to the appropriate node using finger table
     */

    public void insertNode(String function, String findNodeIP) {
        Scanner src = new Scanner(System.in);
        System.out.println("Enter file name");
        String fileName = src.next();
        System.out.println("Enter size of the file");
        int size = 100;
        try {
            size = src.nextInt();
        } catch (InputMismatchException e) {
            System.out.println("Enter correct size.");
            return;
        }
        int keyhash = Math.abs((fileName + size).hashCode() % N);
        System.out.println("key hash: " + keyhash);
        Key key = new Key(keyhash, fileName, size);

        if (function.equals("Store")) {

            if (nodeID == keyhash) {
                System.out.println("Adding key here.");
                keys.add(key);
            } else {
                peer.findNodeForKey(key, function, findNodeIP);
            }
        } else {
            if (nodeID == keyhash) {
                int flag = 0;
                for (int i = 0; i < keys.size(); i++) {
                    if (keys.get(i).id == key.id) {
                        System.out.println(key.message + "File is stored at this node");
                        flag = 1;
                        break;
                    }
                }
                if (flag == 0) {
                    System.out.println("File is not present in the system");
                }
            } else {
                peer.findNodeForKey(key, function, findNodeIP);
            }
        }
    }

    /**
     * Send keys to the successor before going offline.
     */

    public void sendKeysToSuccessor() {
        String successorIP = presentIP.get(0);

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

            peer.connection(successorIP, 8020, method, requestID, list);

        }
        peer.contactSuccessorToRemove();
    }

    /**
     * contacts the successor and pass on the key
     */

    public void contactSuccessorToRemove() {
        String successorIP = presentIP.get(0);

        ArrayList<Object> list = new ArrayList<>();
        list.add("" + nodeID);
        list.add(ip);

        String method = "removeMe";
        int requestID = 19;

        peer.connection(successorIP, 8020, method, requestID, list);

    }

    /**
     * to collect the list of online nodes when a node wants to go offline
     *
     * @param onlineNodes    map of online nodes
     * @param startingNodeID ID of the starting node
     * @param offlineNodeID  ID of the node going offline
     * @param offlineNodeIP  IP address of the node going offline
     */

    public void contactOnlineNodesRemove(HashMap<String, String> onlineNodes, String startingNodeID, String offlineNodeID, String offlineNodeIP) {

        // completes the loop
        if (startingNodeID.equals("" + nodeID) && onlineNodes.containsKey((String) "" + nodeID)) {
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

        String method = "collectingOnlineNodesRemove";
        int requestID = 20;
        ArrayList<Object> list = new ArrayList<>();

        onlineNodes.put("" + nodeID, ip);

        list.add(startingNodeID);
        list.add(offlineNodeID);
        list.add(offlineNodeIP);
        list.add(onlineNodes);

        peer.connection(successorIP, 8020, method, requestID, list);

    }

    /**
     * to let the node know that it can go offline
     *
     * @param offlineNodeIP IP address of the node which wants to go offline
     */

    public void allowNodeGoOffline(String offlineNodeIP) {

        String method = "allowNodeGoOffline";
        int requestID = 21;

        ArrayList<Object> list = new ArrayList<>();

        peer.connection(offlineNodeIP, 8020, method, requestID, list);

    }

    /**
     * to update the finger table when a node goes offline from the system
     *
     * @param onlineNodes   map of online nodes
     * @param offlineNodeID ID of the offline node
     * @param offlineNodeIP IP address of the offline node
     */

    public void updateFingerTableNodeRemove(HashMap<String, String> onlineNodes, String offlineNodeID, String offlineNodeIP) {

        ArrayList<String> onlineNodeList = new ArrayList<>();

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            onlineNodeList.add(entry.getKey());
        }
        onlineNodeList.remove(offlineNodeID);

        ArrayList<Integer> temp = new ArrayList<>();
        for (String s : onlineNodeList) {
            temp.add(Integer.parseInt(s));
        }

        Collections.sort(temp);

        onlineNodeList.clear();
        int pos = 0;
        for (Integer i : temp) {
            onlineNodeList.add(pos++, "" + i);
        }

        int size = onlineNodeList.size();
        // adding elements by adding with N
        for (int i = 0; i < size; i++) {
            onlineNodeList.add("" + (Integer.parseInt(onlineNodeList.get(i)) + N));
        }

        for (int i = 0; i < (int) (Math.log(16) / Math.log(2)); i++) {
            int k = 0;
            while (actual.get(i) > Integer.parseInt(onlineNodeList.get(k))) {
                k++;
            }
            present.remove(i);
            present.add(i, Integer.parseInt(onlineNodeList.get(k)) % N);
            presentIP.remove(i);
            String IPadd = (String) onlineNodes.get("" + (Integer.parseInt(onlineNodeList.get(k)) % N));
            presentIP.add(i, IPadd);
        }
    }

    /**
     * to update the finger table when a node got removed
     *
     * @param onlineNodes        map of online nodes
     * @param oflineNodeID       ID of the offline node
     * @param offlineNodeIP      IP address of the offline node
     * @param onlineNodeListDone list of online nodes who is done upating their finger table
     */

    public void updateFingerTableNodeRemovePassOn(HashMap<String, String> onlineNodes, String oflineNodeID, String offlineNodeIP, ArrayList<String> onlineNodeListDone) {

        onlineNodeListDone.add("" + nodeID);

        String ipAdd = null;

        for (Map.Entry<String, String> entry : onlineNodes.entrySet()) {
            if (onlineNodeListDone.contains(entry.getKey())) {
                continue;
            }
            ipAdd = entry.getValue();
            break;
        }

        if (!(ipAdd == null)) {


            String method = "updateFingerTableNodeRemovePassOn";
            int requestID = 20;

            ArrayList<Object> list = new ArrayList<>();

            list.add(onlineNodes);
            list.add(oflineNodeID);
            list.add(offlineNodeIP);
            list.add(onlineNodeListDone);

            peer.connection(ipAdd, 8020, method, requestID, list);

        }
    }

    /**
     * to let the node know of the position of the file
     *
     * @param status     tells if the node is present in the system or not
     * @param findNodeIP IP address of the node where the search request for file got initiated
     */

    public void notifyNodeAboutKey(String status, String findNodeIP) {

        String method = "notifyAboutKey";
        int requestID = 22;
        ArrayList<Object> list = new ArrayList<>();
        list.add(status);
        list.add("" + nodeID);

        peer.connection(findNodeIP, 8020, method, requestID, list);

    }

    /**
     * Acknowledging manager to remove from current list of anchor nodes
     */

    public void removeFromAnchorNodeList() {

        String method = "removeFromAnchorNodeList";
        int requestID = 25;
        ArrayList<Object> list = new ArrayList<>();
        list.add(ip);

        peer.connection(managerIP, 8015, method, requestID, list);

    }

    /**
     * Establishes connection to other peers
     *
     * @param ipAdd
     * @param port
     * @param method
     * @param requestID
     * @param list
     */

    public void connection (String ipAdd, int port, String method, int requestID, ArrayList<Object> list) {
        try {
            serverURL = new URL("http://" + ipAdd + ":" + port);

        } catch (MalformedURLException e) {
            // handle exception...
        }

        // Create new JSON-RPC 2.0 client session
        JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

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
//            System.out.println(response.getResult());
        } else
            System.out.println(response.getError().getMessage());
    }

    /**
     * The main function
     *
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */

    public static void main(String[] args) throws IOException, InterruptedException {

        InetAddress localhost = InetAddress.getLocalHost();
        PORT = 8020;
        System.out.println(localhost.toString().split("/")[1]);
        ip = localhost.toString().split("/")[1];
        managerIP = args[0];

        peer.start();

        System.out.println("Welcome Peer to the DHT Chord system!");

        peer.initialize(8015);

        System.out.println("Choose Node id: Y/N ?");

        while (true) {
            System.out.println("Select the following functionalities \n 1. Display Keys | 2. Upload File |" +
                    " 3. Get list of files \n 4. Go Offline | 5. Find a File");
            int ch;

            Scanner src = new Scanner(System.in);
            try {
                ch = src.nextInt();
                switch (ch) {
                    case 1:
                        peer.display();
                        break;
                    case 2:
                        peer.insertNode("Store", ip);
                        break;
                    case 3:
                        peer.displayKeys();
                        break;
                    case 4:
                        peer.sendKeysToSuccessor();
                        break;
                    case 5:
                        peer.insertNode("Find", ip);
                        break;
                    default:
                        System.out.println("Invalid choice");
                }

            } catch (InputMismatchException e) {
                System.out.println("incorrect input");
            }

        }
    }
}

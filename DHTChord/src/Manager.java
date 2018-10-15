

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This is a lite manager which will store data for some of the online nodes in the system.
 */

public class Manager {

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

                // checking for the first element in the system
                if (resp.getResult().toString().charAt(0) == '1') {
                    onlineNodes.put(1, resp.getResult().toString().split(",")[1]);
                    // send response
                    out.write("HTTP/1.1 200 OK\r\n");
                    out.write("Content-Type: application/json\r\n");
                    out.write("\r\n");
                    out.write("First Node");

                    out.flush();
                    out.close();
                    socket.close();

                } else {
                    System.out.println("Request received");

                    out.flush();
                    out.close();
                    socket.close();


                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONRPC2ParseException e) {
                e.printStackTrace();
            }
        }
    }



    public static void main(String[] args) {

    }
}

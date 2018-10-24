package DHTChord;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.util.List;

public class JsonHandlerForClient1 {

    public static class PassKey implements RequestHandler {

        @Override
        public String[] handledRequests() {
            return new String[]{"passKey"};
        }

        @Override
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext) {
            if (req.getMethod().equals("passKey")) {
                List params = (List) req.getParams();
                String option = params.get(0).toString();
                return new JSONRPC2Response("" + option + ", 345", req.getID());
            }
            return null;
        }
    }

    public static class NewNodeJoiner implements RequestHandler {

        @Override
        public String[] handledRequests() {
            return new String[]{"newNode", "getOnlineNodes", "collectingOnlineNodes", "initializeNewNode", "updateFingerTableNodeAddition"};
        }

        @Override
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext) {


            if (req.getMethod().equals("newNode")) {
//                System.out.println("this ip address is: " + (String) req.getParams());

                List params = (List) req.getParams();
                String ip = (String) params.get(0);
                return new JSONRPC2Response("JoinNewNode," + ip, req.getID());

            } else if (req.getMethod().equals("getOnlineNodes")) {
                List params = (List) req.getParams();
                String newNodeIP = params.get(0).toString();

            } else if (req.getMethod().equals("collectingOnlineNodes")) {

                return new JSONRPC2Response("CollectOnlineNodes", req.getID());
            } else if (req.getMethod().equals("initializeNewNode")) {

                return new JSONRPC2Response("initializeNewNode", req.getID());
            } else if (req.getMethod().equals("updateFingerTableNodeAddition")) {

                return new JSONRPC2Response("updateFingerTableNodeAddition", req.getID());
            }
            return null;
        }



        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext, Object params) {


            if (req.getMethod().equals("getClientID")) {
//                System.out.println("this ip address is: " + (String) req.getParams());
                return new JSONRPC2Response("IP address successfully received", req.getID());
            }
            return null;
        }
    }
}

package DHTChord;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.util.List;
import java.util.Random;

public class JsonHandlerForManager {

    public static class NodeJoiner implements RequestHandler {

        Random random = new Random();

        @Override
        public String[] handledRequests() {
            return new String[]{"joinNode", "removeFromAnchorNodeList"};
        }

        @Override
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext) {
            if (req.getMethod().equals("joinNode")) {
                // TODO: 10/14/18 join node
                int size = Manager.onlineNodes.size();

                List params = (List) req.getParams();
                String ip  = (String) params.get(0);
                // if size == 0 => first online node in the system
                if (size == 0) {
                    return new JSONRPC2Response("FirstNode," + ip, req.getID());
                } else {
                    return new JSONRPC2Response("NotFirstNode," + ip, req.getID());
                }
                //return new JSONRPC2Response("string to return", req.getID());
            } else if (req.getMethod().equals("removeFromAnchorNodeList")) {
                List params = (List) req.getParams();
                String ip  = (String) params.get(0);

                return new JSONRPC2Response("RemoveNode," + ip, req.getID());
            }

            return null;
        }
    }
}
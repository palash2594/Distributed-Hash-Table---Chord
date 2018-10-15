
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.util.List;

public class JsonHandlerForManager {

    public static class NodeJoiner implements RequestHandler {

        @Override
        public String[] handledRequests() {
            return new String[]{"joinNode"};
        }

        @Override
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext) {
            if (req.getMethod().equals("joinNode")) {
                // TODO: 10/14/18 join node
                int size = Manager.onlineNodes.size();

                List params = (List) req.getParams();
                Object input = params.get(0);
                String ip = (String) input;
                // if size == 0 => first online node in the system
                if (size == 0) {
                    return new JSONRPC2Response("1," + ip, req.getID());
                } else {
                    return new JSONRPC2Response("2," + ip, req.getID());
                }
                //return new JSONRPC2Response("string to return", req.getID());
            }
            return null;
        }
    }
}

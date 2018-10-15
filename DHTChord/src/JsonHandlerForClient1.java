

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.net.InetAddress;
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

    public static class Trial2 implements RequestHandler {

        @Override
        public String[] handledRequests() {
            return new String[]{"getClientID"};
        }

        @Override
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext messageContext) {


            if (req.getMethod().equals("getClientID")) {
//                System.out.println("this ip address is: " + (String) req.getParams());

                List params = (List) req.getParams();
                Object input = params.get(0);
                InetAddress ip = (InetAddress) input;
                return new JSONRPC2Response("IP address successfully received" + ip.toString(), req.getID());
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

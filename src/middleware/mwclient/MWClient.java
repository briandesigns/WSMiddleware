package middleware.mwclient;

import java.net.MalformedURLException;

/**
 * Created by brian on 05/10/15.
 */
public class MWClient extends MWWSClient{

    public MWClient(String serviceName, String serviceHost, int servicePort) throws MalformedURLException {
        super(serviceName, serviceHost, servicePort);
    }
}

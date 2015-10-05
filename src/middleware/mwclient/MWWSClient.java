package middleware.mwclient;

import java.net.MalformedURLException;
import java.net.URL;






public class MWWSClient {

    public middleware.mwclient.ResourceManagerImplService service;

    public middleware.mwclient.ResourceManager proxy;

    public MWWSClient(String serviceName, String serviceHost, int servicePort)
            throws MalformedURLException {

        URL wsdlLocation = new URL("http", serviceHost, servicePort,
                "/" + serviceName + "/service?wsdl");

        try {
            service = new middleware.mwclient.ResourceManagerImplService(wsdlLocation);


            proxy = service.getResourceManagerImplPort();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
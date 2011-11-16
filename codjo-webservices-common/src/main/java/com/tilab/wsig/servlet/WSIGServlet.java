/*****************************************************************
 JADE - Java Agent DEvelopment Framework is a framework to develop
 multi-agent systems in compliance with the FIPA specifications.
 Copyright (C) 2002 TILAB

 GNU Lesser General Public License

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation,
 version 2.1 of the License.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the
 Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 Boston, MA  02111-1307, USA.
 *****************************************************************/

package com.tilab.wsig.servlet;

import com.tilab.wsig.WSIGConfiguration;
import com.tilab.wsig.agent.WSIGBehaviour;
import com.tilab.wsig.soap.JadeToSoapAgif;
import com.tilab.wsig.soap.SoapToJade;
import com.tilab.wsig.store.WSIGService;
import com.tilab.wsig.store.WSIGStore;
import jade.content.AgentAction;
import jade.content.abs.AbsTerm;
import jade.content.onto.Ontology;
import jade.core.AID;
import jade.util.leap.Properties;
import jade.wrapper.ControllerException;
import jade.wrapper.gateway.JadeGateway;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import org.apache.axis.Message;
import org.apache.axis.transport.http.HTTPConstants;
import org.apache.log4j.Logger;
import org.apache.soap.rpc.SOAPContext;

public class WSIGServlet extends HttpServlet {

    private static Logger log = Logger.getLogger(WSIGServlet.class.getName());

    private WSIGStore wsigStore = new WSIGStore();
    private int executionTimeout = 0;
    private ServletContext servletContext = null;
    private String consoleUri;


    /**
     * Init wsig servlet
     */
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);

        log.info("Starting WSIG Servlet...");
        servletContext = servletConfig.getServletContext();
        String wsigPropertyPath = servletContext
              .getRealPath(WSIGConfiguration.WSIG_DEFAULT_CONFIGURATION_FILE);
        log.info("Configuration file= " + wsigPropertyPath);

        // Init configuration
        WSIGConfiguration.init(wsigPropertyPath);
        servletContext.setAttribute("WSIGConfiguration", WSIGConfiguration.getInstance());

        // Get properties
        WSIGConfiguration config = WSIGConfiguration.getInstance();
        consoleUri = config.getWsigConsoleUri();
        String gatewayClassName = config.getAgentClassName();
        String wsdlDirectory = config.getWsdlDirectory();
        String wsdlPath = servletContext.getRealPath(wsdlDirectory);
        executionTimeout = config.getWsigTimeout();

        // Create a wsig store
        wsigStore = new WSIGStore();
        servletContext.setAttribute("WSIGStore", wsigStore);

        // Create JADE profile
        Properties jadeProfile = new Properties();
        copyPropertyIfExists(config, jadeProfile, "host");
        copyPropertyIfExists(config, jadeProfile, "port");
        copyPropertyIfExists(config, jadeProfile, "local-port");
        copyPropertyIfExists(config, jadeProfile, "container-name");

        // Init Jade Gateway
        log.info("Init Jade Gateway...");
        Object[] wsigArguments = new Object[]{wsigPropertyPath, wsdlPath, wsigStore};
        jadeProfile.setProperty(jade.core.Profile.MAIN, "false");

        JadeGateway.init(gatewayClassName, wsigArguments, jadeProfile);
        log.info("Jade Gateway initialized");

        // Start WSIGAgent
        startupWSIGAgent();

        log.info("WSIG Servlet started");
    }


    private void copyPropertyIfExists(WSIGConfiguration from, Properties to, String key) {
        if (from.containsKey(key)) {
            to.setProperty(key, from.getProperty(key));
        }
    }


    /**
     * close wsig servlet
     */
    public void destroy() {

        // Close WSIGAgent
        shutdownWSIGAgent();

        super.destroy();
        log.info("WSIG Servlet destroyed");
    }


    /**
     * Manage get request
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {

        doPost(request, response);
    }


    /**
     * Manage post request
     */
    protected void doPost(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
          throws ServletException, IOException {

        // Check if the request is a WSIG agent command
        String wsigAgentCommand = httpRequest.getParameter("WSIGAgentCommand");
        if (wsigAgentCommand != null && !wsigAgentCommand.equals("")) {

            // Elaborate WSIG agent command
            elaborateWSIGAgentCommand(wsigAgentCommand, httpResponse);
            return;
        }

        // A typical Web Service convention is that a request of the form
        // http://<wsig-url>/<service-name>?WSDL (elements following the '?' are HTTP
        // request parameters), e.g. http://localhost:8080/wsig/ws/MatchService?WSDL,
        // is intended to retrieve the WSDL of the specified service.
        if (httpRequest.getParameterMap().containsKey("WSDL") ||
            httpRequest.getParameterMap().containsKey("wsdl")) {
            // Elaborate WSDL request
            elaborateWSDLRequest(httpRequest.getRequestURL().toString(), httpResponse);
            return;
        }

        // SOAP message elaboration
        log.info("WSIG SOAP request arrived, start elaboration...");

        // Extract soap messge from http
        Message soapRequest = null;
        try {
            soapRequest = extractSOAPMessage(httpRequest);
            log.debug("SOAP request:");
            log.debug(soapRequest.getSOAPPartAsString());
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        // Get wsig service & operation name
        String serviceName = null;
        String operationName = null;
        try {
            SOAPBody body = soapRequest.getSOAPPart().getEnvelope().getBody();

            serviceName = getServiceName(body);
            log.info("Request service: " + serviceName);

            operationName = getOperationName(body);
            log.info("Request operation: " + operationName);
        }
        catch (SOAPException ex) {
            log.error("Error extracting service and operation name", ex);
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }

        // Get WSIGService
        WSIGService wsigService = wsigStore.getService(serviceName);
        if (wsigService == null) {
            log.error("Service " + serviceName + " not present in wsig");
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                   "Service " + serviceName + " not present in wsig");
            return;
        }

        // Convert soap to jade
        AgentAction agentAction = null;
        try {
            SoapToJade soapToJade = new SoapToJade();
            agentAction = (AgentAction)soapToJade.convert(soapRequest, wsigService, operationName);
            log.info("Jade Action: " + agentAction.toString());
        }
        catch (Exception e) {
            log.error("Error in soap to jade conversion", e);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                   "Error in soap to jade conversion. " + e.getMessage());
            return;
        }

        // Execute operation
        AbsTerm operationAbsResult = null;
        try {
            operationAbsResult = executeOperation(agentAction, wsigService);
            if (operationAbsResult != null) {
                log.info("operationResult: " + operationAbsResult + ", type " + operationAbsResult
                      .getTypeName());
            }
            else {
                log.info("operation without result");
            }
        }
        catch (Exception e) {
            log.error("Error executing operation " + serviceName + "." + operationName);
            int errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            if (WSIGBehaviour.TIMEOUT.equals(e.getMessage())) {
                errorCode = HttpServletResponse.SC_REQUEST_TIMEOUT;
            }
            httpResponse.sendError(errorCode, "Error executing operation " + serviceName + "." + operationName
                                              + ", error: " + e.getMessage());
            return;
        }

        // Convert jade to soap
        SOAPMessage soapResponse = null;
        try {
            JadeToSoapAgif jadeToSoap = new JadeToSoapAgif();
            soapResponse = jadeToSoap.convert(operationAbsResult, wsigService, operationName);

            log.debug("SOAP response:");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            soapResponse.writeTo(baos);
            log.debug(baos.toString());
        }
        catch (Exception e) {
            log.error("Error in jade to soap conversion", e);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                   "Error in jade to soap conversion");
            return;
        }

        // Fill http response
        try {
            fillHttpResponse(soapResponse, httpResponse);
        }
        catch (Exception e) {
            log.error("Error filling http response", e);
            httpResponse
                  .sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error filling http response");
            return;
        }

        log.info("WSIGServlet doPost elaboration terminated");
    }


    /**
     * execute wsig operation
     *
     * @param agentAction
     * @param wsigService
     *
     * @return
     *
     * @throws Exception
     */
    private AbsTerm executeOperation(AgentAction agentAction, WSIGService wsigService) throws Exception {

        AID agentReceiver = wsigService.getAid();
        Ontology onto = wsigService.getOnto();
        AbsTerm absResult = null;

        WSIGBehaviour wsigBh = new WSIGBehaviour(agentReceiver, agentAction, onto, executionTimeout);

        log.debug("Execute action " + agentAction + " on " + agentReceiver.getLocalName());

        JadeGateway.execute(wsigBh, executionTimeout);
        if (wsigBh.getStatus() == WSIGBehaviour.EXECUTED_STATUS) {
            log.debug("Behaviour executed");
            absResult = wsigBh.getAbsResult();
        }
        else {
            throw new Exception(wsigBh.getError());
        }

        return absResult;
    }


    /**
     * Elaborate WSIG Agent Command
     *
     * @param wsigAgentCommand
     * @param httpResponse
     *
     * @throws ServletException
     * @throws IOException
     */
    private void elaborateWSIGAgentCommand(String wsigAgentCommand, HttpServletResponse httpResponse)
          throws ServletException, IOException {

        log.info("WSIG agent command arrived (" + wsigAgentCommand + ")");

        if (wsigAgentCommand.equalsIgnoreCase("start")) {
            // Start WSIGAgent
            startupWSIGAgent();
        }
        else if (wsigAgentCommand.equalsIgnoreCase("stop")) {
            // Stop WSIGAgent
            shutdownWSIGAgent();
        }
        else {
            log.warn("WSIG agent command not implemented");
        }

        log.info("WSIG agent command elaborated");

        // Redirect to console home page
        httpResponse.sendRedirect(consoleUri);
    }


    /**
     * Elaborate WSDL request
     *
     * @param requestURL
     * @param httpResponse
     */
    private void elaborateWSDLRequest(String requestURL, HttpServletResponse httpResponse)
          throws ServletException, IOException {

        log.info("WSDL request arrived (" + requestURL + ")");

        int pos = requestURL.lastIndexOf('/');
        if (pos == -1) {
            httpResponse
                  .sendError(HttpServletResponse.SC_NOT_FOUND, "WSDL request " + requestURL + " not correct");
            return;
        }

        String serviceName = requestURL.substring(pos + 1);
        log.info("WSDL request for service " + serviceName);

        // Get WSIGService
        WSIGService wsigService = wsigStore.getService(serviceName);
        if (wsigService == null) {
            log.error("Service " + serviceName + " not present in wsig");
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                   "Service " + serviceName + " not present in wsig");
            return;
        }

        // Redirect to wsdl
        String wsdlUrl = wsigService.getWsdl().toString();
        log.info("Redirect to " + wsdlUrl);
        httpResponse.sendRedirect(wsdlUrl);
    }


    /**
     * get operation name from soap body
     *
     * @param body
     *
     * @return
     */
    private String getOperationName(SOAPBody body) {
        SOAPElement el;
        String operationName = null;
        Iterator it = body.getChildElements();
        while (it.hasNext()) {
            el = (SOAPElement)it.next();
            operationName = el.getElementName().getLocalName();
        }

        return operationName;
    }


    /**
     * get service name from soap body
     *
     * @param body
     *
     * @return
     */
    private String getServiceName(SOAPBody body) {
        SOAPElement el;
        String serviceName = null;
        Iterator it = body.getChildElements();
        while (it.hasNext()) {
            el = (SOAPElement)it.next();
            String nsUri = el.getNamespaceURI();
            int pos = nsUri.indexOf(':');
            serviceName = nsUri.substring(pos + 1);
        }
        return serviceName;
    }


    /**
     * extract SOAP Message from http request
     *
     * @param request
     *
     * @return
     *
     * @throws Exception
     */
    private Message extractSOAPMessage(HttpServletRequest request) throws Exception {

        // Get http header
        String contentLocation = request.getHeader(HTTPConstants.HEADER_CONTENT_LOCATION);
        log.debug("contentLocation: " + contentLocation);

        String contentType = request.getHeader(HTTPConstants.HEADER_CONTENT_TYPE);
        log.debug("contentType: " + contentType);

        // Get soap message
        Message soapRequest = null;
        try {
            soapRequest = new Message(request.getInputStream(),
                                      false,
                                      contentType,
                                      contentLocation);
        }
        catch (IOException e) {
            throw new Exception("Error extracting soap message", e);
        }

        // Transfer HTTP headers to MIME headers for request message
        MimeHeaders requestMimeHeaders = soapRequest.getMimeHeaders();
        SOAPContext soapContext = new SOAPContext();
        for (Enumeration e = request.getHeaderNames(); e.hasMoreElements();) {
            String headerName = (String)e.nextElement();
            for (Enumeration f = request.getHeaders(headerName);
                 f.hasMoreElements();) {
                String headerValue = (String)f.nextElement();
                requestMimeHeaders.addHeader(headerName, headerValue);
                MimeBodyPart p = new MimeBodyPart();
                try {
                    p.addHeader(headerName, headerValue);
                    log.debug("headerName: " + headerName + ", headerValue: " + headerValue);
                    soapContext.addBodyPart(p);
                }
                catch (MessagingException ex) {
                    throw new Exception("Error building soap context", ex);
                }
            }
        }

        return soapRequest;
    }


    /**
     * Fill http response
     *
     * @param soapResponse
     * @param httpResponse
     *
     * @return
     */
    private void fillHttpResponse(SOAPMessage soapResponse, HttpServletResponse httpResponse)
          throws Exception {

        byte[] content = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        soapResponse.writeTo(baos);
        content = baos.toByteArray();

        // Set http response
        httpResponse.setHeader("Cache-Control", "no-store");
        httpResponse.setHeader("Pragma", "no-cache");
        httpResponse.setDateHeader("Expires", 0);
        httpResponse.setContentType("soap+xml; charset=utf-8");
        ServletOutputStream responseOutputStream = httpResponse.getOutputStream();
        responseOutputStream.write(content);
        responseOutputStream.flush();
        responseOutputStream.close();
    }


    /**
     * Start WSIG Agent
     */
    private void startupWSIGAgent() {
        try {
            log.info("Starting WSIG agent...");
            JadeGateway.checkJADE();
            log.info("WSIG agent started");
        }
        catch (ControllerException e) {
            log.warn("Jade platform not present...WSIG agent not started");
        }
        setWSIGStatus();
    }


    /**
     * Close WSIG Agent
     */
    private void shutdownWSIGAgent() {

        JadeGateway.shutdown();
        setWSIGStatus();
        log.info("WSIG agent closed");
    }


    /**
     * Set WSIG agent status
     *
     */
	private void setWSIGStatus() {
		servletContext.setAttribute("WSIGActive", new Boolean(JadeGateway.isGatewayActive()));
	}
}

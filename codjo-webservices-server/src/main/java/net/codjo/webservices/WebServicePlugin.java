package net.codjo.webservices;
import net.codjo.agent.Agent;
import net.codjo.agent.AgentContainer;
import net.codjo.agent.ContainerConfiguration;
import net.codjo.agent.ContainerFailureException;
import net.codjo.agent.DFService;
import net.codjo.agent.DFService.AgentDescription;
import net.codjo.agent.DFService.DFServiceException;
import net.codjo.agent.DFService.ServiceDescription;
import net.codjo.agent.JadeWrapper;
import net.codjo.agent.protocol.RequestProtocol;
import net.codjo.plugin.server.AbstractServerPlugin;
import com.tilab.wsig.WSIGConfiguration;
import jade.content.ContentManager;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.domain.FIPAAgentManagement.FIPAManagementOntology;
import java.io.File;
import java.io.InputStream;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
/**
 *
 */
public class WebServicePlugin extends AbstractServerPlugin {
    private WebServiceOperations operations = new WebServiceOperationsImpl();
    private static final String KEY_WEBSERVICES_PORT = "webservices.port";
    private int webservicesPort = 8080;
    private WebServiceConfiguration webServiceConfiguration = new DefaultWebServiceConfiguration();


    public WebServiceOperations getOperations() {
        return operations;
    }


    public WebServiceConfiguration getConfiguration() {
        return webServiceConfiguration;
    }


    @Override
    public void initContainer(ContainerConfiguration containerConfiguration) throws Exception {
        WSIGConfiguration config = WSIGConfiguration.getInstance();

        // Paramètres du profil JADE du conteneur du plugin WSIG
        config.setProperty("container-name", "WSIG-Container");
        config.setProperty("host", containerConfiguration.getHost());
        config.setProperty("port", String.valueOf(containerConfiguration.getLocalPort()));

        webservicesPort = Integer.parseInt(containerConfiguration.getParameter(KEY_WEBSERVICES_PORT));
    }


    @Override
    public void start(AgentContainer agentContainer) throws Exception {
        startJetty(webservicesPort);
    }


    private void startJetty(int port) throws Exception {
        File tempDir = File.createTempFile("wsig_webapp", "");
        tempDir.delete();
        tempDir.mkdir();

        WSIGConfiguration config = WSIGConfiguration.getInstance();
        config.setProperty(WSIGConfiguration.KEY_WSIG_AGENT_CLASS_NAME, "com.tilab.wsig.agent.WSIGAgent");
        config.setProperty(WSIGConfiguration.KEY_WSIG_TIMEOUT, "30000");

        config.setProperty(WSIGConfiguration.KEY_LOCAL_NAMESPACE_PREFIX, "impl");
        config.setProperty(WSIGConfiguration.KEY_WSDL_DIRECTORY, "wsdl");

        config.setProperty(WSIGConfiguration.KEY_UDDI_ENABLE, "false");
        config.setProperty(WSIGConfiguration.KEY_UDDI4J_LOG_ENABLED, "true");

        Server server = new Server(port);
        WebAppContext appContext = new WebAppContext(server, tempDir.getAbsolutePath(), "/");
        MyWebXmlConfiguration configuration = new MyWebXmlConfiguration();
        configuration.setWebAppContext(appContext);
        configuration.configure(getClass().getResourceAsStream("web.xml"));
        appContext.setConfigurations(new Configuration[]{configuration});

        server.start();
    }


    public static class WebServiceOperationsImpl implements WebServiceOperations {
        public static final String WSIG_FLAG = "wsig";
        private SLCodec codec = new SLCodec();


        public void declareWebService(Ontology ontology, Agent agent, String webServiceName)
              throws DFServiceException, ContainerFailureException {
            String wsdlDir = (String)WSIGConfiguration.getInstance().get("wsdl.directory");
            new File(wsdlDir).mkdirs();

            ContentManager contentManager = JadeWrapper.unwrapp(agent).getContentManager();
            contentManager.registerLanguage(codec);
            contentManager.registerOntology(FIPAManagementOntology.getInstance());
            contentManager.registerOntology(ontology);

            AgentDescription description = new AgentDescription();
            description.setAID(agent.getAID());

            ServiceDescription service = new ServiceDescription();
            description.addService(service);
            service.addLanguage(codec.getName());
            service.addProtocol(RequestProtocol.REQUEST);
            service.setType(webServiceName);
            service.addOntology(ontology.getName());

            service.addProperty(WSIG_FLAG, "true");
            service.setName(webServiceName);

            description.addService(service);

            WSIGConfiguration.getInstance().put("onto." + ontology.getName(), ontology.getClass().getName());

            DFService.register(agent, description);
        }
    }
    private static class MyWebXmlConfiguration extends WebXmlConfiguration {
        public void configure(InputStream xmlStream) throws Exception {
            initialize(_xmlParser.parse(xmlStream));
        }
    }

    private static class DefaultWebServiceConfiguration implements WebServiceConfiguration {
        public void setWSIGTimeout(String timeout) {
            WSIGConfiguration.getInstance().setProperty(WSIGConfiguration.KEY_WSIG_TIMEOUT, timeout);
        }
    }
}

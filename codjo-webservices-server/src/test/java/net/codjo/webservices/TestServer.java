package net.codjo.product;
import net.codjo.agent.AgentContainer;
import net.codjo.plugin.common.CommandLineArguments;
import net.codjo.plugin.server.AbstractServerPlugin;
import net.codjo.plugin.server.AgentServer;
import net.codjo.webservices.WebServicePlugin;
/**
 *
 */
public class TestServer {
    private TestServer() {
    }


    public static void main(String[] arguments) throws Exception {
        AgentServer server = new AgentServer();

        server.addPlugin(WebServicePlugin.class);
        server.addPlugin(MyServerPlugin.class);

        server.startAndExitIfFailure(new CommandLineArguments(arguments));
    }


    public static class MyServerPlugin extends AbstractServerPlugin {
        private WebServicePlugin webServicePlugin;


        public MyServerPlugin(WebServicePlugin webServicePlugin) {
            this.webServicePlugin = webServicePlugin;
        }


        @Override
        public void start(AgentContainer agentContainer) throws Exception {
/*
            ProductAgent productAgent = new ProductAgent();
            agentContainer.acceptNewAgent("ProductAgent", productAgent).start();

            try {
                webServicePlugin.getOperations().declareWebService(JadeProductOntology.getInstance(),
                                                                   productAgent, "product");
            }
            catch (DFServiceException e) {
                e.printStackTrace();  // Todo
            }
            catch (ContainerFailureException e) {
                e.printStackTrace();  // Todo
            }
*/
        }
    }
}


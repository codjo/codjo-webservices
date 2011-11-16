package net.codjo.webservices;
import net.codjo.agent.Agent;
import net.codjo.agent.ContainerFailureException;
import net.codjo.agent.DFService.DFServiceException;
import jade.content.onto.Ontology;
/**
 *
 */
public interface WebServiceOperations {
    void declareWebService(Ontology ontology, Agent agent, String webServiceName)
          throws DFServiceException, ContainerFailureException;
}

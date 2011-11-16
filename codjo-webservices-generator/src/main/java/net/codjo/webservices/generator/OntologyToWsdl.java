package net.codjo.webservices.generator;
import com.tilab.wsig.wsdl.WSDLConstants;
import com.tilab.wsig.wsdl.WSDLGeneratorUtils;
import jade.content.onto.Ontology;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.AggregateSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.Facet;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PrimitiveSchema;
import jade.content.schema.facets.CardinalityFacet;
import jade.content.schema.facets.TypedAggregateFacet;
import javax.wsdl.Binding;
import javax.wsdl.BindingInput;
import javax.wsdl.BindingOperation;
import javax.wsdl.BindingOutput;
import javax.wsdl.Definition;
import javax.wsdl.Input;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.Output;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.Service;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.factory.WSDLFactory;
import org.apache.log4j.Logger;
import org.eclipse.xsd.XSDComplexTypeDefinition;
import org.eclipse.xsd.XSDComponent;
import org.eclipse.xsd.XSDModelGroup;
import org.eclipse.xsd.XSDSchema;
import java.io.File;

public class OntologyToWsdl {
    final private static Logger LOG = Logger.getLogger(OntologyToWsdl.class);


    public static Definition createWsdlFromOntology(Ontology onto, File wsdlFile) throws Exception {

        String wsigServicePrefix = "";
        String sdName = onto.getName();

        // Create Schema and Definition
        WSDLFactory factory = WSDLFactory.newInstance();
        String tns = "urn:" + wsigServicePrefix + sdName;

        XSDSchema xsdSchema = WSDLGeneratorUtils.createSchema(tns);
        Definition definition = WSDLGeneratorUtils.createWSDLDefinition(factory, tns);

        //Extension Registry
        ExtensionRegistry registry = null;
        registry = factory.newPopulatedExtensionRegistry();
        definition.setExtensionRegistry(registry);

        //Port Type
        PortType portType = WSDLGeneratorUtils.createPortType(tns);
        definition.addPortType(portType);

        //Binding
        Binding binding = WSDLGeneratorUtils.createBinding(tns);
        try {
            binding.addExtensibilityElement(WSDLGeneratorUtils.createSOAPBinding(registry));
        }
        catch (WSDLException e) {
            throw new Exception("Error in SOAPBinding Handling", e);
        }
        definition.addBinding(binding);

        Port port = WSDLGeneratorUtils.createPort(tns);
        try {
            port.addExtensibilityElement(WSDLGeneratorUtils.createSOAPAddress(registry));
        }
        catch (WSDLException e) {
            throw new Exception("Error in SOAPAddress Handling", e);
        }

        //Service
        Service service = WSDLGeneratorUtils.createService(tns);
        service.addPort(port);
        definition.addService(service);

        java.util.List<String> actionNames = onto.getActionNames();

        AgentActionSchema actionSchema;
        ObjectSchema resultSchema;
        for (String actionName : actionNames) {
            try {
                LOG.debug("Elaborate action: " + actionName);
                int methodNumber = 1;

                // Loop all methods
                for (int j = 0; j < methodNumber; j++) {

                    // Prepare names
                    String outputName = actionName + WSDLConstants.SEPARATOR + WSDLConstants
                          .OUTPUT_PARAM_SUFFIX;
                    String inputName = actionName + WSDLConstants.SEPARATOR + WSDLConstants
                          .INPUT_PARAM_SUFFIX;
                    String operationName = wsigServicePrefix + actionName;
                    if (methodNumber > 1) {
                        outputName = outputName + WSDLConstants.SEPARATOR + j;
                        inputName = inputName + WSDLConstants.SEPARATOR + j;
                        operationName = operationName + WSDLConstants.SEPARATOR + j;
                    }

                    // Operation
                    Operation operation = WSDLGeneratorUtils.createOperation(operationName);
                    portType.addOperation(operation);

                    // Output Params
                    Message messageOut = WSDLGeneratorUtils.createMessage(tns, outputName);
                    Output output = WSDLGeneratorUtils.createOutput(outputName);
                    output.setMessage(messageOut);
                    operation.setOutput(output);
                    definition.addMessage(messageOut);

                    BindingOperation operationB = WSDLGeneratorUtils
                          .createBindingOperation(registry, operationName);
                    binding.addBindingOperation(operationB);

                    BindingInput inputB = WSDLGeneratorUtils.createBindingInput(registry, tns, inputName);
                    operationB.setBindingInput(inputB);

                    BindingOutput outputB = WSDLGeneratorUtils.createBindingOutput(registry, tns, outputName);
                    operationB.setBindingOutput(outputB);

                    actionSchema = (AgentActionSchema)onto.getSchema(actionName);
                    resultSchema = actionSchema.getResultSchema();
                    if (resultSchema != null) {
                        String resultType = convertObjectSchemaIntoXsdType(tns, true, actionSchema,
                                                                           resultSchema, xsdSchema,
                                                                           resultSchema.getTypeName(),
                                                                           null, -1, -1);
                        Part partR = WSDLGeneratorUtils
                              .createPart(WSDLGeneratorUtils.getResultName(operationName), resultType, tns);
                        messageOut.addPart(partR);
                    }

                    // Input Parameters
                    Message messageIn = WSDLGeneratorUtils.createMessageIn(tns, inputName);
                    definition.addMessage(messageIn);
                    Input input = WSDLGeneratorUtils.createInput(messageIn, inputName);
                    operation.setInput(input);

                    String[] slotNames = actionSchema.getNames();
                    for (String slotName : slotNames) {
                        ObjectSchema slotSchema = actionSchema.getSchema(slotName);
                        String slotType = convertObjectSchemaIntoXsdType(tns, false, actionSchema,
                                                                         slotSchema, xsdSchema,
                                                                         slotName, null, -1, -1);
                        Part part = WSDLGeneratorUtils.createPart(slotName, slotType, tns);
                        messageIn.addPart(part);
                    }
                }
            }
            catch (Exception e) {
                throw new Exception("Error in Agent Action Handling", e);
            }
        }

        xsdSchema.setTargetNamespace(tns);

        try {
            definition.setTypes(WSDLGeneratorUtils.createTypes(registry, xsdSchema.getElement()));
        }
        catch (WSDLException e) {
            throw new Exception("Error adding type to definition", e);
        }

        try {
            LOG.info("Write file: " + wsdlFile);
            WSDLGeneratorUtils.writeWSDL(factory, definition, wsdlFile.getAbsolutePath());
        }
        catch (Exception e) {
            LOG.error("Error in WSDL file writing", e);
        }

        return definition;
    }


    private static String convertObjectSchemaIntoXsdType(String tns,
                                                         boolean firstlevelResult,
                                                         ConceptSchema containerSchema,
                                                         ObjectSchema objSchema,
                                                         XSDSchema xsdSchema,
                                                         String slotName,
                                                         XSDComponent parentComponent,
                                                         int cardMin,
                                                         int cardMax) throws Exception {

        LOG.debug("Elaborate slot: " + slotName);

        String slotType = null;
        if (objSchema instanceof PrimitiveSchema) {
            slotType = WSDLConstants.jade2xsd.get(objSchema.getTypeName());
            if (parentComponent != null) {
                WSDLGeneratorUtils.addElementToSequence(true, tns, xsdSchema, slotName, slotType,
                                                        (XSDModelGroup)parentComponent, cardMin, cardMax);
            }
        }
        else if (objSchema instanceof ConceptSchema) {
            slotType = objSchema.getTypeName();
            if (parentComponent != null) {
                WSDLGeneratorUtils.addElementToSequence(false, tns, xsdSchema, slotName, slotType,
                                                        (XSDModelGroup)parentComponent, cardMin, cardMax);
            }
            if (WSDLGeneratorUtils.getTypeDefinition(xsdSchema, xsdSchema.getTargetNamespace(), slotType)
                == null) {
                XSDComplexTypeDefinition complexType = WSDLGeneratorUtils
                      .addComplexTypeToSchema(tns, xsdSchema, slotType);
                XSDModelGroup sequence = WSDLGeneratorUtils.addSequenceToComplexType(complexType);
                for (String conceptSlotName : objSchema.getNames()) {
                    ObjectSchema slotSchema = objSchema.getSchema(conceptSlotName);
                    convertObjectSchemaIntoXsdType(tns, false, (ConceptSchema)objSchema, slotSchema,
                                                   xsdSchema, conceptSlotName, sequence, -1, -1);
                }
            }
        }
        else if (objSchema instanceof AggregateSchema) {

            Facet[] facets;
            if (firstlevelResult) {
                // output first level
                facets = ((AgentActionSchema)containerSchema).getResultFacets();
            }
            else {
                // input or output complex type
                facets = containerSchema.getFacets(slotName);
            }

            ObjectSchema aggregateSchema = null;
            for (Facet facet : facets) {
                if (facet instanceof CardinalityFacet) {
                    cardMax = ((CardinalityFacet)facet).getCardMax();
                    cardMin = ((CardinalityFacet)facet).getCardMin();
                }
                else if (facet instanceof TypedAggregateFacet) {
                    aggregateSchema = ((TypedAggregateFacet)facet).getType();
                }
                else {
                    LOG.warn("Facet (" + facet.toString() + ") is unknown");
                }
            }

            slotType = aggregateSchema.getTypeName();
            if (aggregateSchema instanceof PrimitiveSchema) {
                slotType = WSDLConstants.jade2xsd.get(slotType);
            }
            String itemName = slotType;
            String aggregateType = objSchema.getTypeName();
            slotType = WSDLGeneratorUtils.getAggregateType(slotType, aggregateType);

            if (WSDLGeneratorUtils.getTypeDefinition(xsdSchema, xsdSchema.getTargetNamespace(), slotType)
                == null) {
                XSDComplexTypeDefinition complexType = WSDLGeneratorUtils
                      .addComplexTypeToSchema(tns, xsdSchema, slotType);
                XSDModelGroup sequence = WSDLGeneratorUtils.addSequenceToComplexType(complexType);
                if (parentComponent != null) {
                    WSDLGeneratorUtils.addElementToSequence(false, tns, xsdSchema, slotName, slotType,
                                                            (XSDModelGroup)parentComponent);
                }
                convertObjectSchemaIntoXsdType(tns, false, containerSchema, aggregateSchema, xsdSchema,
                                               itemName, sequence, cardMin, cardMax);
            }
        }
        return slotType;
    }
}

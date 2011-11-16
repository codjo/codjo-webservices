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

package com.tilab.wsig.wsdl;

import com.tilab.wsig.store.ActionBuilder;
import com.tilab.wsig.store.MapperBasedActionBuilder;
import com.tilab.wsig.store.OntologyBasedActionBuilder;
import com.tilab.wsig.store.OperationName;
import com.tilab.wsig.store.SuppressOperation;
import com.tilab.wsig.store.WSIGService;
import jade.content.ContentManager;
import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.AggregateSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.Facet;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PrimitiveSchema;
import jade.content.schema.facets.CardinalityFacet;
import jade.content.schema.facets.TypedAggregateFacet;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
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

public class SDToWSDL {

    private static Logger log = Logger.getLogger(SDToWSDL.class.getName());


    /**
     * createDefinitionFromOntologies
     *
     * @param agent
     * @param sd
     * @param wsigService
     *
     * @return
     *
     * @throws Exception
     */
    public static Definition createWSDLFromSD(Agent agent, ServiceDescription sd, WSIGService wsigService)
          throws Exception {

        // Create mapper object
        Object mapperObject = null;
        Class mapperClass = wsigService.getMapperClass();
        if (mapperClass != null) {
            try {
                mapperObject = mapperClass.newInstance();
            }
            catch (Exception e) {
                log.error("Mapper class " + mapperClass.getName() + " not found", e);
                throw e;
            }
        }

        // Create Schema and Definition
        WSDLFactory factory = WSDLFactory.newInstance();
        String tns = "urn:" + wsigService.getServicePrefix() + sd.getName();

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

        // Managing Ontologies
        Iterator ontologies = sd.getAllOntologies();
        ContentManager cntManager = agent.getContentManager();

        // FIX-ME: Manage only FIRST ontology for ServiceDescription
        if (ontologies.hasNext()) {
            String ontoName = (String)ontologies.next();
            Ontology onto = cntManager.lookupOntology(ontoName);
            java.util.List actionNames = onto.getActionNames();

            AgentActionSchema actionSchema = null;
            ObjectSchema resultSchema = null;
            for (int i = 0; i < actionNames.size(); i++) {
                try {
                    String actionName = (String)actionNames.get(i);
                    log.debug("Elaborate action: " + actionName);
                    int methodNumber = 1;

                    // Check if action is suppressed
                    if (isActionSuppressed(mapperClass, actionName)) {
                        log.debug("Action " + actionName + " suppressed");
                        continue;
                    }

                    // Get action methods of mapper
                    Vector<Method> methodsForAction = getMethodsForAction(mapperClass, actionName);
                    if (methodsForAction.size() > 0) {
                        methodNumber = methodsForAction.size();
                    }

                    // Loop all methods
                    for (int j = 0; j < methodNumber; j++) {

                        // Prepare names
                        String outputName = actionName + WSDLConstants.SEPARATOR + WSDLConstants
                              .OUTPUT_PARAM_SUFFIX;
                        String inputName = actionName + WSDLConstants.SEPARATOR + WSDLConstants
                              .INPUT_PARAM_SUFFIX;
                        String operationName = wsigService.getServicePrefix() + actionName;
                        if (methodNumber > 1) {
                            outputName = outputName + WSDLConstants.SEPARATOR + j;
                            inputName = inputName + WSDLConstants.SEPARATOR + j;
                            operationName = operationName + WSDLConstants.SEPARATOR + j;
                        }

                        // Create appropriate ActionBuilder
                        ActionBuilder actionBuilder = null;
                        if (methodsForAction.size() > 0) {
                            // Mapper
                            Method method = methodsForAction.get(j);

                            // Check is the operation has a specific name
                            OperationName annotationOperationName = method.getAnnotation(OperationName.class);
                            if (annotationOperationName != null) {

                                // Set specific operation name
                                operationName = annotationOperationName.name();
                            }

                            actionBuilder = new MapperBasedActionBuilder(mapperObject, method, onto,
                                                                         actionName);
                        }
                        else {
                            // Ontology/reflection
                            actionBuilder = new OntologyBasedActionBuilder(onto, actionName);
                        }

                        // Operation
                        Operation operation = WSDLGeneratorUtils.createOperation(operationName);
                        portType.addOperation(operation);

                        // Add ActionBuilder
                        wsigService.addActionBuilder(operationName, actionBuilder);

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

                        BindingOutput outputB = WSDLGeneratorUtils
                              .createBindingOutput(registry, tns, outputName);
                        operationB.setBindingOutput(outputB);

                        actionSchema = (AgentActionSchema)onto.getSchema(actionName);
                        resultSchema = actionSchema.getResultSchema();
                        if (resultSchema != null) {
                            String resultType = convertObjectSchemaIntoXsdType(tns, true, actionSchema,
                                                                               resultSchema, xsdSchema,
                                                                               resultSchema.getTypeName(),
                                                                               null, -1, -1);
                            Part partR = WSDLGeneratorUtils.createPart(
                                  WSDLGeneratorUtils.getResultName(operationName), resultType, tns);
                            messageOut.addPart(partR);
                        }

                        // Input Parameters
                        Message messageIn = WSDLGeneratorUtils.createMessageIn(tns, inputName);
                        definition.addMessage(messageIn);
                        Input input = WSDLGeneratorUtils.createInput(messageIn, inputName);
                        operation.setInput(input);

                        if (methodsForAction.size() > 0) {
                            Method method = methodsForAction.get(j);
                            Class[] parameterTypes = method.getParameterTypes();
                            String[] parameterNames = WSDLGeneratorUtils.getParameterNames(method);

                            for (int k = 0; k < parameterTypes.length; k++) {
                                Class parClass = parameterTypes[k];
                                String slotName;
                                if (parameterNames != null) {
                                    slotName = parameterNames[k];
                                }
                                else {
                                    slotName = parClass.getSimpleName() + WSDLConstants.SEPARATOR + k;
                                }

                                String slotType = convertObjectSchemaIntoXsdType(tns, onto, actionSchema,
                                                                                 parClass, xsdSchema,
                                                                                 slotName, null);
                                Part part = WSDLGeneratorUtils.createPart(slotName, slotType, tns);
                                messageIn.addPart(part);
                            }
                        }
                        else {
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
                String filename = WSDLGeneratorUtils
                      .getWSDLFilename(wsigService.getServicePrefix() + sd.getName());
                log.info("Write file: " + filename);

                WSDLGeneratorUtils.writeWSDL(factory, definition, filename);
            }
            catch (Exception e) {
                log.error("Error in WSDL file writing", e);
            }
        }

        return definition;
    }


    /**
     * getMethodsForAction
     *
     * @param mapperClass
     * @param actionName
     *
     * @return
     */
    private static Vector<Method> getMethodsForAction(Class mapperClass, String actionName) {

        Vector<Method> methodsAction = new Vector<Method>();

        if (mapperClass != null) {
            Method[] methods = mapperClass.getDeclaredMethods();

            Method method = null;
            String methodNameToCheck = WSDLConstants.MAPPER_METHOD_PREFIX + actionName;
            for (int j = 0; j < methods.length; j++) {
                method = methods[j];
                if (method.getName().equalsIgnoreCase(methodNameToCheck)) {
                    methodsAction.add(method);
                }
            }
        }
        return methodsAction;
    }


    /**
     * isActionSuppressed
     *
     * @param mapperClass
     * @param actionName
     *
     * @return
     */
    private static boolean isActionSuppressed(Class mapperClass, String actionName) {

        boolean isSuppressed = false;
        if (mapperClass != null) {
            Method[] methods = mapperClass.getDeclaredMethods();

            Method method = null;
            String methodNameToCheck = WSDLConstants.MAPPER_METHOD_PREFIX + actionName;
            for (int j = 0; j < methods.length; j++) {
                method = methods[j];

                SuppressOperation annotationSuppressOperation = method.getAnnotation(SuppressOperation.class);
                if ((method.getName().equalsIgnoreCase(methodNameToCheck)
                     && annotationSuppressOperation != null)) {
                    isSuppressed = true;
                    break;
                }
            }
        }
        return isSuppressed;
    }


    /**
     * convertObjectSchemaIntoXsdType
     *
     * @param onto
     * @param containerSchema
     * @param paramType
     * @param xsdSchema
     * @param slotName
     * @param parentComponent
     *
     * @return
     *
     * @throws Exception
     */
    private static String convertObjectSchemaIntoXsdType(String tns,
                                                         Ontology onto,
                                                         ConceptSchema containerSchema,
                                                         Class paramType,
                                                         XSDSchema xsdSchema,
                                                         String slotName,
                                                         XSDComponent parentComponent) throws Exception {

        String slotType = null;
        if (paramType.isPrimitive() || WSDLConstants.java2xsd.get(paramType) != null) {
            //primitive dataType XSD
            if (paramType.isPrimitive()) {
                slotType = paramType.getName();
            }
            else {
                slotType = (String)WSDLConstants.java2xsd.get(paramType);
            }
            if (parentComponent != null) {
                WSDLGeneratorUtils.addElementToSequence(true, tns, xsdSchema, slotName, slotType,
                                                        (XSDModelGroup)parentComponent, 0, -1);
            }
        }
        else if (paramType.isArray()) {
            Class aggrType = paramType.getComponentType();
            slotName = aggrType.getSimpleName().toLowerCase();
            slotType = WSDLGeneratorUtils.getAggregateType(slotName, BasicOntology.SEQUENCE);
            if (WSDLGeneratorUtils.getTypeDefinition(xsdSchema, xsdSchema.getTargetNamespace(), slotType)
                == null) {
                XSDComplexTypeDefinition complexType = WSDLGeneratorUtils
                      .addComplexTypeToSchema(tns, xsdSchema, slotType);
                XSDModelGroup sequence = WSDLGeneratorUtils.addSequenceToComplexType(complexType);
                convertObjectSchemaIntoXsdType(tns, onto, containerSchema, aggrType, xsdSchema, slotName,
                                               sequence);
            }
        }
        else if (Collection.class.isAssignableFrom(paramType) ||
                 jade.util.leap.Collection.class.isAssignableFrom(paramType)) {
            //TODO Collection Handling
            // Manage collection element type with a specif annotation associated to mapper method
            // es. @CollectionElementType (parameter=xxx, type=java.util.String)
            throw new Exception("Collection NOT supported");
        }
        else {
            //built-in type
            String[] names = containerSchema.getNames();
            String slot = null;
            for (String name : names) {
                if (paramType.equals(onto.getClassForElement(name))) {
                    slot = name;
                    break;
                }
            }
            if (slot == null) {
                throw new Exception(
                      "Concept " + paramType.getSimpleName() + " doesn't exist in " + onto.getName());
            }
            ObjectSchema slotSchema = containerSchema.getSchema(slot);
            slotType = convertObjectSchemaIntoXsdType(tns, false, containerSchema, slotSchema, xsdSchema,
                                                      slot, parentComponent, -1, -1);
        }
        return slotType;
    }


    /**
     * convertObjectSchemaIntoXsdType
     *
     * @param containerSchema
     * @param objSchema
     * @param xsdSchema
     * @param slotName
     * @param parentComponent
     * @param cardMin
     * @param cardMax
     *
     * @return
     *
     * @throws Exception
     */
    private static String convertObjectSchemaIntoXsdType(String tns,
                                                         boolean firstlevelResult,
                                                         ConceptSchema containerSchema,
                                                         ObjectSchema objSchema,
                                                         XSDSchema xsdSchema,
                                                         String slotName,
                                                         XSDComponent parentComponent,
                                                         int cardMin,
                                                         int cardMax) throws Exception {

        log.debug("Elaborate slot: " + slotName);

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
                    log.warn("Facet (" + facet.toString() + ") is unknown");
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
                convertObjectSchemaIntoXsdType(tns, false, containerSchema, aggregateSchema, xsdSchema, itemName, sequence, cardMin, cardMax);
			}

		}
		return slotType;
	}

	
}
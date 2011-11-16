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

import jade.content.onto.BasicOntology;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.Collection;

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
import javax.wsdl.Types;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap.SOAPBody;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLWriter;
import javax.xml.namespace.QName;

import org.apache.axis.utils.bytecode.ParamReader;
import org.eclipse.xsd.XSDAnnotation;
import org.eclipse.xsd.XSDComplexTypeDefinition;
import org.eclipse.xsd.XSDCompositor;
import org.eclipse.xsd.XSDElementDeclaration;
import org.eclipse.xsd.XSDFactory;
import org.eclipse.xsd.XSDModelGroup;
import org.eclipse.xsd.XSDParticle;
import org.eclipse.xsd.XSDSchema;
import org.eclipse.xsd.XSDTypeDefinition;
import org.eclipse.xsd.impl.XSDComplexTypeDefinitionImpl;
import org.w3c.dom.Element;

import com.ibm.wsdl.BindingImpl;
import com.ibm.wsdl.BindingInputImpl;
import com.ibm.wsdl.BindingOperationImpl;
import com.ibm.wsdl.BindingOutputImpl;
import com.ibm.wsdl.InputImpl;
import com.ibm.wsdl.MessageImpl;
import com.ibm.wsdl.OperationImpl;
import com.ibm.wsdl.OutputImpl;
import com.ibm.wsdl.PartImpl;
import com.ibm.wsdl.PortImpl;
import com.ibm.wsdl.PortTypeImpl;
import com.ibm.wsdl.ServiceImpl;
import com.ibm.wsdl.TypesImpl;

import com.tilab.wsig.WSIGConfiguration;

public class WSDLGeneratorUtils {
	
	public static XSDSchema createSchema(String tns) {

		XSDFactory xsdFactory = XSDFactory.eINSTANCE;
		XSDSchema xsd = xsdFactory.createXSDSchema();
		xsd.setSchemaForSchemaQNamePrefix("xsd");
		xsd.setTargetNamespace(tns);

		Map qNamePrefixToNamespaceMap = xsd.getQNamePrefixToNamespaceMap();
		qNamePrefixToNamespaceMap.put("xsd", xsd.getTargetNamespace());
		qNamePrefixToNamespaceMap.put(xsd.getSchemaForSchemaQNamePrefix(), WSDLConstants.XSD);
		qNamePrefixToNamespaceMap.put(WSIGConfiguration.getInstance().getLocalNamespacePrefix(), tns);
		
		XSDAnnotation xsdAnnotation = xsdFactory.createXSDAnnotation();
		xsd.getContents().add(xsdAnnotation);

		xsdAnnotation.createUserInformation(null);

		return xsd;
	}

	public static XSDTypeDefinition getTypeDefinition(XSDSchema schema, String targetNameSpace, String localName) {
		XSDTypeDefinition result = null;
        for (XSDTypeDefinition type : (Collection<XSDTypeDefinition>)schema.getTypeDefinitions()) {
        // for (XSDTypeDefinition type : schema.getTypeDefinitions()) {
        // copied back from old version
            if (type.hasNameAndTargetNamespace(localName, targetNameSpace)) {
				result = type;
				break;
			}
		}
		return result;
	}

	public static XSDComplexTypeDefinition addComplexTypeToSchema(String tns, XSDSchema schema, String complexTypeName) {
		XSDComplexTypeDefinitionImpl simpleRecursiveComplexTypeDefinition = (XSDComplexTypeDefinitionImpl) XSDFactory.eINSTANCE.createXSDComplexTypeDefinition();
		simpleRecursiveComplexTypeDefinition.setName(complexTypeName);
		simpleRecursiveComplexTypeDefinition.setTargetNamespace(tns);

		schema.getContents().add(simpleRecursiveComplexTypeDefinition);

		return simpleRecursiveComplexTypeDefinition;

	}

	public static XSDModelGroup addSequenceToComplexType(XSDComplexTypeDefinition complexTypeDefinition) {

		XSDParticle contentParticle = XSDFactory.eINSTANCE.createXSDParticle();
		XSDModelGroup contentSequence = XSDFactory.eINSTANCE.createXSDModelGroup();
		contentSequence.setCompositor(XSDCompositor.SEQUENCE_LITERAL);
		contentParticle.setContent(contentSequence);
		complexTypeDefinition.setContent(contentParticle);
		
		return contentSequence;
	}

	public static XSDParticle addElementToSequence(boolean primitive, String tns, XSDSchema schema, String elementName, String elementType, XSDModelGroup sequence) {
		XSDElementDeclaration elementAdded = XSDFactory.eINSTANCE.createXSDElementDeclaration();
		elementAdded.setName(elementName);
		
		XSDTypeDefinition xsdTypeDefinition;
		if (primitive) {
			xsdTypeDefinition = schema.resolveSimpleTypeDefinition(WSDLConstants.XSD, elementType);
		} else {
			xsdTypeDefinition = getTypeDefinition(schema, tns, elementType);
			
			if (xsdTypeDefinition == null) {
				// Not already present in definition type
				// Create a temp definition in my tns
				xsdTypeDefinition = (XSDComplexTypeDefinitionImpl) XSDFactory.eINSTANCE.createXSDComplexTypeDefinition();
				xsdTypeDefinition.setName(elementType);
				xsdTypeDefinition.setTargetNamespace(tns);
			}
		}
		elementAdded.setTypeDefinition(xsdTypeDefinition);

		XSDParticle elementParticle = XSDFactory.eINSTANCE.createXSDParticle();
		elementParticle.setContent(elementAdded);
		sequence.getContents().add(elementParticle);
		
		return elementParticle;
	}

	public static XSDParticle addElementToSequence(boolean primitive, String tns, XSDSchema schema, String elementName, String elementType, XSDModelGroup sequence, int minOcc, int maxOcc) {
		XSDParticle elementParticle = addElementToSequence(primitive, tns, schema, elementName, elementType, sequence);
		if (minOcc != -1) {
			elementParticle.setMaxOccurs(maxOcc);
			elementParticle.setMinOccurs(minOcc);
		}
		return elementParticle;
	}
	
	public static Types createTypes(ExtensionRegistry registry, Element element) throws WSDLException {
		
		Types types = new TypesImpl();

		Schema schema = (Schema) registry.createExtension(
							Types.class, new QName(WSDLConstants.XSD,
							WSDLConstants.SCHEMA));
		schema.setElement(element);
		types.addExtensibilityElement(schema);
		
		return types;
	}
	
	public static Definition createWSDLDefinition(WSDLFactory factory, String tns) {
		Definition definition = factory.newDefinition();
		definition.setQName(new QName(tns, tns.substring(tns.indexOf(":")+1)));
		definition.setTargetNamespace(tns);
		definition.addNamespace(WSIGConfiguration.getInstance().getLocalNamespacePrefix(), tns);
		definition.addNamespace("xsd", WSDLConstants.XSD);
		definition.addNamespace("xsi", WSDLConstants.XSI);
		definition.addNamespace("wsdlsoap", WSDLConstants.WSDL_SOAP);
		
		return definition;
	}
	
	public static PortType createPortType(String tns) {
		PortType portType = new PortTypeImpl();
		portType.setUndefined(false);
		portType.setQName(new QName(tns.substring(tns.indexOf(":")+1)));
		return portType;
	}
	
	public static Binding createBinding(String tns) {
		Binding binding = new BindingImpl();
		PortType portTypeB = new PortTypeImpl();
		portTypeB.setUndefined(false);
		portTypeB.setQName(new QName(tns, tns.substring(tns.indexOf(":")+1)));
		binding.setPortType(portTypeB);
		binding.setUndefined(false);
		binding.setQName(new QName(tns.substring(4)+WSDLConstants.PUBLISH_BINDING_SUFFIX));
		return binding;
	}

	public static SOAPBinding createSOAPBinding(ExtensionRegistry registry) throws WSDLException {
		SOAPBinding soapBinding = (SOAPBinding) registry.createExtension(
				Binding.class, new QName(WSDLConstants.WSDL_SOAP, "binding"));
		soapBinding.setStyle(WSDLConstants.SOAP_STYLE);
		soapBinding.setTransportURI(WSDLConstants.TRANSPORT_URI);
		return soapBinding;
	}
	
	public static Port createPort(String tns) {
		Binding bindingP = new BindingImpl();
		bindingP.setQName(new QName(tns, tns.substring(4)+WSDLConstants.PUBLISH_BINDING_SUFFIX));
		bindingP.setUndefined(false);
		Port port = new PortImpl();
		port.setName(WSDLConstants.PUBLISH);
		port.setBinding(bindingP);
		return port;
	}
	
	public static SOAPAddress createSOAPAddress(ExtensionRegistry registry) throws WSDLException {
		SOAPAddress soapAddress = null;
		soapAddress = (SOAPAddress)registry.createExtension(Port.class,new QName(WSDLConstants.WSDL_SOAP,"address"));		
		soapAddress.setLocationURI(WSIGConfiguration.getInstance().getWsigUri());
		return soapAddress;
	}

	public static Service createService(String tns) {
		Service service = new ServiceImpl();
		service.setQName(new QName(tns.substring(tns.indexOf(":")+1)));
		return service;
	}

	public static Operation createOperation(String actionName) {
		Operation operation = new OperationImpl();
		operation.setName(actionName);
		operation.setUndefined(false);
		return operation;
	}

	public static Message createMessage(String tns, String name) {
		Message messageOut = new MessageImpl();
		messageOut.setQName(new QName(tns, name));
		messageOut.setUndefined(false);
		return messageOut;
	}

	public static Output createOutput(String name) {
		Output output = new OutputImpl();
		output.setName(name);
		return output;
	}
	
	public static BindingOperation createBindingOperation(ExtensionRegistry registry, String actionName) throws WSDLException {

		BindingOperation operationB = new BindingOperationImpl();
		operationB.setName(actionName);
		SOAPOperation soapOperation = (SOAPOperation) registry
				.createExtension(BindingOperation.class,
						new QName(WSDLConstants.WSDL_SOAP, WSDLConstants.OPERATION));
		soapOperation.setSoapActionURI(WSDLConstants.SOAP_ACTION_URI);
		operationB.addExtensibilityElement(soapOperation);
		return operationB;
	}
	
	public static BindingInput createBindingInput(ExtensionRegistry registry, String tns, String name) throws Exception{
		
		BindingInput inputB = new BindingInputImpl();
		inputB.setName(name);
		SOAPBody soapBodyInput;
		try {
			soapBodyInput = (SOAPBody) registry.createExtension(BindingInput.class,
							new QName(WSDLConstants.WSDL_SOAP, WSDLConstants.BODY));
		} catch (WSDLException e) {
			throw new Exception("Error in SOAPBodyInput Handling", e);
		}
		soapBodyInput.setUse(WSDLConstants.ENCODED);
		ArrayList encodingStylesInput = new ArrayList();
		encodingStylesInput.add(WSDLConstants.ENCODING_STYLE);
		soapBodyInput.setEncodingStyles(encodingStylesInput);
		soapBodyInput.setNamespaceURI(tns);
		inputB.addExtensibilityElement(soapBodyInput);
		return inputB;
	}
	
	public static BindingOutput createBindingOutput(ExtensionRegistry registry,
			String tns, String name) throws Exception  {

		BindingOutput outputB = new BindingOutputImpl();
		outputB.setName(name);
		SOAPBody soapBodyOutput;
		try {
			soapBodyOutput = (SOAPBody) registry.createExtension(
					BindingOutput.class, new QName(WSDLConstants.WSDL_SOAP, WSDLConstants.BODY));
		} catch (WSDLException e) {
			throw new Exception("Error in SOAPBodyOutput Handling", e);
		}
		soapBodyOutput.setUse(WSDLConstants.ENCODED);
		ArrayList encodingStylesOutput = new ArrayList();
		encodingStylesOutput.add(WSDLConstants.ENCODING_STYLE);
		soapBodyOutput.setEncodingStyles(encodingStylesOutput);
		soapBodyOutput.setNamespaceURI(tns);
		outputB.addExtensibilityElement(soapBodyOutput);
		return outputB;
	}

	public static Message createMessageIn(String tns, String name) {
		Message messageIn = new MessageImpl();
		messageIn.setQName(new QName(tns, name));
		messageIn.setUndefined(false);
		return messageIn;
	}

	public static Input createInput(Message messageIn, String name) {
		Input input = new InputImpl();
		input.setMessage(messageIn);
		input.setName(name);
		return input;
	}

	public static Part createPart(String name, String className, String tns) {
		Part part = new PartImpl();
		
		String namespaceURI;
		if (WSDLConstants.jade2xsd.values().contains(className)) {
			namespaceURI = WSDLConstants.XSD;
		} else {
			namespaceURI = tns;
		}
		
		QName qNameType = new QName(namespaceURI, className);
		part.setTypeName(qNameType);
		part.setName(name);
		return part;
	}
	
	public static void writeWSDL(WSDLFactory factory, Definition definition, String fileName) throws FileNotFoundException, WSDLException {
		WSDLWriter writer = factory.newWSDLWriter();
		File file = new File(fileName);
		PrintWriter output = new PrintWriter(file);
		writer.writeWSDL(definition, output);
	}
	
	public static void deleteWSDL(String fileName) {
		
		File file = new File(fileName);
		if (!file.exists()) {
			return;
		}

		file.delete();
	}
	
    public static String[] getParameterNames(Method method) {
        // Don't worry about it if there are no params.
        int numParams = method.getParameterTypes().length;
        if (numParams == 0)
            return null;

        // Get declaring class
        Class c = method.getDeclaringClass();
        
        // Don't worry about it if the class is a Java dynamic proxy 
        if(Proxy.isProxyClass(c)) {
            return null;
        }
        
        try {
            // Get a parameter reader
            ParamReader pr = new ParamReader(c);

            // Get the paramter names
            return pr.getParameterNames(method);
            
        } catch (IOException e) {
        	return null;
        }
    }
	
	public static String getWSDLFilename(String serviceName) {
		return WSIGConfiguration.getInstance().getWsdlDirectory() + File.separator + serviceName + ".wsdl";
	}

	public static String getResultName(String operationName) { 
		return WSDLConstants.RESULT_PREFIX+WSDLConstants.SEPARATOR+operationName+WSDLConstants.SEPARATOR+WSDLConstants.RESULT_SUFFIX;
	}

	public static String getAggregateToken() {
		return WSDLConstants.SEPARATOR+WSDLConstants.AGGREGATE+WSDLConstants.SEPARATOR;
	}
	
	public static String getAggregateType(String elementType, String aggregateType) { 
		return elementType+getAggregateToken()+aggregateType;
	}
}

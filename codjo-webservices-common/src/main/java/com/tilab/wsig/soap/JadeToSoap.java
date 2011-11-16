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

package com.tilab.wsig.soap;

import jade.content.abs.AbsAggregate;
import jade.content.abs.AbsPrimitive;
import jade.content.abs.AbsTerm;
import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.AggregateSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.Facet;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PrimitiveSchema;
import jade.content.schema.facets.TypedAggregateFacet;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.log4j.Logger;

import com.tilab.wsig.store.ActionBuilder;
import com.tilab.wsig.store.WSIGService;
import com.tilab.wsig.wsdl.WSDLConstants;
import com.tilab.wsig.wsdl.WSDLGeneratorUtils;

public class JadeToSoap {

	private static Logger log = Logger.getLogger(JadeToSoap.class.getName());
	
	private static final String PREFIX_Q0 = "q0";
	private static final String PREFIX_Q1 = "q1";
	
	private Ontology onto;
	private SOAPEnvelope envelope;
	private String tns;
	
	public JadeToSoap() {
	}

	/**
	 * convert
	 * @param resultObject
	 * @param wsigService
	 * @param operationName
	 * @return
	 * @throws Exception
	 */
	public SOAPMessage convert(AbsTerm resultAbsObject, WSIGService wsigService, String operationName) throws Exception {
	
		// Get tns
		tns = "urn:" + wsigService.getServicePrefix() + wsigService.getServiceName();
			
		// Get ontology
		onto = wsigService.getOnto();
		
		// Create soap message
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapResponse = messageFactory.createMessage();
        
        // Create objects for the message parts            
        SOAPPart soapPart = soapResponse.getSOAPPart();
        envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration(PREFIX_Q0, WSDLConstants.XSD);
        envelope.addNamespaceDeclaration(PREFIX_Q1, tns);
        
        SOAPBody body = envelope.getBody();

        String responseElementName = operationName + "Response";
        SOAPElement responseElement = addSoapElement(body, responseElementName, null, null);

        // Get action builder
        log.debug("Operation name: "+operationName);
        ActionBuilder actionBuilder = wsigService.getActionBuilder(operationName);
        if (actionBuilder == null) {
			throw new Exception("Action builder not found for operation "+operationName+" in WSIG");
        }
        
        // Get action schema
        AgentActionSchema actionSchema;
        try {
	        String ontoActionName = actionBuilder.getOntoActionName();
	        actionSchema = (AgentActionSchema)onto.getSchema(ontoActionName);
        } catch (OntologyException oe) {
        	throw new Exception("Operation schema not found for operation "+operationName+" in "+onto.getName()+" ontology", oe);
        }

		// Get result schema
        ObjectSchema resultSchema = actionSchema.getResultSchema();
        if (resultSchema != null) {
        	log.debug("Ontology result type: "+resultSchema.getTypeName());

        	// Create soap message
            convertObjectToSoapElement(actionSchema, resultSchema, resultAbsObject, WSDLGeneratorUtils.getResultName(operationName), responseElement);
        } else {
        	log.debug("Ontology with no result type");
        }

        // Save all modifies of soap message
        soapResponse.saveChanges();
        
		return soapResponse;
	}

	/**
	 * convertObjectToSoapElement
	 * @param envelope
	 * @param resultSchema
	 * @param resultObj
	 * @param elementName
	 * @param rootSoapElement
	 * @return
	 * @throws Exception
	 */
	private SOAPElement convertObjectToSoapElement(ObjectSchema containerSchema, ObjectSchema resultSchema, AbsTerm resultAbsObj, String elementName, SOAPElement rootSoapElement) throws Exception {
		
		SOAPElement soapElement = null;
		String soapType = null;
		ObjectSchema newContainerSchema = resultSchema;
		
		if (resultSchema instanceof PrimitiveSchema) {
			
			// PrimitiveSchema
			log.debug("Elaborate primitive schema: "+elementName+" of type: "+resultSchema.getTypeName());

			// Get type and create soap element
	        soapType = (String) WSDLConstants.jade2xsd.get(resultSchema.getTypeName());
			soapElement = addSoapElement(rootSoapElement, elementName, WSDLConstants.XSD, soapType);

			AbsPrimitive primitiveAbsObj = (AbsPrimitive)resultAbsObj;
			
	        // Create a text node which contains the value of the object.
	        // Format date objects in ISO8601 format;
	        // for every other kind of object, just call toString.
	        if (BasicOntology.DATE.equals(primitiveAbsObj.getTypeName())) {
	        	soapElement.addTextNode(SoapUtils.ISO8601_DATE_FORMAT.format(primitiveAbsObj.getDate()));
	        } else {
	        	soapElement.addTextNode(primitiveAbsObj.toString());
	        }
		} else if (resultSchema instanceof ConceptSchema) {
			
			// ConceptSchema
			log.debug("Elaborate concept schema: "+elementName+" of type: "+resultSchema.getTypeName());

			// Get type and create soap element
	        soapType = resultSchema.getTypeName();
			soapElement = addSoapElement(rootSoapElement, elementName, tns, soapType);
			
			// Elaborate all sub-schema of current complex schema 
			for (String conceptSlotName : resultSchema.getNames()) {
				ObjectSchema slotSchema = resultSchema.getSchema(conceptSlotName);
			
				// Get sub-object value 
				AbsTerm subAbsObject = (AbsTerm)resultAbsObj.getAbsObject(conceptSlotName);
				
				// Do recursive call
				convertObjectToSoapElement(newContainerSchema, slotSchema, subAbsObject, conceptSlotName, soapElement);
			}
		} else if (resultSchema instanceof AggregateSchema) {
			
			// AggregateSchema
			log.debug("Elaborate aggregate schema: "+elementName);

			// Get facets 
			Facet[] facets;
			if (containerSchema instanceof AgentActionSchema) {
				// first level
				facets = ((AgentActionSchema)containerSchema).getResultFacets();
			} else {
				// next level 
				facets = containerSchema.getFacets(elementName);
			}
			
			// Get aggregate type
			ObjectSchema aggrSchema = null;
			for (Facet facet : facets) {
				if (facet instanceof TypedAggregateFacet) {
					aggrSchema = ((TypedAggregateFacet) facet).getType();
					break;
				}
			}
			
			// Get slot type
			soapType = aggrSchema.getTypeName();
			if (aggrSchema instanceof PrimitiveSchema) {
				soapType = WSDLConstants.jade2xsd.get(soapType);
			}
			String itemName = soapType;
			String aggrType = resultSchema.getTypeName();
			soapType = WSDLGeneratorUtils.getAggregateType(soapType, aggrType);
			
			// Create element
			soapElement = addSoapElement(rootSoapElement, elementName, tns, soapType);
			
			// Elaborate all item of current aggregate schema 
			AbsAggregate aggregateAbsObj = (AbsAggregate)resultAbsObj;
			if (aggregateAbsObj != null) {
				for (int i=0; i<aggregateAbsObj.size(); i++) {
					
					//Get object value of index i
					AbsTerm itemObject = aggregateAbsObj.get(i);
	
					// Do ricorsive call
					convertObjectToSoapElement(newContainerSchema, aggrSchema, itemObject, itemName, soapElement);
				}
			}
		}
					
		return soapElement;
	}


	/**
	 * addSoapElement
	 * @param rootSoapElement
	 * @param elementName
	 * @param uri
	 * @param soapType
	 * @return
	 * @throws Exception
	 */
	private SOAPElement addSoapElement(SOAPElement rootSoapElement, String elementName, String uri, String soapType) throws Exception {
		
		String prefix = PREFIX_Q1;
		if (uri != null && uri.equals(WSDLConstants.XSD)) {
			prefix = PREFIX_Q0;
		}
			
		// Create Name and Element
	    Name soapName = envelope.createName(elementName, "", "");
	    SOAPElement soapElement = rootSoapElement.addChildElement(soapName);
	    
	    // Add encoding style only to first element
	    if (rootSoapElement instanceof SOAPBody) {
	    	soapElement.setEncodingStyle(WSDLConstants.ENCODING_STYLE);
	    }
	    
	    // Add type
	    if (soapType != null) {
		    Name typeName = envelope.createName("type", "xsi", WSDLConstants.XSI);
		    soapElement.addAttribute(typeName, prefix+":"+soapType);
	    }
	    
	    return soapElement;
	}
}

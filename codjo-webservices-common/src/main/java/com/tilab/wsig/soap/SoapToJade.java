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
import jade.content.abs.AbsConcept;
import jade.content.abs.AbsHelper;
import jade.content.abs.AbsObject;
import jade.content.onto.Ontology;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Vector;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.axis.Message;
import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.tilab.wsig.store.ActionBuilder;
import com.tilab.wsig.store.ParameterInfo;
import com.tilab.wsig.store.WSIGService;
import com.tilab.wsig.wsdl.WSDLConstants;

public class SoapToJade extends DefaultHandler {

	private static Logger log = Logger.getLogger(SoapToJade.class.getName());

	private int level = 0;
	private StringBuffer elContent;
	private XMLReader xr = null;
	private Ontology onto;
	private Vector<Vector<ParameterInfo>> params4Level;

	/**
	 * SoapToJade2
	 */
	public SoapToJade() {
	    try { 
	    	String parserName = getSaxParserName();
	    	
			xr = (XMLReader)Class.forName(parserName).newInstance();
			xr.setContentHandler(this);
			xr.setErrorHandler(this);
		}
	    catch(Exception e) {
			log.error("Unable to create XML reader", e);
		}
	}

	/**
	 * Get SAX parser class name
	 * @param s
	 * @return parser name
	 */
	private static String getSaxParserName() throws Exception {
		String saxFactory = System.getProperty( "org.xml.sax.driver" );
		if( saxFactory != null ) {
			// SAXParser specified by means of the org.xml.sax.driver Java option
			return saxFactory;
		}
		else {
			// Use the JVM default SAX Parser
			SAXParserFactory newInstance = SAXParserFactory.newInstance();
			SAXParser newSAXParser = newInstance.newSAXParser();
			XMLReader reader = newSAXParser.getXMLReader();
			String name = reader.getClass().getName();
			return name;
		}
	}
	
	/**
	 * convert
	 * @param soapMessage
	 * @param wsigService
	 * @param operationName
	 * @return
	 * @throws Exception
	 */
	public Object convert(Message soapRequest, WSIGService wsigService, String operationName) throws Exception {

		Object actionObj = null;
		String soapMessage = soapRequest.getSOAPPartAsString();
		
		// Verify if parser is ready
		if (xr == null) {
			throw new Exception("Parser not initialized");
		}

		// Set ontology
		onto = wsigService.getOnto();

		// Parse xml to extract parameters value
		xr.parse(new InputSource(new StringReader(soapMessage)));

		// Get parameters
		Vector<ParameterInfo> params = getParameters();
		
		// Get action builder
		ActionBuilder actionBuilder = wsigService.getActionBuilder(operationName);
		
		// Prepare jade action
		actionObj = actionBuilder.getAgentAction(params);
		
		return actionObj;
	}
	
	/**
	 * getParameters
	 * @return
	 */
	private Vector<ParameterInfo> getParameters() {
	
		Vector<ParameterInfo> params = null;
		
		log.debug("Begin parameters list");
		if (params4Level.size() >= 1) {
			params = params4Level.get(0);
			
			if (log.isDebugEnabled()) {
				for (ParameterInfo param : params) {
					log.debug("   "+param.getName()+"= "+param.getAbsValue());
				}
			}
		} else {
			log.debug("   No parameters");
		}
		log.debug("End parameters list");
		
		return params;
	}

	
	
	//-------- SAX2 EVENT HANDLERS -----------------//

	/**
	 * startDocument
	 */
	public void startDocument () {
		elContent = new StringBuffer();
		params4Level = new Vector<Vector<ParameterInfo>>();
	}

	/**
	 * endDocument
	 */
	public void endDocument () {
	}

	/**
	 * startPrefixMapping
	 */
	public void startPrefixMapping (String prefix, String uri) {
	}

	/**
	 * endPrefixMapping
	 */
	public void endPrefixMapping (String prefix) {
	}

	/**
	 * startElement
	 *
	 * Level:
	 * 1: Envelope
	 * 2: Body
	 * 3: Operation
	 * >=4: Parameters
	 */
	public void startElement (String uri, String name, String qName, Attributes attrs) {

		try {
			elContent.setLength(0);
			++level;

			// Manage only parameters levels
			if (level >= 4) {

				// Get parameter type
				String attrValue = attrs.getValue(WSDLConstants.XSI, "type");
				if (attrValue != null) {
					int pos = attrValue.indexOf(':');
					String valueType = attrValue.substring(pos+1);
					log.debug("Start managing parameter "+name+" of type "+valueType);
	
					// Prepare vector store for this level
					int params4LevelIndex = level - 4; 
					Vector<ParameterInfo> params = null;
					if (params4Level.size() <= params4LevelIndex) {
						params = new Vector<ParameterInfo>();
						params4Level.add(params4LevelIndex, params);
					} else {
						params = params4Level.get(params4LevelIndex);
					}
	
					// Create new ElementInfo for this parameter
					ParameterInfo ei = new ParameterInfo();
					ei.setName(name);
					ei.setType(valueType);
					params.add(ei);
				}
			}

		} catch(Exception e) {
			level = 0;
			throw new RuntimeException("Error parsing element "+name+" - "+e.getMessage(), e);
		}
	}

	/**
	 * endElement
	 * 
	 * Level:
	 * 1: Envelope
	 * 2: Body
	 * 3: Operation
	 * >=4: Parameters
	 */
	public void endElement (String uri, String name, String qName) {
		
		try {
			// Manage only parameters levels
			if (level >= 4) {

				// Get parameter value
				String fieldValue = elContent.toString();

				// Get vector store for this level
				int params4LevelIndex = level - 4; 
				if (params4LevelIndex < params4Level.size()) {
					Vector<ParameterInfo> params = params4Level.get(params4LevelIndex);
					
					// Get parameter infos & verify...
					ParameterInfo paramEi = params.lastElement();
					if (!paramEi.getName().equals(name)) {
						throw new RuntimeException("Parameter "+name+" doesn't match with parameter in store ("+paramEi.getName()+")");
					}
	
					// Get parameter type 
					String xsdType = paramEi.getType();

					// Manage parameter
					if (SoapUtils.isPrimitiveAbsType(xsdType)) {
						
						// Primitive type
						paramEi.setAbsValue(SoapUtils.getPrimitiveAbsValue(paramEi.getType(), fieldValue));
						log.debug("Set "+name+" with " + fieldValue);
					} else {

						// NOT Primitive type
						AbsConcept absObj = null;
						
						// Get store of object parameters
						if ((params4LevelIndex+1) < params4Level.size()) {
							Vector<ParameterInfo> objParams = params4Level.get(params4LevelIndex+1);
		
							if (SoapUtils.isAggregateType(xsdType)) {
		
								// Type is an aggregate
								String aggType = SoapUtils.getAggregateType(xsdType);
								if (aggType == null) {
									throw new RuntimeException("Aggregate parameter "+name+" dont'have an associated type");
								}
								absObj = new AbsAggregate(aggType);
								
								for (int count = 0; count < objParams.size(); ++count) {
									
									// Add param to aggregate
									ParameterInfo objParamEi = objParams.get(count);
									((AbsAggregate)absObj).add(objParamEi.getAbsValue());
									log.debug("Add element "+count+" to "+name+" with "+objParamEi.getAbsValue());
								}
							} else {
								
								// Type is custom
								absObj = new AbsConcept(xsdType);
								
								// Set value to every fields
								for (int count = 0; count < objParams.size(); ++count) {
									ParameterInfo paramEi1 = objParams.get(count);
								
									// Get parameter and verify...
									Field declaredField;
									String paramName = paramEi1.getName();
									AbsObject paramValue = paramEi1.getAbsValue();
									
									// Set value
									AbsHelper.setAttribute(absObj, paramName, paramValue);
								}
							}

							// Remove params level from store
							params4Level.remove(objParams);
						}

						// Set value in param object
						paramEi.setAbsValue(absObj);
					
						log.debug("End managing parameter "+name);						
					}
				}
			}
		} catch(Exception e) {
			level = 0;
			throw new RuntimeException("Error parsing element "+name+" - "+e.getMessage(), e);
		}

		--level;
	}

	/**
	 * characters
	 */
	public void characters (char ch[], int start, int length) {
		elContent.append(ch, start, length);
	}
	
}


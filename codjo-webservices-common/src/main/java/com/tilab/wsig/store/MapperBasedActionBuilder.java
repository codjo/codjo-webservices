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

package com.tilab.wsig.store;

import jade.content.AgentAction;
import jade.content.abs.AbsPrimitive;
import jade.content.abs.AbsTerm;
import jade.content.onto.Ontology;

import java.lang.reflect.Method;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.tilab.wsig.wsdl.WSDLGeneratorUtils;

public class MapperBasedActionBuilder implements ActionBuilder {
	
	private static Logger log = Logger.getLogger(MapperBasedActionBuilder.class.getName());

	private Method method;
	private Object mapperObj;
	private String ontoActionName;
	private String[] methodParameterNames;
	private Ontology onto;

	/**
	 * MapperBasedActionBuilder
	 * @param mapperObj
	 * @param method
	 */
	public MapperBasedActionBuilder(Object mapperObj, Method method, Ontology onto, String ontoActionName) {
		this.method = method;
		this.mapperObj = mapperObj;
		this.ontoActionName = ontoActionName;
		this.methodParameterNames = WSDLGeneratorUtils.getParameterNames(method);
		this.onto = onto;
	}

	/**
	 * getAgentAction
	 */
	public AgentAction getAgentAction(Vector<ParameterInfo> soapParams) throws Exception {
		
		Object[] parameterValues = new Object[0];
		String parameterList = "";

		// Prepare mapper parameter
		if (soapParams != null) {
			
	        // If the mapper class is a Java dynamic proxy is not possible to have 
			// the name of the parameters of the methods (methodParameterNames == null)
			// Use SOAP parameters as master and apply it in the order of vector   
			// See: WSDLGeneratorUtils.getParameterNames(method)
			if (methodParameterNames == null) {
				parameterValues = new Object[soapParams.size()];
				for (int i = 0; i < soapParams.size(); i++) {
					ParameterInfo objParamEi = soapParams.get(i);
					AbsTerm absValue = objParamEi.getAbsValue();
					Object javaValue = onto.toObject(absValue);
					parameterValues[i] = javaValue;
					parameterList += objParamEi.getType()+",";
				}
			} else {
				parameterValues = new Object[methodParameterNames.length];
				for (int i = 0; i < methodParameterNames.length; i++) {
					try {
						ParameterInfo objParamEi = getSoapParamByName(soapParams, methodParameterNames[i]);
						AbsTerm absValue = objParamEi.getAbsValue();
						Object javaValue = onto.toObject(absValue);
						parameterValues[i] = javaValue;
						parameterList += objParamEi.getType()+",";
					} catch(Exception e) {
						log.error("Method "+method.getName()+", mandatory param "+methodParameterNames[i]+" not found in soap request");
						throw e;
					}
				}
			}
		}
		
		AgentAction actionObj = null;
		try {
			// Invoke mapper method
			actionObj = (AgentAction)method.invoke(mapperObj, parameterValues);
			log.debug("Invoked method "+method.getName()+"("+parameterList.substring(0,parameterList.length()-1)+") in mapper");

		} catch(Exception e) {
			log.error("Method "+method.getName()+"("+parameterList.substring(0,parameterList.length()-1)+") error invocation");
			throw e;
		}
		
		return actionObj;
	}
	
	public String getOntoActionName() {
		return ontoActionName;
	}
	
	private ParameterInfo getSoapParamByName(Vector<ParameterInfo> soapParams, String methodParamName) throws Exception {
		for (ParameterInfo param : soapParams) {
			if (param.getName().equalsIgnoreCase(methodParamName)) {
				return param;
			}
		}
		
		throw new Exception();
	}
}

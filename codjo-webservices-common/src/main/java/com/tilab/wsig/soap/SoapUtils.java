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

import jade.content.abs.AbsPrimitive;
import jade.content.abs.AbsTerm;

import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

import com.tilab.wsig.wsdl.WSDLConstants;
import com.tilab.wsig.wsdl.WSDLGeneratorUtils;

public class SoapUtils {
	
	private static Logger log = Logger.getLogger(SoapUtils.class.getName());

	/**
	 * ISO 8601 date format 
	 */
	public static SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	/**
	 * Return true if xsdType is a primitive abs type
	 * @param xsdType
	 * @return
	 */
	public static boolean isPrimitiveAbsType(String xsdType) {
		
		return (WSDLConstants.XSD_STRING.equals(xsdType) || 
				WSDLConstants.XSD_BOOLEAN.equals(xsdType) ||
				WSDLConstants.XSD_INT.equals(xsdType) ||
				WSDLConstants.XSD_LONG.equals(xsdType) ||
				WSDLConstants.XSD_FLOAT.equals(xsdType) ||
				WSDLConstants.XSD_DOUBLE.equals(xsdType) ||
				WSDLConstants.XSD_DATETIME.equals(xsdType) || 
				WSDLConstants.XSD_BYTE.equals(xsdType) ||
				WSDLConstants.XSD_SHORT.equals(xsdType));
	}
	
	/**
	 * Return true if xsdType is a aggregate
	 * @param xsdType
	 * @return
	 */
	public static boolean isAggregateType(String xsdType) {

		return xsdType.indexOf(WSDLGeneratorUtils.getAggregateToken()) > 0;
	}

	/**
	 * Return the type of aggregate
	 * @param xsdType
	 * @return
	 */
	public static String getAggregateType(String xsdType) {
		
		int sepPos = xsdType.lastIndexOf(WSDLConstants.SEPARATOR);
		if (sepPos <= 0)
			return null;
		
		return xsdType.substring(sepPos+1);
	}

	/**
	 * getPrimitiveAbsValue
	 * @param xsdType
	 * @param value
	 * @return
	 * @throws Exception
	 */
	public static AbsTerm getPrimitiveAbsValue(String xsdType, String value) throws Exception {
		
		AbsTerm absObj = null;
		// Find in base type
		if(WSDLConstants.XSD_STRING.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(value);
		} else if(WSDLConstants.XSD_BOOLEAN.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Boolean.parseBoolean(value));
		} else if(WSDLConstants.XSD_BYTE.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Byte.parseByte(value));
		} else if(WSDLConstants.XSD_DOUBLE.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Double.parseDouble(value));
		} else if(WSDLConstants.XSD_FLOAT.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Float.parseFloat(value));
		} else if(WSDLConstants.XSD_INT.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Integer.parseInt(value));
		} else if(WSDLConstants.XSD_LONG.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Long.parseLong(value));
		} else if(WSDLConstants.XSD_SHORT.equals(xsdType)) {
			absObj = AbsPrimitive.wrap(Short.parseShort(value));
		} else if (WSDLConstants.XSD_DATETIME.equals (xsdType)) {
			absObj = AbsPrimitive.wrap(ISO8601_DATE_FORMAT.parse(value));			
		} else {
			// No primitive type
			throw new Exception(xsdType+" is not a primitive type");
		}
		return absObj;
	}
}

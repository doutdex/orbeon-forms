/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.transformer.xupdate.statement;

import org.orbeon.jaxen.NamespaceContext;
import org.orbeon.oxf.transformer.xupdate.DocumentContext;
import org.orbeon.oxf.transformer.xupdate.Statement;
import org.orbeon.oxf.transformer.xupdate.VariableContextImpl;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.ArrayList;
import java.util.List;

public class While extends Statement {

    private String select;
    private NamespaceContext namespaceContext;
    private Statement[] statements;

    public While(LocationData locationData, String select, NamespaceContext namespaceContext, Statement[] statements) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        List result = new ArrayList();
        while (true) {
            Boolean success = (Boolean) Utils.evaluate
                    (uriResolver, context, variableContext, documentContext, getLocationData(), "boolean(" + select + ")", namespaceContext);
            if (!success.booleanValue()) break;
            Object statementsResult = Utils.execute(uriResolver, context, variableContext, documentContext, statements);
            if (statementsResult instanceof List) {
                result.addAll((List) statementsResult);
            } else {
                result.add(statementsResult);
            }
        }
        return result.size() == 1 ? result.get(0) : result;
    }
}

/*******************************************************************************
 * Copyright (c) 2006 Symbian Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Symbian - Initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.core.pdom.dom;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBinding;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.core.runtime.CoreException;

/**
 * Mirrors type-hierarchy from DOM interfaces
 */
abstract public class PDOMCPPBinding extends PDOMBinding implements ICPPBinding {
	public PDOMCPPBinding(PDOM pdom, int record) {
		super(pdom, record);
	}
	public PDOMCPPBinding(PDOM pdom, PDOMNode parent, IASTName name) throws CoreException {
		super(pdom, parent, name);
	}
		
	// TODO: performance?
	public String[] getQualifiedName() throws DOMException {
		List result = new ArrayList();
		try {
			PDOMNode node = this;
			while (node != null) {
				if (node instanceof PDOMBinding) {							
					result.add(0, ((PDOMBinding)node).getName());
				}
				node = node.getParentNode();
			}
			return (String[]) result.toArray(new String[result.size()]);
		} catch(CoreException ce) {
			CCorePlugin.log(ce);
			return null;
		}
	}

	// TODO: performance?
	public char[][] getQualifiedNameCharArray() throws DOMException {
		String[] preResult = getQualifiedName();
		char[][] result = new char[preResult.length][];
		for(int i=0; i<preResult.length; i++) {
			result[i] = preResult[i].toCharArray();
		}
		return result;
	}

	public boolean isGloballyQualified() throws DOMException {
		throw new PDOMNotImplementedError();
	}
}

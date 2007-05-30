/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.wala.ipa.callgraph.propagation;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;

/**
 * An instance key which represents a unique set for each multinewarray
 * allocation site
 */
public final class MultiNewArrayAllocationSiteKey extends AllocationSiteKey {
  private final int dim;

  /**
   * @return null if the element type is a primitive
   */
  private static IClass myElementType(IClass T, int d) {
    if (d == 0) {
      return ((ArrayClass) T).getElementClass();
    } else {
      IClass element = ((ArrayClass)T).getElementClass();
      if (element == null) {
        return null;
      } else {
        return myElementType(element, d-1);
      }
    }
  }

  public MultiNewArrayAllocationSiteKey(CGNode node, NewSiteReference allocation, IClass type, int dim) {
    super(node, allocation, myElementType(type, dim));
    this.dim = dim;
  }

  @Override
  public boolean equals(Object obj) {
    // instanceof is OK because this class is final
    if (obj instanceof MultiNewArrayAllocationSiteKey) {
      MultiNewArrayAllocationSiteKey other = (MultiNewArrayAllocationSiteKey) obj;
      return (dim == other.dim && getNode().equals(other.getNode()) && getSite().equals(other.getSite()));
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return 9967 * dim + getNode().hashCode() * 8647 * getSite().hashCode();
  }

  @Override
  public String toString() {
    return super.toString() + "<dim:" + dim + ">";
  }

  public int getDim() {
    return dim;
  }
}

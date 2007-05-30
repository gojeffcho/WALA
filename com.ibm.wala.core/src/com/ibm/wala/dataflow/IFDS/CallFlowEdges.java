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
package com.ibm.wala.dataflow.IFDS;

import com.ibm.wala.util.debug.Trace;
import com.ibm.wala.util.intset.BasicNaturalRelation;
import com.ibm.wala.util.intset.BitVectorIntSet;
import com.ibm.wala.util.intset.IBinaryNaturalRelation;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.SparseIntSet;
import com.ibm.wala.util.intset.SparseVector;

/**
 * 
 * A set of call flow edges which lead to a particular procedure entry s_p.
 * 
 * @author sfink
 */
public class CallFlowEdges {

  /**
   * A map from integer -> (IBinaryNonNegativeIntRelation)
   * 
   * 
   * For a fact d2, edges[c] gives a relation R=(d2,d1) s.t. (<c, d1> ->
   * <s_p,d2>) was recorded as a call flow edge.
   * 
   * Note that we handle paths of the form <c, d1> -> <s_p,d1> specially, below.
   * 
   * TODO: more representation optimization. A special represention for triples?
   * sparse representations for CFG? exploit shorts for ints?
   */
  private final SparseVector<IBinaryNaturalRelation> edges = new SparseVector<IBinaryNaturalRelation>(1, 1.1f);

  /**
   * a map from integer d1 -> int set.
   * 
   * for fact d1, identityPaths[d1] gives the set of block numbers C s.t. for c
   * \in C, <c, d1> -> <s_p, d1> is an edge.
   */
  private final SparseVector<IntSet> identityEdges = new SparseVector<IntSet>(1, 1.1f);

  public CallFlowEdges() {
  }

  /**
   * Record that we've discovered a call edge <c,d1> -> <s_p, d2>
   * 
   * @param c
   *          global number identifying the call site node
   * @param d1
   *          source fact at the call edge
   * @param d2
   *          result fact (result of the call flow function)
   */
  public void addCallEdge(int c, int d1, int d2) {
    if (TabulationSolver.DEBUG_LEVEL > 0) {
      Trace.println("addCallEdge " + c + " " + d1 + " " + d2);
    }
    if (d1 == d2) {
      BitVectorIntSet s = (BitVectorIntSet) identityEdges.get(d1);
      if (s == null) {
        s = new BitVectorIntSet();
        identityEdges.set(d1, s);
      }
      s.add(c);
    } else {
      IBinaryNaturalRelation R = edges.get(c);
      if (R == null) {
        // we expect the first dimention of R to be dense, the second sparse
        R = new BasicNaturalRelation(new byte[] { BasicNaturalRelation.SIMPLE_SPACE_STINGY },
            BasicNaturalRelation.TWO_LEVEL);
        edges.set(c, R);
      }
      R.add(d2, d1);
    }
  }

  /**
   * @param c
   * @param d2
   * @return set of d1 s.t. <c, d1> -> <s_p, d2> was recorded as call flow, or
   *         null if none found.
   */
  public IntSet getCallFlowSources(int c, int d2) {
    BitVectorIntSet s = (BitVectorIntSet) identityEdges.get(d2);
    IBinaryNaturalRelation R = edges.get(c);
    IntSet result = null;
    if (R == null) {
      if (s != null) {
        result = s.contains(c) ? SparseIntSet.singleton(d2) : null;
      }
    } else {
      if (s == null) {
        result = R.getRelated(d2);
      } else {
        if (s.contains(c)) {
          if (R.getRelated(d2) == null) {
            result = SparseIntSet.singleton(d2);
          } else {
            result = new MutableSparseIntSet(R.getRelated(d2));
            ((MutableSparseIntSet) result).add(d2);
          }
        } else {
          result = R.getRelated(d2);
        }
      }
    }
    if (TabulationSolver.DEBUG_LEVEL > 0) {
      Trace.println("getCallFlowSources " + c + " " + d2 + " " + result);
    }
    return result;
  }
}
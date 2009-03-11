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
package com.ibm.wala.util.shrike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.Instruction;
import com.ibm.wala.shrikeBT.ThrowInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

/**
 * 
 * Utility class to help deal with analysis of exceptions.
 * 
 * @author sfink
 */
public class Exceptions implements Constants {

  private static final Collection<TypeReference> arrayAccessExceptions = Collections.unmodifiableCollection(Arrays.asList(new TypeReference[] {
      TypeReference.JavaLangNullPointerException, TypeReference.JavaLangArrayIndexOutOfBoundsException }));;

  private static final Collection<TypeReference> aaStoreExceptions = Collections.unmodifiableCollection(Arrays.asList(new TypeReference[] {
      TypeReference.JavaLangNullPointerException, TypeReference.JavaLangArrayIndexOutOfBoundsException,
      TypeReference.JavaLangArrayStoreException }));

  private static final Collection<TypeReference> newScalarExceptions = Collections.unmodifiableCollection(Arrays.asList(new TypeReference[] {
      TypeReference.JavaLangExceptionInInitializerError, TypeReference.JavaLangOutOfMemoryError }));

  private static final Collection<TypeReference> newArrayExceptions = Collections.unmodifiableCollection(Arrays.asList(new TypeReference[] {
      TypeReference.JavaLangOutOfMemoryError, TypeReference.JavaLangNegativeArraySizeException }));

  private static final Collection<TypeReference> exceptionInInitializerError = Collections.singleton(TypeReference.JavaLangExceptionInInitializerError);

  private static final Collection<TypeReference> nullPointerException = Collections.singleton(TypeReference.JavaLangNullPointerException);

  private static final Collection<TypeReference> arithmeticException = Collections.singleton(TypeReference.JavaLangArithmeticException);

  private static final Collection<TypeReference> classCastException = Collections.singleton(TypeReference.JavaLangClassCastException);

  private static final Collection<TypeReference> classNotFoundException = Collections.singleton(TypeReference.JavaLangClassNotFoundException);


  /**
   * @param pei
   *          a potentially-excepting instruction
   * @param cha
   *          the governing class hierarchy
   * @return the exception types that pei may throw
   * 
   * Notes
   * <ul>
   * <li>this method does not handle athrow instructions
   * <li>this method ignores OutOfMemoryError
   * <li>this method ignores linkage errors
   * <li>this method ignores IllegalMonitorState exceptions
   * </ul>
   * @throws IllegalArgumentException  if pei is null
   * 
   */
  public static Collection<TypeReference> getExceptionTypes(ClassLoaderReference loader, IInstruction pei, IClassHierarchy cha) {
    if (pei == null) {
      throw new IllegalArgumentException("pei is null");
    }
    if (pei instanceof IInvokeInstruction) {
      IInvokeInstruction call = (IInvokeInstruction) pei;
      Collection<TypeReference> result = null;
        try {
          result = inferInvokeExceptions(MethodReference.findOrCreate(loader, call.getClassType(), call.getMethodName(),
              call.getMethodSignature()), cha);
        } catch (InvalidClassFileException e) {
          e.printStackTrace();
          Assertions.UNREACHABLE();
        }
      return result;
    } else if (pei instanceof ThrowInstruction) {
       Assertions.UNREACHABLE("This class does not have the smarts to infer exception types for athrow");
      return null;
    } else {
      return getIndependentExceptionTypes(pei);
    }
  }

  /**
   * @return Collection<TypeReference>, set of exception types a call to a
   *         declared target might throw.
   * @throws InvalidClassFileException 
   * @throws IllegalArgumentException  if target is null
   * @throws IllegalArgumentException  if cha is null
   */
  public static Collection<TypeReference> inferInvokeExceptions(MethodReference target, IClassHierarchy cha) throws InvalidClassFileException {
    if (cha == null) {
      throw new IllegalArgumentException("cha is null");
    }
    if (target == null) {
      throw new IllegalArgumentException("target is null");
    }
    ArrayList<TypeReference> set = new ArrayList<TypeReference>(cha.getJavaLangRuntimeExceptionTypes());
    set.addAll(cha.getJavaLangErrorTypes());

    IClass klass = cha.lookupClass(target.getDeclaringClass());
    if (klass == null) {
      Warnings.add(MethodResolutionFailure.moderate(target));
    }
    if (klass != null) {
      IMethod M = klass.getMethod(target.getSelector());
      if (M == null) {
        Warnings.add(MethodResolutionFailure.severe(target));
      } else {
        TypeReference[] exceptionTypes = M.getDeclaredExceptions();
        if (exceptionTypes != null) {
          set.addAll(Arrays.asList(exceptionTypes));
        }
      }
    }
    return set;
  }

  /**
   * A warning for when we fail to resolve the type for a checkcast
   */
  private static class MethodResolutionFailure extends Warning {

    final MemberReference method;

    MethodResolutionFailure(byte code, MemberReference method) {
      super(code);
      this.method = method;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + method;
    }

    public static MethodResolutionFailure moderate(MemberReference method) {
      return new MethodResolutionFailure(Warning.MODERATE, method);
    }

    public static MethodResolutionFailure severe(MemberReference method) {
      return new MethodResolutionFailure(Warning.SEVERE, method);
    }
  }

  /**
   * @param pei
   *          a potentially-excepting instruction
   * @return the exception types that pei may throw, independent of the class
   *         hierarchy.   null if none.
   * 
   * Notes
   * <ul>
   * <li>this method will <em>NOT</em> return the exception type explicitly
   * thrown by an athrow
   * <li>this method will <em>NOT</em> return the exception types that a
   * called method may throw
   * <li>this method ignores OutOfMemoryError
   * <li>this method ignores linkage errors
   * <li>this method ignores IllegalMonitorState exceptions
   * </ul>
   * 
   * TODO: move this elsewhere.
   * @throws IllegalArgumentException  if pei is null
   */
  public static Collection<TypeReference> getIndependentExceptionTypes(IInstruction pei) {
    if (pei == null) {
      throw new IllegalArgumentException("pei is null");
    }
    switch (((Instruction)pei).getOpcode()) {
    case OP_iaload:
    case OP_laload:
    case OP_faload:
    case OP_daload:
    case OP_aaload:
    case OP_baload:
    case OP_caload:
    case OP_saload:
    case OP_iastore:
    case OP_lastore:
    case OP_fastore:
    case OP_dastore:
    case OP_bastore:
    case OP_castore:
    case OP_sastore:
      return getArrayAccessExceptions();
    case OP_aastore:
      return getAaStoreExceptions();
    case OP_getfield:
    case OP_putfield:
    case OP_invokevirtual:
    case OP_invokespecial:
    case OP_invokeinterface:
      return getNullPointerException();
    case OP_idiv:
    case OP_irem:
    case OP_ldiv:
    case OP_lrem:
      return getArithmeticException();
    case OP_new:
      return newScalarExceptions;
    case OP_newarray:
    case OP_anewarray:
    case OP_multianewarray:
      return newArrayExceptions;
    case OP_arraylength:
      return getNullPointerException();
    case OP_athrow:
      // N.B: the caller must handle the explicitly-thrown exception
      return getNullPointerException();
    case OP_checkcast:
      return getClassCastException();
    case OP_monitorenter:
    case OP_monitorexit:
      // we're currently ignoring MonitorStateExceptions, since J2EE stuff
      // should be
      // logically single-threaded
      return getNullPointerException();
    case OP_ldc_w:
      if (((ConstantInstruction) pei).getType().equals(TYPE_Class))
        return getClassNotFoundException();
      else
        return null;
    case OP_getstatic:
    case OP_putstatic:
      return getExceptionInInitializerError();
    default:
      return null;
    }
  }

  public static Collection<TypeReference> getAaStoreExceptions() {
    return aaStoreExceptions;
  }

  public static Collection<TypeReference> getArithmeticException() {
    return arithmeticException;
  }

  public static Collection<TypeReference> getArrayAccessExceptions() {
    return arrayAccessExceptions;
  }

  public static Collection<TypeReference> getClassCastException() {
    return classCastException;
  }

  public static Collection<TypeReference> getClassNotFoundException() {
    return classNotFoundException;
  }

  public static Collection<TypeReference> getNewArrayExceptions() {
    return newArrayExceptions;
  }

  public static Collection<TypeReference> getNewScalarExceptions() {
    return newScalarExceptions;
  }

  public static Collection<TypeReference> getNullPointerException() {
    return nullPointerException;
  }
  
  public static Collection<TypeReference> getExceptionInInitializerError() {
    return exceptionInInitializerError;
  }
  
}

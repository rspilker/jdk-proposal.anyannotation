/*
 * Copyright (C) 2012 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.projectlombok.anyannotation;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.ScriptManager;
import lombok.patcher.scripts.ScriptBuilder;

public class JavaPatcher {
	public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
		ScriptManager sm = new ScriptManager();
		sm.registerTransformer(instrumentation);
		
		patchJavac(sm);
		patchReflectionCore(sm);
	}
	
	private static void patchReflectionCore(ScriptManager sm) {
		sm.addScript(ScriptBuilder.replaceMethodCall()
				.target(new MethodTarget("sun.reflect.annotation.AnnotationParser", "parseArray"))
				.methodToReplace(new Hook("java.lang.Class", "isAnnotation", "boolean"))
				.transplant()
				.replacementMethod(new Hook("org.projectlombok.anyannotation.JavaPatcher", "fixIsAnnotationForReflectionCore", "boolean", "java.lang.Class"))
				.build());
	}
	
	public static boolean fixIsAnnotationForReflectionCore(Class<?> clazz) {
		return clazz.isAnnotation() || clazz == java.lang.annotation.Annotation.class;
	}
	
	private static void patchJavac(ScriptManager sm) {
		sm.addScript(ScriptBuilder.replaceMethodCall()
				.target(new MethodTarget("com.sun.tools.javac.comp.Annotate", "enterAttributeValue"))
				.target(new MethodTarget("com.sun.tools.javac.comp.Check", "validateAnnotationType"))
				.methodToReplace(new Hook("com.sun.tools.javac.code.Symbol$TypeSymbol", "flags", "long"))
				.transplant()
				.replacementMethod(new Hook("org.projectlombok.anyannotation.JavaPatcher", "fixFlagsForJavac", "long", "java.lang.Object"))
				.build());
		
		sm.addScript(ScriptBuilder.replaceMethodCall()
				.target(new MethodTarget("com.sun.tools.javac.comp.Attr", "attribClassBody"))
				.methodToReplace(new Hook("com.sun.tools.javac.comp.Check", "checkNonCyclicElements", "void", "com.sun.tools.javac.tree.JCTree$JCClassDecl"))
				.replacementMethod(new Hook("org.projectlombok.anyannotation.JavaPatcher", "fixCheckNonCyclicElements", "void", "java.lang.Object", "java.lang.Object"))
				.transplant()
				.build());
	}
	
	public static long fixFlagsForJavac(Object obj) {
		Method m;
		try {
			m = obj.getClass().getDeclaredMethod("flags");
			long originals = (Long) m.invoke(obj);
			if (obj.toString().equals("java.lang.annotation.Annotation")) {
				return originals | 1L<<13;
			}
			return originals;
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("anyannotation fail!", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("anyannotation fail!", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("anyannotation fail!", e);
		}
	}
	
	public static void fixCheckNonCyclicElements(Object chk, Object tree) {
		try {
			init(chk, tree);
			checkNonCyclicElements(chk, tree);
		} catch (Exception e) {
			throw new RuntimeException("anyannotation fail!", e);
		}
	}
	
	
	// Flags
	private static final long ANNOTATION = 0x2000L;
	private static final long LOCKED = 0x8000000L;
	private static final long ACYCLIC_ANN = 0x800000000L;
	
	// JCTree
	// Some tags have been renumbered in java7
	private static int TAG_METHODDEF;
	private static int TAG_ANNOTATION;
	private static int TAG_ASSIGN;
	private static int TAG_NEWARRAY;
	
	// Kinds
	private static final int MTH = 0x10;
	
	// TypeTags
	private static final int CLASS = 0xa;
	private static final int ARRAY = 0xb;
	
	
	static Field JCClassDecl_sym;
	static Field JCClassDecl_defs;

	static Method JCTree_getTag;
	static Field JCTree_tag;
	static Method JCTree_pos;
	static Field JCTree_type;
	
	static Field JCMethod_restype;
	static Field JCMethod_defaultValue;
	
	static Field JCAnnotation_args;
	static Field JCAssign_rhs;
	static Field JCNewArray_elems;
	
	static Field Symbol_flags_field;
	static Method Symbol_flags;
	static Field Symbol_kind;
	static Field Symbol_type;
	static Method Symbol_members;
	
	static Field MethodSymbol_defaultValue;
	
	static Field Check_log;
	static Field Check_types;
	
	static Method Log_error;
	
	static Field Scope_elems;
	static Field Entry_sibling;
	static Field Entry_sym;
	
	static Method Type_getReturnType;
	static Field Type_tag;
	static Field Type_tsym;
	
	static Method Types_isSameType;
	static Method Types_elemtype;
	
	static Object annotationType;
	
	static Field Attribute_type;
	static Field Array_values;
	
	private static synchronized void init(Object chk, Object jcClassDecl) throws Exception {
		if (JCClassDecl_sym != null) return;
		
		Class<?> jcClassDeclClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCClassDecl");
		JCClassDecl_sym = jcClassDeclClass.getField("sym");
		JCClassDecl_defs = jcClassDeclClass.getField("defs");
		
		Class<? > symbolClass = Class.forName("com.sun.tools.javac.code.Symbol");
		Symbol_flags_field = symbolClass.getField("flags_field");
		Symbol_flags = symbolClass.getMethod("flags");
		Symbol_kind = symbolClass.getField("kind");
		Symbol_type = symbolClass.getField("type");
		Symbol_members = symbolClass.getMethod("members");
		
		Class<? > methodSymbolClass = Class.forName("com.sun.tools.javac.code.Symbol$MethodSymbol");
		MethodSymbol_defaultValue = methodSymbolClass.getField("defaultValue");
		
		Class<?> jcTreeClass = Class.forName("com.sun.tools.javac.tree.JCTree");
		try {
			JCTree_getTag = jcTreeClass.getMethod("getTag");
		}
		catch (Exception e) {
			// java6 only has a tag field
			JCTree_tag = jcTreeClass.getField("tag");
		}
		JCTree_pos = jcTreeClass.getMethod("pos");
		JCTree_type = jcTreeClass.getField("type");
		TAG_METHODDEF = jcTreeClass.getField("METHODDEF").getInt(null);
		TAG_ANNOTATION = jcTreeClass.getField("ANNOTATION").getInt(null);
		TAG_ASSIGN = jcTreeClass.getField("ASSIGN").getInt(null);
		TAG_NEWARRAY = jcTreeClass.getField("NEWARRAY").getInt(null);
		
		Class<?> jcMethodDeclClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCMethodDecl");
		JCMethod_restype = jcMethodDeclClass.getField("restype");
		JCMethod_defaultValue = jcMethodDeclClass.getField("defaultValue");
		
		Class<?> jcAnnotationClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCAnnotation");
		JCAnnotation_args = jcAnnotationClass.getField("args");
		
		Class<?> jcAssignClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCAssign");
		JCAssign_rhs = jcAssignClass.getField("rhs");
		
		Class<?> jcNewArrayClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCNewArray");
		JCNewArray_elems = jcNewArrayClass.getField("elems");
		
		Class<?> scopeClass = Class.forName("com.sun.tools.javac.code.Scope");
		Scope_elems = scopeClass.getField("elems");
		
		Class<?> scopeEntryClass = Class.forName("com.sun.tools.javac.code.Scope$Entry");
		Entry_sibling = scopeEntryClass.getField("sibling");
		Entry_sym = scopeEntryClass.getField("sym");
		
		Class<?> typeClass = Class.forName("com.sun.tools.javac.code.Type");
		Type_getReturnType = typeClass.getMethod("getReturnType");
		Type_tag = typeClass.getField("tag");
		Type_tsym = typeClass.getField("tsym");
		
		Class<?> typesClass = Class.forName("com.sun.tools.javac.code.Types");
		Types_isSameType = typesClass.getMethod("isSameType", typeClass, typeClass);
		Types_elemtype = typesClass.getMethod("elemtype", typeClass);
		
		Class<?> attributeClass = Class.forName("com.sun.tools.javac.code.Attribute");
		Attribute_type = attributeClass.getField("type");
		
		Class<?> arrayClass = Class.forName("com.sun.tools.javac.code.Attribute$Array");
		Array_values = arrayClass.getField("values");
		
		Class<?> checkClass = Class.forName("com.sun.tools.javac.comp.Check");
		
		Field symsField = checkClass.getDeclaredField("syms");
		symsField.setAccessible(true);
		Object syms = symsField.get(chk);
		annotationType = syms.getClass().getField("annotationType").get(syms);
		Check_types = checkClass.getDeclaredField("types");
		Check_types.setAccessible(true);
		Check_log = checkClass.getDeclaredField("log");
		Check_log.setAccessible(true);
		
		Class<?> logClass;
		try {
			logClass = Class.forName("com.sun.tools.javac.util.AbstractLog");
		}
		catch (Exception e) {
			// java6 does not have Abstractlog
			logClass = Class.forName("com.sun.tools.javac.util.Log");
		}
		Class<?> posClass = Class.forName("com.sun.tools.javac.util.JCDiagnostic$DiagnosticPosition");
		
		Log_error = logClass.getMethod("error", posClass, String.class, Object[].class);
	}
	
	private static boolean isTypeFlagSet(Object jcClassDecl, long value) throws Exception {
		return isSymFlagSet(JCClassDecl_sym.get(jcClassDecl), value);
	}
	
	private static boolean isSymFlagSet(Object tsym, long value) throws Exception {
		long flags = Symbol_flags_field.getLong(tsym);
		return (flags & value) != 0;
	}
	
	private static boolean isSymFlagSetViaFlagsCall(Object tsym, long value) throws Exception {
		long flags = (Long)Symbol_flags.invoke(tsym);
		return (flags & value) != 0;
	}
	
	private static void setTypeFlag(Object jcClassDecl, long value) throws Exception {
		setSymFlag(JCClassDecl_sym.get(jcClassDecl), value);
	}
	
	private static void setSymFlag(Object tsym, long value) throws Exception {
		long flags = Symbol_flags_field.getLong(tsym);
		flags |= value;
		Symbol_flags_field.setLong(tsym, flags);
	}
	
	private static void clearTypeFlag(Object jcClassDecl, long value) throws Exception {
		clearSymFlag(JCClassDecl_sym.get(jcClassDecl), value);
	}
	
	private static void clearSymFlag(Object tsym, long value) throws Exception {
		long flags = Symbol_flags_field.getLong(tsym);
		flags &= ~value;
		Symbol_flags_field.setLong(tsym, flags);
	}
	
	@SuppressWarnings("unchecked")
	private static List<Object> defs(Object jcClassDecl) throws Exception {
		return (List<Object>) JCClassDecl_defs.get(jcClassDecl);
	}
	
	private static boolean isJCTreeTag(Object jcTree, int value) throws Exception {
		if (JCTree_getTag != null) {
			return ((Integer)JCTree_getTag.invoke(jcTree)) == value;
		}
		return JCTree_tag.getInt(jcTree) == value;
	}
	
	private static Object pos(Object jcMethod) throws Exception {
		return JCTree_pos.invoke(jcMethod);
	}
	
	private static Object resTypeType(Object meth) throws Exception {
		return JCTree_type.get(JCMethod_restype.get(meth));
	}
	
	private static final Object[] EMPTY_OBJECT_ARRAY = {};
	
	private static void logError(Object chk, Object pos, String message) throws Exception {
		Log_error.invoke(Check_log.get(chk), pos, message, EMPTY_OBJECT_ARRAY);
	}
	
	private static List<Object> memberMethods(Object typeSymbol) throws Exception {
		List<Object> result = new ArrayList<Object>();
		for (Object e = Scope_elems.get(Symbol_members.invoke(typeSymbol)); e != null; e = Entry_sibling.get(e)) {
			Object sym = Entry_sym.get(e);
			if (Symbol_kind.getInt(sym) == MTH) {
				result.add(sym);
			}
		}
		return result;
	}
	
	static void checkNonCyclicElements(Object chk, Object jcClassDecl) throws Exception {
		if (!isTypeFlagSet(jcClassDecl, ANNOTATION)) return;
		if (isTypeFlagSet(jcClassDecl, LOCKED)) throw new AssertionError();
		try {
			setTypeFlag(jcClassDecl, LOCKED);
			for (Object def : defs(jcClassDecl)) {
				if (!isJCTreeTag(def, TAG_METHODDEF)) continue;
				checkAnnotationElementType(chk, pos(def), resTypeType(def));
				checkNonCyclicAnnotationDefaultValues(chk, def);
			}
		} finally {
			clearTypeFlag(jcClassDecl, LOCKED);
			setTypeFlag(jcClassDecl, ACYCLIC_ANN);
		}
	}
	
	static void checkNonCyclicElementsInternal(Object chk, Object pos, Object typeSymbol) throws Exception {
		if (isSymFlagSet(typeSymbol, ACYCLIC_ANN)) return;
		if (isSymFlagSet(typeSymbol, LOCKED)) {
			logError(chk, pos, "cyclic.annotation.element");
			return;
		}
		try {
			setSymFlag(typeSymbol, LOCKED);
			for (Object s : memberMethods(typeSymbol)) {
				checkAnnotationElementType(chk, pos, Type_getReturnType.invoke(Symbol_type.get(s)));
				checkNonCyclicAnnotationDefaultValues(chk, pos, s);
			}
		} finally {
			clearSymFlag(typeSymbol, LOCKED);
			setSymFlag(typeSymbol, ACYCLIC_ANN);
		}
	}
	
	static void checkAnnotationElementType(Object chk, Object pos, Object type) throws Exception {
		switch (Type_tag.getInt(type)) {
		case CLASS:
			Object tsym = Type_tsym.get(type);
			if (isSymFlagSetViaFlagsCall(tsym, ANNOTATION))
				checkNonCyclicElementsInternal(chk, pos, tsym);
			break;
		case ARRAY:
			checkAnnotationElementType(chk, pos, Types_elemtype.invoke(Check_types.get(chk), type));
			break;
		default:
			break; // int etc
		}
	}
	
	private static void checkNonCyclicAnnotationDefaultValues(Object chk, Object jcMethodDecl) throws Exception {
		if (!isAnnotationType(chk, resTypeType(jcMethodDecl))) return;
		Object defaultValue = JCMethod_defaultValue.get(jcMethodDecl);
		if (defaultValue == null) return;
		List<Object> allAnnotations = new ArrayList<Object>();
		collectAllAnnotations(allAnnotations, defaultValue);
		for (Object o : allAnnotations) {
			checkAnnotationElementType(chk, JCTree_pos.invoke(o) , JCTree_type.get(o));
		}
	}
	
	private static void collectAllAnnotations(List<Object> collector, Object jcTree) throws Exception {
		if (isJCTreeTag(jcTree, TAG_ANNOTATION)) {
			collector.add(jcTree);
			@SuppressWarnings("unchecked")
			List<Object> args = (List<Object>)JCAnnotation_args.get(jcTree);
			for (Object o : args) {
				if (isJCTreeTag(o, TAG_ASSIGN)) {
					collectAllAnnotations(collector, JCAssign_rhs.get(o));
				}
			}
		}
		else if (isJCTreeTag (jcTree, TAG_NEWARRAY)) {
			@SuppressWarnings("unchecked")
			List<Object> args = (List<Object>)JCNewArray_elems.get(jcTree);
			for (Object o : args) {
				collectAllAnnotations(collector, o);
			}
		}
		return;
	}
	
	private static void checkNonCyclicAnnotationDefaultValues(Object chk, final Object pos, Object methodSymbol) throws Exception {
		if (!isAnnotationType(chk, Type_getReturnType.invoke(Symbol_type.get(methodSymbol)))) return;
		Object defaultValue = MethodSymbol_defaultValue.get(methodSymbol);
		if (defaultValue == null) return;
		Object type = Attribute_type.get(defaultValue);
		if (Type_tag.getInt(type) == ARRAY) { // ARRAY = 0xb
			for (Object a : (Object[])Array_values.get(defaultValue)) {
				checkAnnotationElementType(chk, pos, Attribute_type.get(a));
			}
		}
		else {
			checkAnnotationElementType(chk, pos, type);
		}
	}
	
	private static boolean isAnnotationType(Object chk, Object type) throws Exception {
		switch (Type_tag.getInt(type)) {
		case CLASS:
			return (Boolean)Types_isSameType.invoke(Check_types.get(chk), type, annotationType);
		case ARRAY:
			Object types = Check_types.get(chk);
			return (Boolean)Types_isSameType.invoke(types, Types_elemtype.invoke(types, type), annotationType);
		default:
			return false;
		}
	}
}

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

import static com.sun.tools.javac.code.Flags.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Attribute.Array;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

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
			// For now, just forward the call
			Method m = chk.getClass().getDeclaredMethod("checkNonCyclicElements", tree.getClass());
			m.setAccessible(true);
			m.invoke(chk, tree);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("anyannotation fail!", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("anyannotation fail!", e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("anyannotation fail!", e);
		}
	}
/*
	
	void checkNonCyclicElements(JCClassDecl tree) {
		if ((tree.sym.flags_field & ANNOTATION) == 0) return; // ANNOTATION = 0x2000
		Assert.check((tree.sym.flags_field & LOCKED) == 0); // LOCKED = 0x8000000
		try {
			tree.sym.flags_field |= LOCKED; // LOCKED = 0x8000000
			for (JCTree def : tree.defs) {
				if (def.getTag() != JCTree.METHODDEF) continue; // METHODDEF = 0x4
				JCMethodDecl meth = (JCMethodDecl)def;
				checkAnnotationElementType(meth.pos(), meth.restype.type);
				checkNonCyclicAnnotationDefaultValues(meth);
			}
		} finally {
			tree.sym.flags_field &= ~LOCKED; // LOCKED = 0x8000000
			tree.sym.flags_field |= ACYCLIC_ANN; // ACYCLIC_ANN = 0x800000000
		}
	}
	
	void checkNonCyclicElementsInternal(DiagnosticPosition pos, TypeSymbol tsym) {
		if ((tsym.flags_field & ACYCLIC_ANN) != 0) // ACYCLIC_ANN = 0x800000000
			return;
		if ((tsym.flags_field & LOCKED) != 0) { // LOCKED = 0x8000000
			log.error(pos, "cyclic.annotation.element");
			return;
		}
		try {
			tsym.flags_field |= LOCKED; // LOCKED = 0x8000000
			for (Scope.Entry e = tsym.members().elems; e != null; e = e.sibling) {
				Symbol s = e.sym;
				if (s.kind != Kinds.MTH) // MTH = 0x10
					continue;
				checkAnnotationElementType(pos, ((MethodSymbol)s).type.getReturnType());
				checkNonCyclicAnnotationDefaultValues(pos, (MethodSymbol)s);
			}
		} finally {
			tsym.flags_field &= ~LOCKED; // LOCKED = 0x8000000
			tsym.flags_field |= ACYCLIC_ANN; // ACYCLIC_ANN = 0x800000000
		}
	}
	
	void checkAnnotationElementType(DiagnosticPosition pos, Type type) {
		switch (type.tag) {
		case TypeTags.CLASS: // CLASS = 0xa
			if ((type.tsym.flags() & ANNOTATION) != 0) // ANNOTATION = 0x2000
				checkNonCyclicElementsInternal(pos, type.tsym);
			break;
		case TypeTags.ARRAY: // ARRAY = 0xb
			checkAnnotationElementType(pos, types.elemtype(type));
			break;
		default:
			break; // int etc
		}
	}
	
	private void checkNonCyclicAnnotationDefaultValues(JCMethodDecl meth) {
		if (!isAnnotationType(meth.restype.type)) return;
		if (meth.defaultValue == null) return;
		meth.defaultValue.accept(new TreeScanner() {
			@Override public void visitAnnotation(JCAnnotation tree) {
				checkAnnotationElementType(tree.pos(), tree.type);
				super.visitAnnotation(tree);
			}
		});
	}
	
	private void checkNonCyclicAnnotationDefaultValues(final DiagnosticPosition pos, MethodSymbol meth) {
		if (!isAnnotationType(meth.type.getReturnType())) return;
		if (meth.defaultValue == null) return;
		if (meth.defaultValue.type.tag == TypeTags.ARRAY) { // ARRAY = 0xb
			for (Attribute a : ((Array)meth.defaultValue).values) {
				checkAnnotationElementType(pos, a.type);
			}
		}
		else {
			checkAnnotationElementType(pos, meth.defaultValue.type);
		}
	}
	
	private boolean isAnnotationType(Type type) {
		switch (type.tag) {
		case TypeTags.CLASS: // CLASS = 0xa
			return types.isSameType(type, syms.annotationType);
		case TypeTags.ARRAY: // ARRAY = 0xb
			return types.isSameType(types.elemtype(type), syms.annotationType);
		default:
			return false;
		}
	}
 
 */
}

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
}

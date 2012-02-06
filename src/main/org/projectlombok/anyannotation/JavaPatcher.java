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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JavaPatcher {
	private static final List<String> REPLACEMENTS = Collections.unmodifiableList(Arrays.asList(
			"com/sun/tools/javac/comp/Annotate$Annotator",
			"com/sun/tools/javac/comp/Annotate",
			"com/sun/tools/javac/comp/Check$1",
			"com/sun/tools/javac/comp/Check$1AnnotationValidator",
			"com/sun/tools/javac/comp/Check$1SpecialTreeVisitor",
			"com/sun/tools/javac/comp/Check$2",
			"com/sun/tools/javac/comp/Check$3",
			"com/sun/tools/javac/comp/Check$4",
			"com/sun/tools/javac/comp/Check$5",
			"com/sun/tools/javac/comp/Check$6",
			"com/sun/tools/javac/comp/Check$ClashFilter",
			"com/sun/tools/javac/comp/Check$ConversionWarner",
			"com/sun/tools/javac/comp/Check$CycleChecker",
			"com/sun/tools/javac/comp/Check$Validator",
			"com/sun/tools/javac/comp/Check",
			"com/sun/tools/javac/model/AnnotationProxyMaker$MirroredTypeExceptionProxy",
			"com/sun/tools/javac/model/AnnotationProxyMaker$MirroredTypeExceptionProxy",
			"com/sun/tools/javac/model/AnnotationProxyMaker$MirroredTypesExceptionProxy",
			"com/sun/tools/javac/model/AnnotationProxyMaker$ValueVisitor$1AnnotationTypeMismatchExceptionProxy",
			"com/sun/tools/javac/model/AnnotationProxyMaker$ValueVisitor",
			"com/sun/tools/javac/model/AnnotationProxyMaker"
			));
	public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
		class ReplaceClassesTransformer implements ClassFileTransformer {
			@Override public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if (!REPLACEMENTS.contains(className)) return null;
				InputStream in = JavaPatcher.class.getResourceAsStream("/replacements/" + className + ".class.rpl");
				if (in == null) return null;
				return readReplacement(in);
			}
			
			private byte[] readReplacement(InputStream in) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					try {
						byte[] b = new byte[65536];
						while (true) {
							int r = in.read(b);
							if (r == -1) break;
							baos.write(b, 0, r);
						}
					} finally {
						in.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				} catch (RuntimeException e) {
					e.printStackTrace();
					throw e;
				}
				
				return baos.toByteArray();
			}
		}
		
		instrumentation.addTransformer(new ReplaceClassesTransformer());
	}
}

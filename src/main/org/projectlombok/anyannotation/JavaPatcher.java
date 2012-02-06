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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaPatcher {
	private static byte[] readFile(InputStream in) {
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
	
	
	private static final Pattern CLASS_NAME_FINDER = Pattern.compile("^.*/patchedJavac/(.*)\\.class$");
	
	public static void premain(String agentArgs, Instrumentation instrumentation) throws Throwable {
		InputStream in = JavaPatcher.class.getResourceAsStream("/replacements/classesToPatch_anyannotation.txt");
		final List<String> classesToReplace = new ArrayList<String>();
		for (String elem : new String(readFile(in), "US-ASCII").split("[:;]")) {
			Matcher m = CLASS_NAME_FINDER.matcher(elem);
			if (m.matches()) classesToReplace.add(m.group(1));
		}
		
		class ReplaceClassesTransformer implements ClassFileTransformer {
			@Override public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if (!classesToReplace.contains(className)) return null;
				InputStream in = JavaPatcher.class.getResourceAsStream("/replacements/" + className + ".class.rpl");
				if (in == null) return null;
				return readFile(in);
			}
			
		}
		
		instrumentation.addTransformer(new ReplaceClassesTransformer());
	}
}

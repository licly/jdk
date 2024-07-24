/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Example test to use the Compile Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver comile_framework.examples.SimpleJavaExample
 */

package comile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * This test shows a simple compilation of java source code, and its invocation.
 */
public class SimpleJavaExample {

    // Generate a source java file as String
    public static String generate() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("public class XYZ {");
        out.println("    public static int test(int i) {");
        out.println("        System.out.println(\"Hello from XYZ.test: \" + i);");
        out.println("        return i * 2;");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        String src = generate();
        SourceFile file = SourceFile.newJavaSourceFile("XYZ", src);
        comp.add(file);

        // Compile the source file.
        comp.compile();

        // Load the compiled class.
        Class c = comp.getClass("XYZ");

        // Invoke the "XYZ.test" method from the compiled and loaded class.
        Object ret;
        try {
            ret = c.getDeclaredMethod("test", new Class[] { int.class }).invoke(null, new Object[] { 5 });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }

        // Extract return value of invocation, verify its value.
        int i = (int)ret;
        System.out.println("Result of call: " + i);
        if (i != 10) {
            throw new RuntimeException("wrong value: " + i);
        }
    }
}

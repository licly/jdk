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
 * @run driver comile_framework.examples.MultiFileJavaExample
 */

package comile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * This test shows a compilation of multiple java source code files.
 */
public class MultiFileJavaExample {

    // Generate a source java file as String
    public static String generate(int i) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("package p.xyz;");
        out.println("");
        out.println("public class XYZ" + i + " {");
        if (i > 0) {
            out.println("    public XYZ" + (i - 1) + " xyz = new XYZ" + (i - 1) + "();");
        }
        out.println("");
        out.println("    public static Object test() {");
        out.println("        return new XYZ" + i + "();");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Generate 10 files.
        for (int i = 0; i < 10; i++) {
            comp.add(SourceFile.newJavaSourceFile("p.xyz.XYZ" + i, generate(i)));
        }

        // Compile the source files.
        comp.compile();

        // Load the compiled class.
        Class c = comp.getClass("p.xyz.XYZ9");

        // Invoke the "XYZ9.test" method from the compiled and loaded class.
        Object ret;
        try {
            ret = c.getDeclaredMethod("test", new Class[] {}).invoke(null, new Object[] {});
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }

        if (!ret.getClass().getSimpleName().equals("XYZ9")) {
            throw new RuntimeException("wrong result:" + ret);
        }
        System.out.println("Success.");
    }
}

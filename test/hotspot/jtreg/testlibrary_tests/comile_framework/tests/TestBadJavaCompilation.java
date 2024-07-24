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
 * @summary Example test with failing compilation.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver comile_framework.tests.TestBadJavaCompilation
 */

package comile_framework.tests;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

public class TestBadJavaCompilation {

    // Generate a source java file as String
    public static String generate() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("public class XYZ {");
        out.println("    asdf"); // some bad code
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        String src = generate();
        SourceFile file = new SourceFile("XYZ", src);
        comp.add(file);

        try {
            // Compile the source file.
            comp.compile();
            throw new RuntimeException("Expected compilation to fail.");
        } catch (CompileFrameworkException e) {
            System.out.println("Success, expected compilation to fail.");
        }
    }
}

/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8157464 8210375
 * @summary Basic test for StackWalker.getCallerClass()
 * @library src
 * @modules java.base/jdk.internal.reflect
 * @build java.base/java.util.CSM csm/*
 * @run main/othervm csm/jdk.test.CallerSensitiveTest
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames csm/jdk.test.CallerSensitiveTest
 * @run main/othervm -Djava.security.manager=allow csm/jdk.test.CallerSensitiveTest sm
 */
public class Main {
}


/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile constant instruction argument validation.
 * @run junit OpcodesValidationTest
 */
import java.lang.classfile.instruction.ConstantInstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static java.lang.classfile.Opcode.*;

class OpcodesValidationTest {

    @Test
    void testArgumentConstant() {
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, 0));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, Short.MIN_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, Short.MAX_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, 0));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, Byte.MIN_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, Byte.MAX_VALUE));

        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(SIPUSH, (int)Short.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(SIPUSH, (int)Short.MAX_VALUE + 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(BIPUSH, (int)Byte.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(BIPUSH, (int)Byte.MAX_VALUE + 1));
    }
}

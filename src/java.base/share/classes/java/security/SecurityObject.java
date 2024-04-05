/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.security;

import javax.crypto.EncryptedPrivateKeyInfo;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.spec.KeySpec;

/**
 * This is a top-level interface for security classes that contain cryptographic
 * data which may not be related or have a common class hierarchy.  These
 * security objects provide standard binary encoding, like ASN.1, and type
 * formats, like X.509 and PKCS#8.  These encodings are used in some form with
 * {@link KeyFactory}, {@link java.security.cert.CertificateFactory},
 * {@link Encoder}, and {@link Decoder}
 *
 * @see Key
 * @see KeyPair
 * @see KeySpec
 * @see EncryptedPrivateKeyInfo
 * @see Certificate
 * @see CRL
 */
public sealed interface SecurityObject permits Key, KeyPair, KeySpec,
    EncryptedPrivateKeyInfo, Certificate, CRL {
}

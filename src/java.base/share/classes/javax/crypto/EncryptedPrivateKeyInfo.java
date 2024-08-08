/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import jdk.internal.javac.PreviewFeature;

import sun.security.pkcs.PKCS8Key;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;

import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.security.*;
import java.security.spec.*;
import java.util.Objects;

/**
 * This class implements the {@code EncryptedPrivateKeyInfo} type
 * as defined in PKCS #8.
 * <p>Its ASN.1 definition is as follows:
 *
 * <pre>
 * EncryptedPrivateKeyInfo ::=  SEQUENCE {
 *     encryptionAlgorithm   AlgorithmIdentifier,
 *     encryptedData   OCTET STRING }
 *
 * AlgorithmIdentifier  ::=  SEQUENCE  {
 *     algorithm              OBJECT IDENTIFIER,
 *     parameters             ANY DEFINED BY algorithm OPTIONAL  }
 * </pre>
 *
 * @author Valerie Peng
 *
 * @see PKCS8EncodedKeySpec
 *
 * @since 1.4
 */

public final class EncryptedPrivateKeyInfo implements DEREncodable {

    // The "encryptionAlgorithm" is stored in either the algid or
    // the params field. Precisely, if this object is created by
    // {@link #EncryptedPrivateKeyInfo(AlgorithmParameters, byte[])}
    // with an uninitialized AlgorithmParameters, the AlgorithmParameters
    // object is stored in the params field and algid is set to null.
    // In all other cases, algid is non-null and params is null.
    private final AlgorithmId algid;
    private final AlgorithmParameters params;

    // the "encryptedData" field
    private final byte[] encryptedData;

    // the ASN.1 encoded contents of this class
    private final byte[] encoded;

    /**
     * Constructs an {@code EncryptedPrivateKeyInfo} from a given Encrypted
     * PKCS#8 ASN.1 encoding.
     * @param encoded the ASN.1 encoding to be parsed.
     * @throws NullPointerException if {@code encoded} is {@code null}.
     * @throws IOException if error occurs when parsing the ASN.1 encoding.
     */
    public EncryptedPrivateKeyInfo(byte[] encoded) throws IOException {
        Objects.requireNonNull(encoded);

        this.encoded = encoded.clone();
        DerValue val = DerValue.wrap(this.encoded);
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("DER header error: no SEQ tag");
        }

        DerValue[] seq = new DerValue[2];

        seq[0] = val.data.getDerValue();
        seq[1] = val.data.getDerValue();

        if (val.data.available() != 0) {
            throw new IOException("overrun, bytes = " + val.data.available());
        }

        this.algid = AlgorithmId.parse(seq[0]);
        this.params = null;
        if (seq[0].data.available() != 0) {
            throw new IOException("encryptionAlgorithm field overrun");
        }

        this.encryptedData = seq[1].getOctetString();
        if (seq[1].data.available() != 0) {
            throw new IOException("encryptedData field overrun");
        }
    }

    /**
     * Constructs an {@code EncryptedPrivateKeyInfo} from the
     * encryption algorithm name and the encrypted data.
     *
     * <p>Note: This constructor will use {@code null} as the value of the
     * algorithm parameters. If the encryption algorithm has
     * parameters whose value is not {@code null}, a different constructor,
     * e.g. EncryptedPrivateKeyInfo(AlgorithmParameters, byte[]),
     * should be used.
     *
     * @param algName encryption algorithm name. See the
     * <a href="{@docRoot}/../specs/security/standard-names.html">
     * Java Security Standard Algorithm Names</a> document
     * for information about standard Cipher algorithm names.
     * @param encryptedData encrypted data. The contents of
     * {@code encryptedData} are copied to protect against subsequent
     * modification when constructing this object.
     * @exception NullPointerException if {@code algName} or
     * {@code encryptedData} is {@code null}.
     * @exception IllegalArgumentException if {@code encryptedData}
     * is empty, i.e. 0-length.
     * @exception NoSuchAlgorithmException if the specified algName is
     * not supported.
     */
    public EncryptedPrivateKeyInfo(String algName, byte[] encryptedData)
        throws NoSuchAlgorithmException {

        if (algName == null)
                throw new NullPointerException("the algName parameter " +
                                               "must be non-null");
        this.algid = AlgorithmId.get(algName);
        this.params = null;

        if (encryptedData == null) {
            throw new NullPointerException("the encryptedData " +
                                           "parameter must be non-null");
        } else if (encryptedData.length == 0) {
            throw new IllegalArgumentException("the encryptedData " +
                                                "parameter must not be empty");
        } else {
            this.encryptedData = encryptedData.clone();
        }
        // delay the generation of ASN.1 encoding until
        // getEncoded() is called
        this.encoded = null;
    }

    /**
     * Constructs an {@code EncryptedPrivateKeyInfo} from the
     * encryption algorithm parameters and the encrypted data.
     *
     * @param algParams the algorithm parameters for the encryption
     * algorithm. {@code algParams.getEncoded()} should return
     * the ASN.1 encoded bytes of the {@code parameters} field
     * of the {@code AlgorithmIdentifier} component of the
     * {@code EncryptedPrivateKeyInfo} type.
     * @param encryptedData encrypted data. The contents of
     * {@code encryptedData} are copied to protect against
     * subsequent modification when constructing this object.
     * @exception NullPointerException if {@code algParams} or
     * {@code encryptedData} is {@code null}.
     * @exception IllegalArgumentException if {@code encryptedData}
     * is empty, i.e. 0-length.
     * @exception NoSuchAlgorithmException if the specified algName of
     * the specified {@code algParams} parameter is not supported.
     */
    public EncryptedPrivateKeyInfo(AlgorithmParameters algParams,
        byte[] encryptedData) throws NoSuchAlgorithmException {

        if (algParams == null) {
            throw new NullPointerException("algParams must be non-null");
        }

        AlgorithmId tmp;
        try {
            tmp = AlgorithmId.get(algParams);
        } catch (IllegalStateException e) {
            // This exception is thrown when algParams.getEncoded() fails.
            // While the spec of this constructor requires that
            // "getEncoded should return...", in reality people might
            // create with an uninitialized algParams first and only
            // initialize it before calling getEncoded(). Thus we support
            // this case as well.
            tmp = null;
        }

        // one and only one is non-null
        this.algid = tmp;
        this.params = this.algid != null ? null : algParams;

        if (encryptedData == null) {
            throw new NullPointerException("encryptedData must be non-null");
        } else if (encryptedData.length == 0) {
            throw new IllegalArgumentException("the encryptedData " +
                                                "parameter must not be empty");
        } else {
            this.encryptedData = encryptedData.clone();
        }

        // delay the generation of ASN.1 encoding until
        // getEncoded() is called
        this.encoded = null;
    }

    private EncryptedPrivateKeyInfo(byte[] encoded, byte[] eData,
        AlgorithmId id, AlgorithmParameters p) {
        this.encoded = encoded;
        encryptedData = eData;
        algid = id;
        params = p;
    }

    /**
     * Returns the encryption algorithm.
     * <p>Note: Standard name is returned instead of the specified one
     * in the constructor when such mapping is available.
     * See the <a href="{@docRoot}/../specs/security/standard-names.html">
     * Java Security Standard Algorithm Names</a> document
     * for information about standard Cipher algorithm names.
     *
     * @return the encryption algorithm name.
     */
    public String getAlgName() {
        return algid == null ? params.getAlgorithm() : algid.getName();
    }

    /**
     * Returns the algorithm parameters used by the encryption algorithm.
     * @return the algorithm parameters.
     */
    public AlgorithmParameters getAlgParameters() {
        return algid == null ? params : algid.getParameters();
    }

    /**
     * Returns the encrypted data.
     * @return the encrypted data. Returns a new array
     * each time this method is called.
     */
    public byte[] getEncryptedData() {
        return this.encryptedData.clone();
    }

    /**
     * Extract the enclosed PKCS8EncodedKeySpec object from the
     * encrypted data and return it.
     * <br>Note: In order to successfully retrieve the enclosed
     * PKCS8EncodedKeySpec object, {@code cipher} needs
     * to be initialized to either Cipher.DECRYPT_MODE or
     * Cipher.UNWRAP_MODE, with the same key and parameters used
     * for generating the encrypted data.
     *
     * @param cipher the initialized {@code Cipher} object which will be
     * used for decrypting the encrypted data.
     * @return the PKCS8EncodedKeySpec object.
     * @exception NullPointerException if {@code cipher}
     * is {@code null}.
     * @exception InvalidKeySpecException if the given cipher is
     * inappropriate for the encrypted data or the encrypted
     * data is corrupted and cannot be decrypted.
     */
    public PKCS8EncodedKeySpec getKeySpec(Cipher cipher)
        throws InvalidKeySpecException {
        byte[] encoded;
        try {
            encoded = cipher.doFinal(encryptedData);
            return pkcs8EncodingToSpec(encoded);
        } catch (GeneralSecurityException |
                 IOException |
                 IllegalStateException ex) {
            throw new InvalidKeySpecException(
                    "Cannot retrieve the PKCS8EncodedKeySpec", ex);
        }
    }

    private PKCS8EncodedKeySpec getKeySpecImpl(Key decryptKey,
        Provider provider) throws NoSuchAlgorithmException,
        InvalidKeyException {
        byte[] encoded;
        Cipher c;
        try {
            if (provider == null) {
                // use the most preferred one
                c = Cipher.getInstance(getAlgName());
            } else {
                c = Cipher.getInstance(getAlgName(), provider);
            }
            c.init(Cipher.DECRYPT_MODE, decryptKey, getAlgParameters());
            encoded = c.doFinal(encryptedData);
            return pkcs8EncodingToSpec(encoded);
        } catch (NoSuchAlgorithmException nsae) {
            // rethrow
            throw nsae;
        } catch (GeneralSecurityException | IOException ex) {
            throw new InvalidKeyException(
                    "Cannot retrieve the PKCS8EncodedKeySpec", ex);
        }
    }

    /**
     * Returns an {@code EncryptedPrivateKeyInfo} from a given PrivateKey.
     * A valid password-based encryption (PBE) algorithm and a PBEKeySpec
     * containing the password must be specified.  AlgorithmParameterSpec,
     * {@code params}, will use the provider default if {@code null} is
     * passed.  If {@code provider} is {@code null}, the provider will be
     * selected through the default configuration.
     * <p>
     * The PBE algorithm string format details can be found in the
     * <a href="{@docRoot}/../specs/security/standard-names.html#cipher-algorithms">
     * Cipher section</a> of the Java Security Standard Algorithm Names
     * Specification.
     *
     * @param key the PrivateKey object to encrypt.
     * @param keySpec PBEKeySpec containing the password
     * @param algorithm the algorithm to encrypt with.
     * @param params the AlgorithmParameterSpec to encrypt with.
     * @param provider the Provider that will perform the encryption
     * @return an EncryptedPrivateKeyInfo.
     * @throws IllegalArgumentException when arguments passed are incorrect.
     * @throws SecurityException on a cryptographic errors.
     * @throws NullPointerException if an argument passed in is unexpectedly null.
     *
     * @since 24
     */
    @PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
    public static EncryptedPrivateKeyInfo encryptKey(PrivateKey key,
        PBEKeySpec keySpec, String algorithm, AlgorithmParameterSpec params,
        Provider provider) {

        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(keySpec);

        Cipher cipher;
        SecretKey skey;

        try {
            SecretKeyFactory factory;
            if (provider == null) {
                factory = SecretKeyFactory.getInstance(algorithm);
                cipher = Cipher.getInstance(algorithm);
            } else {
                factory = SecretKeyFactory.getInstance(algorithm, provider);
                cipher = Cipher.getInstance(algorithm, provider);
            }
            skey = factory.generateSecret(keySpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeySpecException e) {
            throw new IllegalArgumentException(e);
        }

        AlgorithmId algId;
        byte[] encryptedData;
        DerOutputStream out = new DerOutputStream();
        try {
            cipher.init(Cipher.ENCRYPT_MODE, skey, params);
            encryptedData = cipher.doFinal(key.getEncoded());
            algId = new AlgorithmId(Pem.getPBEID(algorithm),
                cipher.getParameters());
            algId.encode(out);
            out.putOctetString(encryptedData);
        } catch (InvalidAlgorithmParameterException |
                 IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException e) {
            throw new SecurityException(e);
        }
        return new EncryptedPrivateKeyInfo(
            DerValue.wrap(DerValue.tag_Sequence, out).toByteArray(),
            encryptedData, algId, cipher.getParameters());
    }

    /**
     * Creates and encrypts an `EncryptedPrivateKeyInfo` from a given PrivateKey
     * and password.
     * <p>
     * The encryption uses the algorithm set by `jdk.epkcs8.defaultAlgorithm`
     * Security Property by the default provider and default the
     * AlgorithmParameterSpec of that provider.
     *
     * @param key The PrivateKey object to encrypt.
     * @param password the password used in the PBE encryption.
     * @return an EncryptedPrivateKeyInfo.
     * @throws IllegalArgumentException when arguments passed are incorrect.
     * @throws SecurityException on a cryptographic errors.
     *
     * @since 24
     */
    @PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
    public static EncryptedPrivateKeyInfo encryptKey(PrivateKey key,
        char[] password) {
        if (Pem.DEFAULT_ALGO == null || Pem.DEFAULT_ALGO.length() == 0) {
            throw new SecurityException("Security property " +
                "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                "valid algorithm.  Operation cannot be performed.");
        }
        return encryptKey(key, new PBEKeySpec(password), Pem.DEFAULT_ALGO,
            null, null);
    }

    /**
     * Return a PrivateKey from the encrypted data in the object.
     *
     * @param password the password used in the PBE encryption.
     * @return a PrivateKey
     * @throws InvalidKeyException if an error occurs during parsing of the
     * encrypted data or creation of the key object.
     *
     * @since 24
     */
    public PrivateKey getKey(char[] password) throws InvalidKeyException {
        return getKey(password, null);
    }
    /**
     * Return a PrivateKey from the object's encrypted data with a KeyFactory
     * from the given Provider.
     *
     * @param password the password
     * @param provider the KeyFactory provider used to generate the key.
     * @return a PrivateKey
     * @throws InvalidKeyException if an error occurs during parsing of the
     * encrypted data or creation of the key object.
     *
     * @since 24
     */
    @PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
    public PrivateKey getKey(char[] password, Provider provider)
        throws InvalidKeyException {
        try {
            PBEKeySpec pks = new PBEKeySpec(password);
            SecretKeyFactory skf;
            PKCS8EncodedKeySpec keySpec;
            if (provider == null) {
                skf = SecretKeyFactory.getInstance(getAlgName());
                keySpec = getKeySpec(skf.generateSecret(pks));
            } else {
                skf = SecretKeyFactory.getInstance(getAlgName(), provider);
                keySpec = getKeySpec(skf.generateSecret(pks), provider);
            }
            return PKCS8Key.parseKey(keySpec.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new InvalidKeyException(e);
        }
    }

    /**
     * Extract the enclosed PKCS8EncodedKeySpec object from the
     * encrypted data and return it.
     * @param decryptKey key used for decrypting the encrypted data.
     * @return the PKCS8EncodedKeySpec object.
     * @exception NullPointerException if {@code decryptKey} is {@code null}.
     * @exception NoSuchAlgorithmException Cannot find appropriate cipher to
     * decrypt.
     * @exception InvalidKeyException if {@code decryptKey}
     * cannot be used to decrypt the encrypted data or the decryption
     * result is not a valid PKCS8KeySpec.
     *
     * @since 1.5
     */
    public PKCS8EncodedKeySpec getKeySpec(Key decryptKey)
        throws NoSuchAlgorithmException, InvalidKeyException {
        if (decryptKey == null) {
            throw new NullPointerException("decryptKey is null");
        }
        return getKeySpecImpl(decryptKey, null);
    }

    /**
     * Extract the enclosed PKCS8EncodedKeySpec object from the
     * encrypted data and return it.
     * @param decryptKey key used for decrypting the encrypted data.
     * @param providerName the name of provider whose cipher
     * implementation will be used.
     * @return the PKCS8EncodedKeySpec object.
     * @exception NullPointerException if {@code decryptKey}
     * or {@code providerName} is {@code null}.
     * @exception NoSuchProviderException if no provider
     * {@code providerName} is registered.
     * @exception NoSuchAlgorithmException if cannot find appropriate
     * cipher to decrypt the encrypted data.
     * @exception InvalidKeyException if {@code decryptKey}
     * cannot be used to decrypt the encrypted data or the decryption
     * result is not a valid PKCS8KeySpec.
     *
     * @since 1.5
     */
    public PKCS8EncodedKeySpec getKeySpec(Key decryptKey,
        String providerName) throws NoSuchProviderException,
        NoSuchAlgorithmException, InvalidKeyException {
        if (decryptKey == null) {
            throw new NullPointerException("decryptKey is null");
        }
        if (providerName == null) {
            throw new NullPointerException("provider is null");
        }
        Provider provider = Security.getProvider(providerName);
        if (provider == null) {
            throw new NoSuchProviderException("provider " +
                providerName + " not found");
        }
        return getKeySpecImpl(decryptKey, provider);
    }

    /**
     * Extract the enclosed PKCS8EncodedKeySpec object from the
     * encrypted data and return it.
     * @param decryptKey key used for decrypting the encrypted data.
     * @param provider the name of provider whose cipher implementation
     * will be used.
     * @return the PKCS8EncodedKeySpec object.
     * @exception NullPointerException if {@code decryptKey}
     * or {@code provider} is {@code null}.
     * @exception NoSuchAlgorithmException if cannot find appropriate
     * cipher to decrypt the encrypted data in {@code provider}.
     * @exception InvalidKeyException if {@code decryptKey}
     * cannot be used to decrypt the encrypted data or the decryption
     * result is not a valid PKCS8KeySpec.
     *
     * @since 1.5
     */
    public PKCS8EncodedKeySpec getKeySpec(Key decryptKey,
        Provider provider) throws NoSuchAlgorithmException,
        InvalidKeyException {
        if (decryptKey == null) {
            throw new NullPointerException("decryptKey is null");
        }
        if (provider == null) {
            throw new NullPointerException("provider is null");
        }
        return getKeySpecImpl(decryptKey, provider);
    }

    /**
     * Returns the ASN.1 encoding of this object.
     * @return the ASN.1 encoding. Returns a new array
     * each time this method is called.
     * @exception IOException if error occurs when constructing its
     * ASN.1 encoding.
     */
    public byte[] getEncoded() throws IOException {
        if (this.encoded == null) {
            DerOutputStream out = new DerOutputStream();
            DerOutputStream tmp = new DerOutputStream();

            // encode encryption algorithm
            if (algid != null) {
                algid.encode(tmp);
            } else {
                try {
                    // Let's hope params has been initialized by now.
                    AlgorithmId.get(params).encode(tmp);
                } catch (Exception e) {
                    throw new IOException("not initialized", e);
                }
            }

            // encode encrypted data
            tmp.putOctetString(encryptedData);

            // wrap everything into a SEQUENCE
            out.write(DerValue.tag_Sequence, tmp);
            return out.toByteArray();
        }
        return this.encoded.clone();
    }

    private static void checkTag(DerValue val, byte tag, String valName)
        throws IOException {
        if (val.getTag() != tag) {
            throw new IOException("invalid key encoding - wrong tag for " +
                                  valName);
        }
    }

    @SuppressWarnings("fallthrough")
    private static PKCS8EncodedKeySpec pkcs8EncodingToSpec(byte[] encodedKey)
        throws IOException {
        DerInputStream in = new DerInputStream(encodedKey);
        DerValue[] values = in.getSequence(3);

        switch (values.length) {
        case 4:
            checkTag(values[3], DerValue.TAG_CONTEXT, "attributes");
            /* fall through */
        case 3:
            checkTag(values[0], DerValue.tag_Integer, "version");
            String keyAlg = AlgorithmId.parse(values[1]).getName();
            checkTag(values[2], DerValue.tag_OctetString, "privateKey");
            return new PKCS8EncodedKeySpec(encodedKey, keyAlg);
        default:
            throw new IOException("invalid key encoding");
        }
    }
}

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

package javax.crypto;

import jdk.internal.javac.PreviewFeature;
import sun.security.jca.GetInstance;
import sun.security.jca.GetInstance.Instance;
import sun.security.util.Debug;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Iterator;
import java.util.Objects;

/**
 * This class provides the functionality of a Key Derivation Function (KDF),
 * which is a cryptographic algorithm for deriving additional keys from input
 * keying material (IKM) and (optionally) other data.
 * <p>
 * {@code KDF} objects are instantiated with the {@code getInstance} family of
 * methods.
 * <p>
 * The class has two derive methods, {@code deriveKey} and {@code deriveData}.
 * The {@code deriveKey} method accepts an algorithm name and returns a
 * {@code SecretKey} object with the specified algorithm. The {@code deriveData}
 * method returns a byte array of raw data.
 * <p>
 * API Usage Example:
 * {@snippet lang = java:
 *    KDF kdfHkdf = KDF.getInstance("HKDF-SHA256");
 *
 *    AlgorithmParameterSpec derivationSpec =
 *             HKDFParameterSpec.ofExtract()
 *                              .addIKM(ikm)
 *                              .addSalt(salt).thenExpand(info, 32);
 *
 *    SecretKey sKey = kdfHkdf.deriveKey("AES", derivationSpec);
 *}
 * <br>
 * <h2><a id="ConcurrentAccess">Concurrent Access</a></h2>
 * Unless otherwise documented by an implementation, the methods defined in this
 * class are not thread-safe. Multiple threads that need to access a single
 * object concurrently should synchronize amongst themselves and provide the
 * necessary locking. Multiple threads each manipulating separate objects need
 * not synchronize.
 * <br>
 * <h2><a id="DelayedProviderSelection">Delayed Provider Selection</a></h2>
 * If a provider is not specified when calling one of the {@code getInstance}
 * methods, the implementation delays the selection of the provider until the
 * {@code deriveKey} or {@code deriveData} method is called. This is called
 * <i>delayed provider selection</i>. The primary reason this is done is to
 * ensure that the selected provider can handle the key material that is passed
 * to those methods - for example, the key material may reside on a hardware
 * device that only a specific {@code KDF} provider can utilize. Once initiated,
 * the selection process traverses the list of registered security providers,
 * starting with the most preferred {@code Provider}. A new {@code KDF} object
 * encapsulating the {@code KDFSpi} implementation from the first provider that
 * supports the specified algorithm and optional parameters is returned.
 * <p>
 * If the {@code getProviderName} or {@code getParameters} method is called
 * before the {@code deriveKey} or {@code deriveData} methods, the first
 * provider supporting the {@code KDF} algorithm and optional
 * {@code KDFParameters} is chosen. This provider may not support the key
 * material that is subsequently passed to the {@code deriveKey} or
 * {@code deriveData} methods. Therefore, it is recommended not to call the
 * {@code getProviderName} or {@code getParameters} methods until after a key
 * derivation operation. Once a provider is selected, it cannot be changed.
 *
 * @see KDFParameters
 * @see SecretKey
 * @since 24
 */
@PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
public final class KDF {

    private static final Debug pdebug = Debug.getInstance("provider",
                                                          "Provider");
    private static final boolean skipDebug = Debug.isOn("engine=")
                                             && !Debug.isOn("kdf");

    private record Delegate(KDFSpi spi, Provider provider) {}

    //guarded by 'lock'
    private Delegate pairOfSpiAndProv;
    //guarded by 'lock'
    private Delegate firstPairOfSpiAndProv;

    // The name of the KDF algorithm.
    private final String algorithm;

    // Additional KDF configuration parameters
    private final KDFParameters kdfParameters;

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    // This lock provides mutual exclusion, preventing multiple threads from
    // concurrently initializing the same instance (delayed provider selection)
    // in a way which would corrupt the internal state.
    private final Object lock = new Object();


    // Instantiates a {@code KDF} object. This constructor is called when a
    // provider is supplied to {@code getInstance}.
    //
    // @param delegate the delegate
    // @param algorithm the algorithm
    // @param kdfParameters the parameters
    private KDF(Delegate delegate, String algorithm,
                KDFParameters kdfParameters) {
        this.pairOfSpiAndProv = delegate;
        this.algorithm = algorithm;
        // note that the parameters are being passed to the impl in getInstance
        this.kdfParameters = kdfParameters;
        serviceIterator = null;
    }

    // Instantiates a {@code KDF} object. This constructor is called when a
    // provider is not supplied to {@code getInstance}.
    //
    // @param firstPairOfSpiAndProv the delegate
    // @param t the service iterator
    // @param algorithm the algorithm
    // @param kdfParameters the algorithm parameters
    private KDF(Delegate firstPairOfSpiAndProv, Iterator<Service> t,
                String algorithm,
                KDFParameters kdfParameters) {
        this.firstPairOfSpiAndProv = firstPairOfSpiAndProv;
        serviceIterator = t;
        this.algorithm = algorithm;
        this.kdfParameters = kdfParameters;
    }

    /**
     * Returns the algorithm name of this {@code KDF} object.
     *
     * @return the algorithm name of this {@code KDF} object
     */
    public String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the name of the provider.
     *
     * @return the name of the provider
     *
     * @see <a href="#DelayedProviderSelection">Delayed Provider
     *         Selection</a>
     */
    public String getProviderName() {
        useFirstSpi();
        return pairOfSpiAndProv.provider().getName();
    }

    /**
     * Returns the {@code KDFParameters} used with this {@code KDF} object.
     * <p>
     * The returned parameters may be the same that were used to initialize this
     * {@code KDF} object, or may contain additional default or random parameter
     * values used by the underlying KDF algorithm. If the required parameters
     * were not supplied and can be generated by the {@code KDF} object, the
     * generated parameters are returned; otherwise {@code null} is returned.
     *
     * @return the parameters used with this {@code KDF} object, or
     *         {@code null}
     *
     * @see <a href="#DelayedProviderSelection">Delayed Provider
     *         Selection</a>
     */
    public KDFParameters getParameters() {
        useFirstSpi();
        return pairOfSpiAndProv.spi().engineGetParameters();
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if no {@code Provider} supports a {@code KDF} implementation for
     *         the specified algorithm
     * @throws NullPointerException
     *         if {@code algorithm} is {@code null}
     * @implNote The JDK Reference Implementation additionally uses the
     *         {@code jdk.security.provider.preferred}
     *         {@link Security#getProperty(String) Security} property to
     *         determine the preferred provider order for the specified
     *         algorithm. This may be different than the order of providers
     *         returned by
     *         {@link Security#getProviders() Security.getProviders()}.
     * @see <a href="#DelayedProviderSelection">Delayed Provider
     *         Selection</a>
     */
    public static KDF getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        try {
            return getInstance(algorithm, (KDFParameters) null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                    "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm from
     * the specified security provider. The specified provider must be
     * registered in the security provider list.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     * @param provider
     *         the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if the specified provider does not support the specified
     *         {@code KDF} algorithm
     * @throws NoSuchProviderException
     *         if the specified provider is not registered in the security
     *         provider list
     * @throws NullPointerException
     *         if {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        try {
            return getInstance(algorithm, null, provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                    "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm from
     * the specified security provider.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     * @param provider
     *         the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if the specified provider does not support the specified
     *         {@code KDF} algorithm
     * @throws NullPointerException
     *         if {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        try {
            return getInstance(algorithm, null, provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                    "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm and
     * is initialized with the specified parameters.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     * @param kdfParameters
     *         the {@code KDFParameters} used to configure the derivation
     *         algorithm or {@code null} if no parameters are provided
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if no {@code Provider} supports a {@code KDF} implementation for
     *         the specified algorithm
     * @throws InvalidAlgorithmParameterException
     *         if at least one {@code Provider} supports a {@code KDF}
     *         implementation for the specified algorithm but none of them
     *         support the specified parameters
     * @throws NullPointerException
     *         if {@code algorithm} is {@code null}
     * @implNote The JDK Reference Implementation additionally uses the
     *         {@code jdk.security.provider.preferred}
     *         {@link Security#getProperty(String) Security} property to
     *         determine the preferred provider order for the specified
     *         algorithm. This may be different than the order of providers
     *         returned by
     *         {@link Security#getProviders() Security.getProviders()}.
     * @see <a href="#DelayedProviderSelection">Delayed Provider
     *         Selection</a>
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters)
            throws NoSuchAlgorithmException,
                   InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        // make sure there is at least one service from a signed provider
        Iterator<Service> t = GetInstance.getServices("KDF", algorithm);
        InvalidAlgorithmParameterException lastException = null;
        while (t.hasNext()) {
            Service s = t.next();
            if (!JceSecurity.canUseProvider(s.getProvider())) {
                continue;
            }
            try {
                Object obj = s.newInstance(kdfParameters);
                if (!(obj instanceof KDFSpi spiObj)) {
                    lastException = new InvalidAlgorithmParameterException(
                            "No provider can be found that supports the "
                            + "specified algorithm and parameters");
                    if (!skipDebug && pdebug != null) {
                        pdebug.println(
                                "obj was not an instance of KDFSpi (should not "
                                + "happen)");
                    }
                    // continue to next iteration
                } else if (t.hasNext()) {
                    return new KDF(new Delegate(spiObj, s.getProvider()), t,
                                   algorithm, kdfParameters);
                } else { // no other choices, lock down provider
                    return new KDF(new Delegate(spiObj, s.getProvider()),
                                   algorithm, kdfParameters);
                }
            } catch (NoSuchAlgorithmException e) {
                lastException =
                        new InvalidAlgorithmParameterException(
                                "No provider can be found that supports the "
                                + "specified algorithm and parameters");
                if (!skipDebug && pdebug != null) {
                    pdebug.println(e.toString());
                }
                // continue to next iteration
            }
        }
        if (lastException != null) {
            throw lastException;
        } else {
            throw new NoSuchAlgorithmException(
                    "Algorithm " + algorithm + " not available");
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm from
     * the specified provider and is initialized with the specified parameters.
     * The specified provider must be registered in the security provider list.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     * @param kdfParameters
     *         the {@code KDFParameters} used to configure the derivation
     *         algorithm or {@code null} if no parameters are provided
     * @param provider
     *         the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if the specified provider does not support the specified
     *         {@code KDF} algorithm
     * @throws NoSuchProviderException
     *         if the specified provider is not registered in the security
     *         provider list
     * @throws InvalidAlgorithmParameterException
     *         if the specified provider does not support a {@code KDF}
     *         implementation for the specified algorithm and parameters
     * @throws NullPointerException
     *         if {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters,
                                  String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException,
                   InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        kdfParameters,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new NoSuchProviderException(msg);
            }
            return new KDF(new Delegate((KDFSpi) instance.impl,
                                        instance.provider), algorithm,
                           kdfParameters);

        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm from
     * the specified provider and is initialized with the specified parameters.
     *
     * @param algorithm
     *         the key derivation algorithm to use. See the {@code KDF} section
     *         in the <a href="{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *         Java Security Standard Algorithm Names Specification</a> for
     *         information about standard KDF algorithm names.
     * @param kdfParameters
     *         the {@code KDFParameters} used to configure the derivation
     *         algorithm or {@code null} if no parameters are provided
     * @param provider
     *         the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *         if the specified provider does not support the specified
     *         {@code KDF} algorithm
     * @throws InvalidAlgorithmParameterException
     *         if the specified provider does not support a {@code KDF}
     *         implementation for the specified algorithm and parameters
     * @throws NullPointerException
     *         if {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters,
                                  Provider provider)
            throws NoSuchAlgorithmException,
                   InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        kdfParameters,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new SecurityException(msg);
            }
            return new KDF(new Delegate((KDFSpi) instance.impl,
                                        instance.provider), algorithm,
                           kdfParameters);

        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    private static KDF handleException(NoSuchAlgorithmException e)
            throws NoSuchAlgorithmException,
                   InvalidAlgorithmParameterException {
        Throwable cause = e.getCause();
        if (cause instanceof InvalidAlgorithmParameterException) {
            throw (InvalidAlgorithmParameterException) cause;
        }
        throw e;
    }

    /**
     * Derives a key, returned as a {@code SecretKey} object.
     *
     * @param alg
     *         the algorithm of the resultant {@code SecretKey} object
     * @param derivationSpec
     *         the object describing the inputs to the derivation function
     *
     * @return the derived key
     *
     * @throws InvalidAlgorithmParameterException
     *         if the information contained within the {@code derivationSpec} is
     *         invalid or if the combination of {@code alg} and the
     *         {@code derivationSpec} results in something invalid
     * @throws NoSuchAlgorithmException
     *         if {@code alg} is empty or invalid
     * @throws NullPointerException
     *         if {@code alg} or {@code derivationSpec} is null
     */
    public SecretKey deriveKey(String alg,
                               AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {
        if (alg == null) {
            throw new NullPointerException(
                    "the algorithm for the SecretKey return value must not be"
                    + " null");
        }
        if (alg.isEmpty()) {
            throw new NoSuchAlgorithmException(
                    "the algorithm for the SecretKey return value must not be "
                    + "empty");
        }
        Objects.requireNonNull(derivationSpec);
        if (delegateAndSpiAreInitialized(pairOfSpiAndProv)) {
            return pairOfSpiAndProv.spi().engineDeriveKey(alg, derivationSpec);
        } else {
            return (SecretKey) chooseProvider(alg, derivationSpec);
        }
    }

    /**
     * Derives a key, returns raw data as a byte array.
     *
     * @param derivationSpec
     *         the object describing the inputs to the derivation function
     *
     * @return the derived key in its raw bytes
     *
     * @throws InvalidAlgorithmParameterException
     *         if the information contained within the {@code derivationSpec} is
     *         invalid
     * @throws UnsupportedOperationException
     *         if the derived keying material is not extractable
     * @throws NullPointerException
     *         if {@code derivationSpec} is null
     */
    public byte[] deriveData(AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {

        Objects.requireNonNull(derivationSpec);
        if (delegateAndSpiAreInitialized(pairOfSpiAndProv)) {
            return pairOfSpiAndProv.spi().engineDeriveData(derivationSpec);
        } else {
            try {
                return (byte[]) chooseProvider(null, derivationSpec);
            } catch (NoSuchAlgorithmException e) {
                // this will never be thrown in the deriveData case
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Use the firstSpi as the chosen KDFSpi and set the fields accordingly
     */
    private void useFirstSpi() {
        if (delegateAndSpiAreInitialized(pairOfSpiAndProv))
            return;

        synchronized (lock) {
            if (delegateIsNullOrSpiIsNull(pairOfSpiAndProv)) {
                pairOfSpiAndProv = firstPairOfSpiAndProv;
                // not needed any more
                firstPairOfSpiAndProv = new Delegate(null, null);
                serviceIterator = null;
            }
        }
    }

    /**
     * Selects the provider which supports the passed {@code algorithm} and
     * {@code derivationSpec} values, and assigns the global spi and provider
     * variables if they have not been assigned yet.
     * <p>
     * If the spi has already been set, it will just return the result.
     */
    private Object chooseProvider(String algorithm,
                                  AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {

        boolean isDeriveData = (algorithm == null);

        synchronized (lock) {
            if (delegateAndSpiAreInitialized(pairOfSpiAndProv)) {
                return (isDeriveData) ? pairOfSpiAndProv.spi().engineDeriveData(
                        derivationSpec) : pairOfSpiAndProv.spi()
                                                          .engineDeriveKey(
                                                                  algorithm,
                                                                  derivationSpec);
            }

            Exception lastException = null;
            while ((delegateAndSpiAreInitialized(firstPairOfSpiAndProv))
                   || serviceIterator.hasNext()) {
                KDFSpi currSpi;
                Provider currProv;
                if (delegateAndSpiAreInitialized(firstPairOfSpiAndProv)) {
                    currSpi = firstPairOfSpiAndProv.spi();
                    currProv = firstPairOfSpiAndProv.provider();
                    firstPairOfSpiAndProv = new Delegate(null, null);
                } else {
                    Service s = serviceIterator.next();
                    currProv = s.getProvider();
                    if (!JceSecurity.canUseProvider(currProv)) {
                        continue;
                    }
                    try {
                        Object obj = s.newInstance(kdfParameters);
                        if (!(obj instanceof KDFSpi)) {
                            continue;
                        }
                        currSpi = (KDFSpi) obj;
                    } catch (Exception e) {
                        // continue to the next provider
                        continue;
                    }
                }

                try {
                    Object result = (isDeriveData) ? currSpi.engineDeriveData(
                            derivationSpec) : currSpi.engineDeriveKey(
                            algorithm, derivationSpec);
                    // found a working KDFSpi
                    this.pairOfSpiAndProv = new Delegate(currSpi, currProv);
                    // not looking further
                    serviceIterator = null;
                    return result;
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if (lastException instanceof InvalidAlgorithmParameterException) {
                throw (InvalidAlgorithmParameterException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
            }
        }
        throw new InvalidAlgorithmParameterException(
                "No installed provider supports the " +
                ((isDeriveData) ? "deriveData" : "deriveKey")
                + " method with these parameters");
    }

    boolean delegateAndSpiAreInitialized(Delegate delegate) {
        return (delegate != null && delegate.spi() != null);
    }

    boolean delegateIsNullOrSpiIsNull(Delegate delegate) {
        return (delegate == null || delegate.spi() == null);
    }
}
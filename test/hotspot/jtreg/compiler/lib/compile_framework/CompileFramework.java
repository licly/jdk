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

package compiler.lib.compile_framework;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public class CompileFramework {
    private static final int COMPILE_TIMEOUT = 60;
    private static final boolean VERBOSE = Boolean.getBoolean("CompileFrameworkVerbose");

    private List<SourceCode> sourceCodes = new ArrayList<SourceCode>();
    private final Path sourceDir = getTempDir("compile-framework-sources-");
    private final Path classesDir = getTempDir("compile-framework-classes-");
    private URLClassLoader classLoader;

    public String getClassPathOfCompiledClasses() {
        return System.getProperty("java.class.path") +
               File.pathSeparator +
               classesDir.toAbsolutePath().toString();
    }

    public void add(SourceCode sourceCode) {
        sourceCodes.add(sourceCode);
    }

    public String sourceCodesAsString() {
        StringBuilder builder = new StringBuilder();
        for (SourceCode sourceCode : sourceCodes) {
            builder.append("SourceCode: ").append(sourceCode.filePathName()).append(System.lineSeparator());
            builder.append(sourceCode.code).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static void println(String s) {
        if (VERBOSE) {
            System.out.println(s);
        }
    }

    public void compile() {
        if (classLoader != null) {
            throw new CompileFrameworkException("Cannot compile twice!");
        }

        println("------------------ CompileFramework: SourceCode -------------------");
        println(sourceCodesAsString());

        List<SourceCode> javaSources = new ArrayList<SourceCode>();
        List<SourceCode> jasmSources = new ArrayList<SourceCode>();
        for (SourceCode sourceCode : sourceCodes) {
            switch (sourceCode.kind) {
                case SourceCode.Kind.JASM -> { jasmSources.add(sourceCode);  }
                case SourceCode.Kind.JAVA -> { javaSources.add(sourceCode);  }
            }
        }

        System.out.println("------------------ CompileFramework: Compilation ------------------");
        System.out.println("Source directory: " + sourceDir.toString());
        System.out.println("Classes directory: " + classesDir.toString());

        compileJasmSources(jasmSources);
        compileJavaSources(javaSources);
        setUpClassLoader();
    }

    private static Path getTempDir(String prefix) {
        try {
            return Files.createTempDirectory(Paths.get("."), prefix);
        } catch (Exception e) {
            throw new InternalCompileFrameworkException("Could not set up temporary directory", e);
        }
    }

    private void compileJasmSources(List<SourceCode> jasmSources) {
        if (jasmSources.size() == 0) {
            println("No jasm sources to compile.");
            return;
        }
        println("Compiling jasm sources: " + jasmSources.size());

        List<Path> jasmFilePaths = writeSourcesToFile(jasmSources);
        compileJasmFiles(jasmFilePaths);
        println("Jasm sources compiled.");
    }

    private void compileJasmFiles(List<Path> paths) {
        // Compile JASM files with asmtools.jar, shipped with jtreg.
        List<String> command = new ArrayList<>();

        command.add("%s/bin/java".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(getAsmToolsPath());
        command.add("org.openjdk.asmtools.jasm.Main");
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        executeCompileCommand(command);
    }

    private static String[] getClassPaths() {
        String separator = File.pathSeparator;
        return System.getProperty("java.class.path").split(separator);
    }

    private static String getAsmToolsPath() {
        for (String path : getClassPaths()) {
            if (path.endsWith("jtreg.jar")) {
                File jtreg = new File(path);
                File dir = jtreg.getAbsoluteFile().getParentFile();
                File asmtools = new File(dir, "asmtools.jar");
                if (!asmtools.exists()) {
                    throw new InternalCompileFrameworkException("Found jtreg.jar in classpath, but could not find asmtools.jar");
                }
                return asmtools.getAbsolutePath();
            }
        }
        throw new InternalCompileFrameworkException("Could not find asmtools because could not find jtreg.jar in classpath");
    }

    private void compileJavaSources(List<SourceCode> javaSources) {
        if (javaSources.size() == 0) {
            println("No java sources to compile.");
            return;
        }
        println("Compiling Java sources: " + javaSources.size());

        List<Path> javaFilePaths = writeSourcesToFile(javaSources);
        compileJavaFiles(javaFilePaths);
        println("Java sources compiled.");
    }

    private void compileJavaFiles(List<Path> paths) {
        // Compile JAVA files with javac, in the "compile.jdk".
        List<String> command = new ArrayList<>();

        command.add("%s/bin/javac".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(getClassPathOfCompiledClasses());
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        executeCompileCommand(command);
    }

    private List<Path> writeSourcesToFile(List<SourceCode> sources) {
        List<Path> storedFiles = new ArrayList<Path>();
        for (SourceCode sourceCode : sources) {
            Path path = sourceDir.resolve(sourceCode.filePathName());
            writeCodeToFile(sourceCode.code, path);
            storedFiles.add(path);
        }
        return storedFiles;
    }

    private static void writeCodeToFile(String code, Path path) {
        println("File: " + path.toString());

        // Ensure directory of the file exists.
        Path dir = path.getParent();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not create directory: " + dir.toString(), e);
        }

        // Write to file.
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(code);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not write file: " + path.toString(), e);
        }
    }

    private static void executeCompileCommand(List<String> command) {
        println("Compile command: " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(COMPILE_TIMEOUT, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                System.out.println("Timeout: compile command: " + String.join(" ", command));
                throw new InternalCompileFrameworkException("Process timeout: compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new InternalCompileFrameworkException("IOException during compilation", e);
        } catch (InterruptedException e) {
            throw new CompileFrameworkException("InterruptedException during compilation", e);
        }

        if (exitCode != 0 || !output.equals("")) {
            System.out.println("Compilation failed.");
            System.out.println("Exit code: " + exitCode);
            System.out.println("Output: '" + output + "'");
            throw new CompileFrameworkException("Compilation failed.");
        }
    }

    private void setUpClassLoader() {
        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();

        try {
            // Classpath for all included classes (e.g. IR Framework).
            // Get all class paths, convert to urls.
            List<URL> urls = new ArrayList<URL>();
            for (String path : getClassPaths()) {
                urls.add(new File(path).toURI().toURL());
            }
            // And add in the compiled classes from this instance of CompileFramework.
            urls.add(new File(classesDir.toString()).toURI().toURL());
            classLoader = URLClassLoader.newInstance(urls.toArray(URL[]::new), sysLoader);
        } catch (IOException e) {
            throw new CompileFrameworkException("IOException while creating ClassLoader", e);
        }
    }

    public Class getClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new CompileFrameworkException("Class not found:", e);
        }
    }

    public Object invoke(String className, String methodName, Object[] args) {
        Class c = getClass(className);

        Method[] methods = c.getDeclaredMethods();

        Method method = null;

        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                if (method != null) {
                  throw new CompileFrameworkException("Method name \"" + methodName + "\" not unique in class \n" + className + "\".");
                }
                method = m;
            }
        }

        if (method == null) {
            throw new CompileFrameworkException("Method \"" + methodName + "\" not found in class \n" + className + "\".");
        }

        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new CompileFrameworkException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new CompileFrameworkException("Invocation target:", e);
        }
    }
}


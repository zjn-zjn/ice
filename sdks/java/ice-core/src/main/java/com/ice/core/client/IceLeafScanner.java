package com.ice.core.client;


import com.ice.core.annotation.IceIgnore;
import com.ice.core.annotation.IceNode;
import com.ice.core.base.BaseLeaf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


public class IceLeafScanner {

    /**
     * 别名到真实类名的映射表。
     * 用于多语言兼容：当配置中的 confName 是其他语言的类名（如 Go 的 "score_flow"），
     * 通过此映射表可以找到对应的 Java 类名。
     */
    private static final Map<String, String> classAliasMap = new ConcurrentHashMap<>();

    private final String packageName;
    //packageName with dot, empty when packageName empty
    private final String packageNameWithDot;
    //in file
    private final String packageDirName;
    //in jar
    private final String packagePath;
    //result
    private final Set<Class<?>> classes = new HashSet<>();

    public static Set<Class<?>> scanPackage(String packageName) throws IOException {
        return new IceLeafScanner(packageName).scan();
    }

    /**
     * 根据配置中的 confName 获取真实的 Java 类名。
     * 如果 confName 是别名，返回映射的真实类名；否则返回原值。
     */
    public static String resolveClassName(String confName) {
        if (confName == null || confName.isEmpty()) {
            return confName;
        }
        return classAliasMap.getOrDefault(confName, confName);
    }

    /**
     * 注册类名别名。
     */
    public static void registerAlias(String alias, String className) {
        if (alias != null && !alias.isEmpty() && className != null && !className.isEmpty()) {
            classAliasMap.put(alias, className);
        }
    }

    /**
     * 获取别名映射表（只读）。
     */
    public static Map<String, String> getClassAliasMap() {
        return classAliasMap;
    }

    public IceLeafScanner(String packageName) {
        packageName = packageName == null ? "" : packageName;
        this.packageName = packageName;
        this.packageNameWithDot = packageName.isEmpty() ? "" : packageName + ".";
        this.packageDirName = packageName.replace('.', File.separatorChar);
        this.packagePath = packageName.replace('.', '/');
    }

    public Set<Class<?>> scan() throws IOException {
        Enumeration<URL> enumeration = getResourceEnumeration(this.packagePath);
        while (enumeration.hasMoreElements()) {
            URL url = enumeration.nextElement();
            switch (url.getProtocol()) {
                case "file":
                    scanFile(new File(decode(url.getFile(), StandardCharsets.UTF_8, true)), null);
                    break;
                case "jar":
                    scanJar(getJarFile(url));
                    break;
            }
        }
        return this.classes;
    }

    public static JarFile getJarFile(URL url) throws IOException {
        JarURLConnection urlConnection = (JarURLConnection) url.openConnection();
        return urlConnection.getJarFile();
    }

    public static Enumeration<URL> getResourceEnumeration(String resource) throws IOException {
        final Enumeration<URL> resources;
        resources = getClassLoader().getResources(resource);
        return resources;
    }

    public static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            //bypass inspection
            return AccessController.doPrivileged(
                    (PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());
        }
    }

    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            //bypass inspection
            return AccessController.doPrivileged(
                    (PrivilegedAction<ClassLoader>) ClassLoader::getSystemClassLoader);
        }
    }

    //first get current thread loader
    //then get current class loader
    //finally get system loader
    public static ClassLoader getClassLoader() {
        ClassLoader classLoader = getContextClassLoader();
        if (classLoader == null) {
            classLoader = IceLeafScanner.class.getClassLoader();
            if (null == classLoader) {
                classLoader = getSystemClassLoader();
            }
        }
        return classLoader;
    }

    public static String decode(String str, Charset charset, boolean isPlusToSpace) {
        if (null == charset) {
            return str;
        }
        return new String(decode(str.getBytes(charset), isPlusToSpace), charset);
    }

    public static byte[] decode(byte[] bytes, boolean isPlusToSpace) {
        if (bytes == null) {
            return null;
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes.length);
        int b;
        for (int i = 0; i < bytes.length; i++) {
            b = bytes[i];
            if (b == '+') {
                buffer.write(isPlusToSpace ? ' ' : b);
            } else if (b == '%') {
                if (i + 1 < bytes.length) {
                    final int u = Character.digit(bytes[i + 1], 16);
                    if (u >= 0 && i + 2 < bytes.length) {
                        final int l = Character.digit(bytes[i + 2], 16);
                        if (l >= 0) {
                            buffer.write((char) ((u << 4) + l));
                            i += 2;
                            continue;
                        }
                    }
                }
                buffer.write(b);
            } else {
                buffer.write(b);
            }
        }
        return buffer.toByteArray();
    }

    public static String[] getJavaClassPaths() {
        return System.getProperty("java.class.path").split(System.getProperty("path.separator"));
    }

    private void scanFile(File file, String rootDir) throws IOException {
        if (file.isFile()) {
            final String fileName = file.getAbsolutePath();
            if (fileName.endsWith(".class")) {
                final String className = fileName
                        //sub end six char of '.class'
                        .substring(rootDir.length(), fileName.length() - 6)
                        //replace to .
                        .replace(File.separatorChar, '.');
                addIfLeaf(className);
            } else if (fileName.endsWith(".jar")) {
                scanJar(new JarFile(file));
            }
        } else if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (null != files) {
                for (File subFile : files) {
                    scanFile(subFile, (null == rootDir) ? subPathBeforePackage(file) : rootDir);
                }
            }
        }
    }

    private void scanJar(JarFile jar) {
        String name;
        Enumeration<JarEntry> jarEntrys = jar.entries();
        while (jarEntrys.hasMoreElements()) {
            JarEntry entry = jarEntrys.nextElement();
            name = removePrefix(entry.getName(), "/");
            if ((packagePath == null || packagePath.isEmpty()) || name.startsWith(this.packagePath)) {
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    final String className = name
                            .substring(0, name.length() - 6)
                            .replace('/', '.');
                    addIfLeaf(loadClass(className));
                }
            }
        }
    }

    public static String removePrefix(String str, String prefix) {
        if (str.startsWith(prefix)) {
            return str.substring(prefix.length(), str.length() - 1);
        }
        return str;
    }

    private Class<?> loadClass(String className) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className, false, IceLeafScanner.getClassLoader());
        } catch (NoClassDefFoundError | ClassNotFoundException | UnsupportedClassVersionError e) {
            // ignore
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return clazz;
    }


    private void addIfLeaf(String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        int classLen = className.length();
        int packageLen = this.packageName.length();
        if (classLen == packageLen) {
            //the package name is likely to be the class name
            if (className.equals(this.packageName)) {
                addIfLeaf(loadClass(className));
            }
        } else if (classLen > packageLen) {
            //check the class start with the package name
            if ("".equals(this.packageName) || className.startsWith(this.packageNameWithDot)) {
                addIfLeaf(loadClass(className));
            }
        }
    }

    //just leaf node & not abstract & not with @IceIgnore
    private void addIfLeaf(Class<?> clazz) {
        if (null != clazz) {
            if (!clazz.isAnnotationPresent(IceIgnore.class) && BaseLeaf.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                this.classes.add(clazz);
                // 注册别名映射
                IceNode nodeAnnotation = clazz.getAnnotation(IceNode.class);
                if (nodeAnnotation != null) {
                    String[] aliases = nodeAnnotation.alias();
                    if (aliases != null && aliases.length > 0) {
                        String className = clazz.getName();
                        for (String alias : aliases) {
                            if (alias != null && !alias.isEmpty()) {
                                registerAlias(alias, className);
                            }
                        }
                    }
                }
            }
        }
    }

    private String subPathBeforePackage(File file) {
        String filePath = file.getAbsolutePath();
        if (this.packageDirName != null && !this.packageDirName.isEmpty()) {
            filePath = subBefore(filePath, this.packageDirName, true);
        }
        if (filePath.endsWith(File.separator)) {
            return filePath;
        } else {
            return filePath + File.separator;
        }
    }

    public static String subBefore(String string, String separator, boolean isLastSeparator) {
        if (string == null || string.isEmpty() || separator == null) {
            return string;
        }

        if (separator.isEmpty()) {
            return "";
        }
        final int pos = isLastSeparator ? string.lastIndexOf(separator) : string.indexOf(separator);
        if (-1 == pos) {
            return string;
        }
        if (0 == pos) {
            return "";
        }
        return string.substring(0, pos);
    }
}

package org.hotswap.agent.config;

import java.security.ProtectionDomain;

/**
 * @author yudong
 * @date 2022/8/20
 */
public class TransformHolder {
    private final ClassLoader classLoader;
    private final String className;
    private final Class<?> redefiningClass;
    private final ProtectionDomain protectionDomain;
    private final byte[] bytes;

    public TransformHolder(ClassLoader classLoader, String className, Class<?> redefiningClass, ProtectionDomain protectionDomain, byte[] bytes) {
        this.classLoader = classLoader;
        this.className = className;
        this.redefiningClass = redefiningClass;
        this.protectionDomain = protectionDomain;
        this.bytes = bytes;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public String getClassName() {
        return className;
    }

    public Class<?> getRedefiningClass() {
        return redefiningClass;
    }

    public ProtectionDomain getProtectionDomain() {
        return protectionDomain;
    }

    public byte[] getBytes() {
        return bytes;
    }
}

package com.lagradost.runtime.security

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.zip.ZipFile

/**
 * Static check run on every plugin jar before it is loaded (after dex2jar and
 * PluginBytecodeTransformer, so Runtime/System exit/exec/load calls should
 * already point at the stubs; any left over are rejected here).
 *
 * The rules target what a stream-provider plugin has no reason to touch:
 * spawning processes, native code, defining classes, JVM-wide state (env
 * vars, system properties, stdio) and NIO file access. They were checked
 * against the 467 plugins of the default repos (Nov 2026): none uses any of it.
 *
 * Deliberately NOT blocked, because real plugins use them: java.io.File
 * (TurkSinema's 108 plugins clean their cache dirs), URL.openConnection /
 * HttpURLConnection, and reflection (11 plugins, incl. Method.invoke). Those
 * are also why this is not a security boundary: reflection reaches anything
 * the static rules forbid. Treat plugins as trusted code and rely on the
 * container (non-root, read-only rootfs, dropped capabilities) to contain them.
 */
object PluginSecurityVerifier {

    private val runtimeDenied = setOf("exec", "load", "loadLibrary", "exit", "halt")
    private val systemDenied = setOf(
        "exit", "load", "loadLibrary", "setSecurityManager",
        "getenv", "setProperty", "clearProperty", "setProperties",
        "setIn", "setOut", "setErr",
    )

    /** Why a call is not allowed, or null when it is. [owner] is an internal name (java/lang/Runtime). */
    fun violation(owner: String, name: String, desc: String): String? = when {
        owner == "java/lang/ProcessBuilder" || owner == "java/lang/Process" ||
            owner == "java/lang/ProcessHandle" || owner.startsWith("java/lang/ProcessHandle$") -> "process execution"
        owner == "java/lang/Runtime" && name in runtimeDenied -> "Runtime.$name"
        owner == "java/lang/System" && name in systemDenied -> "System.$name"
        owner.startsWith("java/nio/file/") || owner.startsWith("java/nio/channels/") ||
            owner == "java/io/RandomAccessFile" -> "NIO / random-access file I/O"
        owner == "java/net/URLClassLoader" ||
            (owner == "java/lang/ClassLoader" && name.startsWith("defineClass")) ||
            (owner == "java/lang/invoke/MethodHandles\$Lookup" && name.startsWith("define")) -> "class definition"
        owner.startsWith("java/lang/invoke/") -> "method handles"
        owner.startsWith("javax/script/") -> "script engines"
        owner.startsWith("sun/") || owner.startsWith("jdk/internal/") || owner.startsWith("com/sun/") -> "JDK internals"
        else -> null
    }

    @Throws(SecurityException::class)
    fun verifyJar(jarFile: File) {
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) continue
                val classNode = ClassNode()
                zip.getInputStream(entry).use { ClassReader(it).accept(classNode, ClassReader.SKIP_FRAMES) }
                for (method in classNode.methods) {
                    for (insn in method.instructions) {
                        if (insn !is MethodInsnNode) continue
                        val why = violation(insn.owner, insn.name, insn.desc) ?: continue
                        throw SecurityException(
                            "Security Sandbox: ${jarFile.name} rejected: ${classNode.name}.${method.name} calls " +
                                "${insn.owner}.${insn.name} ($why)"
                        )
                    }
                }
            }
        }
    }
}

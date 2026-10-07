package com.lagradost.runtime.loader

import org.objectweb.asm.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PluginBytecodeTransformer {

    private fun AppLoggerWarn(msg: String) {
        runCatching { com.lagradost.common.logging.AppLogger.i("BytecodeTransformer: $msg") }
    }

    fun transform(jarFile: File) {
        val tempFile = File(jarFile.absolutePath + ".tmp")
        ZipInputStream(FileInputStream(jarFile)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)

                    val bytes = zis.readBytes()
                    if (entry.name.endsWith(".class")) {
                        val reader = ClassReader(bytes)
                        // COMPUTE_FRAMES: dex2jar emits wrong/missing stack map
                        // frames for some methods ("Expecting a stackmap frame
                        // at branch target" VerifyError on AnimeUnity). Rebuild
                        // them; getCommonSuperClass must not classload plugin
                        // types, so fall back to Object on any lookup failure
                        // (over-conservative merges always verify).
                        val writer = object : ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                            override fun getCommonSuperClass(type1: String, type2: String): String {
                                return try {
                                    super.getCommonSuperClass(type1, type2)
                                } catch (t: Throwable) {
                                    "java/lang/Object"
                                }
                            }
                        }

                        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {

                            override fun visitMethod(
                                access: Int,
                                name: String,
                                descriptor: String?,
                                signature: String?,
                                exceptions: Array<out String>?,
                            ): MethodVisitor {
                                val mv = super.visitMethod(access, fixMethodName(name), descriptor, signature, exceptions)
                                return object : MethodVisitor(Opcodes.ASM9, mv) {
                                    override fun visitTryCatchBlock(
                                        start: Label?,
                                        end: Label?,
                                        handler: Label?,
                                        type: String?,
                                    ) {
                                        // dex2jar sometimes emits a non-Throwable
                                        // catch type -> VerifyError. Widening every
                                        // catch to Throwable always verifies and
                                        // only broadens the catch.
                                        super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable")
                                    }
                                    override fun visitMethodInsn(
                                        opcode: Int,
                                        owner: String,
                                        methodName: String,
                                        descriptor: String?,
                                        isInterface: Boolean,
                                    ) {
                                        var newOpcode = opcode
                                        var newOwner = owner
                                        var newDesc = descriptor
                                        var methodName = methodName

                                        // dex2jar mangles JVM-illegal Kotlin value-class name mangling ('-' -> '_'),
                                        // while modern Kotlin emits the raw dashed name into class files.
                                        if (owner.startsWith("kotlinx/coroutines/")) {
                                            val m = Regex("^(.+?)_([A-Za-z0-9]{7,8})$").find(methodName)
                                            if (m != null) {
                                                methodName = m.groupValues[1] + "-" + m.groupValues[2]
                                            } else if (methodName == "runBlockingK") {
                                                methodName = "runBlocking"
                                            }
                                        }

                                        if (owner == "java/lang/Runtime" && (methodName == "exec" || methodName == "loadLibrary" || methodName == "load" || methodName == "exit" || methodName == "halt")) {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                            newDesc = descriptor?.replace("(", "(Ljava/lang/Runtime;")
                                        } else if (owner == "java/lang/System" && (methodName == "exit" || methodName == "loadLibrary" || methodName == "load" || methodName == "setSecurityManager")) {
                                            newOwner = "com/lagradost/runtime/loader/stubs/SystemStub"
                                        }

                                        super.visitMethodInsn(newOpcode, newOwner, fixMethodName(methodName), newDesc, isInterface)
                                    }
                                }
                            }
                        }

                        try {
                            reader.accept(visitor, 0)
                            zos.write(writer.toByteArray())
                        } catch (t: Throwable) {
                            // transformation (incl. frame recompute) failed —
                            // keep the raw dex2jar output for this class
                            AppLoggerWarn("frame recompute failed for ${entry.name}: $t")
                            zos.write(bytes)
                        }
                    } else {
                        zos.write(bytes)
                    }

                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        jarFile.delete()
        tempFile.renameTo(jarFile)
    }

    private fun fixMethodName(name: String): String {
        return when (name) {
            "constructor_impl" -> "constructor-impl"
            "box_impl" -> "box-impl"
            "unbox_impl" -> "unbox-impl"
            "isSuccess_impl" -> "isSuccess-impl"
            "isFailure_impl" -> "isFailure-impl"
            "getOrNull_impl" -> "getOrNull-impl"
            "exceptionOrNull_impl" -> "exceptionOrNull-impl"
            else -> name
        }
    }
}

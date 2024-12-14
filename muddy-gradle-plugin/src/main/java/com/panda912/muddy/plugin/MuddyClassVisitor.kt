package com.panda912.muddy.plugin

import com.panda912.muddy.ObfuscatedString
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

internal const val OBFUSCATED_STRING_CLASS = "com/panda912/muddy/ObfuscatedString"

class MuddyClassVisitor(private val nextClassVisitor: ClassVisitor) : ClassNode(Opcodes.ASM9) {
  override fun visitEnd() {
    super.visitEnd()

    modifyBytecode()

    accept(nextClassVisitor)
  }

  private fun modifyBytecode() {
    for (method in methods) {
      for (insn in method.instructions.toArray()) {
        if (insn is LdcInsnNode && insn.cst is String) {
          val originString = insn.cst as String
          val insnList = InsnList()
          deobfuscate(originString, insnList)
          if (insnList.size() != 0) {
            method.instructions.insert(insn, insnList)
            method.instructions.remove(insn)
          }
        }
      }
    }

    val constFields: List<FieldNode> = fields.filter {
      it.desc == "Ljava/lang/String;" && !(it.value as? String).isNullOrEmpty() &&
              (it.access == Opcodes.ACC_STATIC + Opcodes.ACC_FINAL ||
                      it.access == Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL ||
                      it.access == Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL ||
                      it.access == Opcodes.ACC_PROTECTED + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL)
    }
    if (constFields.isNotEmpty()) {
      val clinit = methods.find { it.name == "<clinit>" }
        ?: MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)

      val insnList = InsnList()
      for (field in constFields) {
        deobfuscate(field.value as String, insnList)
        if (insnList.size() != 0) {
          insnList.add(FieldInsnNode(Opcodes.PUTSTATIC, name, field.name, "Ljava/lang/String;"))
          field.value = null
        }
      }
      insnList.add(clinit.instructions)
      if (insnList.last.type != AbstractInsnNode.INSN || insnList.last.opcode != Opcodes.RETURN) {
        insnList.add(InsnNode(Opcodes.RETURN))
      }
      clinit.instructions = insnList
      methods.removeIf { it.name == "<clinit>" }
      methods.add(clinit)
    }
  }

  private fun deobfuscate(originString: String, insnList: InsnList) {
    try {
      val obfuscateArr = ObfuscatedString.array(originString)
      if (obfuscateArr.isEmpty() || obfuscateArr.size > 32767) {
        return
      }
      insnList.add(TypeInsnNode(Opcodes.NEW, OBFUSCATED_STRING_CLASS))
      insnList.add(InsnNode(Opcodes.DUP))
      insnList.add(
        when (obfuscateArr.size) {
          1 -> InsnNode(Opcodes.ICONST_1)
          2 -> InsnNode(Opcodes.ICONST_2)
          3 -> InsnNode(Opcodes.ICONST_3)
          4 -> InsnNode(Opcodes.ICONST_4)
          5 -> InsnNode(Opcodes.ICONST_5)
          in 6..127 -> IntInsnNode(Opcodes.BIPUSH, obfuscateArr.size)
          else -> IntInsnNode(Opcodes.SIPUSH, obfuscateArr.size)
        }
      )
      insnList.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG))
      insnList.add(InsnNode(Opcodes.DUP))
      for (i in obfuscateArr.indices) {
        insnList.add(
          when (i) {
            0 -> InsnNode(Opcodes.ICONST_0)
            1 -> InsnNode(Opcodes.ICONST_1)
            2 -> InsnNode(Opcodes.ICONST_2)
            3 -> InsnNode(Opcodes.ICONST_3)
            4 -> InsnNode(Opcodes.ICONST_4)
            5 -> InsnNode(Opcodes.ICONST_5)
            in 6..127 -> IntInsnNode(Opcodes.BIPUSH, i)
            else -> IntInsnNode(Opcodes.SIPUSH, i)
          }
        )
        insnList.add(LdcInsnNode(obfuscateArr[i]))
        insnList.add(InsnNode(Opcodes.LASTORE))
        if (i != obfuscateArr.size - 1) {
          insnList.add(InsnNode(Opcodes.DUP))
        }
      }
      insnList.add(
        MethodInsnNode(
          Opcodes.INVOKESPECIAL,
          OBFUSCATED_STRING_CLASS,
          "<init>",
          "([J)V"
        )
      )
      insnList.add(
        MethodInsnNode(
          Opcodes.INVOKEVIRTUAL,
          OBFUSCATED_STRING_CLASS,
          "toString",
          "()Ljava/lang/String;"
        )
      )
    } catch (_: IllegalArgumentException) {
    }
  }

}
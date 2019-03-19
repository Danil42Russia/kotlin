/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.coroutines

import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.inline.coroutines.FOR_INLINE_SUFFIX
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.JVMConstructorCallNormalizationMode
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.MethodNode

// For named suspend function we generate two methods:
// 1) to use as noinline function, which have state machine
// 2) to use from inliner: private one without state machine
class SuspendInlineFunctionGenerationStrategy(
    state: GenerationState,
    originalSuspendDescriptor: FunctionDescriptor,
    declaration: KtFunction,
    containingClassInternalName: String,
    constructorCallNormalizationMode: JVMConstructorCallNormalizationMode,
    private val codegen: FunctionCodegen
) : SuspendFunctionGenerationStrategy(
    state,
    originalSuspendDescriptor,
    declaration,
    containingClassInternalName,
    constructorCallNormalizationMode
) {
    private val defaultStrategy = FunctionGenerationStrategy.FunctionDefault(state, declaration)

    override fun wrapMethodVisitor(mv: MethodVisitor, access: Int, name: String, desc: String): MethodVisitor {
        if (access and Opcodes.ACC_ABSTRACT != 0) return mv

        return MethodNodeCopyingMethodVisitor(
            super.wrapMethodVisitor(mv, access, name, desc), access, name, desc, null, null, codegen,
            classBuilder = null, keepAccess = false
        )
    }

    override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
        super.doGenerateBody(codegen, signature)
        defaultStrategy.doGenerateBody(codegen, signature)
    }
}

class MethodNodeCopyingMethodVisitor(
    delegate: MethodVisitor,
    private val access: Int,
    private val name: String,
    private val desc: String,
    private val signature: String?,
    private val exceptions: Array<out String>?,
    private val codegen: FunctionCodegen?,
    private val classBuilder: ClassBuilder?,
    private val keepAccess: Boolean
) : TransformationMethodVisitor(
    delegate,
    if (keepAccess) access else calculateAccessForInline(access),
    "$name$FOR_INLINE_SUFFIX",
    desc,
    signature,
    exceptions
) {
    override fun performTransformations(methodNode: MethodNode) {
        val newMethodNode = codegen?.newMethod(
            JvmDeclarationOrigin.NO_ORIGIN, if (keepAccess) access else calculateAccessForInline(access),
            "$name$FOR_INLINE_SUFFIX", desc, signature, exceptions
        ) ?: classBuilder.sure {
            "Either codegenData or inlinerData shall be not null"
        }.newMethod(
            JvmDeclarationOrigin.NO_ORIGIN, if (keepAccess) access else calculateAccessForInline(access),
            "$name$FOR_INLINE_SUFFIX", desc, signature, exceptions
        )
        methodNode.instructions.resetLabels()
        methodNode.accept(newMethodNode)
    }

    companion object {
        private fun calculateAccessForInline(access: Int): Int {
            var accessForInline = access
            if (accessForInline and Opcodes.ACC_PUBLIC != 0) {
                accessForInline = accessForInline xor Opcodes.ACC_PUBLIC
            }
            if (accessForInline and Opcodes.ACC_PROTECTED != 0) {
                accessForInline = accessForInline xor Opcodes.ACC_PROTECTED
            }
            return accessForInline or Opcodes.ACC_PRIVATE
        }
    }
}
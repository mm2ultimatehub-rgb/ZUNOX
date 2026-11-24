@file:Suppress("DEPRECATION")

package app.zunox.patches.music

import app.revanced.patcher.firstMethodOrNull
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val MUSIC_ACTIVITY =
    "Lcom/google/android/apps/youtube/music/activities/MusicActivity;"
private const val LOTTIE_VIEW =
    "Lcom/airbnb/lottie/LottieAnimationView;"
private const val LAYOUT_INFLATER =
    "Landroid/view/LayoutInflater;"
private const val VIEW =
    "Landroid/view/View;"

private fun Instruction.usesRegister(register: Int): Boolean = when (this) {
    is FiveRegisterInstruction -> register in listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> register in startRegister until (startRegister + registerCount)
    is ThreeRegisterInstruction -> register == registerA || register == registerB || register == registerC
    is TwoRegisterInstruction -> register == registerA || register == registerB
    is OneRegisterInstruction -> register == registerA
    else -> false
}

private fun Method.hasCairoStartupShape(): Boolean {
    val instructions = implementation?.instructions ?: return false
    val hasSetContentView = instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.name == "setContentView"
    }
    val hasLottieCast = instructions.any { instruction ->
        instruction.opcode == Opcode.CHECK_CAST &&
            ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == LOTTIE_VIEW
    }
    return definingClass == MUSIC_ACTIVITY &&
        name == "onCreate" &&
        parameterTypes == listOf("Landroid/os/Bundle;") &&
        returnType == "V" &&
        hasSetContentView &&
        hasLottieCast
}

@Suppress("unused")
val disableCairoSplashAnimationPatch = bytecodePatch(
    name = "Disable Cairo splash animation",
    description = "Hides the YouTube Music 9.47.51 Cairo splash overlay while preserving normal Activity startup."
) {
    compatibleWith(
        "com.google.android.apps.youtube.music" to setOf("9.47.51")
    )

    apply {
        val method = classDefs.firstMethodOrNull("sa_e") { hasCairoStartupShape() }
            ?: throw PatchException("Could not uniquely locate MusicActivity.onCreate(Bundle) for YouTube Music 9.47.51.")
        val instructions = method.implementation?.instructions
            ?: throw PatchException("MusicActivity.onCreate has no bytecode implementation.")

        val lottieCastIndex = instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CHECK_CAST &&
                ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == LOTTIE_VIEW
        }
        if (lottieCastIndex < 0) {
            throw PatchException("Could not find the Cairo LottieAnimationView cast.")
        }

        val lottieRegister = (instructions[lottieCastIndex] as OneRegisterInstruction).registerA

        val findViewIndex = (lottieCastIndex - 1 downTo 0).firstOrNull { index ->
            val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            reference?.name == "findViewById" &&
                reference.definingClass == VIEW &&
                reference.returnType == VIEW
        } ?: throw PatchException("Could not locate the Cairo overlay findViewById call.")

        val findViewCall = instructions[findViewIndex] as? FiveRegisterInstruction
            ?: throw PatchException("Cairo findViewById call has an unexpected register format.")
        if (findViewCall.registerCount != 2) {
            throw PatchException("Cairo findViewById call does not have the expected receiver/ID arguments.")
        }
        val overlayRootRegister = findViewCall.registerC

        val rootResultIndex = (findViewIndex - 1 downTo 0).firstOrNull { index ->
            val instruction = instructions[index]
            instruction.opcode == Opcode.MOVE_RESULT_OBJECT &&
                (instruction as? OneRegisterInstruction)?.registerA == overlayRootRegister
        } ?: throw PatchException("Could not locate the Cairo overlay root view result.")

        val inflateIndex = rootResultIndex - 1
        val inflateReference = (instructions.getOrNull(inflateIndex) as? ReferenceInstruction)
            ?.reference as? MethodReference
            ?: throw PatchException("Could not locate the Cairo overlay inflate call.")
        if (
            inflateReference.definingClass != LAYOUT_INFLATER ||
            inflateReference.name != "inflate" ||
            inflateReference.parameterTypes != listOf("I", "Landroid/view/ViewGroup;") ||
            inflateReference.returnType != VIEW
        ) {
            throw PatchException("The Cairo overlay is no longer inflated using the expected LayoutInflater overload.")
        }

        val inflateCall = instructions[inflateIndex] as? FiveRegisterInstruction
            ?: throw PatchException("The Cairo overlay inflate call has an unexpected register format.")
        if (inflateCall.registerCount != 3) {
            throw PatchException("The Cairo overlay inflate call does not have the expected three registers.")
        }

        // The third inflate argument is the null ViewGroup parameter. It is dead after inflate returns.
        // Reuse it for View.GONE, but fail closed if the expected register layout changes.
        val visibilityRegister = inflateCall.registerE
        // The target APK initializes this null ViewGroup register before the first
        // overlay inflate and reuses it for the second inflate. Find its latest write.
        val nullInitializerIndex = (inflateIndex - 1 downTo 0).firstOrNull { index ->
            val candidate = instructions[index] as? OneRegisterInstruction
            candidate?.registerA == visibilityRegister
        } ?: throw PatchException("Could not locate the null ViewGroup register initialization.")

        val nullInitializer = instructions[nullInitializerIndex] as? NarrowLiteralInstruction
        if (
            instructions[nullInitializerIndex].opcode != Opcode.CONST_4 ||
            nullInitializer?.narrowLiteral != 0
        ) {
            throw PatchException("The temporary ViewGroup register is not initialized to null.")
        }

        for (index in nullInitializerIndex + 1 until inflateIndex) {
            val candidate = instructions[index] as? OneRegisterInstruction
            if (candidate?.registerA == visibilityRegister) {
                throw PatchException("The null ViewGroup register is overwritten before the Cairo inflate call.")
            }
        }

        if (overlayRootRegister > 15 || visibilityRegister > 15) {
            throw PatchException("Cairo overlay registers exceed the safe 4-bit instruction range.")
        }

        // The inflate parent register is null and becomes dead after inflate returns.
        // Prove it is not read before the first instruction that overwrites it.
        val firstOverwriteIndex = (rootResultIndex + 1 until instructions.size).firstOrNull { index ->
            val instruction = instructions[index]
            val registerWrite = instruction as? OneRegisterInstruction
            registerWrite?.registerA == visibilityRegister &&
                (
                    instruction.opcode.name.startsWith("MOVE_RESULT") ||
                        instruction.opcode.name.startsWith("CONST") ||
                        instruction.opcode.name == "MOVE_EXCEPTION" ||
                        instruction.opcode.name == "NEW_INSTANCE" ||
                        instruction.opcode.name == "NEW_ARRAY" ||
                        instruction.opcode.name.startsWith("SGET")
                    )
        } ?: throw PatchException("Could not prove the temporary visibility register is overwritten.")

        for (index in rootResultIndex + 1 until firstOverwriteIndex) {
            if (instructions[index].usesRegister(visibilityRegister)) {
                throw PatchException("Temporary visibility register is read before it is overwritten.")
            }
        }

        method.addInstructions(
            rootResultIndex + 1,
            """
                const/4 v$visibilityRegister, 0x8
                invoke-virtual {v$overlayRootRegister, v$visibilityRegister}, Landroid/view/View;->setVisibility(I)V
            """.trimIndent()
        )

        if (lottieRegister == overlayRootRegister) {
            throw PatchException("Unexpected Cairo overlay root/Lottie register alias.")
        }
    }
}

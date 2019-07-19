/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.processor.staledataclass

import com.android.codegen.CODEGEN_NAME
import com.android.codegen.CODEGEN_VERSION
import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.code.Type
import java.io.File
import java.io.FileNotFoundException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

private const val STALE_FILE_THRESHOLD_MS = 1000
private val WORKING_DIR = File(".").absoluteFile

private const val DATACLASS_ANNOTATION_NAME = "com.android.internal.util.DataClass"
private const val GENERATED_ANNOTATION_NAME = "com.android.internal.util.DataClass.Generated"
private const val GENERATED_MEMBER_ANNOTATION_NAME
        = "com.android.internal.util.DataClass.Generated.Member"


@SupportedAnnotationTypes(DATACLASS_ANNOTATION_NAME, GENERATED_ANNOTATION_NAME)
class StaleDataclassProcessor: AbstractProcessor() {

    private var dataClassAnnotation: TypeElement? = null
    private var generatedAnnotation: TypeElement? = null
    private var repoRoot: File? = null

    private val stale = mutableListOf<Stale>()

    /**
     * This is the main entry point in the processor, called by the compiler.
     */
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        if (generatedAnnotation == null) {
            generatedAnnotation = annotations.find {
                it.qualifiedName.toString() == GENERATED_ANNOTATION_NAME
            }
        }
        if (dataClassAnnotation == null) {
            dataClassAnnotation = annotations.find {
                it.qualifiedName.toString() == DATACLASS_ANNOTATION_NAME
            }
        }

        val generatedAnnotatedElements = roundEnv.getElementsAnnotatedWith(generatedAnnotation)
        generatedAnnotatedElements.forEach {
            processSingleFile(it)
        }


        val dataClassesWithoutGeneratedPart =
                roundEnv.getElementsAnnotatedWith(dataClassAnnotation) -
                        generatedAnnotatedElements.map { it.enclosingElement }

        dataClassesWithoutGeneratedPart.forEach { dataClass ->
            stale += Stale(dataClass.toString(), file = null, lastGenerated = 0L)
        }


        if (!stale.isEmpty()) {
            error("Stale generated dataclass(es) detected. " +
                    "Run the following command(s) to update them:" +
                    stale.joinToString("") { "\n" + it.refreshCmd })
        }
        return true
    }

    private fun elemToString(elem: Element): String {
        return buildString {
            append(elem.modifiers.joinToString(" ") { it.name.toLowerCase() }).append(" ")
            append(elem.annotationMirrors.joinToString(" ")).append(" ")
            if (elem is Symbol) {
                if (elem.type is Type.MethodType) {
                    append((elem.type as Type.MethodType).returnType)
                } else {
                    append(elem.type)
                }
                append(" ")
            }
            append(elem)
        }
    }

    private fun processSingleFile(elementAnnotatedWithGenerated: Element) {

        val inputSignatures = elementAnnotatedWithGenerated
                .enclosingElement
                .enclosedElements
                .filterNot {
                    it.annotationMirrors.any { "Generated" in it.annotationType.toString() }
                }.map {
                    elemToString(it)
                }.toSet()

        val annotationParams = elementAnnotatedWithGenerated
                .annotationMirrors
                .find { ann -> isGeneratedAnnotation(ann) }!!
                .elementValues
                .map { (k, v) -> k.getSimpleName().toString() to v.getValue() }
                .toMap()

        val lastGenerated = annotationParams["time"] as Long
        val codegenVersion = annotationParams["codegenVersion"] as String
        val sourceRelative = File(annotationParams["sourceFile"] as String)

        val lastGenInputSignatures = (annotationParams["inputSignatures"] as String).lines().toSet()

        if (repoRoot == null) {
            repoRoot = generateSequence(WORKING_DIR) { it.parentFile }
                    .find { it.resolve(sourceRelative).isFile }
                    ?.canonicalFile
                    ?: throw FileNotFoundException(
                            "Failed to detect repository root: " +
                                    "no parent of $WORKING_DIR contains $sourceRelative")
        }

        val source = repoRoot!!.resolve(sourceRelative)
        val clazz = elementAnnotatedWithGenerated.enclosingElement.toString()

        if (inputSignatures != lastGenInputSignatures) {
            error(buildString {
                append(sourceRelative).append(":\n")
                append("  Added:\n").append((inputSignatures-lastGenInputSignatures).joinToString("\n"))
                append("\n")
                append("  Removed:\n").append((lastGenInputSignatures-inputSignatures).joinToString("\n"))
            })
            stale += Stale(clazz, source, lastGenerated)
        }

        if (codegenVersion != CODEGEN_VERSION) {
            stale += Stale(clazz, source, lastGenerated)
        }
    }

    private fun error(msg: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
    }

    private fun isGeneratedAnnotation(ann: AnnotationMirror): Boolean {
        return generatedAnnotation!!.qualifiedName.toString() == ann.annotationType.toString()
    }

    data class Stale(val clazz: String, val file: File?, val lastGenerated: Long) {
        val refreshCmd = if (file != null) {
            "$CODEGEN_NAME $file"
        } else {
            "find \$ANDROID_BUILD_TOP -path */${clazz.replace('.', '/')}.java -exec $CODEGEN_NAME {} \\;"
        }
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }
}
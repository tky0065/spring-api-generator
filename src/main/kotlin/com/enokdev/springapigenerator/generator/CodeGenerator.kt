package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project

/**
 * Base interface for all code generators.
 */
interface CodeGenerator {
    /**
     * Generate code based on entity metadata.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return Generated file content
     */
    fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String

    /**
     * Get the target file path for the generated code.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return File path where the code should be saved
     */
    fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String
}

package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiMethodUtil

/**
 * Service for detecting JPA entities in the project.
 */
class EntityDetectionService(private val project: Project) {

    /**
     * Checks if a class is a JPA entity.
     * @param psiClass the class to check
     * @return true if the class is a JPA entity
     */
    fun isJpaEntity(psiClass: PsiClass): Boolean {
        // Check if the class has @Entity annotation
        val hasEntityAnnotation = psiClass.annotations.any {
            it.qualifiedName == "javax.persistence.Entity" || it.qualifiedName == "jakarta.persistence.Entity"
        }

        // Check if the class has @Id annotation on any field or getter
        val hasIdAnnotation = psiClass.allFields.any { field ->
            field.annotations.any {
                it.qualifiedName == "javax.persistence.Id" || it.qualifiedName == "jakarta.persistence.Id"
            }
        } || psiClass.allMethods.any { method ->
            method.name.startsWith("get") && method.annotations.any {
                it.qualifiedName == "javax.persistence.Id" || it.qualifiedName == "jakarta.persistence.Id"
            }
        }

        return hasEntityAnnotation && hasIdAnnotation
    }

    /**
     * Checks if a class extends AbstractEntity.
     * @param psiClass the class to check
     * @return true if the class extends AbstractEntity
     */
    fun extendsAbstractEntity(psiClass: PsiClass): Boolean {
        var currentClass: PsiClass? = psiClass.superClass
        while (currentClass != null) {
            if (currentClass.name == "AbstractEntity") {
                return true
            }
            currentClass = currentClass.superClass
        }
        return false
    }

    /**
     * Finds all JPA entities in the project.
     * @return list of JPA entity classes
     */
    fun findAllJpaEntities(): List<PsiClass> {
        val searchScope = GlobalSearchScope.projectScope(project)
        val shortNamesCache = PsiShortNamesCache.getInstance(project)
        val entityClasses = mutableListOf<PsiClass>()

        // Find all classes in the project
        val allClassNames = shortNamesCache.allClassNames

        for (className in allClassNames) {
            val classes = shortNamesCache.getClassesByName(className, searchScope)
            for (psiClass in classes) {
                if (isJpaEntity(psiClass)) {
                    entityClasses.add(psiClass)
                }
            }
        }

        return entityClasses
    }
}

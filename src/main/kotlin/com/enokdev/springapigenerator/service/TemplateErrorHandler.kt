package com.enokdev.springapigenerator.service

import com.intellij.openapi.diagnostic.Logger
import freemarker.core.Environment
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler
import java.io.Writer

/**
 * Enhanced template exception handler that provides detailed error reporting,
 * logging, and graceful fallbacks for template processing errors.
 */
class TemplateErrorHandler : TemplateExceptionHandler {
    private val logger = Logger.getInstance(TemplateErrorHandler::class.java)

    /**
     * Handles a template exception by logging detailed information and
     * optionally continuing template processing with a fallback.
     *
     * @param te The template exception that occurred
     * @param env The template processing environment
     * @param out The output writer
     */
    override fun handleTemplateException(te: TemplateException, env: Environment, out: Writer) {
        // Get template information for better error context
        val templateName = env.mainTemplate.name ?: "Unknown template"
        val lineNumber = te.lineNumber
        val columnNumber = te.columnNumber
        
        // Extract error location and message
        val errorLocation = if (lineNumber > 0) "line $lineNumber" + 
                            if (columnNumber > 0) ", column $columnNumber" else "" 
                            else "unknown location"
        val errorMessage = te.message ?: "Unknown error"
        
        // Create detailed error message with context
        val detailedMessage = buildString {
            appendLine("Template processing error in '$templateName' at $errorLocation:")
            appendLine("  Error: $errorMessage")
            
            // Add template snippet if available
            te.ftlInstructionStack?.let { stack ->
                if (stack.isNotEmpty()) {
                    appendLine("  Template context:")
                    stack.take(3).forEach { instruction ->
                        appendLine("    $instruction")
                    }
                }
            }
        }
        
        // Log the detailed error for debugging
        logger.error(detailedMessage, te)
        
        try {
            // Write a graceful error message to the output
            out.write("\n<!-- Template Error: Processing failed for template '$templateName' -->\n")
            out.write("<!-- Error details: ${errorMessage.replace("--", "- -")} -->\n")
            
            // Provide fallback content
            out.write("\n/* Fallback content due to template error */\n")
            
            // Continue processing the template (FreeMarker will decide whether to continue based on configuration)
        } catch (e: Exception) {
            logger.error("Failed to write error message to output", e)
            // Let FreeMarker handle the exception
        }
    }
    
    companion object {
        /**
         * Creates a detailed error message from a template exception.
         * This can be used outside the handler for consistent error reporting.
         */
        fun createDetailedErrorMessage(te: TemplateException, templateName: String): String {
            return buildString {
                appendLine("Template processing error in '$templateName':")
                appendLine("  Error: ${te.message ?: "Unknown error"}")
                
                if (te.lineNumber > 0) {
                    append("  Location: line ${te.lineNumber}")
                    if (te.columnNumber > 0) {
                        append(", column ${te.columnNumber}")
                    }
                    appendLine()
                }
                
                te.ftlInstructionStack?.let { stack ->
                    if (stack.isNotEmpty()) {
                        appendLine("  Template context:")
                        stack.take(3).forEach { instruction ->
                            appendLine("    $instruction")
                        }
                    }
                }
            }
        }
    }
}
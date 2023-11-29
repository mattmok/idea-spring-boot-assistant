package com.tiamaes.cloud.assistant.idea.plugin;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Chenlili
 * @date 2023/11/24
 */
public class DocumentationManager {
    public static final Key<SmartPsiElementPointer<?>> ORIGINAL_ELEMENT_KEY = Key.create("Original element");

    @NotNull
    public static DocumentationProvider getProviderFromElement(PsiElement element) {
        return getProviderFromElement(element, null);
    }

    @NotNull
    public static DocumentationProvider getProviderFromElement(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
        if (element != null && !element.isValid()) {
            element = null;
        }
        if (originalElement != null && !originalElement.isValid()) {
            originalElement = null;
        }

        if (originalElement == null) {
            originalElement = getOriginalElement(element);
        }

        PsiFile containingFile =
                originalElement != null ? originalElement.getContainingFile() : element != null ? element.getContainingFile() : null;
        Set<DocumentationProvider> result = new LinkedHashSet<>();

        Language containingFileLanguage = containingFile != null ? containingFile.getLanguage() : null;
        DocumentationProvider originalProvider =
                containingFile != null ? LanguageDocumentation.INSTANCE.forLanguage(containingFileLanguage) : null;

        Language elementLanguage = element != null ? element.getLanguage() : null;
        DocumentationProvider elementProvider =
                element == null || elementLanguage.is(containingFileLanguage) ? null : LanguageDocumentation.INSTANCE.forLanguage(elementLanguage);

        ContainerUtil.addIfNotNull(result, elementProvider);
        ContainerUtil.addIfNotNull(result, originalProvider);

        if (containingFile != null) {
            Language baseLanguage = containingFile.getViewProvider().getBaseLanguage();
            if (!baseLanguage.is(containingFileLanguage)) {
                ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
            }
        }
        else if (element instanceof PsiDirectory) {
            Set<Language> set = new HashSet<>();

            for (PsiFile file : ((PsiDirectory)element).getFiles()) {
                Language baseLanguage = file.getViewProvider().getBaseLanguage();
                if (!set.contains(baseLanguage)) {
                    set.add(baseLanguage);
                    ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
                }
            }
        }
        return CompositeDocumentationProvider.wrapProviders(result);
    }


    @Nullable
    public static PsiElement getOriginalElement(PsiElement element) {
        SmartPsiElementPointer<?> originalElementPointer = element != null ? element.getUserData(ORIGINAL_ELEMENT_KEY) : null;
        return originalElementPointer != null ? originalElementPointer.getElement() : null;
    }
}

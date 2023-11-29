package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.lang.Language;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.suggestion.filetype.YamlPropertiesFileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.Suggestion;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static java.util.stream.Collectors.joining;

public class YamlDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        String doc = null;
        if (element instanceof DocumentationProxyElement documentationProxyElement) {
            doc = documentationProxyElement.generateDoc();
        }
        if (doc == null) {
            doc = super.generateDoc(element, originalElement);
        }
        return doc;
    }

    /*
     * This will be called if the user tries to lookup documentation for the choices being shown (ctrl+q within suggestion dialog)
     */
    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object,
                                                           @Nullable PsiElement element) {
        if (object instanceof Suggestion suggestion) {
            return new DocumentationProxyElement(
                    psiManager,
                    JavaLanguage.INSTANCE,
                    element != null ? findModule(element) : null,
                    suggestion.getFullPath(),
                    suggestion.getMatchesTopFirst().get(suggestion.getMatchesTopFirst().size() - 1),
                    suggestion.isForValue(),
                    suggestion.getSuggestionToDisplay()
            );
        }
        return super.getDocumentationElementForLookupItem(psiManager, object, element);
    }

    @Nullable
    @Override
    @SuppressWarnings({"squid:S3776", "squid:S6541"})
    public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                    @Nullable PsiElement element, int targetOffset) {
        if (file.getVirtualFile() == null || !(file.getVirtualFile().getFileType() instanceof YamlPropertiesFileType)) {
            return super.getCustomDocumentationElement(editor, file, element, targetOffset);
        }
        if (element == null) {
            return super.getCustomDocumentationElement(editor, file, null, targetOffset);
        }
        Module module = findModule(element);
        if (module == null) {
            return super.getCustomDocumentationElement(editor, file, element, targetOffset);
        }

        // If element is EOL or white space, move to its visual previous element on same line for better user experience.
        if (element.getText().isBlank()) {
            Document document = PsiDocumentManager.getInstance(module.getProject()).getDocument(file);
            if (document == null) {
                return super.getCustomDocumentationElement(editor, file, element, targetOffset);
            }
            int lineStartOffset = DocumentUtil.getLineStartOffset(element.getTextOffset(), document);
            CharSequence text = document.getImmutableCharSequence();
            int i;
            for (i = element.getTextOffset(); i >= lineStartOffset; i--) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    break;
                }
            }
            PsiElement elementForDoc = file.findElementAt(i);
            if (elementForDoc == null) {
                return super.getCustomDocumentationElement(editor, file, element, targetOffset);
            } else {
                element = elementForDoc;
            }
        }
        // Bottom-up traverse the psi tree, retrieve ancestral keys.
        List<String> ancestralKeys = new ArrayList<>();
        PsiElement context;
        if (element instanceof YAMLKeyValue) {
            // Barely possible.
            context = element;
        } else {
            context = PsiTreeUtil.getContextOfType(element, YAMLKeyValue.class);
        }
        while (context != null) {
            ancestralKeys.add(0, truncateIdeaDummyIdentifier(((YAMLKeyValue) context).getKeyText()));
            context = PsiTreeUtil.getContextOfType(context, YAMLKeyValue.class);
        }

        // Find matches using suggestion service.
        List<SuggestionNode> matchedNodesFromRootTillLeaf = null;
        if (!ancestralKeys.isEmpty()) {
            SuggestionService suggestionService = module.getService(SuggestionService.class);
            matchedNodesFromRootTillLeaf = suggestionService.findMatchedNodesRootTillEnd(ancestralKeys);
        }
        if (matchedNodesFromRootTillLeaf == null) {
            return super.getCustomDocumentationElement(editor, file, element, 0);
        }

        // Create documentation proxy element and return.
        // -- Whether document is requested for key or value.
        boolean requestedForTargetValue;
        String value;
        PsiElement elementContext = element.getContext();
        if (elementContext instanceof YAMLKeyValue yamlKeyValue) {
            value = truncateIdeaDummyIdentifier(yamlKeyValue.getKeyText());
            requestedForTargetValue = false;
        } else if (elementContext instanceof YAMLPlainTextImpl) {
            value = truncateIdeaDummyIdentifier(element.getText());
            requestedForTargetValue = true;
        } else {
            value = null;
            requestedForTargetValue = false;
        }

        if (CollectionUtils.isEmpty(matchedNodesFromRootTillLeaf)) {
            return null;
        }

        SuggestionNode target = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);
        String targetNavigationPathDotDelimited = matchedNodesFromRootTillLeaf
                .stream()
                .map(v -> v.getNameForDocumentation(module))
                .collect(joining("."));
        return new DocumentationProxyElement(
                file.getManager(),
                file.getLanguage(),
                findModuleForFile(file),
                targetNavigationPathDotDelimited,
                target,
                requestedForTargetValue,
                value
        );
    }

    @ToString(of = "nodeNavigationPathDotDelimited")
    private static class DocumentationProxyElement extends LightElement {
        private final DocumentationProvider target;
        private final boolean requestedForTargetValue;
        @Nullable
        private final String value;
        private final Module module;
        private final String nodeNavigationPathDotDelimited;

        DocumentationProxyElement(
                @NotNull final PsiManager manager, @NotNull final Language language, Module module,
                String nodeNavigationPathDotDelimited, @NotNull final DocumentationProvider target,
                boolean requestedForTargetValue, @Nullable String value) {
            super(manager, language);
            this.module = module;
            this.nodeNavigationPathDotDelimited = nodeNavigationPathDotDelimited;
            this.target = target;
            this.requestedForTargetValue = requestedForTargetValue;
            this.value = value;
        }

        @Override
        public String getText() {
            return nodeNavigationPathDotDelimited;
        }

        public String generateDoc() {
            // Intermediate nodes will not have documentation
            if (target != null && target.supportsDocumentation()) {
                if (requestedForTargetValue) {
                    return target.getDocumentationForValue(module, nodeNavigationPathDotDelimited, value);
                } else {
                    return target.getDocumentationForKey(module, nodeNavigationPathDotDelimited);
                }
            }
            return null;
        }
    }

}

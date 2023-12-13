package in.oneton.idea.spring.assistant.plugin.suggestion.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static in.oneton.idea.spring.assistant.plugin.misc.GenericUtil.truncateIdeaDummyIdentifier;
import static in.oneton.idea.spring.assistant.plugin.misc.PsiCustomUtil.findModule;
import static in.oneton.idea.spring.assistant.plugin.suggestion.completion.FileType.yaml;
import static java.util.Objects.requireNonNull;

class YamlCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull final CompletionParameters completionParameters,
      final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {

    PsiElement element = completionParameters.getPosition();
    if (element instanceof PsiComment) {
      return;
    }

    Module module = findModule(element);
    if (module == null) {
      return;
    }
    SuggestionService service = module.getService(SuggestionService.class);

    if (!service.canProvideSuggestions()) {
      return;
    }

    PsiElement elementContext = element.getContext();
    PsiElement parent = requireNonNull(elementContext).getParent();
    if (parent instanceof YAMLSequence) {
      // lets force user to create array element prefix before he can ask for suggestions
      return;
    }

    List<LookupElement> suggestions;
    // For top level element, since there is no parent parentKeyValue would be null
    String queryWithDotDelimitedPrefixes = truncateIdeaDummyIdentifier(element);

    List<String> ancestralKeys = null;
    PsiElement context = elementContext;
    do {
      if (context instanceof YAMLKeyValue) {
        if (ancestralKeys == null) {
          ancestralKeys = new ArrayList<>();
        }
        ancestralKeys.add(0, truncateIdeaDummyIdentifier(((YAMLKeyValue) context).getKeyText()));
      }
      context = requireNonNull(context).getParent();
    } while (context != null);

    suggestions = service.findSuggestionsForQueryPrefix(
        yaml,
        element,
        ancestralKeys,
        queryWithDotDelimitedPrefixes,
        null
    );

    if (suggestions != null) {
      suggestions.forEach(resultSet::addElement);
    }
  }

  @NotNull
  private Set<String> getNewIfNotPresent(@Nullable Set<String> siblingsToExclude) {
    if (siblingsToExclude == null) {
      return new HashSet<>();
    }
    return siblingsToExclude;
  }

}

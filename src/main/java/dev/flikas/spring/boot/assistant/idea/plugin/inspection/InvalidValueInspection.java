package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import dev.flikas.spring.boot.assistant.idea.plugin.misc.ServiceUtil;
import dev.flikas.spring.boot.assistant.idea.plugin.suggestion.filetype.YamlPropertiesFileType;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.SuggestionNodeType;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.GenericClassMemberWrapper;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.IterableKeySuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.clazz.MetadataProxy;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.MetadataPropertySuggestionNode;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataProperty;
import in.oneton.idea.spring.assistant.plugin.suggestion.service.SuggestionService;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class InvalidValueInspection extends LocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        SuggestionService service = ServiceUtil.getServiceFromEligibleFile(
                holder.getFile(),
                YamlPropertiesFileType.INSTANCE,
                SuggestionService.class
        );
        if (service == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Module module = ModuleUtil.findModuleForFile(holder.getFile());
        assert module != null;
        return new YamlPsiElementVisitor() {
            @Override
            public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
                ProgressIndicatorProvider.checkCanceled();
                if (keyValue.getValue() == null) return;
                List<String> ancestralKeys = new ArrayList<>();
                PsiElement context = keyValue;
                do {
                    if (context instanceof YAMLKeyValue) {
                        ancestralKeys.add(0, truncateIdeaDummyIdentifier(((YAMLKeyValue) context).getKeyText()));
                    }
                    context = requireNonNull(context).getParent();
                } while (context != null);
                List<SuggestionNode> matchedNodesFromRootTillLeaf = service.findMatchedNodesRootTillEnd(ancestralKeys);
                if (CollectionUtils.isEmpty(matchedNodesFromRootTillLeaf)) {
                    return;
                }
                SuggestionNode node = matchedNodesFromRootTillLeaf.get(matchedNodesFromRootTillLeaf.size() - 1);
                SuggestionNodeType suggestionNodeType = node.getSuggestionNodeType(module);
                if (!suggestionNodeType.representsLeaf()) {
                    return;
                }
                PsiType valueType;
                if (node instanceof IterableKeySuggestionNode) {
                    node = ((IterableKeySuggestionNode) node).getUnwrapped();
                }
                if (node instanceof MetadataPropertySuggestionNode) {
                    SpringConfigurationMetadataProperty property = ((MetadataPropertySuggestionNode) node).getProperty();
                    if (property == null) return;
                    MetadataProxy delegate = property.getDelegate(module);
                    if (delegate == null) return;
                    valueType = delegate.getPsiType(module);

                } else if (node instanceof GenericClassMemberWrapper) {
                    valueType = ((GenericClassMemberWrapper) node).getMemberReferredClassMetadataProxy(module)
                            .getPsiType(module);
                } else {
                    valueType = null;
                }
                if (valueType == null) return;
                PsiPrimitiveType primitiveValueType = valueType.accept(new PsiTypeVisitor<>() {
                    @Override
                    public PsiPrimitiveType visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
                        return primitiveType;
                    }

                    @Override
                    public PsiPrimitiveType visitType(@NotNull PsiType type) {
                        return PsiPrimitiveType.getUnboxedType(type);
                    }
                });
                if (primitiveValueType == null) return;
                try {
                    Class<?> valueClass = Class.forName(primitiveValueType.getBoxedTypeName());
                    Constructor<?> constructor = valueClass.getConstructor(String.class);
                    constructor.newInstance(keyValue.getValueText());
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                         InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    assert keyValue.getValue() != null;
                    holder.registerProblem(
                            keyValue.getValue(),
                            "Value \"" + keyValue.getValueText() + "\" cannot be converted to: " + primitiveValueType.getName()
                    );
                }
            }
        };
    }
}

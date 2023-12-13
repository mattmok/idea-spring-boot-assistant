package com.tiamaes.cloud.assistant.idea.plugin;

import com.intellij.psi.PsiClass;
import lombok.Getter;

import java.util.Objects;

/**
 * @author Chenlili
 * @date 2023/12/8
 */
@Getter
public class PsiClassWapper {

    private final PsiClass psiClass;

    public PsiClassWapper(PsiClass psiClass) {
        this.psiClass = psiClass;
    }


    @Override
    public int hashCode() {
        return Objects.requireNonNull(psiClass.getQualifiedName()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (Objects.nonNull(obj) && obj instanceof PsiClass) {
            return this.hashCode() == obj.hashCode();
        }
        return false;
    }
}

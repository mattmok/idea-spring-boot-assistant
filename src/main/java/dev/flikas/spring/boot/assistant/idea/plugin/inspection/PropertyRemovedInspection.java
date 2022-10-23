package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataDeprecation;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataDeprecationLevel;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataProperty;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * Report deprecated properties whose deprecation level is error, which means that the property is completely unsupported.
 * <p>
 * refer to <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html#appendix.configuration-metadata.format.property">Spring Boot Document</a>
 */
public class PropertyRemovedInspection extends PropertyDeprecatedInspectionBase {
  @Override
  protected void foundDeprecatedKey(YAMLKeyValue keyValue, SpringConfigurationMetadataProperty property,
                                    SpringConfigurationMetadataDeprecation deprecation, ProblemsHolder holder,
                                    boolean isOnTheFly) {
    if (deprecation.getLevel() == SpringConfigurationMetadataDeprecationLevel.error) {
      assert keyValue.getKey() != null;
      holder.registerProblem(
          keyValue.getKey(),
          "Property \"" + property.getName() + "\" is deprecated and no longer supported."
      );
    }
  }
}

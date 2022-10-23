package dev.flikas.spring.boot.assistant.idea.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataDeprecation;
import in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataProperty;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import static in.oneton.idea.spring.assistant.plugin.suggestion.metadata.json.SpringConfigurationMetadataDeprecationLevel.warning;

/**
 * Report deprecated properties whose deprecation level is warning, which means that the property is still be bound in the environment.
 * <p>
 * refer to <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html#appendix.configuration-metadata.format.property">Spring Boot Document</a>
 */
public class PropertyDeprecatedInspection extends PropertyDeprecatedInspectionBase {
  @Override
  protected void foundDeprecatedKey(YAMLKeyValue keyValue, SpringConfigurationMetadataProperty property,
                                    SpringConfigurationMetadataDeprecation deprecation, ProblemsHolder holder,
                                    boolean isOnTheFly) {
    if (deprecation.getLevel() == null || deprecation.getLevel() == warning) {
      assert keyValue.getKey() != null;
      holder.registerProblem(
          keyValue.getKey(),
          "Property \"" + property.getName() + "\" is deprecated."
      );
    }
  }
}

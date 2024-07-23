package dev.nicho.rolesync.config.linter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LintResult {

    private final Map<String, List<String>> result;

    LintResult() {
        result = new HashMap<>();
    }

    /**
     * Adds a rule result to this lint result
     *
     * @param ruleName The rule's name
     * @param ruleResult The rule's result
     */
    void add(String ruleName, List<String> ruleResult) {
        result.put(ruleName, ruleResult);
    }

    /**
     * @return the lint results as a map
     */
    public Map<String, List<String>> asMap() {
        return result;
    }

    /**
     * @return true if this lint result is a valid config, false if not
     */
    public boolean isValid() {
        return result.isEmpty();
    }

    /**
     * @return the lint results as a string
     */
    @Override
    public String toString() {
        if (result.isEmpty()) {
            return "No config errors";
        }

        StringBuilder mainBuilder = new StringBuilder(
                String.format("%d config rule%s had errors.\n", result.size(), result.size() == 1 ? "" : "s")
        );

        for (String rule : result.keySet()) {
            StringBuilder builder = new StringBuilder()
                    .append(String.format("Config lint rule \"%s\" had errors:", rule));

            List<String> errors = result.get(rule);
            for (String err : errors) {
                builder.append("\n")
                        .append("\t- ")
                        .append(err);
            }

            builder.append("\n");
            mainBuilder.append("\n").append(builder);
        }

        return mainBuilder.toString();
    }
}

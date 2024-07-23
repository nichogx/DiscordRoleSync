package dev.nicho.rolesync.config.linter;

import org.bukkit.configuration.Configuration;

import java.util.List;
import java.util.function.Function;

public class LintRule {

    private final String name;
    private final Function<Configuration, List<String>> func;

    LintRule(String name, Function<Configuration, List<String>> func) {
        this.name = name;
        this.func = func;
    }

    public String getName() {
        return name;
    }

    public List<String> run(Configuration c) {
        List<String> result = func.apply(c);
        if (result == null || result.isEmpty()) return null;

        return result;
    }
}

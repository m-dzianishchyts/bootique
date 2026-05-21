/*
 * Licensed to ObjectStyle LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ObjectStyle LLC licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.bootique.meta.application;

import io.bootique.BootiqueException;
import io.bootique.meta.MetadataNode;
import io.bootique.meta.config.ConfigValueMetadata;

import java.util.*;

/**
 * Metadata object representing current application and its command-line interface.
 */
public class ApplicationMetadata implements MetadataNode {

    private String name;
    private String description;
    private final List<CommandMetadata> commands;
    private final List<OptionMetadata> options;
    private final List<ConfigValueMetadata> variables;

    private ApplicationMetadata() {
        this.commands = new ArrayList<>();
        this.options = new ArrayList<>();
        this.variables = new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder().defaultName();
    }

    public static Builder builder(String name) {
        return new Builder().name(name);
    }

    public static Builder builder(String name, String description) {
        return new Builder().name(name).description(description);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public Collection<CommandMetadata> getCommands() {
        return commands;
    }

    public Collection<OptionMetadata> getOptions() {
        return options;
    }


    /**
     * Returns a collection of metadata objects representing publicly exposed environment variables.
     *
     * @return a collection of metadata objects representing publicly exposed environment variables.
     */
    public Collection<ConfigValueMetadata> getVariables() {
        return variables;
    }

    public static class Builder {

        private final ApplicationMetadata application;

        private Builder() {
            this.application = new ApplicationMetadata();
        }

        public ApplicationMetadata build() {
            throwOnConflictingGlobalOptions();
            rewriteConflictingGlobalShortNames();

            application.options.sort(Comparator.comparing(OptionMetadata::getName));
            application.commands.sort(Comparator.comparing(CommandMetadata::getName));
            application.variables.sort(Comparator.comparing(ConfigValueMetadata::getName));
            return application;
        }

        private void throwOnConflictingGlobalOptions() {
            Set<String> seen = new HashSet<>();
            application.options.forEach(om -> {
                if (!seen.add(om.getName())) {
                    throw new BootiqueException(1, "Duplicate option name declaration: '" + om.getName() + "'");
                }
            });
        }

        private void rewriteConflictingGlobalShortNames() {
            // Disable conflicting short names among global options only.
            // Command options are isolated per scope.

            int len = application.options.size();
            Map<String, List<Integer>> shortNames = new HashMap<>();
            for (int i = 0; i < len; i++) {
                shortNames.computeIfAbsent(application.options.get(i).getShortName(), sn -> new ArrayList<>(3)).add(i);
            }

            for (Map.Entry<String, List<Integer>> e : shortNames.entrySet()) {
                int slen = e.getValue().size();
                if (slen > 1) {
                    for (int i = 0; i < slen; i++) {
                        int oi = e.getValue().get(i);
                        OptionMetadata oldOpt = application.options.get(oi);
                        application.options.set(oi, sansShortName(oldOpt));
                    }
                    e.getValue().clear();
                }
            }

            for (OptionMetadata o : application.options) {
                if (o.getName().length() == 1) {
                    List<Integer> conflicting = shortNames.getOrDefault(o.getName(), List.of());
                    if (conflicting.size() == 1) {
                        int i = conflicting.getFirst();
                        OptionMetadata oldOpt = application.options.get(i);
                        application.options.set(i, sansShortName(oldOpt));
                    }
                }
            }
        }

        private OptionMetadata sansShortName(OptionMetadata md) {
            return new OptionMetadata(
                    md.getName(),
                    md.getDescription(),
                    null,
                    md.getValueCardinality(),
                    md.getValueName(),
                    md.getDefaultValue()
            );
        }

        public Builder name(String name) {
            application.name = name;
            return this;
        }

        public Builder defaultName() {
            return name(ApplicationIntrospector.appNameFromRuntime());
        }

        public Builder description(String description) {
            application.description = description;
            return this;
        }

        public Builder addCommand(CommandMetadata commandMetadata) {
            application.commands.add(commandMetadata);
            return this;
        }

        public Builder addCommands(Collection<CommandMetadata> commandMetadata) {
            commandMetadata.forEach(this::addCommand);
            return this;
        }

        public Builder addOption(OptionMetadata option) {
            application.options.add(option);
            return this;
        }

        public Builder addOptions(Collection<OptionMetadata> options) {
            options.forEach(this::addOption);
            return this;
        }

        public Builder addVariable(ConfigValueMetadata var) {
            application.variables.add(var);
            return this;
        }

        public Builder addVariables(Collection<ConfigValueMetadata> vars) {
            application.variables.addAll(vars);
            return this;
        }
    }
}

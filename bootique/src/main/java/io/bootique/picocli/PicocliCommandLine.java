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

package io.bootique.picocli;

import io.bootique.meta.application.ApplicationMetadata;
import io.bootique.meta.application.CommandMetadata;
import io.bootique.meta.application.OptionMetadata;
import io.bootique.picocli.CommandLine.Model.CommandSpec;
import io.bootique.picocli.CommandLine.Model.OptionSpec;
import io.bootique.picocli.CommandLine.Model.PositionalParamSpec;
import io.bootique.picocli.CommandLine.ParseResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Internal wrapper around picocli's CommandLine. Encapsulates picocli API.
 * Builds root + subcommand hierarchy: global options on root, command-specific options on subcommands.
 */
class PicocliCommandLine {

    private final CommandLine commandLine;
    private final Map<String, OptionSpec> globalOptionByName;
    private final Map<String, Map<String, OptionSpec>> commandOptionsByName;

    PicocliCommandLine(ApplicationMetadata application) {
        this.globalOptionByName = new HashMap<>();
        this.commandOptionsByName = new HashMap<>();

        CommandSpec rootSpec = CommandSpec.create();
        rootSpec.parser().abbreviatedOptionsAllowed(false);
        rootSpec.parser().posixClusteredShortOptionsAllowed(false);

        // Add global options to root
        for (OptionMetadata om : application.getOptions()) {
            OptionSpec picocliSpec = toOptionSpec(om);
            rootSpec.addOption(picocliSpec);
            globalOptionByName.put(om.getName(), picocliSpec);
            if (om.getShortName() != null) {
                globalOptionByName.put(om.getShortName(), picocliSpec);
            }
        }

        // Add subcommands
        Set<String> usedAliases = new HashSet<>();
        for (CommandMetadata cmd : application.getCommands()) {
            CommandSpec subSpec = CommandSpec.create();
            String cmdName = "--" + cmd.getName();
            subSpec.name(cmdName);
            String shortName = cmd.getShortName();
            if (shortName != null) {
                String alias = "-" + shortName;
                if (usedAliases.add(alias)) {
                    subSpec.aliases(alias);
                }
            }
            if (cmd.getDescription() != null) {
                subSpec.usageMessage().description(cmd.getDescription());
            }

            // Add command-specific options to subcommand
            Map<String, OptionSpec> cmdOptions = new HashMap<>();
            for (OptionMetadata om : cmd.getOptions()) {
                OptionSpec picocliSpec = toOptionSpec(om);
                subSpec.addOption(picocliSpec);
                cmdOptions.put(om.getName(), picocliSpec);
                if (om.getShortName() != null) {
                    cmdOptions.put(om.getShortName(), picocliSpec);
                }
            }
            commandOptionsByName.put(cmd.getName(), cmdOptions);

            rootSpec.addSubcommand(cmdName, new CommandLine(subSpec));
        }

        // Add positional parameter for remaining args
        rootSpec.addPositional(PositionalParamSpec.builder()
                .index("0..*")
                .arity("*")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .build());

        this.commandLine = new CommandLine(rootSpec);
    }

    PicocliParseResult parseArgs(String[] args) {
        ParseResult rootResult = commandLine.parseArgs(args);
        return new PicocliParseResult(rootResult, globalOptionByName, commandOptionsByName);
    }

    private OptionSpec toOptionSpec(OptionMetadata option) {
        List<String> names = new LinkedList<>();
        names.add("--" + option.getName());
        Optional.ofNullable(option.getShortName()).ifPresent(sn -> names.add("-" + sn));

        OptionSpec.Builder builder = OptionSpec.builder(names.toArray(String[]::new))
                .description(option.getDescription() != null ? option.getDescription() : "");

        switch (option.getValueCardinality()) {
            case OPTIONAL:
                builder.arity("0..1").type(List.class).auxiliaryTypes(String.class);
                Optional.ofNullable(option.getDefaultValue()).ifPresent(builder::fallbackValue);
                Optional.ofNullable(option.getValueName()).ifPresent(builder::paramLabel);
                break;
            case REQUIRED:
                builder.arity("1").type(List.class).auxiliaryTypes(String.class);
                Optional.ofNullable(option.getValueName()).ifPresent(builder::paramLabel);
                break;
            default:
                builder.arity("0").type(Boolean.class);
                break;
        }
        return builder.build();
    }
}

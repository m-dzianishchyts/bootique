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

import io.bootique.picocli.CommandLine.Model.OptionSpec;
import io.bootique.picocli.CommandLine.ParseResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Internal wrapper around picocli's ParseResult. Encapsulates picocli API.
 * Provides access to both global and command-specific options.
 */
class PicocliParseResult {

    private final ParseResult rootResult;
    private final Map<String, OptionSpec> globalOptionByName;
    private final Map<String, Map<String, OptionSpec>> commandOptionsByName;

    PicocliParseResult(ParseResult rootResult, Map<String, OptionSpec> globalOptionByName,
                       Map<String, Map<String, OptionSpec>> commandOptionsByName) {
        this.rootResult = rootResult;
        this.globalOptionByName = globalOptionByName;
        this.commandOptionsByName = commandOptionsByName;
    }

    String getCommandName() {
        ParseResult subResult = rootResult.subcommand();
        return subResult != null ? subResult.commandSpec().name() : null;
    }

    boolean hasOption(String optionName) {
        // Check global options
        OptionSpec spec = globalOptionByName.get(optionName);
        if (spec != null && rootResult.hasMatchedOption(spec)) {
            return true;
        }

        // Check command-specific options
        return Optional.ofNullable(getCommandName())
                .map(commandOptionsByName::get)
                .map(cmdOpts -> cmdOpts.get(optionName))
                .flatMap(s -> Optional.ofNullable(rootResult.subcommand())
                        .filter(sr -> sr.hasMatchedOption(s)))
                .isPresent();
    }

    List<String> getOptionStrings(String optionName) {
        // Check global options
        OptionSpec spec = globalOptionByName.get(optionName);
        if (spec != null && rootResult.hasMatchedOption(spec)) {
            return getOptionValues(rootResult, spec);
        }

        // Check command-specific options
        return Optional.ofNullable(getCommandName())
                .map(commandOptionsByName::get)
                .map(cmdOpts -> cmdOpts.get(optionName))
                .flatMap(optionSpec -> Optional.ofNullable(rootResult.subcommand())
                        .filter(command -> command.hasMatchedOption(optionSpec))
                        .map(command -> getOptionValues(command, optionSpec)))
                .orElse(List.of());
    }

    List<String> getDetectedOptions() {
        Stream<String> globalMatches = rootResult.matchedOptions().stream()
                .map(spec -> spec.longestName().substring(2));

        Stream<String> commandMatches = Optional.ofNullable(rootResult.subcommand())
                .map(command -> command.matchedOptions().stream())
                .orElseGet(Stream::empty)
                .map(option -> option.longestName().substring(2));

        return Stream.concat(globalMatches, commandMatches).toList();
    }

    List<String> getStandaloneArguments() {
        if (rootResult.matchedPositionals().isEmpty()) {
            return List.of();
        }
        try {
            Object value = rootResult.matchedPositionalValue(0, null);
            if (value instanceof List<?>) {
                return ((List<?>) value).stream()
                        .map(Object::toString)
                        .toList();
            }
        } catch (Exception e) {
            // Ignore
        }
        return List.of();
    }

    private List<String> getOptionValues(ParseResult parseResult, OptionSpec spec) {
        // Try to get the matched option value
        Object value = null;
        try {
            value = parseResult.matchedOptionValue(spec.longestName(), null);
        } catch (Exception e) {
            // Fallback if method fails
        }

        if (value == null) {
            String fallback = spec.fallbackValue();
            if (fallback != null && !fallback.isEmpty()) {
                return List.of(fallback);
            }
            return List.of();
        }

        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .toList();
        }

        return List.of(value.toString());
    }
}

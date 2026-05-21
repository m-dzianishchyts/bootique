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

import io.bootique.BootiqueException;
import io.bootique.cli.Cli;
import io.bootique.cli.CliFactory;
import io.bootique.cli.NoArgsCli;
import io.bootique.meta.application.ApplicationMetadata;

/**
 * Picocli-based CLI factory. Encapsulates picocli API; no picocli types leak to public interfaces.
 * Uses root + subcommand hierarchy: global options on root, command-specific options on subcommands.
 */
public class PicocliCliFactory implements CliFactory {

    private final Object lock;
    private final ApplicationMetadata application;

    private volatile PicocliCommandLine commandLine;

    public PicocliCliFactory(ApplicationMetadata application) {
        // Provider parameter kept for backward compatibility with DI binding
        this.application = application;
        this.lock = new Object();
    }

    @Override
    public Cli createCli(String[] args) {
        if (args.length == 0) {
            return NoArgsCli.getInstance();
        }
        PicocliParseResult parsed = parse(args);
        return new PicocliCli(parsed);
    }

    private PicocliParseResult parse(String[] args) {
        try {
            return getCommandLine().parseArgs(args);
        } catch (Exception e) {
            throw new BootiqueException(1, e.getMessage(), e);
        }
    }

    private PicocliCommandLine getCommandLine() {
        ensureInitialized();
        return commandLine;
    }

    private void ensureInitialized() {
        if (commandLine == null) {
            synchronized (lock) {
                if (commandLine == null) {
                    commandLine = buildCommandLine();
                }
            }
        }
    }

    protected PicocliCommandLine buildCommandLine() {
        return new PicocliCommandLine(application);
    }
}

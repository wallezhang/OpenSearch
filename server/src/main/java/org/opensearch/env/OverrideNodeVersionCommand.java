/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.env;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.opensearch.OpenSearchException;
import org.opensearch.Version;
import org.opensearch.cli.Terminal;
import org.opensearch.cluster.coordination.OpenSearchNodeCommand;
import org.opensearch.gateway.PersistedClusterStateService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class OverrideNodeVersionCommand extends OpenSearchNodeCommand {
    private static final String TOO_NEW_MESSAGE =
        DELIMITER +
            "\n" +
            "This data path was last written by OpenSearch version [V_NEW] and may no\n" +
            "longer be compatible with OpenSearch version [V_CUR]. This tool will bypass\n" +
            "this compatibility check, allowing a version [V_CUR] node to start on this data\n" +
            "path, but a version [V_CUR] node may not be able to read this data or may read\n" +
            "it incorrectly leading to data loss.\n" +
            "\n" +
            "You should not use this tool. Instead, continue to use a version [V_NEW] node\n" +
            "on this data path. If necessary, you can use reindex-from-remote to copy the\n" +
            "data from here into an older cluster.\n" +
            "\n" +
            "Do you want to proceed?\n";

    private static final String TOO_OLD_MESSAGE =
        DELIMITER +
            "\n" +
            "This data path was last written by OpenSearch version [V_OLD] which may be\n" +
            "too old to be readable by OpenSearch version [V_CUR].  This tool will bypass\n" +
            "this compatibility check, allowing a version [V_CUR] node to start on this data\n" +
            "path, but this version [V_CUR] node may not be able to read this data or may\n" +
            "read it incorrectly leading to data loss.\n" +
            "\n" +
            "You should not use this tool. Instead, upgrade this data path from [V_OLD] to\n" +
            "[V_CUR] using one or more intermediate versions of OpenSearch.\n" +
            "\n" +
            "Do you want to proceed?\n";

    static final String NO_METADATA_MESSAGE = "no node metadata found, so there is no version to override";
    static final String SUCCESS_MESSAGE = "Successfully overwrote this node's metadata to bypass its version compatibility checks.";

    public OverrideNodeVersionCommand() {
        super("Overwrite the version stored in this node's data path with [" + Version.CURRENT +
            "] to bypass the version compatibility checks");
    }

    @Override
    protected void processNodePaths(Terminal terminal, Path[] dataPaths, int nodeLockId, OptionSet options, Environment env)
        throws IOException {
        final Path[] nodePaths = Arrays.stream(toNodePaths(dataPaths)).map(p -> p.path).toArray(Path[]::new);
        final NodeMetadata nodeMetadata = PersistedClusterStateService.nodeMetadata(nodePaths);
        if (nodeMetadata == null) {
            throw new OpenSearchException(NO_METADATA_MESSAGE);
        }

        try {
            nodeMetadata.upgradeToCurrentVersion();
            throw new OpenSearchException("found [" + nodeMetadata + "] which is compatible with current version [" + Version.CURRENT
                + "], so there is no need to override the version checks");
        } catch (IllegalStateException e) {
            // ok, means the version change is not supported
        }

        confirm(terminal, (nodeMetadata.nodeVersion().before(Version.CURRENT) ? TOO_OLD_MESSAGE : TOO_NEW_MESSAGE)
            .replace("V_OLD", nodeMetadata.nodeVersion().toString())
            .replace("V_NEW", nodeMetadata.nodeVersion().toString())
            .replace("V_CUR", Version.CURRENT.toString()));

        PersistedClusterStateService.overrideVersion(Version.CURRENT, dataPaths);

        terminal.println(SUCCESS_MESSAGE);
    }

    //package-private for testing
    OptionParser getParser() {
        return parser;
    }
}

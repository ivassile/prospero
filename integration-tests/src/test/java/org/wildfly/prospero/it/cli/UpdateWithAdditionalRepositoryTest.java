/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it.cli;

import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.test.MetadataTestUtils.upgradeStreamInManifest;

public class UpdateWithAdditionalRepositoryTest extends WfCoreTestBase {

    private File targetDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        targetDir = temp.newFolder();
    }

    @Test
    public void updateCli() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-19-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()));

        install(provisionConfig);

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo();

        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.Y,
                        CliConstants.NO_LOCAL_MAVEN_CACHE,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        final Optional<Stream> wildflyCliStream = ManifestYamlSupport.parse(targetDir.toPath().resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile())
                .getStreams().stream()
                .filter(s -> s.getArtifactId().equals(resolvedUpgradeArtifact.getArtifactId()))
                .findFirst();

        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
        // verify the temporary repository has not been added
        assertThat(ProsperoConfig.readConfig(targetDir.toPath().resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH)).getChannels())
                .flatMap(Channel::getRepositories)
                .map(Repository::getUrl)
                .containsExactlyInAnyOrder("https://repo1.maven.org/maven2/",
                        "https://repository.jboss.org/nexus/content/groups/public-jboss",
                        "https://maven.repository.redhat.com/ga"
                        )
                .doesNotContain(temporaryRepo.toExternalForm());
    }

    private void install(Path provisionConfig) throws Exception {
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, provisionConfig.toString(),
                        CliConstants.FPL, "wildfly-core@maven(org.jboss.universe:community-universe):19.0",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);
    }
}

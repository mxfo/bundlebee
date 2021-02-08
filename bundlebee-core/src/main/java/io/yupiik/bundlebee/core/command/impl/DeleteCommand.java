/*
 * Copyright (c) 2021 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.bundlebee.core.command.impl;

import io.yupiik.bundlebee.core.command.Executable;
import io.yupiik.bundlebee.core.configuration.Description;
import io.yupiik.bundlebee.core.descriptor.Manifest;
import io.yupiik.bundlebee.core.kube.KubeClient;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.bundlebee.core.service.VersioningService;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static io.yupiik.bundlebee.core.lang.CompletionFutures.all;
import static java.util.stream.Collectors.toList;

@Log
@Dependent
public class DeleteCommand implements Executable {
    @Inject
    @Description("Alveolus name to rollback (in currently deployed version). " +
            "When set to `auto`, it will look up all manifests found in the classpath (it is not recommended until you perfectly know what you do). " +
            "If you set manifest option, alveolus is set to `auto` and there is a single alveolus in it, " +
            "this will default to it instead of using classpath deployment.")
    @ConfigProperty(name = "bundlebee.delete.alveolus", defaultValue = "auto")
    private String alveolus;

    @Inject
    @Description("Manifest to load to start to find the alveolus. This optional setting mainly enables to use dependencies easily. " +
            "Ignored if set to `skip`.")
    @ConfigProperty(name = "bundlebee.delete.manifest", defaultValue = "skip")
    private String manifest;

    @Inject
    @Description("Root dependency to download to get the manifest. If set to `auto` it is assumed to be present in current classpath.")
    @ConfigProperty(name = "bundlebee.delete.from", defaultValue = "auto")
    private String from;

    @Inject
    private KubeClient kube;

    @Inject
    private AlveolusHandler visitor;

    @Inject
    private ArchiveReader archives;

    @Inject
    private VersioningService versioningService;

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public String description() {
        return "Delete an alveolus deployment by deleting all related descriptors.\n" +
                "// end of short desc\n" +
                "`bundlebee.delete.propagationPolicy` can be set in descriptor(s) metadata to force default CLI behavior for this descriptor.";
    }

    @Override
    public CompletionStage<?> execute() {
        return internalDelete(from, manifest, alveolus, archives.newCache());
    }

    public CompletionStage<?> internalDelete(final String from, final String manifest, final String alveolus,
                                             final ArchiveReader.Cache cache) {
        return visitor
                .findRootAlveoli(from, manifest, alveolus)
                .thenCompose(alveoli -> all(
                        alveoli.stream()
                                .map(it -> doDelete(cache, it))
                                .collect(toList()), toList(),
                        true));
    }

    public CompletionStage<?> doDelete(final ArchiveReader.Cache cache, final Manifest.Alveolus it) {
        return visitor.executeOnAlveolus(
                "Deleting", it, null,
                (ctx, desc) -> kube.delete(desc.getContent(), desc.getExtension()),
                cache);
    }
}

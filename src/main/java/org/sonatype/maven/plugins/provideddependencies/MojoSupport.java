/*
 * Copyright (c) 2007-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.maven.plugins.provideddependencies;

import java.io.File;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

/**
 * Support for mojo implementations.
 */
public abstract class MojoSupport
    extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     * @readonly
     */
    protected MavenProject project;

    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * The location where generated POM files will be saved.
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File outputDirectory;

    /**
     * The groupId of the generated POM files.
     *
     * @parameter default-value="${project.groupId}"
     */
    protected String groupId;

    /**
     * The artifactId of the dependencies POM (which has dependenciesManagement entries).
     *
     * @parameter default-value="${project.artifactId}-dependencies"
     */
    protected String dependenciesArtifactId;

    /**
     * The artifactId of the compile POM (which has dependencies entries).
     *
     * @parameter default-value="${project.artifactId}-compile"
     */
    protected String compileArtifactId;

    /**
     * The (artifact) version of the generated POM files.
     *
     * @parameter default-value="${project.version}"
     */
    protected String version;
}

package org.sonatype.maven.plugins.provideddependencies;

import java.io.File;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

public abstract class AbstractGenerationMojo
    extends AbstractMojo
{

    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @parameter default-value="${project.groupId}"
     */
    protected String groupId;

    /**
     * @parameter default-value="${project}"
     * @readonly
     */
    protected MavenProject project;
    /**
     * @parameter default-value="${project.build.directory}"
     * @readonly
     */
    protected File target;

    /**
     * @parameter default-value="${project.version}"
     */
    protected String version;

    /**
     * @parameter default-value="${project.artifactId}-dependencies"
     */
    protected String artifactId;

    /**
     * @parameter default-value="${project.artifactId}-compile"
     */
    protected String compileDependenciesArtifactId;

}

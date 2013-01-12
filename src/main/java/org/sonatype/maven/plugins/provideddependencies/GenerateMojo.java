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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.maven.artifact.Artifact.SCOPE_IMPORT;
import static org.apache.maven.artifact.Artifact.SCOPE_SYSTEM;
import static org.apache.maven.artifact.Artifact.SCOPE_TEST;
import static org.apache.maven.artifact.ArtifactUtils.versionlessKey;

/**
 * Generate {@code *-dependencies} and {@code *-compile} POM files.
 *
 * @goal generate
 * @phase prepare-package
 * @requiresDependencyResolution test
 */
public class GenerateMojo
    extends MojoSupport
{
    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * Representation of {@code groupId:artifactId} for use in collections.
     */
    private static class GroupArtifact
    {
        public final String groupId;

        public final String artifactId;

        private GroupArtifact(final String groupId, final String artifactId) {
            this.groupId = checkNotNull(groupId);
            this.artifactId = checkNotNull(artifactId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GroupArtifact that = (GroupArtifact) o;

            if (!artifactId.equals(that.artifactId)) return false;
            if (!groupId.equals(that.groupId)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return versionlessKey(groupId, artifactId);
        }
    }

    private Multimap<String, GroupArtifact> exclusions;

    private String description(final String flavor) {
        return String.format(
            "Automatically generated POM (created by provided-dependencies-maven-plugin) for %s:%s:%s containing all known dependencies as '%s' entries.",
            project.getGroupId(), project.getArtifactId(), project.getVersion(), flavor);
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        exclusions = collectExclusions();

        Model pom = new Model();
        pom.setModelVersion("4.0.0");
        pom.setGroupId(groupId);
        pom.setVersion(version);
        pom.setPackaging("pom");
        pom.setOrganization(project.getOrganization());
        pom.setLicenses(project.getLicenses());

        // build *-dependencies.pom
        getLog().debug("Generating: " + dependenciesArtifactId);
        pom.setDescription(description("dependencyManagement/dependencies"));
        pom.setArtifactId(dependenciesArtifactId);
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(collectDependencies(new ArtifactFilter()
        {
            // exclude scope 'system' and 'import'
            public boolean include(final Artifact artifact) {
                return !(
                    SCOPE_SYSTEM.equals(artifact.getScope()) || SCOPE_IMPORT.equals(artifact.getScope())
                );
            }
        }));
        pom.setDependencyManagement(dependencyManagement);
        persist(pom);

        // FIXME: Consider killing generation of this (*-compile) POM... not sure there is any *valid* use of it at all

        // build *-compile.pom
        getLog().debug("Generating: " + compileArtifactId);
        pom.setDescription(description("dependencies"));
        pom.setArtifactId(compileArtifactId);
        pom.setDependencyManagement(null);
        pom.setDependencies(collectDependencies(new ArtifactFilter()
        {
            // exclude scope 'test', 'system' and 'import'
            public boolean include(final Artifact artifact) {
                return !(
                    SCOPE_TEST.equals(artifact.getScope()) || SCOPE_SYSTEM.equals(artifact.getScope()) || SCOPE_IMPORT.equals(artifact.getScope())
                );
            }
        }));
        persist(pom);
    }

    /**
     * Builds a multimap of dependency groupId:artifactId to exclusions {@link GroupArtifact}.
     */
    private Multimap<String, GroupArtifact> collectExclusions() {
        Multimap<String, GroupArtifact> mapping = LinkedHashMultimap.create();

        for (Artifact artifact : project.getArtifacts()) {
            MavenProject p;
            try {
                p = projectBuilder.buildFromRepository(artifact, project.getRemoteArtifactRepositories(), localRepository);
            }
            catch (ProjectBuildingException e) {
                // ignore
                continue;
            }

            appendExclusions(mapping, p.getDependencies());

            if (p.getDependencyManagement() != null) {
                appendExclusions(mapping, p.getDependencyManagement().getDependencies());
            }
        }

        return mapping;
    }

    private void appendExclusions(final Multimap<String, GroupArtifact> mapping, final List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            for (Exclusion exclusion : dependency.getExclusions()) {
                mapping.put(versionlessKey(dependency.getGroupId(), dependency.getArtifactId()),
                    new GroupArtifact(exclusion.getGroupId(), exclusion.getArtifactId()));
            }
        }
    }

    private List<Dependency> collectDependencies(final @Nullable ArtifactFilter filter) {
        List<Dependency> dependencies = Lists.newArrayList();
        for (Artifact artifact : project.getArtifacts()) {
            if (filter != null && !filter.include(artifact)) {
                getLog().debug("Excluding: " + artifact);
                continue;
            }
            else {
                getLog().debug("Including: " + artifact);
            }

            Dependency dep = new Dependency();
            dep.setGroupId(artifact.getGroupId());
            dep.setArtifactId(artifact.getArtifactId());
            dep.setVersion(artifact.getBaseVersion());
            dep.setClassifier(artifact.getClassifier());
            dep.setType(artifact.getType());

            for (GroupArtifact ga : exclusions.get(versionlessKey(artifact))) {
                Exclusion exclusion = new Exclusion();
                exclusion.setGroupId(ga.groupId);
                exclusion.setArtifactId(ga.artifactId);
                dep.addExclusion(exclusion);
            }

            dependencies.add(dep);
        }

        return dependencies;
    }

    protected void persist(final Model pom)
        throws MojoExecutionException
    {
        File file = new File(outputDirectory, String.format("%s-%s.pom", pom.getArtifactId(), pom.getVersion()));
        file.getParentFile().mkdirs();

        getLog().info("Generating POM file: " + file.getAbsolutePath());

        try {
            Writer writer = new BufferedWriter(new FileWriter(file));
            new MavenXpp3Writer().write(writer, pom);
            writer.flush();
            writer.close();
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed to generate POM file: " + file.getAbsolutePath(), e);
        }

        Artifact artifact = artifactFactory.createArtifact(pom.getGroupId(), pom.getArtifactId(), pom.getVersion(), null, "pom");
        artifact.setFile(file);

        project.addAttachedArtifact(artifact);
    }

}

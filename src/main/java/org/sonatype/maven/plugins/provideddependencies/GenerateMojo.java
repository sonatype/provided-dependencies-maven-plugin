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
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
import org.codehaus.plexus.util.IOUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;

import static org.apache.maven.artifact.Artifact.SCOPE_IMPORT;
import static org.apache.maven.artifact.Artifact.SCOPE_SYSTEM;
import static org.apache.maven.artifact.Artifact.SCOPE_TEST;

/**
 * Generate {@code *-dependencies} and {@code *-compile} POM files.
 *
 * @goal generate
 * @phase generate-resources
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

    private Multimap<String, String> exclusions;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        exclusions = collectExclusions();

        Model pom = new Model();
        pom.setModelVersion( "4.0.0" );
        pom.setGroupId( groupId );
        pom.setArtifactId( dependenciesArtifactId );
        pom.setVersion( version );
        pom.setPackaging( "pom" );
        pom.setLicenses( project.getLicenses() );

        List<Dependency> dependencies = getDependencies();

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies( dependencies );
        pom.setDependencyManagement( dependencyManagement );

        persist( pom );

        pom.setArtifactId(compileArtifactId);
        pom.setDependencyManagement( null );
        pom.setDependencies( dependencies );

        persist( pom );
    }

    /**
     * Builds a multimap of dependency artifactId:groupId to exclusions artifactId:groupId.
     */
    private Multimap<String, String> collectExclusions()
    {
        Multimap<String, String> exclusions = LinkedHashMultimap.create();
        for ( Artifact artifact : project.getArtifacts() )
        {
            MavenProject p;
            try
            {
                p = projectBuilder.buildFromRepository( artifact, project.getRemoteArtifactRepositories(), localRepository );
            }
            catch ( ProjectBuildingException e )
            {
                // ignore
                continue;
            }

            appendExclusions( exclusions, p.getDependencies() );
            if ( p.getDependencyManagement() != null )
            {
                appendExclusions( exclusions, p.getDependencyManagement().getDependencies() );
            }
        }

        return exclusions;
    }

    private void appendExclusions( final Multimap<String, String> exclusions, final List<Dependency> dependencies )
    {
        for ( Dependency dependency : dependencies )
        {
            List<Exclusion> excls = dependency.getExclusions();
            if ( !excls.isEmpty() )
            {
                for ( Exclusion excl : excls )
                {
                    // FIXME: Unsure why we don't simply map to an Exclusion instance
                    // FIXME: ... so we don't have to think about parsing this out later when we configure the dependency
                    exclusions.put(
                        ArtifactUtils.versionlessKey( dependency.getGroupId(), dependency.getArtifactId() ),
                        ArtifactUtils.versionlessKey( excl.getGroupId(), excl.getArtifactId() ) );
                }
            }
        }
    }

    private List<Dependency> getDependencies()
    {
        List<Dependency> dependencies = Lists.newArrayList();
        for ( Artifact artifact : project.getArtifacts() )
        {
            // skip test, system and import scope dependencies
            if ( SCOPE_TEST.equals( artifact.getScope() ) ||
                 SCOPE_SYSTEM.equals( artifact.getScope() ) ||
                 SCOPE_IMPORT.equals( artifact.getScope() ) )
            {
                // do not include
                continue;
            }

            Dependency dep = new Dependency();
            dep.setGroupId( artifact.getGroupId() );
            dep.setArtifactId( artifact.getArtifactId() );
            dep.setVersion( artifact.getBaseVersion() );
            dep.setClassifier( artifact.getClassifier() );
            dep.setType( artifact.getType() );

            configureExclusions(artifact, dep);

            dependencies.add( dep );
        }

        return dependencies;
    }

    private void configureExclusions( final Artifact artifact, final Dependency dependency )
    {
        Collection<String> excls = exclusions.get( ArtifactUtils.versionlessKey(artifact) );
        for ( String exclusion : excls )
        {
            String[] pattern = exclusion.split( ":" );
            if ( pattern.length == 2 )
            {
                Exclusion ex = new Exclusion();
                ex.setGroupId( pattern[0] );
                ex.setArtifactId( pattern[1] );
                dependency.addExclusion(ex);
            }
        }
    }

    protected void persist( final Model pom )
        throws MojoExecutionException
    {
        File file = new File(outputDirectory, String.format("%s-%s.pom", pom.getArtifactId(), pom.getVersion()));
        file.getParentFile().mkdirs();

        getLog().info("Generating POM file: " + file.getAbsolutePath());

        Writer writer = null;
        try
        {
            writer = new BufferedWriter(new FileWriter( file ));
            new MavenXpp3Writer().write( writer, pom );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to generate POM file: " + file.getAbsolutePath(), e );
        }
        finally
        {
            IOUtil.close( writer );
        }

        Artifact artifact = artifactFactory.createArtifact( pom.getGroupId(), pom.getArtifactId(), pom.getVersion(), null, "pom" );
        artifact.setFile( file );
    
        project.addAttachedArtifact( artifact );
    }

}

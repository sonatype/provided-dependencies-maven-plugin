package org.sonatype.maven.plugins.provideddependencies;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.util.IOUtil;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * @author velo
 * @goal generate
 * @requiresDependencyResolution test
 * @phase generate-resources
 */
public class GenerateMojo
    extends AbstractMojo
{

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @parameter default-value="${project.artifactId}-dependencies"
     */
    private String artifactId;

    /**
     * @parameter default-value="${project.artifacts}"
     * @readonly
     */
    private Collection<Artifact> artifacts;

    /**
     * @parameter default-value="${project.artifactId}-compile"
     */
    private String compileDependenciesArtifactId;

    /**
     * @parameter default-value="${project.groupId}"
     */
    private String groupId;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /**
     * @parameter default-value="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * @parameter default-value="${project.build.directory}"
     * @readonly
     */
    private File target;

    /**
     * @parameter default-value="${project.version}"
     */
    private String version;

    private void appendExclusions( Multimap<String, String> exclusions, List<Dependency> dependencies )
    {
        for ( Dependency dependency : dependencies )
        {
            List<Exclusion> excls = dependency.getExclusions();
            if ( !excls.isEmpty() )
            {
                for ( Exclusion excl : excls )
                {
                    exclusions.put(
                        ArtifactUtils.versionlessKey( dependency.getGroupId(), dependency.getArtifactId() ),
                        ArtifactUtils.versionlessKey( excl.getGroupId(), excl.getArtifactId() ) );
                }
            }
        }
    }

    private Multimap<String, String> collectExclusions()
    {
        Multimap<String, String> exclusions = LinkedHashMultimap.create();
        for ( Artifact artifact : artifacts )
        {
            MavenProject p;
            try
            {
                p = projectBuilder.buildFromRepository( artifact, remoteRepositories, localRepository );
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

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

        Multimap<String, String> exclusions = collectExclusions();

        Model pom = new Model();
        pom.setModelVersion( "4.0.0" );
        pom.setGroupId( groupId );
        pom.setArtifactId( artifactId );
        pom.setVersion( version );
        pom.setPackaging( "pom" );

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies( getDependencies( Artifact.SCOPE_PROVIDED, exclusions ) );
        pom.setDependencyManagement( dependencyManagement );

        persist( pom, "-dependencies.pom" );

        pom.setDependencyManagement( null );
        pom.setDependencies( getDependencies( Artifact.SCOPE_COMPILE, exclusions ) );
        pom.setArtifactId( compileDependenciesArtifactId );
        persist( pom, "-compile.pom" );
    }

    private List<Dependency> getDependencies( String scope, Multimap<String, String> exclusions )
    {
        List<Dependency> dependencies = new ArrayList<Dependency>();
        for ( Artifact artifact : artifacts )
        {
            Dependency dep = new Dependency();
            dep.setGroupId( artifact.getGroupId() );
            dep.setArtifactId( artifact.getArtifactId() );
            dep.setVersion( artifact.getBaseVersion() );
            dep.setClassifier( artifact.getClassifier() );
            dep.setType( artifact.getType() );

            if ( Artifact.SCOPE_TEST.equals( artifact.getScope() )
                || Artifact.SCOPE_SYSTEM.equals( artifact.getScope() )
                || Artifact.SCOPE_IMPORT.equals( artifact.getScope() ) )
            {
                // dep.setScope( Artifact.SCOPE_TEST );
                continue;
            }
            else
            {
                dep.setScope( scope );
            }

            Collection<String> excls = exclusions.get( ArtifactUtils.versionlessKey( artifact ) );
            for ( String exclusion : excls )
            {
                String[] pattern = exclusion.split( ":" );
                if ( pattern.length == 2 )
                {
                    Exclusion ex = new Exclusion();
                    ex.setGroupId( pattern[0] );
                    ex.setArtifactId( pattern[1] );
                    dep.addExclusion( ex );
                }
            }

            dependencies.add( dep );
        }

        return dependencies;
    }

    private void persist( Model pom, String suffix )
        throws MojoExecutionException
    {
        File file = new File( target, pom.getArtifactId() + suffix );

        FileWriter writer = null;
        try
        {
            file.createNewFile();
            writer = new FileWriter( file );
            new MavenXpp3Writer().write( writer, pom );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to generate pom", e );
        }
        IOUtil.close( writer );

        Artifact artifact = artifactFactory.createArtifact( groupId, pom.getArtifactId(), version, null, "pom" );
        artifact.setFile( file );

        project.addAttachedArtifact( artifact );
    }

}

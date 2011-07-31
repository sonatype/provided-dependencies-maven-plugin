package org.sonatype.maven.plugins.provideddependencies;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
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
    extends AbstractGenerationMojo
{

    /**
     * @parameter default-value="${project.artifacts}"
     * @readonly
     */
    private Collection<Artifact> artifacts;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * @parameter default-value="${project.name}"
     */
    private String name;

    /**
     * @parameter default-value="${project.description}"
     */
    private String description;

    /**
     * @parameter default-value="${project.url}"
     */
    private String url;

    /**
     * @parameter default-value="${project.licenses}"
     */
    private List licenses;

    /**
     * @parameter default-value="${project.scm}"
     */
    private Scm scm;

    /**
     * @parameter default-value="${project.contributors}"
     */
    private List contributors;

    /**
     * @parameter default-value="${project.developers}"
     */
    private List developers;

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
        
        pom.setName( name );
        pom.setDescription( description );
        pom.setUrl( url );
        pom.setLicenses( licenses );
        pom.setScm( scm );
        pom.setContributors( contributors );
        pom.setDevelopers( developers );

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies( getDependencies( Artifact.SCOPE_PROVIDED, exclusions ) );
        pom.setDependencyManagement( dependencyManagement );

        persist( pom );

        pom.setArtifactId( compileDependenciesArtifactId );
        pom.setDependencyManagement( null );
        pom.setDependencies( getDependencies( Artifact.SCOPE_COMPILE, exclusions ) );

        persist( pom );
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

    protected void persist( Model pom )
        throws MojoExecutionException
    {
        String suffix = "-" + version + "." + "pom";
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

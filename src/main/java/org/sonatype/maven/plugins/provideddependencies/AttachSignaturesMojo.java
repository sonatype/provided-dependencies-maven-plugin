package org.sonatype.maven.plugins.provideddependencies;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * @author Benjamin Hanzelmann
 * @goal attach-signatures
 * @phase verify
 */
public class AttachSignaturesMojo
    extends AbstractGenerationMojo
{

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String suffix = "-" + version + ".pom.asc";
        attachSignature( artifactId, suffix );
        attachSignature( compileDependenciesArtifactId, suffix );
    }

    private void attachSignature( String aid, String suffix )
    {
        File file = new File( target, aid + suffix );
        if ( file.exists() )
        {
            Artifact artifact = artifactFactory.createArtifact( groupId, aid, version, null, "pom.asc" );
            artifact.setFile( file );

            project.addAttachedArtifact( artifact );
        }
    }

}

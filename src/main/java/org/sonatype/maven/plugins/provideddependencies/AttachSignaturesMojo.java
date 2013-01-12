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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;

/**
 * Attach GPG signature files, if they exist for the generated POM files.
 *
 * @goal attach-signatures
 * @phase verify
 */
public class AttachSignaturesMojo
    extends MojoSupport
{
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String suffix = String.format("-%s.pom.asc", version);
        attachSignature(dependenciesArtifactId, suffix );
        attachSignature(compileArtifactId, suffix );
    }

    private void attachSignature( final String artifactId, final String suffix )
    {
        File file = new File(outputDirectory, artifactId + suffix );
        if ( file.exists() )
        {
            Artifact artifact = artifactFactory.createArtifact( groupId, artifactId, version, null, "pom.asc" );
            artifact.setFile( file );

            project.addAttachedArtifact( artifact );
        }
    }
}

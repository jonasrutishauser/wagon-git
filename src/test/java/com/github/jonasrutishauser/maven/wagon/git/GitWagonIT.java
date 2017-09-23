package com.github.jonasrutishauser.maven.wagon.git;

/*
 * Copyright (C) 2017 Jonas Rutishauser
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation version 3 of the License.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.txt>.
 */

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith( MavenJUnitTestRunner.class )
@MavenVersions( { "3.3.3", "3.3.9", "3.5.0" } )
public class GitWagonIT
{

    @Rule
    public final TestResources resources = new TestResources();

    public final MavenRuntime mavenRuntime;

    public GitWagonIT( MavenRuntimeBuilder builder )
        throws Exception
    {
        this.mavenRuntime = builder.withCliOptions( "-X" ).build();
    }

    @Test
    public void siteDeploySimpleUrl()
        throws Exception
    {
        File basedir = resources.getBasedir( "site" );
        File repo = createRemoteRepo( basedir );

        MavenExecutionResult result = mavenRuntime.forProject( basedir ).withCliOption( "-Dsite.url=git:" + repo.toURI()
            + "!/" ).execute( "clean", "site-deploy" );

        result.assertErrorFreeLog();
        assertTrue( new File( repo, "refs/heads/master" ).exists() );
        File clone = cloneRemoteRepo( basedir, repo, "master" );
        assertTrue( new File( clone, "index.html" ).exists() );
        assertTrue( new File( clone, "site-sub/index.html" ).exists() );
    }

    @Test
    public void siteDeployUrlWithDirectory()
        throws Exception
    {
        File basedir = resources.getBasedir( "site" );
        File repo = createRemoteRepo( basedir );

        MavenExecutionResult result = mavenRuntime.forProject( basedir ).withCliOption( "-Dsite.url=git:" + repo.toURI()
            + "!test/" ).execute( "clean", "site-deploy" );

        result.assertErrorFreeLog();
        assertTrue( new File( repo, "refs/heads/master" ).exists() );
        File clone = cloneRemoteRepo( basedir, repo, "master" );
        assertTrue( new File( clone, "test/index.html" ).exists() );
        assertTrue( new File( clone, "test/site-sub/index.html" ).exists() );
    }

    @Test
    public void siteDeployUrlWithBranch()
        throws Exception
    {
        File basedir = resources.getBasedir( "site" );
        File repo = createRemoteRepo( basedir );

        MavenExecutionResult result = mavenRuntime.forProject( basedir ).withCliOption( "-Dsite.url=git:" + repo.toURI()
            + "!gh-pages!/" ).execute( "clean", "site-deploy" );

        result.assertErrorFreeLog();
        assertTrue( new File( repo, "refs/heads/gh-pages" ).exists() );
        File clone = cloneRemoteRepo( basedir, repo, "gh-pages" );
        assertTrue( new File( clone, "index.html" ).exists() );
        assertTrue( new File( clone, "site-sub/index.html" ).exists() );
    }

    @Test
    public void siteDeployUrlWithBranchAndDirectory()
        throws Exception
    {
        File basedir = resources.getBasedir( "site" );
        File repo = createRemoteRepo( basedir );

        MavenExecutionResult result = mavenRuntime.forProject( basedir ).withCliOption( "-Dsite.url=git:" + repo.toURI()
            + "!gh-pages!/some/dir/" ).execute( "clean", "site-deploy" );

        result.assertErrorFreeLog();
        assertTrue( new File( repo, "refs/heads/gh-pages" ).exists() );
        File clone = cloneRemoteRepo( basedir, repo, "gh-pages" );
        assertTrue( new File( clone, "some/dir/index.html" ).exists() );
        assertTrue( new File( clone, "some/dir/site-sub/index.html" ).exists() );
    }

    private File cloneRemoteRepo( File basedir, File repo, String branch )
        throws ExecuteException, IOException
    {
        File clone = new File( basedir, "clone" );
        clone.mkdir();
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory( clone );
        executor.execute( CommandLine.parse( "git clone -b " + branch + " " + repo.getAbsolutePath() + " ./" ) );
        return clone;
    }

    private File createRemoteRepo( File basedir )
        throws ExecuteException, IOException
    {
        File repo = new File( basedir, "repo.git" );
        repo.mkdir();
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory( repo );
        executor.execute( CommandLine.parse( "git init --bare" ) );
        return repo;
    }

}

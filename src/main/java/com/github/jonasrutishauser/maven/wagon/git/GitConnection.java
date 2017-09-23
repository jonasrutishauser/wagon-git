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

import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jonasrutishauser.maven.wagon.git.exception.GitAuthenticationException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitCloneException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitPushException;
import com.github.jonasrutishauser.maven.wagon.git.exception.NoSuchResourceInGitException;
import com.github.jonasrutishauser.maven.wagon.git.util.LoggerProgressMonitor;

public class GitConnection
    implements AutoCloseable
{

    private static final Logger LOGGER = LoggerFactory.getLogger( GitConnection.class );

    private final Git git;

    private final CredentialsProvider credentialsProvider;

    private final Path workingDirectory;

    private GitConnection( Git git, CredentialsProvider credentialsProvider, Path pathInWorkingDirectory )
    {
        this.git = git;
        this.credentialsProvider = credentialsProvider;
        this.workingDirectory = git.getRepository().getWorkTree().toPath().resolve( pathInWorkingDirectory );
    }

    public static GitConnection open( GitConfiguration configuration, Optional<String> username,
                                      Optional<String> password )
        throws GitCloneException, GitAuthenticationException
    {
        String branch = configuration.getBranch().orElse( Constants.MASTER );
        CredentialsProvider credentialsProvider = null;
        if ( username.isPresent() )
        {
            credentialsProvider =
                new UsernamePasswordCredentialsProvider( username.get(),
                                                         password.map( String::toCharArray ).orElse( null ) );
        }
        File workingDirectory = null;
        Git git;
        try
        {
            workingDirectory = configuration.getWorkingDirectory().toFile();
            git = Git.init().setDirectory( workingDirectory ).call();
            RemoteConfig remoteConfig = setRemote( configuration.getUrl(), git );
            if ( remoteHasBranch( git, branch ) )
            {
                fetchAndCheckoutBranch( git, branch, remoteConfig, credentialsProvider );
            }
            else if ( configuration.getBranch().isPresent() )
            {
                git.checkout().setName( branch ).setOrphan( true ).call();
            }
        }
        catch ( GitAPIException | IOException | URISyntaxException e )
        {
            if ( workingDirectory != null )
            {
                deleteWorkTree( workingDirectory );
            }
            if ( e instanceof TransportException && isAuthenticationFailureMessage( e.getMessage() ) )
            {
                throw new GitAuthenticationException( "invalid credentials for repository: " + configuration.getUrl(),
                                                      e );
            }
            throw new GitCloneException( "failed to clone from remote repository: " + e.getMessage(), e );
        }
        return new GitConnection( git, credentialsProvider, configuration.getPath().orElse( Paths.get( "" ) ) );
    }

    public boolean getIfNewer( Path resource, Path destination, long timestamp )
        throws NoSuchResourceInGitException, GitException
    {
        Path realResource = workingDirectory.resolve( resource );
        if ( !Files.exists( realResource ) )
        {
            throw new NoSuchResourceInGitException( "resource '" + realResource + "' does not exist" );
        }
        if ( getCommitTime( realResource ) <= timestamp )
        {
            return false;
        }
        try
        {
            copy( realResource, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES );
        }
        catch ( IOException e )
        {
            throw new GitException( "failed to read resource: " + e.getMessage(), e );
        }
        return true;
    }

    public void put( Path source, Path destination )
        throws GitException
    {
        Path realDestination = workingDirectory.resolve( destination ).normalize();
        try
        {
            if ( !Files.isDirectory( workingDirectory ) )
            {
                Files.createDirectories( workingDirectory );
            }
            copy( source, realDestination, StandardCopyOption.REPLACE_EXISTING );
        }
        catch ( IOException e )
        {
            throw new GitException( "failed to write resource: " + e.getMessage(), e );
        }
        try
        {
            git.add().addFilepattern( getRepoPath( realDestination ) ).call();
        }
        catch ( GitAPIException e )
        {
            throw new GitException( "failed to write resource: " + e.getMessage(), e );
        }
    }

    @Override
    public void close()
        throws GitPushException, GitAuthenticationException
    {
        try
        {
            if ( needsPush() )
            {
                pushChanges();
            }
        }
        catch ( GitAPIException e )
        {
            throw new GitPushException( "failed to push all changes to the remote repository: " + e.getMessage(), e );
        }
        File workTree = git.getRepository().getWorkTree();
        git.close();
        deleteWorkTree( workTree );
    }

    private void copy( Path source, Path destination, CopyOption... copyOptions )
        throws IOException
    {
        if ( Files.isDirectory( source ) )
        {
            copyDirectory( source, destination, copyOptions );
        }
        else
        {
            Files.copy( source, destination, copyOptions );
        }
    }

    private void copyDirectory( Path source, Path destination, CopyOption... copyOptions )
        throws IOException
    {
        Files.walkFileTree( source, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs )
                throws IOException
            {
                Path targetDir = destination.resolve( source.relativize( dir ) );
                if ( !Files.isDirectory( targetDir ) )
                {
                    Files.createDirectory( targetDir );
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
                throws IOException
            {
                Files.copy( file, destination.resolve( source.relativize( file ) ), copyOptions );
                return FileVisitResult.CONTINUE;
            }
        } );
    }

    private long getCommitTime( Path realResource )
        throws GitException
    {
        LogCommand logCommand = git.log().addPath( getRepoPath( realResource ) ).setMaxCount( 1 );
        long commitTime = Instant.now().getEpochSecond();
        try
        {
            for ( RevCommit commit : logCommand.call() )
            {
                commitTime = commit.getCommitTime();
            }
        }
        catch ( GitAPIException e )
        {
            throw new GitException( "failed to get git history", e );
        }
        return commitTime;
    }

    private String getRepoPath( Path realPath )
    {
        String path = git.getRepository().getWorkTree().toPath().relativize( realPath ).toString();
        return path.isEmpty() ? "." : path;
    }

    private boolean needsPush()
        throws GitAPIException
    {
        return git.status().call().hasUncommittedChanges();
    }

    private void pushChanges()
        throws GitAPIException, GitPushException
    {
        git.commit().setMessage( "[wagon-git] adding files to repository" ).call();
        PushCommand pushCommand = git.push().setCredentialsProvider( credentialsProvider );
        pushCommand.setProgressMonitor( getProgressMonitor() );
        for ( PushResult result : pushCommand.call() )
        {
            for ( RemoteRefUpdate update : result.getRemoteUpdates() )
            {
                if ( update.getStatus() != OK )
                {
                    throw new GitPushException( "failed to push all changes to the remote repository: "
                        + update.getStatus() );
                }
            }
        }
    }

    private static void deleteWorkTree( File workTree )
    {
        try
        {
            Files.walkFileTree( workTree.toPath(), new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
                    throws IOException
                {
                    Files.delete( file );
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory( Path dir, IOException exc )
                    throws IOException
                {
                    Files.delete( dir );
                    return FileVisitResult.CONTINUE;
                }
            } );
        }
        catch ( IOException e )
        {
            LOGGER.warn( "failed to delete closed local repository: " + e.getMessage(), e );
        }
    }

    private static RemoteConfig setRemote( String url, Git git )
        throws URISyntaxException, GitAPIException
    {
        RemoteAddCommand remoteAdd = git.remoteAdd();
        remoteAdd.setName( Constants.DEFAULT_REMOTE_NAME );
        remoteAdd.setUri( new URIish( url ) );
        RemoteConfig remoteConfig = remoteAdd.call();
        return remoteConfig;
    }

    private static boolean remoteHasBranch( Git git, String branch )
        throws GitAPIException, InvalidRemoteException, TransportException
    {
        return git.lsRemote().callAsMap().containsKey( Constants.R_HEADS + branch );
    }

    private static void fetchAndCheckoutBranch( Git git, String branch, RemoteConfig remoteConfig,
                                                CredentialsProvider credentialsProvider )
        throws GitAPIException, IOException
    {
        RefSpec refSpec = remoteConfig.getFetchRefSpecs().get( 0 ).expandFromSource( Constants.R_HEADS + branch );
        FetchCommand fetchCommand = git.fetch().setCredentialsProvider( credentialsProvider );
        fetchCommand.setRefSpecs( refSpec ).setProgressMonitor( getProgressMonitor() ).call();
        git.checkout().setName( branch ).setCreateBranch( true ).setStartPoint( refSpec.getDestination() ).call();
    }

    private static boolean isAuthenticationFailureMessage( String message )
    {
        return message.contains( "CredentialsProvider" ) || message.toLowerCase().contains( "auth" );
    }

    private static ProgressMonitor getProgressMonitor()
    {
        return new LoggerProgressMonitor();
    }

}

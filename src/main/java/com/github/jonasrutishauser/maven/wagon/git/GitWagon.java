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

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;

import com.github.jonasrutishauser.maven.wagon.git.exception.GitAuthenticationException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitCloneException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitPushException;
import com.github.jonasrutishauser.maven.wagon.git.exception.NoSuchResourceInGitException;

public class GitWagon
    extends AbstractWagon
{

    private GitConnection connection;

    @Override
    protected void openConnectionInternal()
        throws ConnectionException, AuthenticationException
    {
        GitConfiguration configuration = GitConfiguration.parse( getRepository().getUrl() );
        try
        {
            connection = openGitConnection( configuration );
        }
        catch ( GitCloneException e )
        {
            throw new ConnectionException( "failed to open a git connection: " + e.getMessage(), e );
        }
        catch ( GitAuthenticationException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }

    GitConnection openGitConnection( GitConfiguration configuration )
        throws GitCloneException, GitAuthenticationException
    {
        return GitConnection.open( configuration, Optional.ofNullable( getAuthenticationInfo().getUserName() ),
                                   Optional.ofNullable( getAuthenticationInfo().getPassword() ) );
    }

    @Override
    protected void closeConnection()
        throws ConnectionException
    {
        try
        {
            connection.close();
        }
        catch ( GitPushException | GitAuthenticationException e )
        {
            throw new ConnectionException( "failed to close the git connection: " + e.getMessage(), e );
        }
    }

    @Override
    public void get( String resourceName, File destination )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        getIfNewer( resourceName, destination, Long.MIN_VALUE );
    }

    @Override
    public boolean getIfNewer( String resourceName, File destination, long timestamp )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        try
        {
            return connection.getIfNewer( Paths.get( resourceName ), destination.toPath(), timestamp );
        }
        catch ( NoSuchResourceInGitException e )
        {
            throw new ResourceDoesNotExistException( "resource does not exist in git: " + e.getMessage(), e );
        }
        catch ( GitException e )
        {
            throw new TransferFailedException( "failed to get git resource: " + e.getMessage(), e );
        }
    }

    @Override
    public void put( File source, String destination )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        try
        {
            connection.put( source.toPath(), Paths.get( destination ));
        }
        catch ( GitException e )
        {
            throw new TransferFailedException( "failed to put git resource: " + e.getMessage(), e );
        }
    }

    @Override
    public void putDirectory( File sourceDirectory, String destinationDirectory )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        put( sourceDirectory, destinationDirectory );
    }

    @Override
    public boolean supportsDirectoryCopy()
    {
        return true;
    }

}

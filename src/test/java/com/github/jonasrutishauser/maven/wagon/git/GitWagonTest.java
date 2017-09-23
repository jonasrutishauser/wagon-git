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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Paths;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.jonasrutishauser.maven.wagon.git.exception.GitAuthenticationException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitCloneException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitPushException;
import com.github.jonasrutishauser.maven.wagon.git.exception.NoSuchResourceInGitException;

@DisplayName( "GitWagon" )
public class GitWagonTest
{

    @Test
    @DisplayName( "openConnectionInternal() opens a GitConnection" )
    void openConnectionInternal_opensGitConnection()
    {
        RuntimeException expectedCall = new RuntimeException( "expectedCall" );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                throw expectedCall;
            }
        };

        RuntimeException exception =
            assertThrows( RuntimeException.class, () -> testee.connect( new Repository( "foo", "git:test" ) ) );

        assertSame( expectedCall, exception );
    }

    @Test
    @DisplayName( "openConnectionInternal() with a GitCloneException on GitConnection.open() throws a ConnectionException" )
    void openConnectionInternal_gitCloneException_throwsConnectionException()
    {
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                throw new GitCloneException( "test", null );
            }
        };

        assertThrows( ConnectionException.class, () -> testee.connect( new Repository( "foo", "git:test" ) ) );
    }

    @Test
    @DisplayName( "openConnectionInternal() with a GitAuthenticationException on GitConnection.open() throws a AuthenticationException" )
    void openConnectionInternal_gitAuthenticationException_throwsAuthenticationException()
    {
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                throw new GitAuthenticationException( "test", null );
            }
        };

        assertThrows( AuthenticationException.class, () -> testee.connect( new Repository( "foo", "git:test" ) ) );
    }

    @Test
    @DisplayName( "getIfNewer() calls GitConnection.getIfNewer()" )
    void getIfNewer_callsGetIfNewerOnGitConnection()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        File target = new File( "target" );
        doReturn( Boolean.TRUE ).when( gitConnection ).getIfNewer( Paths.get( "foo" ), Paths.get( "target" ), 42 );

        assertTrue( testee.getIfNewer( "foo", target, 42 ) );

        verify( gitConnection ).getIfNewer( Paths.get( "foo" ), Paths.get( "target" ), 42 );
    }

    @Test
    @DisplayName( "getIfNewer() with a NoSuchResourceInGitException on GitConnection.getIfNewer() throws a ResourceDoesNotExistException" )
    void getIfNewer_noSuchResourceInGitException_throwsResourceDoesNotExistException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( NoSuchResourceInGitException.class ).when( gitConnection ).getIfNewer( any(), any(), anyLong() );

        assertThrows( ResourceDoesNotExistException.class, () -> testee.getIfNewer( "foo", new File( "target" ), 0 ) );
    }

    @Test
    @DisplayName( "getIfNewer() with a GitException on GitConnection.getIfNewer() throws a TransferFailedException" )
    void getIfNewer_gitException_throwsTransferFailedException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitException.class ).when( gitConnection ).getIfNewer( any(), any(), anyLong() );

        assertThrows( TransferFailedException.class, () -> testee.getIfNewer( "foo", new File( "target" ), 0 ) );
    }

    @Test
    @DisplayName( "get() calls GitConnection.getIfNewer()" )
    void get_callsGetIfNewerOnGitConnection()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        File target = new File( "target" );

        testee.get( "foo", target );

        verify( gitConnection ).getIfNewer( Paths.get( "foo" ), Paths.get( "target" ), Long.MIN_VALUE );
    }

    @Test
    @DisplayName( "get() with a NoSuchResourceInGitException on GitConnection.getIfNewer() throws a ResourceDoesNotExistException" )
    void get_noSuchResourceInGitException_throwsResourceDoesNotExistException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( NoSuchResourceInGitException.class ).when( gitConnection ).getIfNewer( any(), any(), anyLong() );

        assertThrows( ResourceDoesNotExistException.class, () -> testee.get( "foo", new File( "target" ) ) );
    }

    @Test
    @DisplayName( "get() with a GitException on GitConnection.getIfNewer() throws a TransferFailedException" )
    void get_gitException_throwsTransferFailedException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitException.class ).when( gitConnection ).getIfNewer( any(), any(), anyLong() );

        assertThrows( TransferFailedException.class, () -> testee.get( "foo", new File( "target" ) ) );
    }

    @Test
    @DisplayName( "putDirectory() calls GitConnection.put()" )
    void putDirectory_callsPutOnGitConnection()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        File source = new File( "source" );

        testee.putDirectory( source, "foo" );

        verify( gitConnection ).put( Paths.get( "source" ), Paths.get( "foo" ) );
    }

    @Test
    @DisplayName( "putDirectory() with a GitException on GitConnection.put() throws a TransferFailedException" )
    void putDirectory_gitException_throwsTransferFailedException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitException.class ).when( gitConnection ).put( any(), any() );

        assertThrows( TransferFailedException.class, () -> testee.putDirectory( new File( "source" ), "foo" ) );
    }

    @Test
    @DisplayName( "put() calls GitConnection.put()" )
    void put_callsPutOnGitConnection()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        File source = new File( "source" );

        testee.put( source, "foo" );

        verify( gitConnection ).put( Paths.get( "source" ), Paths.get( "foo" ) );
    }

    @Test
    @DisplayName( "put() with a GitException on GitConnection.put() throws a TransferFailedException" )
    void put_gitException_throwsTransferFailedException()
        throws Exception
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitException.class ).when( gitConnection ).put( any(), any() );

        assertThrows( TransferFailedException.class, () -> testee.put( new File( "source" ), "foo" ) );
    }

    @Test
    @DisplayName( "closeConnection() calls GitConnection.close()" )
    void closeConnection_callsCloseOnGitConnection()
        throws ConnectionException, AuthenticationException, GitPushException, GitAuthenticationException
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );

        testee.closeConnection();

        verify( gitConnection ).close();
    }

    @Test
    @DisplayName( "closeConnection() with a GitPushException on GitConnection.close() throws a ConnectionException" )
    void closeConnection_gitPushException_throwsConnectionException()
        throws ConnectionException, AuthenticationException, GitPushException, GitAuthenticationException
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitPushException.class ).when( gitConnection ).close();

        assertThrows( ConnectionException.class, () -> testee.closeConnection() );
    }

    @Test
    @DisplayName( "closeConnection() with a GitAuthenticationException on GitConnection.close() throws a ConnectionException" )
    void closeConnection_gitAuthenticationException_throwsConnectionException()
        throws ConnectionException, AuthenticationException, GitPushException, GitAuthenticationException
    {
        GitConnection gitConnection = mock( GitConnection.class );
        GitWagon testee = new GitWagon()
        {
            @Override
            protected GitConnection openGitConnection( GitConfiguration configuration )
                throws GitCloneException, GitAuthenticationException
            {
                return gitConnection;
            }
        };
        testee.connect( new Repository( "foo", "git:test" ) );
        doThrow( GitAuthenticationException.class ).when( gitConnection ).close();

        assertThrows( ConnectionException.class, () -> testee.closeConnection() );
    }

    @Test
    @DisplayName( "supportsDirectoryCopy() returns true" )
    void supportsDirectoryCopy()
    {
        GitWagon testee = new GitWagon();

        assertTrue( testee.supportsDirectoryCopy() );
    }

}

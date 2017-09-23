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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName( "GitConfiguration" )
public class GitConfigurationTest
{

    private static class ValidParseArguments
        implements ArgumentsProvider
    {

        private static final List<String> GIT_URLS =
            Arrays.asList( "file:/repos/test", "ssh://git@github.com/jonasrutishauser/test.git",
                           "git@github.com:jonasrutishauser/test.git" );

        private static final List<Optional<String>> BRANCHES =
            Arrays.asList( Optional.empty(), Optional.of( "master" ), Optional.of( "gh-pages" ),
                           Optional.of( "feature/test" ) );

        private static final List<Optional<Path>> PATHS =
            Arrays.asList( Optional.empty(), Optional.of( Paths.get( "snapshot" ) ),
                           Optional.of( Paths.get( "snapshot/test" ) ) );

        @Override
        public Stream<? extends Arguments> provideArguments( ExtensionContext context )
        {
            return GIT_URLS.stream().flatMap( this::addBranch ).flatMap( this::addPaths ).flatMap( this::addInput );
        }

        private Stream<Object[]> addBranch( String gitUrl )
        {
            return BRANCHES.stream().map( branch -> new Object[] { gitUrl, branch } );
        }

        private Stream<Object[]> addPaths( Object[] arguments )
        {
            return PATHS.stream().map( path -> new Object[] { arguments[0], arguments[1], path } );
        }

        private Stream<? extends Arguments> addInput( Object[] arguments )
        {
            Object gitUrl = arguments[0];
            Optional<?> branch = (Optional<?>) arguments[1];
            Optional<?> path = (Optional<?>) arguments[2];
            StringBuilder url = new StringBuilder( "git:" );
            url.append( gitUrl );
            if ( branch.isPresent() )
            {
                url.append( '!' ).append( branch.get() );
            }
            List<String> urls = new ArrayList<>();
            if ( path.isPresent() )
            {
                url.append( '!' );
                int offset = url.length();
                url.append( path.get() );
                urls.add( url.toString() );
                urls.add( url.toString() + '/' );
                url.insert( offset, '/' );
                urls.add( url.toString() );
                url.append( '/' );
                urls.add( url.toString() );
            }
            else
            {
                if ( !branch.isPresent() )
                {
                    urls.add( url.toString() );
                }
                url.append( '!' );
                urls.add( url.toString() );
                url.append( '/' );
                urls.add( url.toString() );
            }
            url.insert( 4, "//foo/" );
            urls.add( url.toString() );
            return urls.stream().map( input -> Arguments.of( input, gitUrl, branch, path ) );
        }
    }

    @DisplayName( "parse()" )
    @ParameterizedTest( name = "{0} => gitUrl={1}, branch={2}, path={3}" )
    @ArgumentsSource( ValidParseArguments.class )
    void parse( String url, String gitUrl, Optional<String> branch, Optional<Path> path )
    {
        GitConfiguration configuration = GitConfiguration.parse( url );

        assertAll( () -> assertEquals( gitUrl, configuration.getUrl() ),
                   () -> assertEquals( branch, configuration.getBranch() ),
                   () -> assertEquals( path, configuration.getPath() ) );
    }

    @DisplayName( "parse() of invalid url" )
    @ParameterizedTest( name = "{0}" )
    @ValueSource( strings = { "file:/repos/test", "git:http://foo.bar/test.git!!" } )
    void parse_invalid( String url )
    {
        assertThrows( IllegalArgumentException.class, () -> GitConfiguration.parse( url ) );
    }

    @Test
    @DisplayName( "getWorkingDirectory() returns allways the same directory" )
    void getWorkingDirectory_returnsAllwaysTheSame()
        throws IOException
    {
        GitConfiguration testee = GitConfiguration.parse( "git:foo" );

        Path workingDirectory = testee.getWorkingDirectory();
        try
        {
            assertSame( workingDirectory, testee.getWorkingDirectory() );
        }
        finally
        {
            Files.delete( workingDirectory );
        }
    }

}

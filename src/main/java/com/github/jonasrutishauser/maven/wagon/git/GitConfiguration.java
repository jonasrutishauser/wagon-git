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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitConfiguration
{

    /**
     * URL format: git:[url]((![branch])?!/?[path])?
     */
    private static final Pattern URL_PATTERN =
        Pattern.compile( "git:(?<url>[^!]+)((!(?<branch>[^!]+))?!/?(?<path>[^!]+)?)?" );

    private final String url;

    private final Optional<String> branch;

    private final Optional<Path> path;

    private Path workingDirectory;

    private GitConfiguration( String url, Optional<String> branch, Optional<Path> path )
    {
        this.url = url;
        this.branch = branch;
        this.path = path;
    }

    public static GitConfiguration parse( String url )
    {
        Matcher matcher = URL_PATTERN.matcher( url );
        if ( !matcher.matches() )
        {
            throw new IllegalArgumentException( "URL not valid" );
        }
        return new GitConfiguration( matcher.group( "url" ), Optional.ofNullable( matcher.group( "branch" ) ),
                                     Optional.ofNullable( matcher.group( "path" ) ).map( Paths::get ) );
    }

    public String getUrl()
    {
        return url;
    }

    public Optional<String> getBranch()
    {
        return branch;
    }

    public Optional<Path> getPath()
    {
        return path;
    }

    public Path getWorkingDirectory()
        throws IOException
    {
        if ( workingDirectory == null )
        {
            workingDirectory = Files.createTempDirectory( "wagon-git-" );
        }
        return workingDirectory;
    }

}

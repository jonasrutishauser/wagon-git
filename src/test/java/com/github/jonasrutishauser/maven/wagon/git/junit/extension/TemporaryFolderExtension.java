package com.github.jonasrutishauser.maven.wagon.git.junit.extension;

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
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class TemporaryFolderExtension
    implements AfterEachCallback, ParameterResolver
{

    @Target( ElementType.PARAMETER )
    @Retention( RetentionPolicy.RUNTIME )
    @Documented
    public @interface Root
    {
    }

    private static final Object KEY = new Object();

    @Override
    public boolean supportsParameter( ParameterContext parameterContext, ExtensionContext extensionContext )
        throws ParameterResolutionException
    {
        return parameterContext.getParameter().isAnnotationPresent( Root.class )
            && parameterContext.getParameter().getType() == Path.class;
    }

    @Override
    public Object resolveParameter( ParameterContext parameterContext, ExtensionContext extensionContext )
        throws ParameterResolutionException
    {
        return getLocalStore( extensionContext ).getOrComputeIfAbsent( KEY,
                                                                       key -> createTemporaryFolder( extensionContext ) );
    }

    @Override
    public void afterEach( ExtensionContext context )
        throws Exception
    {
        Path temporaryFolder = getLocalStore( context ).get( KEY, Path.class );
        if ( temporaryFolder != null )
        {
            Files.walkFileTree( temporaryFolder, new SimpleFileVisitor<Path>()
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
    }

    private Path createTemporaryFolder( ExtensionContext context )
    {
        String tempDirName =
            context.getTestMethod().map( Method::getName ).orElse( context.getTestClass().map( Class::getName ).orElse( context.getDisplayName() ) );
        Path targetDirectory = Paths.get( "target", "junit" );
        try
        {
            Files.createDirectories( targetDirectory );
            return Files.createTempDirectory( targetDirectory, tempDirName );
        }
        catch ( IOException e )
        {
            throw new ParameterResolutionException( "Could not create temporary folder", e );
        }
    }

    private Store getLocalStore( ExtensionContext extensionContext )
    {
        return extensionContext.getStore( Namespace.create( KEY, extensionContext ) );
    }

}

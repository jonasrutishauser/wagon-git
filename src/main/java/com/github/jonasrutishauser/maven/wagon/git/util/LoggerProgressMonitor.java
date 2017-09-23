package com.github.jonasrutishauser.maven.wagon.git.util;

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

import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerProgressMonitor
    extends BatchingProgressMonitor
{

    private final Logger logger;

    public LoggerProgressMonitor()
    {
        this( LoggerFactory.getLogger( LoggerProgressMonitor.class ) );
    }

    LoggerProgressMonitor( Logger logger )
    {
        this.logger = logger;
    }

    @Override
    protected void onUpdate( String taskName, int workCurr )
    {
        if ( logger.isDebugEnabled() )
        {
            logger.debug( String.format( "%-24s%s", taskName + ": ", Integer.valueOf( workCurr ) ) );
        }
    }

    @Override
    protected void onEndTask( String taskName, int workCurr )
    {
        if ( logger.isDebugEnabled() )
        {
            logger.debug( String.format( "%-24s%s", taskName + ": ", Integer.valueOf( workCurr ) ) );
        }
    }

    @Override
    protected void onUpdate( String taskName, int workCurr, int workTotal, int percentDone )
    {
        if ( logger.isDebugEnabled() && workCurr != workTotal )
        {
            String total = String.valueOf( workTotal );
            logger.debug( String.format( "%-24s%3s%% (%" + total.length() + "s/%s)", taskName + ": ",
                                         Integer.valueOf( percentDone ), Integer.valueOf( workCurr ), total ) );
        }
    }

    @Override
    protected void onEndTask( String taskName, int workCurr, int workTotal, int percentDone )
    {
        if ( logger.isDebugEnabled() )
        {
            String total = String.valueOf( workTotal );
            logger.debug( String.format( "%-24s%3s%% (%" + total.length() + "s/%s)", taskName + ": ",
                                         Integer.valueOf( percentDone ), Integer.valueOf( workCurr ), total ) );
        }
    }

}
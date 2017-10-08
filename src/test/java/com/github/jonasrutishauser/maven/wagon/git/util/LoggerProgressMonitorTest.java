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

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.github.jonasrutishauser.maven.wagon.git.util.LoggerProgressMonitor;

@DisplayName("LoggerProgressMontor")
public class LoggerProgressMonitorTest {

    private final Logger logger = mock(Logger.class);

    private final LoggerProgressMonitor testee = new LoggerProgressMonitor(logger);

    @DisplayName("with debug disabled")
    @Nested
    public class DebugDisabled {

        @Test
        @DisplayName("logs nothing onUpdate(String, int)")
        void nothingOnUpdate() {
            testee.onUpdate("task", 42);

            verify(logger, atLeast(0)).isDebugEnabled();
            verifyNoMoreInteractions(logger);
        }

        @Test
        @DisplayName("logs nothing onUpdate(String, int, int, int)")
        void nothingOnUpdatePercent() {
            testee.onUpdate("task", 42, 84, 50);

            verify(logger, atLeast(0)).isDebugEnabled();
            verifyNoMoreInteractions(logger);
        }

        @Test
        @DisplayName("logs nothing onEndTask(String, int)")
        void nothingOnEndTask() {
            testee.onEndTask("task", 42);

            verify(logger, atLeast(0)).isDebugEnabled();
            verifyNoMoreInteractions(logger);
        }

        @Test
        @DisplayName("logs nothing onEndTask(String, int, int, int)")
        void nothingOnEndTaskPercent() {
            testee.onEndTask("task", 42, 84, 50);

            verify(logger, atLeast(0)).isDebugEnabled();
            verifyNoMoreInteractions(logger);
        }
    }

    @DisplayName("with debug enabled")
    @Nested
    public class DebugEnabled {

        @BeforeEach
        void setDebugEnabled() {
            doReturn(Boolean.TRUE).when(logger).isDebugEnabled();
        }

        @Test
        @DisplayName("correct log onUpdate(String, int)")
        void onUpdate() {
            testee.onUpdate("task", 3);

            verify(logger).debug("task:                   3");
        }

        @Test
        @DisplayName("correct log onUpdate(String, int, int, int)")
        void onUpdatePercent() {
            testee.onUpdate("foo", 2, 142, 5);

            verify(logger).debug("foo:                      5% (  2/142)");
        }

        @Test
        @DisplayName("logs nothing onUpdate(String, int, int, int) if ended")
        void nothingOnUpdatePercentEnded() {
            testee.onUpdate("task", 42, 42, 100);

            verify(logger, atLeast(0)).isDebugEnabled();
            verifyNoMoreInteractions(logger);
        }

        @Test
        @DisplayName("correct log onEndTask(String, int)")
        void onEndTask() {
            testee.onEndTask("bar", 42);

            verify(logger).debug("bar:                    42");
        }

        @Test
        @DisplayName("correct log onEndTask(String, int, int, int)")
        void onEndTaskPercent() {
            testee.onEndTask("test", 42, 42, 100);

            verify(logger).debug("test:                   100% (42/42)");
        }
    }

}

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.TestAbortedException;

import com.github.jonasrutishauser.maven.wagon.git.exception.GitAuthenticationException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitCloneException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitException;
import com.github.jonasrutishauser.maven.wagon.git.exception.GitPushException;
import com.github.jonasrutishauser.maven.wagon.git.exception.NoSuchResourceInGitException;
import com.github.jonasrutishauser.maven.wagon.git.junit.extension.TemporaryFolderExtension;
import com.github.jonasrutishauser.maven.wagon.git.junit.extension.TemporaryFolderExtension.Root;

@ExtendWith(TemporaryFolderExtension.class)
@DisplayName("GitConnection")
public class GitConnectionTest {

    private static class PathArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(null, Paths.get("test"), Paths.get("foo", "bar")).map(Optional::ofNullable)
                    .map(Arguments::of);
        }
    }

    private static class BranchArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(null, "gh-pages", "foo/bar").map(Optional::ofNullable).map(Arguments::of);
        }
    }

    private static class PathAndBranchArguments extends PathArguments {

        private final BranchArguments branchArguments = new BranchArguments();

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return super.provideArguments(context).flatMap(path -> //
            branchArguments.provideArguments(context).map(branch -> //
            Arguments.of(path.get()[0], branch.get()[0])));
        }
    }

    @Test
    @DisplayName("open() with an IOException on getting workingDirectory throws a GitCloneException")
    void open_IOExceptionOnWorkingDirectory_GitCloneException(@Root Path tempDir) throws IOException {
        GitConfiguration configuration = createConfiguration("", tempDir, Optional.empty(), Optional.empty());
        when(configuration.getWorkingDirectory()).thenThrow(IOException.class);

        assertThrows(GitCloneException.class,
                () -> GitConnection.open(configuration, Optional.empty(), Optional.empty()));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "https://example.org/no-repo.git"})
    @DisplayName("open() with an invalid remote throws a GitCloneException")
    void open_invalidRemote_GitCloneException(String url, @Root Path tempDir) throws IOException {
        GitConfiguration configuration = createConfiguration(url, tempDir, Optional.empty(), Optional.empty());

        assertThrows(GitCloneException.class,
                () -> GitConnection.open(configuration, Optional.empty(), Optional.empty()));

        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @ParameterizedTest(name = "with pathInRepo={0} and branch={1}")
    @ArgumentsSource(PathAndBranchArguments.class)
    @DisplayName("open() works for a valid remote")
    void open_validRemote(Optional<Path> pathInRepo, Optional<String> branch, @Root Path tempDir) throws Exception {
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, pathInRepo, branch);

        GitConnection.open(configuration, Optional.empty(), Optional.empty());

        assertTrue(Files.isDirectory(tempDir.resolve("work/.git")));
    }

    @ParameterizedTest(name = "branch {1}")
    @ArgumentsSource(PathAndBranchArguments.class)
    @DisplayName("open() works for a valid remote with content in branch gh-pages")
    void open_validRemoteWithContent(Optional<Path> pathInRepo, Optional<String> branch, @Root Path tempDir)
            throws Exception {
        GitConfiguration configuration = createConfiguration(createRemoteRepoWithContent(tempDir, "gh-pages"), tempDir,
                pathInRepo, branch);

        GitConnection.open(configuration, Optional.empty(), Optional.empty());

        assertTrue(Files.isDirectory(tempDir.resolve("work/.git")));
        Path remoteBranches = tempDir.resolve("work/.git/refs/remotes/origin");
        if (Files.isDirectory(remoteBranches)) {
            assertAll(Files.list(remoteBranches).map(remoteBranch -> //
            () -> assertEquals(Paths.get(branch.orElse("master")).subpath(0, 1), remoteBranch.getFileName())));
        }
    }

    @ParameterizedTest(name = "username={0}, password={1}")
    @CsvSource({",", "foo,", ",foo", "foo,bar"})
    @DisplayName("open() repo on github via https with invalid credentials throws GitAuthenticationException")
    void open_githubHttpsWithInvalidCredentials_GitAuthenticationException(String username, String password,
            @Root Path tempDir) throws Exception {
        GitConfiguration configuration = createConfiguration("https://github.com/jonasrutishauser/no-such-repo.git",
                tempDir, Optional.empty(), Optional.empty());

        assertThrows(GitAuthenticationException.class,
                () -> GitConnection.open(configuration, Optional.ofNullable(username), Optional.ofNullable(password)));

        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @ParameterizedTest(name = "username={0}, password={1}")
    @CsvSource({",", "foo,", ",foo", "foo,bar"})
    @DisplayName("open() own projects repo on github via ssh throws GitAuthenticationException")
    void open_ownRemoteViaSsh(String username, String password, @Root Path tempDir) throws Exception {
        GitConfiguration configuration = createConfiguration("ssh://git@github.com/jonasrutishauser/wagon-git.git",
                tempDir, Optional.empty(), Optional.empty());

        assertThrows(GitAuthenticationException.class,
                () -> GitConnection.open(configuration, Optional.ofNullable(username), Optional.ofNullable(password)));

        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @ParameterizedTest(name = "with branch={0}")
    @ArgumentsSource(BranchArguments.class)
    @DisplayName("open() works for own projects repo on github")
    void open_ownRemote(Optional<String> branch, @Root Path tempDir) throws Exception {
        GitConfiguration configuration = createConfiguration("https://github.com/jonasrutishauser/wagon-git.git",
                tempDir, Optional.empty(), branch);

        GitConnection.open(configuration, Optional.empty(), Optional.empty());

        assertTrue(Files.isDirectory(tempDir.resolve("work/.git")));
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("close() removes working directory")
    void close_removesWorkingDir(Optional<Path> pathInRepo, @Root Path tempDir) throws Exception {
        GitConnection testee = createTestee(tempDir, pathInRepo);

        testee.close();

        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("close() with deleted working directory")
    void close_workingDirDeleted_doNotFail(Optional<Path> pathInRepo, @Root Path tempDir) throws Exception {
        GitConnection testee = createTestee(tempDir, pathInRepo);
        Files.walkFileTree(tempDir.resolve("work"), new SimpleFileVisitor<Path>() {

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        testee.close();
    }

    @Test
    @DisplayName("close() without a change does not push")
    void close_withoutChange_doNotPush(@Root Path tempDir) throws Exception {
        GitConnection testee = createTestee(tempDir);

        testee.close();

        assertFalse(Files.exists(tempDir.resolve("remote.git/refs/heads/master")));
    }

    @ParameterizedTest(name = "branch {0}")
    @ArgumentsSource(BranchArguments.class)
    @DisplayName("close() with a change does push")
    void close_withChange_doPush(Optional<String> branch, @Root Path tempDir) throws Exception {
        GitConnection testee = createTestee(tempDir, Optional.empty(), branch);
        addChange(tempDir);

        testee.close();

        assertTrue(Files.exists(tempDir.resolve("remote.git/refs/heads/" + branch.orElse("master"))));
    }

    @ParameterizedTest(name = "branch {0}")
    @ArgumentsSource(BranchArguments.class)
    @DisplayName("close() with a change does push (repeate twice)")
    void close_withChange_doPush_repeatedTwice(Optional<String> branch, @Root Path tempDir) throws Exception {
        Path remoteBranchRefFile = tempDir.resolve("remote.git/refs/heads/" + branch.orElse("master"));
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, Optional.empty(),
                branch);
        GitConnection testee = createTestee(configuration);
        addChange(tempDir);
        testee.close();
        assumeTrue(Files.exists(remoteBranchRefFile));
        String ref = Files.readAllLines(remoteBranchRefFile, StandardCharsets.UTF_8).get(0);
        testee = createTestee(configuration);
        addChange(tempDir);

        testee.close();

        assertNotEquals(ref, Files.readAllLines(remoteBranchRefFile, StandardCharsets.UTF_8).get(0));
    }

    @Test
    @DisplayName("close() with a change conflicting with remote throws GitPushException")
    void close_withChangeConflict_GitPushException(@Root Path tempDir) throws IOException {
        GitConnection testee = createTestee(tempDir);
        addChange(tempDir);
        Path otherWorkingDir = tempDir.resolve("other");
        Files.createDirectory(otherWorkingDir);
        Executor executor = createExecutor();
        executor.setWorkingDirectory(otherWorkingDir.toFile());
        executor.execute(CommandLine.parse("git clone ../remote.git ."));
        executor.execute(CommandLine.parse("git commit -m 'test' --allow-empty"));
        executor.execute(CommandLine.parse("git push"));

        assertThrows(GitPushException.class, () -> testee.close());
    }

    @Test
    @DisplayName("close() with a change failed to connecto to remote throws GitPushException")
    void close_withChangeAndRemoteNotAccessible_GitPushException(@Root Path tempDir)
            throws IOException, GitPushException, GitAuthenticationException {
        GitConnection testee = createTestee(tempDir);
        addChange(tempDir);
        FileUtils.deleteDirectory(tempDir.resolve("remote.git").toFile());

        assertThrows(GitPushException.class, () -> testee.close());
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("put() of a file adds it")
    void put_addsFile(Optional<Path> pathInRepo, @Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir, pathInRepo);
        Path file = tempDir.resolve("foo");
        Files.write(file, Arrays.asList("bar"), StandardCharsets.UTF_8);

        testee.put(file, Paths.get("foo"));

        Path targetFile = tempDir.resolve("work").resolve(pathInRepo.orElse(Paths.get(""))).resolve("foo");
        String targetPathInRepo = pathInRepo.map(Path::toString).map(path -> path + '/').orElse("") + "foo";
        try (Git git = Git.open(tempDir.resolve("work").toFile())) {
            assertAll(() -> assertEquals(Arrays.asList("bar"), Files.readAllLines(targetFile, StandardCharsets.UTF_8)),
                    () -> assertEquals(Collections.singleton(targetPathInRepo), git.status().call().getAdded()));
        }
    }

    @Test
    @DisplayName("put() of a file with a directory there throws GitException")
    void put_directoryThere_GitException(@Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir);
        Path file = tempDir.resolve("foo");
        Files.write(file, Arrays.asList("bar"), StandardCharsets.UTF_8);
        Files.createDirectory(tempDir.resolve("work/foo"));
        Files.write(tempDir.resolve("work/foo/bar"), Arrays.asList("test"), StandardCharsets.UTF_8);

        assertThrows(GitException.class, () -> testee.put(file, Paths.get("foo")));
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("put() of a directory adds it")
    void put_addsDirectory(Optional<Path> pathInRepo, @Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir, pathInRepo);
        Path file = tempDir.resolve("test/deep/foo");
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList("bar"), StandardCharsets.UTF_8);
        Files.createDirectory(tempDir.resolve("work/test"));

        testee.put(tempDir.resolve("test"), Paths.get("test"));

        Path targetFile = tempDir.resolve("work").resolve(pathInRepo.orElse(Paths.get(""))).resolve("test/deep/foo");
        String targetPathInRepo = pathInRepo.map(Path::toString).map(path -> path + '/').orElse("") + "test/deep/foo";
        try (Git git = Git.open(tempDir.resolve("work").toFile())) {
            assertAll(() -> assertEquals(Arrays.asList("bar"), Files.readAllLines(targetFile, StandardCharsets.UTF_8)),
                    () -> assertEquals(Collections.singleton(targetPathInRepo), git.status().call().getAdded()));
        }
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("put() of a directory to root adds it")
    void put_addsDirectoryToRoot(Optional<Path> pathInRepo, @Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir, pathInRepo);
        Path file = tempDir.resolve("test/deep/foo");
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList("bar"), StandardCharsets.UTF_8);
        Files.createDirectory(tempDir.resolve("work/test"));

        testee.put(tempDir.resolve("test"), Paths.get("."));

        Path targetFile = tempDir.resolve("work").resolve(pathInRepo.orElse(Paths.get(""))).resolve("deep/foo");
        String targetPathInRepo = pathInRepo.map(Path::toString).map(path -> path + '/').orElse("") + "deep/foo";
        try (Git git = Git.open(tempDir.resolve("work").toFile())) {
            assertAll(() -> assertEquals(Arrays.asList("bar"), Files.readAllLines(targetFile, StandardCharsets.UTF_8)),
                    () -> assertEquals(Collections.singleton(targetPathInRepo), git.status().call().getAdded()));
        }
    }

    @Test
    @DisplayName("getIfNewer() of an inexisting file throws NoSuchResourceInGitException")
    void getIfNewer_inexistingFile_NoSuchResourceInGitException(@Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir);

        assertThrows(NoSuchResourceInGitException.class,
                () -> testee.getIfNewer(Paths.get("foo"), tempDir.resolve("foo"), 42));
    }

    @Test
    @DisplayName("getIfNewer() with an illegal state throws GitException")
    void getIfNewer_illegalGitState_GitException(@Root Path tempDir) throws IOException, GitException {
        GitConnection testee = createTestee(tempDir);
        Files.write(tempDir.resolve("work/foo"), Arrays.asList("test"), StandardCharsets.UTF_8);

        assertThrows(GitException.class, () -> testee.getIfNewer(Paths.get("foo"), tempDir.resolve("foo"), 42));
    }

    @ParameterizedTest(name = "pathInRepo is {0}")
    @ArgumentsSource(PathArguments.class)
    @DisplayName("getIfNewer() of an older file returns false")
    void getIfNewer_olderFile_false(Optional<Path> pathInRepo, @Root Path tempDir)
            throws IOException, GitException, InterruptedException {
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, pathInRepo,
                Optional.empty());
        addFooFileToRepo(tempDir, pathInRepo, Optional.empty());
        GitConnection testee = createTestee(configuration);
        Path target = tempDir.resolve("foo");
        long timestamp = Instant.now().getEpochSecond() - 10;

        boolean result = testee.getIfNewer(Paths.get("foo"), target, timestamp);

        assertFalse(result || Files.exists(target));
    }

    @ParameterizedTest(name = "with pathInRepo={0} and branch={1}")
    @ArgumentsSource(PathAndBranchArguments.class)
    @DisplayName("getIfNewer() of a newer file returns true and copies the file")
    void getIfNewer_newerFile_trueAndCopy(Optional<Path> pathInRepo, Optional<String> branch, @Root Path tempDir)
            throws IOException, GitException {
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, pathInRepo, branch);
        long timestamp = Instant.parse("2005-04-07T22:13:13Z").getEpochSecond() - 1;
        addFooFileToRepo(tempDir, pathInRepo, branch);
        GitConnection testee = createTestee(configuration);
        Path target = tempDir.resolve("foo");

        boolean result = testee.getIfNewer(Paths.get("foo"), target, timestamp);

        assertTrue(result && Files.exists(target));
    }

    @Test
    @DisplayName("getIfNewer() of a newer file with IOException when copy throws GitException")
    void getIfNewer_newerFileButIOException_GitException(@Root Path tempDir) throws IOException, GitException {
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, Optional.empty(),
                Optional.empty());
        addFooFileToRepo(tempDir, Optional.empty(), Optional.empty());
        GitConnection testee = createTestee(configuration);
        Path target = tempDir.resolve("foo");
        Files.createDirectory(target);
        Files.write(target.resolve("bar"), Arrays.asList("test"), StandardCharsets.UTF_8);

        assertThrows(GitException.class, () -> testee.getIfNewer(Paths.get("foo"), target, 42));
    }

    private void addChange(Path tempDir) throws IOException, ExecuteException {
        Files.write(tempDir.resolve("work/foo"), Arrays.asList("" + new Random().nextLong()), StandardCharsets.UTF_8);
        Executor executor = createExecutor();
        executor.setWorkingDirectory(tempDir.resolve("work").toFile());
        executor.execute(CommandLine.parse("git add foo"));
    }

    private void addFooFileToRepo(Path tempDir, Optional<Path> pathInRepo, Optional<String> branch)
            throws IOException, ExecuteException {
        Path otherWorkingDir = tempDir.resolve("remote-init");
        Files.createDirectory(otherWorkingDir);
        Executor executor = createExecutor();
        executor.setWorkingDirectory(otherWorkingDir.toFile());
        executor.execute(CommandLine.parse("git clone ../remote.git ."));
        pathInRepo.map(otherWorkingDir::resolve).ifPresent(path -> {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        Files.write(otherWorkingDir.resolve(pathInRepo.orElse(Paths.get(""))).resolve("foo"), Arrays.asList("test"),
                StandardCharsets.UTF_8);
        executor.execute(CommandLine.parse("git add ."));
        Map<String, String> env = new HashMap<>();
        env.putAll(EnvironmentUtils.getProcEnvironment());
        env.put("GIT_COMMITTER_DATE", "2005-04-07T22:13:13Z");
        executor.execute(CommandLine.parse("git commit -m 'test'"), env);
        executor.execute(CommandLine.parse("git push origin master:" + branch.orElse("master")));
    }

    private GitConnection createTestee(Path tempDir) throws IOException {
        return createTestee(tempDir, Optional.empty());
    }

    private GitConnection createTestee(Path tempDir, Optional<Path> pathInRepo) throws IOException {
        return createTestee(tempDir, pathInRepo, Optional.empty());
    }

    private GitConnection createTestee(Path tempDir, Optional<Path> pathInRepo, Optional<String> branch)
            throws IOException {
        GitConfiguration configuration = createConfiguration(createRemoteRepo(tempDir), tempDir, pathInRepo, branch);
        return createTestee(configuration);
    }

    private GitConnection createTestee(GitConfiguration configuration) {
        try {
            return GitConnection.open(configuration, Optional.empty(), Optional.empty());
        } catch (GitException e) {
            throw new TestAbortedException("failed to open GitConnection: " + e.getMessage(), e);
        }
    }

    private GitConfiguration createConfiguration(String url, Path tempDir, Optional<Path> pathInRepo,
            Optional<String> branch) throws IOException {
        GitConfiguration configuration = mock(GitConfiguration.class);
        when(configuration.getUrl()).thenReturn(url);
        when(configuration.getWorkingDirectory()).thenReturn(tempDir.resolve("work"));
        when(configuration.getPath()).thenReturn(pathInRepo);
        when(configuration.getBranch()).thenReturn(branch);
        return configuration;
    }

    private String createRemoteRepo(Path tempDir) throws IOException {
        Path remote = tempDir.resolve("remote.git");
        Files.createDirectory(remote);
        Executor executor = createExecutor();
        executor.setWorkingDirectory(remote.toFile());
        executor.execute(CommandLine.parse("git init --bare"));
        return remote.toUri().toString();
    }

    private String createRemoteRepoWithContent(Path tempDir, String branch) throws IOException {
        Path remote = tempDir.resolve("remote.git");
        Files.createDirectory(remote);
        Executor executor = createExecutor();
        executor.setWorkingDirectory(remote.toFile());
        executor.execute(CommandLine.parse("git init --bare"));

        Path otherWorkingDir = tempDir.resolve("remote-init");
        Files.createDirectory(otherWorkingDir);
        executor.setWorkingDirectory(otherWorkingDir.toFile());
        executor.execute(CommandLine.parse("git clone ../remote.git ."));
        executor.execute(CommandLine.parse("git commit -m 'test' --allow-empty"));
        executor.execute(CommandLine.parse("git push origin master:" + branch));

        return remote.toUri().toString();
    }

    private Executor createExecutor() {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(System.out));
        return executor;
    }

}

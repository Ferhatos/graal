/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.image.sources;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static com.oracle.svm.hosted.image.sources.SourceManager.GRAALVM_SRC_PACKAGE_PREFIXES;
public class GraalVMSourceCache extends SourceCache {
    /**
     * create a GraalVM source cache
     */
    protected GraalVMSourceCache() {
        super(SourceCache.GRAALVM_CACHE_KEY);
        initSrcRoots();
    }

    private static final String JAVA_CLASSPATH_PROP = "java.class.path";

    private void initSrcRoots() {
        String javaClassPath = System.getProperty(JAVA_CLASSPATH_PROP);
        assert javaClassPath != null;
        String[] classPathEntries = javaClassPath.split(File.pathSeparator);
        for (String classPathEntry : classPathEntries) {
            Path entryPath = Paths.get(classPathEntry);
            String fileNameString = entryPath.getFileName().toString();
            if (fileNameString.endsWith(".jar")) {
                // GraalVM jar /path/to/xxx.jar should have
                // sources /path/to/xxx.src.zip.jar
                int length = fileNameString.length();
                String srcFileNameString = fileNameString.substring(0, length - 3) + "src.zip";
                Path srcPath = entryPath.getParent().resolve(srcFileNameString);
                if (srcPath.toFile().exists()) {
                    try {
                        FileSystem fileSystem = FileSystems.newFileSystem(srcPath, null);
                        for (Path root : fileSystem.getRootDirectories()) {
                            if (filterSrcRoot(root)) {
                                srcRoots.add(root);
                            }
                        }
                    } catch (IOException ioe) {
                        /* ignore this entry */
                    } catch (FileSystemNotFoundException fnfe) {
                        /* ignore this entry */
                    }
                }
            } else  {
                /* graal classpath dir entries should have a src and/or src_gen subdirectory */
                Path srcPath = entryPath.resolve("src");
                if (filterSrcRoot(srcPath)) {
                    srcRoots.add(srcPath);
                }
                srcPath = entryPath.resolve("src_gen");
                if (filterSrcRoot(srcPath)) {
                    srcRoots.add(srcPath);
                }
            }
        }
    }
    /**
     * Ensure that the supplied root dir contains
     * at least one  subdirectory that matches one
     * of the expected Graal package dir hierarchies.
     *
     * @param root A root path under which to locate
     * the desired subdirectory
     * @return true if a
     */
    private boolean filterSrcRoot(Path root) {
        // we are only interested in source roots
        // that potentially contain GraalVM code
        String separator = root.getFileSystem().getSeparator();

        LinkedList<Path> toTest = new LinkedList<>();
        LinkedList<Path> toBeMatched = new LinkedList<>();
        /* build a list of GraalVM Paths to look for */
        for (String prefix : GRAALVM_SRC_PACKAGE_PREFIXES) {
            String subDir = prefix.replaceAll("\\.", separator);
            toBeMatched.add(root.resolve(subDir));
        }
        /* start by checking immediate subdirs of root */
        try {
            addSubDirs(root, toTest);
        } catch (IOException e) {
            // hmm, ignore this root then
            return false;
        }

        return searchDirectories(toTest, toBeMatched);

    }
    private void addSubDirs(Path parent, LinkedList<Path> toSearch) throws IOException {
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(parent);
        for (Path dir : directoryStream) {
            toSearch.addLast(dir);
        }
    }
    private boolean searchDirectories(LinkedList<Path> toTest, LinkedList<Path> toBeMatched) {
        try {
            while (!toTest.isEmpty()) {
                Path next = toTest.pop();
                for (Path p : toBeMatched) {
                    if (p.equals(next)) {
                        /* yes, the full monty! */
                        return true;
                    } else if (p.startsWith(next)) {
                        /* this may lead where we want to go -- check subdirs */
                        addSubDirs(next, toTest);
                        /* other matches are disjoint so we can break */
                        break;
                    }
                }
            }
        } catch (IOException e) {
            /* ignore the exception and also skip the jar */
        }
        /* nope, no useful dirs under this root */
        return false;
    }
}

/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright (c) 2014 Edugility LLC.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT.  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * The original copy of this license is available at
 * http://www.opensource.org/license/mit-license.html.
 */
package com.edugility.maven.liquibase;

import java.io.File;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.edugility.maven.ArtifactsProcessingException;
import com.edugility.maven.ArtifactsProcessor;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.plugin.logging.Log;

import org.apache.maven.model.Build;

import org.apache.maven.project.MavenProject;

/**
 * An {@link ArtifactsProcessor} for use in conjunction with the
 * <code>artifact-maven-plugin</code> that creates a Liquibase
 * changelog aggregating changelog fragments found in a {@link
 * MavenProject}'s dependencies.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public class LiquibaseChangeLogArtifactsProcessor implements ArtifactsProcessor {


  /*
   * Instance fields.
   */


  /**
   * The names of Liquibase changelog fragments to be sought.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getChangeLogResourceNames()
   *
   * @see #setChangeLogResourceNames(Collection)
   */
  private Collection<String> changeLogResourceNames;

  /**
   * The {@link AggregateChangeLogGenerator} to use to perform
   * changelog generation.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getChangeLogGenerator()
   *
   * @see #setChangeLogGenerator(AggregateChangeLogGenerator)
   */
  private AggregateChangeLogGenerator changeLogGenerator;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link LiquibaseChangeLogArtifactsProcessor}.
   */
  public LiquibaseChangeLogArtifactsProcessor() {
    super();
    this.setChangeLogGenerator(new AggregateChangeLogGenerator());
    this.setChangeLogResourceNames(Collections.singleton("META-INF/liquibase/changelog.xml"));
  }


  /*
   * Properties.
   */


  /**
   * Returns the {@link AggregateChangeLogGenerator} to be used to
   * generate changelogs.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return an {@link AggregateChangeLogGenerator}, or {@code null}
   *
   * @see #setChangeLogGenerator(AggregateChangeLogGenerator)
   */
  public AggregateChangeLogGenerator getChangeLogGenerator() {
    return this.changeLogGenerator;
  }

  /**
   * Sets the {@link AggregateChangeLogGenerator} to be used to
   * generate changelogs.
   *
   * @param changeLogGenerator the new generator to use; may be {@code
   * null} in which case a new {@link AggregateChangeLogGenerator}
   * will be used internally instead
   *
   * @see #getChangeLogGenerator()
   */
  public void setChangeLogGenerator(final AggregateChangeLogGenerator changeLogGenerator) {
    this.changeLogGenerator = changeLogGenerator;
  }

  /**
   * Returns the relative names of resources representing Liquibase
   * changelog fragments that this {@link
   * LiquibaseChangeLogArtifactsProcessor} will look for.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>Typically, this method returns a singleton {@link Collection}
   * containing the text {@code META-INF/liquibase/changelog.xml}.</p>
   *
   * @return a {@link Collection} of relative resource names
   * representing Liquibase changelog fragments, or {@code null}
   *
   * @see #setChangeLogResourceNames(Collection)
   */
  public Collection<String> getChangeLogResourceNames() {
    return this.changeLogResourceNames;
  }

  /**
   * Sets the {@link Collection} of relative resource names
   * representing Liquibase changelog fragments that this {@link
   * LiquibaseChangeLogArtifactsProcessor} will look for.
   *
   * @param changeLogResourceNames the names; may be {@code null}
   *
   * @see #getChangeLogResourceNames()
   */
  public void setChangeLogResourceNames(final Collection<String> changeLogResourceNames) {
    this.changeLogResourceNames = changeLogResourceNames;
  }


  /*
   * ArtifactsProcessor implementation.
   */


  /**
   * Harvests {@code jar:} {@link URL}s from the supplied resolved
   * {@link Artifact}s and lists them in topological order as {@code
   * <include>} elements inside a generated Liquibase changelog file.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param project the {@link MavenProject} currently in effect; will
   * not be {@code null}
   *
   * @param artifacts a {@link Collection} of {@link Artifact}s
   * representing the full, transitive set of resolved dependencies of
   * the supplied {@link MavenProject}; will not be {@code null}
   *
   * @param log a {@link Log} for logging to a Maven console; may be
   * {@code null}
   *
   * @return the supplied artifacts
   *
   * @exception ArtifactsProcessingException if an error occurs
   *
   * @see ArtifactsProcessor
   */
  @Override
  public Collection<? extends Artifact> process(final MavenProject project, final Collection<? extends Artifact> artifacts, final Log log) throws ArtifactsProcessingException {
    final Collection<? extends URL> changeLogUrls = this.gatherUrls(project, artifacts, log);
    if (changeLogUrls != null && !changeLogUrls.isEmpty()) {
      this.generateChangeLog(project, changeLogUrls, log);
    }
    return artifacts;
  }


  /*
   * Private methods.
   */


  private final Collection<? extends URL> gatherUrls(final MavenProject project, final Collection<? extends Artifact> artifacts, final Log log) throws ArtifactsProcessingException {
    final Collection<? extends URL> artifactUrls = this.gatherArtifactUrls(project, artifacts, log);
    final int artifactUrlsSize = artifactUrls == null ? 0 : artifactUrls.size();

    final Collection<? extends URL> projectUrls = this.gatherProjectUrls(project, log);
    final int projectUrlsSize = projectUrls == null ? 0 : projectUrls.size();

    final Collection<URL> returnValue = new ArrayList<URL>(artifactUrlsSize + projectUrlsSize);
    if (artifactUrlsSize > 0) {
      returnValue.addAll(artifactUrls);
    }
    if (projectUrlsSize > 0) {
      returnValue.addAll(projectUrls);
    }
    return returnValue;
  }

  private final Collection<? extends URL> gatherArtifactUrls(final MavenProject project, final Collection<? extends Artifact> artifacts, final Log log) throws ArtifactsProcessingException {
    Collection<URL> returnValue = null;
    if (artifacts != null && !artifacts.isEmpty()) {
      final Collection<? extends String> names = this.getChangeLogResourceNames();
      if (names != null && !names.isEmpty()) {
        for (final Artifact artifact : artifacts) {
          if (artifact != null && artifact.isResolved()) {
            final File artifactFile = artifact.getFile();
            if (artifactFile != null && artifactFile.canRead()) {
              for (final String name : names) {
                if (name != null) {
                  URL url = null;
                  try {
                    url = artifactFile.toURI().toURL();
                  } catch (final MalformedURLException wrapMe) {
                    throw new ArtifactsProcessingException(wrapMe);
                  }
                  final URLClassLoader loader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
                  final URL jarUrlToChangeLog = loader.getResource(name);
                  if (jarUrlToChangeLog != null) {
                    if (returnValue == null) {
                      returnValue = new ArrayList<URL>(artifacts.size() * names.size());
                    }
                    returnValue.add(jarUrlToChangeLog);
                  }
                }
              }
            }
          }
        }
      }
    }
    return returnValue;
  }

  private final Collection<? extends URL> gatherProjectUrls(final MavenProject project, final Log log) throws ArtifactsProcessingException {
    Collection<URL> urls = null;
    if (project != null) {
      final Build build = project.getBuild();
      if (build != null) {
        final Collection<? extends String> names = this.getChangeLogResourceNames();
        if (names != null && !names.isEmpty()) {
          final String[] directoryNames = new String[] { build.getOutputDirectory(), build.getTestOutputDirectory() };
          for (final String directoryName : directoryNames) {
            if (directoryName != null) {
              final File directory = new File(directoryName);
              if (directory.isDirectory()) {
                for (final String resourceName : names) {
                  if (resourceName != null) {
                    final File changeLogFile = new File(directory, resourceName);
                    if (changeLogFile.isFile() && changeLogFile.canRead()) {
                      if (urls == null) {
                        urls = new ArrayList<URL>(2 * names.size());
                      }
                      try {
                        urls.add(changeLogFile.toURI().toURL());
                      } catch (final MalformedURLException wrapMe) {
                        throw new ArtifactsProcessingException(wrapMe);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return urls;
  }

  private final File generateChangeLog(final MavenProject project, final Collection<? extends URL> urls, final Log log) throws ArtifactsProcessingException {
    File returnValue = null;
    if (urls != null && !urls.isEmpty()) {
      AggregateChangeLogGenerator generator = this.getChangeLogGenerator();
      if (generator == null) {
        generator = new AggregateChangeLogGenerator();
      }
      final File changeLogFile = generator.getAggregateChangeLogFile();
      if (changeLogFile != null) {
        final File parent = changeLogFile.getParentFile();
        if (parent != null) {
          parent.mkdirs();
        }
      }
      try {
        returnValue = generator.generate(urls);
      } catch (final IOException wrapMe) {
        throw new ArtifactsProcessingException(wrapMe);
      }
    }
    return returnValue;
  }

}

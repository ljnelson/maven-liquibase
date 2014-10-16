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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.URL;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

/**
 * A generator that creates a Liquibase changelog file with {@code
 * <include>} elements inside it referencing other Liquibase changelog
 * fragments.
 *
 * <p>This class is chiefly for use by a {@link LiquibaseChangeLogArtifactsProcessor}.</p>
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see LiquibaseChangeLogArtifactsProcessor
 */
public class AggregateChangeLogGenerator {

  private static final String LS = System.getProperty("line.separator", "\n");

  /**
   * The aggregating Liquibase changelog file that includes other
   * changelog files in the appropriate order.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getAggregateChangeLogFile()
   *
   * @see #setAggregateChangeLogFile(File)
   */
  private File aggregateChangeLogFile;

  /**
   * A {@link String} containing the contents of a logical template
   * into which will be "poured" other logical changelog fragments.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getTemplate()
   *
   * @see #setTemplate(String)
   */
  private String template;

  /**
   * The version of the Liquibase changelog file generated.
   *
   * <p>This field must not be {@code null}.</p>
   *
   * @see #getDatabaseChangeLogXsdVersion()
   *
   * @see #setDatabaseChangeLogXsdVersion(String)
   */
  private String databaseChangeLogXsdVersion;

  /**
   * A {@link Properties} object representing Liquibase changelog
   * parameters that will be included in the generated changelog.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getProperties()
   *
   * @see #setProperties(Properties)
   */
  private Properties properties;

  /**
   * Represents whether the aggregate changelog was actually
   * generated, or supplied via the {@link
   * #setAggregateChangeLogFile(File)} method.
   *
   * @see #generateEmptyAggregateChangeLogFile()
   */
  private transient boolean fileWasGenerated;

  /**
   * An MVEL representation of the contents of the {@link #template}
   * field.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getTemplate()
   *
   * @see #setTemplate(String)
   */
  private transient CompiledTemplate compiledTemplate;

  
  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AggregateChangeLogGenerator}.
   */
  public AggregateChangeLogGenerator() {
    super();
    this.setDatabaseChangeLogXsdVersion("3.2");
  }

  /**
   * Returns the version of the Liquibase changelog file that will be
   * generated.
   *
   * <p>This method may return {@code null} in which case "{@code
   * 3.2}" will be used internally instead.</p>
   *
   * <p>By default, "{@code 3.2}" is returned, and this default value
   * will change when Liquibase is updated and this project is updated
   * to depend on it.</p>
   *
   * @return the version of the Liquibase changelog file that will be
   * generated, or {@code null}
   *
   * @see #setDatabaseChangeLogXsdVersion(String)
   */
  public String getDatabaseChangeLogXsdVersion() {
    return this.databaseChangeLogXsdVersion;
  }

  /**
   * Sets the version of the Liquibase changelog file that will be
   * generated.
   *
   * @param version the new version; may be {@code null}
   *
   * @see #getDatabaseChangeLogXsdVersion()
   */
  public void setDatabaseChangeLogXsdVersion(final String version) {
    this.databaseChangeLogXsdVersion = version;
  }

  /**
   * Loads a notional resource with the given {@code name} from the
   * classpath.
   *
   * @param name the name of the resource to load; may be {@code null}
   * in which case {@code null} will be returned
   *
   * @return the resource that was found, or {@code null}
   *
   * @see ClassLoader#getResource(String)
   */
  protected URL getResource(final String name) {
    URL returnValue = null;
    if (name != null) {
      for (int i = 0; i < 3; i++) {
        final ClassLoader loader;
        switch (i) {
        case 0:
          loader = Thread.currentThread().getContextClassLoader();
          break;
        case 1:
          loader = this.getClass().getClassLoader();
          break;
        case 2:
          loader = null;
          returnValue = ClassLoader.getSystemResource(name);
          break;
        default:
          loader = null;
          break;
        }
        if (loader != null) {
          returnValue = loader.getResource(name);
        }
        if (returnValue != null) {
          break;
        }
      }
    }
    return returnValue;
  }

  /**
   * Returns a {@link Properties} object representing any custom
   * properties that are to be converted to Liquibase changelog
   * parameters.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link Properties} object representing custom
   * properties, or {@code null}
   *
   * @see #setProperties(Properties)
   */
  public Properties getProperties() {
    return this.properties;
  }

  /**
   * Installs a {@link Properties} object that represents custom
   * properties that are to be converted to Liquibase changelog
   * parameters.
   *
   * @param properties the {@link Properties} to be installed; may be
   * {@code null}
   *
   * @see #getProperties()
   */
  public void setProperties(final Properties properties) {
    this.properties = properties;
  }

  /**
   * Returns the source code of an MVEL template that represents the
   * skeleton of a Liquibase changelog into which will be placed
   * {@code <include>} elements.
   *
   * <p>This method will not return {@code null} and overrides of it
   * must not either.</p>
   *
   * @return a non-{@code null} {@link String} containing the source
   * code of an MVEL template
   */
  public String getTemplate() {
    if (this.template == null) {
      final URL templateURL = this.getResource("changeLogTemplate.xml");
      if (templateURL != null) {
        BufferedReader reader = null;
        InputStream stream = null;
        try {
          stream = templateURL.openStream();
          if (stream != null) {
            reader = new BufferedReader(new InputStreamReader(stream)); // TODO: charset?
            String line = null;
            final StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
              sb.append(line);
              sb.append(LS);
            }
            this.template = sb.toString();
          }
        } catch (final IOException boom) {
          // TODO: log
          template = null;
        } finally {
          if (stream != null) {
            try {
              stream.close();
            } catch (final IOException nothingWeCanDo) {
              
            }
          }
          if (reader != null) {
            try {
              reader.close();
            } catch (final IOException nothingWeCanDo) {

            }
          }
        }
      }
    }
    return this.template;
  }

  /**
   * Sets the source code of an MVEL template that will be used to
   * produce a Liquibase changelog.
   *
   * @param template the new source code; must not be {@code null}
   *
   * @exception IllegalArgumentException if {@code template} is {@code
   * null}
   *
   * @see #getTemplate()
   */
  public void setTemplate(final String template) {    
    if (template == null) {
      throw new IllegalArgumentException("template", new NullPointerException("template"));
    }
    this.template = template;
    this.compiledTemplate = null;
    if (template != null) {
      this.compiledTemplate = TemplateCompiler.compileTemplate(template);
    }
  }

  /**
   * Returns a {@link File} that either does house or will house
   * Liquibase changelog contents.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>This method may {@linkplain
   * #generateEmptyAggregateChangeLogFile() generate} such a file.</p>
   *
   * @return a {@link File} representing the aggregate changelog, or
   * {@code null}
   *
   * @see #generateEmptyAggregateChangeLogFile()
   */
  public File getAggregateChangeLogFile() {
    if (this.aggregateChangeLogFile == null) {
      try {
        this.aggregateChangeLogFile = this.generateEmptyAggregateChangeLogFile();
      } catch (final IOException oops) {
        // TODO log
        this.aggregateChangeLogFile = null;
      }
    }
    return this.aggregateChangeLogFile;
  }
  
  /**
   * Installs a {@link File} representing the path to a file that will
   * be erased and that will eventually contain Liquibase changelog
   * contents.
   *
   * @param file a path to a file that will be used as the Liquibase
   * changelog file generated by this {@link
   * AggregateChangeLogGenerator}; may be {@code null}
   */
  public void setAggregateChangeLogFile(final File file) {
    this.aggregateChangeLogFile = file;
    this.fileWasGenerated = false;
  }

  /**
   * Generates an empty (temporary) {@link File} that will eventually
   * contain Liquibase changelog contents.
   *
   * <p>This implementation calls {@link File#createTempFile(String,
   * String)} with {@code changelog} and {@code .tmp.xml} as its
   * arguments, instructs the {@link File} so created to be
   * {@linkplain File#deleteOnExit() deleted when the Java Virtual
   * Machine exits} and returns the result.</p>
   *
   * @return a non-{@code null} {@link File}
   *
   * @exception IOException if an error occurs
   */
  protected File generateEmptyAggregateChangeLogFile() throws IOException {
    final File f = File.createTempFile("changelog", ".tmp.xml");
    assert f != null;    
    f.deleteOnExit();
    this.fileWasGenerated = true;
    return f;
  }

  /**
   * Generates a Liquibase changelog file that, from a high level,
   * logically contains the Liquibase changelog fragments reachable
   * from the supplied {@link URL}s.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param resources a {@link Collection} of {@link URL}s, each
   * element of which resolves to a Liquibase changelog file; may be
   * {@code null} or {@linkplain Collection#isEmpty() empty} in whihc
   * case a generally useless changelog will be generated
   *
   * @return a non-{@code null} {@link File} representing the path to
   * the generated file
   *
   * @exception IOException if an error occurs
   *
   * @exception IllegalStateException if somehow the {@link File} into
   * which content will be poured is {@code null}
   *
   * @see #getAggregateChangeLogFile()
   *
   * @see #generateEmptyAggregateChangeLogFile()
   */
  public File generate(final Collection<? extends URL> resources) throws IOException {
    // Get the aggregate file ready to go.
    File aggregateChangeLogFile = this.getAggregateChangeLogFile();
    if (aggregateChangeLogFile == null) {
      aggregateChangeLogFile = this.generateEmptyAggregateChangeLogFile();
    }
    if (aggregateChangeLogFile == null) {
      throw new IllegalStateException("Could not get or generate a temporary aggregate change log file");
    }
    
    this.fill(aggregateChangeLogFile, resources);
    
    return aggregateChangeLogFile;
  }

  private final void fill(final File changeLogFile, final Collection<? extends URL> resources) throws IOException {
    if (changeLogFile == null) {
      throw new IllegalArgumentException("changeLogFile", new NullPointerException("changeLogFile == null"));
    }
    final String changeLogContents = this.getAggregateChangeLogContents(resources);
    if (changeLogContents == null) {
      throw new IllegalStateException("this.getAggregateChangeLogContents() == null");
    }
    this.write(changeLogFile, changeLogContents);
  }

  private final void write(final File aggregateChangeLogFile, final String changeLogContents) throws IOException {
    if (aggregateChangeLogFile != null && changeLogContents != null) {
      BufferedWriter writer = null;
      try {
        writer = new BufferedWriter(new FileWriter(aggregateChangeLogFile)); // TODO: charset?
        writer.write(changeLogContents, 0, changeLogContents.length());
        writer.flush();
      } finally {
        if (writer != null) {
          try {
            writer.close();
          } catch (final IOException nothingWeCanDo) {
            
          }
        }
      }
    }
  }

  private final String getAggregateChangeLogContents(final Collection<? extends URL> resources) throws IOException {
    if (resources == null || resources.isEmpty()) {
      throw new IllegalStateException("No sub-changelogs to aggregate");
    }

    final Map<String, Object> parameters = new HashMap<String, Object>(5);
    parameters.put("resources", resources);
    parameters.put("databaseChangeLogXsdVersion", this.getDatabaseChangeLogXsdVersion());
    parameters.put("properties", this.getProperties());
    
    return this.getAggregateChangeLogContents(parameters);
  }

  private final String getAggregateChangeLogContents(final Map<?, ?> parameters) {
    final String template = this.getTemplate();
    if (template == null) {
      throw new IllegalStateException("No template present; please call setTemplate(String) first.");
    }

    String returnValue = null;
    if (this.compiledTemplate == null) {
      this.compiledTemplate = TemplateCompiler.compileTemplate(template);
      assert this.compiledTemplate != null;
    }
    if (parameters == null || parameters.isEmpty()) {
      returnValue = (String)TemplateRuntime.execute(compiledTemplate);
    } else {
      returnValue = (String)TemplateRuntime.execute(compiledTemplate, parameters);
    }
    return returnValue;
  }

}

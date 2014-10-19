Maven Liquibase Utilities
=========================

Utilities for where Maven and Liquibase intersect.

## Quick Start ##

Suppose you have a JPA-oriented Maven project and you want to set up
an in-memory database for integration testing.  Suppose further your
JPA project depends on other JPA-oriented projects.  Suppose further
that all such projects have a `META-INF/liquibase/changelog.xml`
embedded within them that manages only their tables.

Add this to your build to have a changelog dynamically and
automatically assembled for you at `process-test-resources` time:

    <plugin>
      <groupId>com.edugility</groupId>
      <artifactId>artifact-maven-plugin</artifactId>
      <version>1.0.1</version>
      <dependencies>
        <dependency>
          <groupId>com.edugility</groupId>
          <artifactId>maven-liquibase</artifactId>
          <version>1.0.1</version>
        </dependency>
      </dependencies>
      <executions>
        <execution>
          <id>Generate aggregate Liquibase changelog for integration tests</id>
          <phase>process-test-resources</phase>
          <goals>
            <goal>process</goal>
          </goals>
          <configuration>
            <artifactsProcessor implementation="com.edugility.maven.liquibase.LiquibaseChangeLogArtifactsProcessor">
              <changeLogGenerator implementation="com.edugility.maven.liquibase.AggregateChangeLogGenerator">
                <aggregateChangeLogFile>${project.build.directory}/generated-sources/changelog.xml</aggregateChangeLogFile>
              </changeLogGenerator>
            </artifactsProcessor>
          </configuration>
        </execution>
      </executions>
    </plugin>

Regarding configuration of all the pieces, make sure you fully understand
[how to configure Maven plugins, including how to specify complex objects][3].
Then see the
[javadoc for the `LiquibaseChangeLogArtifactsProcessor`][2] and the
[javadoc for the `AggregateChangeLogGenerator`][1].

Then, once you have this `changelog.xml` in place, you'll need to run
it in your unit or integration tests.  Currently that's an exercise
left to the reader.  To do this, you'll need a `ResourceAccessor` that
can read URLs.  For that, see the [liquibase-extensions project][4].

## See Also ##

 * The
   [`artifact-maven-plugin`](http://ljnelson.github.io/artifact-maven-plugin)
   project. 
 * The
   [`liquibase-extensions`](http://ljnelson.github.io/liquibase-extensions)
   project.
 * Maven's
   [guide to configuring plugins][3].

[1]: http://ljnelson.github.io/maven-liquibase/apidocs/com/edugility/maven/liquibase/AggregateChangeLogGenerator.html
[2]: http://ljnelson.github.io/maven-liquibase/apidocs/com/edugility/maven/liquibase/LiquibaseChangeLogArtifactsProcessor.html
[3]: http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects
[4]: http://ljnelson.github.io/liquibase-extensions/apidocs/index.html

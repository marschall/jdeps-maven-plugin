Maven jdeps Plugin
==================

Maven plugin that runs the Java 8 `jdeps` tool.

Currently the tool is only included with Java 8 so `JAVA_HOME` needs to point to a Java 8 installation in order to run this plugin.

Usage
-----
```xml
  <build>
    <plugins>
      <plugin>
        <groupId>com.github.marschall</groupId>
        <artifactId>jdeps-maven-plugin</artifactId>
        <version>0.1.2</version>
      </plugin>
    </plugins>
  </build>
```

Options
-------

```
summary                 Print dependency summary only
verbose                 Print additional information
verbose-level=<level>   Print package-level or class-level dependencies
                        Valid levels are: "package" and "class"
package=<pkg name>      Restrict analysis to classes in this package
                        (may be given multiple times)
regex=<regex>           Restrict analysis to packages matching pattern
                        (package and regex are exclusive)
profile                 Show profile or the file containing a package
recursive               Recursively traverse all dependencies
version                 Version information
```
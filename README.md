# ph-merge-jaxb-episodes-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.helger.maven/ph-merge-jaxb-episodes-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.helger.maven/ph-merge-jaxb-episodes-maven-plugin) 

A specific Maven plugin that merges multiple `META-INF/sun-jaxb.episode` files into a single one.

This plugin is helpful if you have multiple executions of the `maven-jaxb2-plugin` in your plugin.
By default only one `META-INF/sun-jaxb.episode` file makes it into the binary representation of the library.

To change this, include the plugin like this (replacing `x.y.z` with the effective version number):

```xml
      <plugin>
        <groupId>com.helger.maven</groupId>
        <artifactId>ph-merge-jaxb-episodes-maven-plugin</artifactId>
        <version>x.y.z</version>
        <executions>
          <execution>
            <phase>generate-resources</phase>
            <goals>
              <goal>merge-jaxb-episodes</goal>
            </goals>
            <configuration>
              <verbose>false</verbose>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

## Parameters:

* **`verbose`** (boolean) - if `true`, more output is written to the log. This is mainly intended to find errors more easily
* **`useJakarta`** (boolean) - if `true`, create an Episode file version `3.0` using namespace URI `https://jakarta.ee/xml/ns/jaxb` whereas when `false` uses version `2.1` and namespace URI `http://java.sun.com/xml/ns/jaxb`.

# News and noteworthy

* v0.0.4 - 2023-01-08
    * Added new option `useJakarta` that create episode files for usage in Jakarta
* v0.0.3 - 2023-01-08
    * Updated to Java 11 as baseline
* v0.0.2 - 2021-08-05
    * Changed merging algorithm to be line-based instead of DOM Node based
* v0.0.1 - 2021-08-05
    * Initial release

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.
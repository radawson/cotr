package org.clockworx.cotr;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Plugin loader for the Coin of the Realm Paper plugin.
 *
 * <p>The shared database stack (Hibernate, Flyway, HikariCP and the JDBC drivers) is no
 * longer shaded/relocated into the plugin jar. Instead it is loaded at runtime by Paper's
 * library-loader: each coordinate below (and its transitive dependencies) is resolved from
 * Paper's Maven mirror into the server's shared library cache and added to this plugin's
 * classloader. This eliminates the per-plugin relocation and {@code META-INF/services} merge
 * that previously left Flyway's PluginRegister empty (the shading NPE), and keeps the jar tiny.
 */
public class CotrLoader implements PluginLoader {

    /** DB stack coordinates — kept in sync with clockworx-data's api() dependencies. */
    private static final String[] LIBRARIES = {
        "org.hibernate:hibernate-core:6.6.40.Final",
        "org.hibernate:hibernate-community-dialects:6.6.40.Final",
        "org.hibernate.orm:hibernate-hikaricp:6.6.40.Final",
        "jakarta.persistence:jakarta.persistence-api:3.1.0",
        "org.flywaydb:flyway-core:12.10.0",
        "org.flywaydb:flyway-mysql:12.10.0",
        "com.zaxxer:HikariCP:7.1.0",
        "org.jboss.logging:jboss-logging:3.6.1.Final",
        "org.xerial:sqlite-jdbc:3.53.2.0",
        "com.mysql:mysql-connector-j:9.1.0",
        "org.postgresql:postgresql:42.7.11",
    };

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        for (String coordinate : LIBRARIES) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinate), null));
        }
        resolver.addRepository(new RemoteRepository.Builder(
            "papermc", "default", "https://repo.papermc.io/repository/maven-public/").build());
        classpathBuilder.addLibrary(resolver);
    }
}

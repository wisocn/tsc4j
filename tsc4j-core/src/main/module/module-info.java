import com.github.tsc4j.api.ConfigValueDecoder;
import com.github.tsc4j.core.BeanMapper;
import com.github.tsc4j.core.ReflectiveBeanMapper;
import com.github.tsc4j.core.Tsc4jLoader;
import com.github.tsc4j.core.impl.ClasspathConfigSourceLoader;
import com.github.tsc4j.core.impl.FilesConfigSourceLoader;
import com.github.tsc4j.core.impl.UrlConfigSourceLoader;

module com.github.tsc4j.core {
    requires static lombok;
    requires static java.desktop;
    requires static java.sql;

    requires transitive org.slf4j;

    requires transitive com.github.tsc4j.api;

    /**
     * Export Test package
     */
    exports com.github.tsc4j.test;

    /**
     * Export Core package for implementations
     */
    exports com.github.tsc4j.core;

    uses ConfigValueDecoder;
    uses Tsc4jLoader;
    provides Tsc4jLoader with
        ClasspathConfigSourceLoader,
        FilesConfigSourceLoader,
        UrlConfigSourceLoader;

    uses BeanMapper;
    provides BeanMapper with ReflectiveBeanMapper;
}


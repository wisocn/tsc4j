module com.github.tsc4j.api {
    requires static lombok;
    requires transitive typesafe.config;

    /**
     * Export API package
     */
    exports com.github.tsc4j.api;
}

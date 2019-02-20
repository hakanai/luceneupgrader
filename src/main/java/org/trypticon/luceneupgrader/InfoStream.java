package org.trypticon.luceneupgrader;

/**
 * Abstraction of a stream for diagnostic information which hopefully will be enough
 * for every version. We'll try to keep it looking like the latest Lucene one to
 * reduce the pain.
 */
public interface InfoStream {

    /**
     * An info stream which logs to nowhere.
     */
    InfoStream NO_OUTPUT = new InfoStream() {
        @Override
        public void message(String component, String line) {

        }

        @Override
        public boolean isEnabled(String component) {
            return false;
        }
    };

    /**
     * Logs a message for a component.
     *
     * @param component the component name.
     * @param line the message line.
     */
    void message(String component, String line);

    /**
     * Tests whether a message for a given component will be logged.
     *
     * @param component the component name.
     * @return {@code true} if messages for that component will be logged.
     */
    boolean isEnabled(String component);
}

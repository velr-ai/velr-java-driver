package ai.velr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Query execution options for bounded result previews and Cypher parameter binding.
 *
 * <p>Parameters are bound out of band. Query text uses {@code $name}; parameter names passed to
 * this API omit the leading dollar sign.
 */
public final class QueryOptions {
    private static final QueryOptions DEFAULT =
            new QueryOptions(null, Collections.<String, Object>emptyMap());

    private final Long maxResultRows;
    private final Map<String, Object> params;

    private QueryOptions(Long maxResultRows, Map<String, Object> params) {
        this.maxResultRows = maxResultRows;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * Return default query options.
     *
     * @return options with no row cap and no parameters
     */
    public static QueryOptions defaults() {
        return DEFAULT;
    }

    /**
     * Create a query-options builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create options that cap rows returned by each result table.
     *
     * @param maxResultRows maximum rows per result table; use zero to return only column metadata
     * @return query options with the row cap set
     */
    public static QueryOptions maxResultRows(long maxResultRows) {
        return builder().maxResultRows(maxResultRows).build();
    }

    /**
     * Return the row cap for each result table.
     *
     * @return maximum row count, or {@code null} when uncapped
     */
    public Long maxResultRows() {
        return maxResultRows;
    }

    /**
     * Return bound openCypher parameters.
     *
     * @return immutable parameter map
     */
    public Map<String, Object> params() {
        return params;
    }

    /**
     * Return a copy of these options with one additional parameter.
     *
     * @param name parameter name without a leading {@code $}
     * @param value parameter value
     * @return updated query options
     */
    public QueryOptions withParam(String name, Object value) {
        Builder builder = builder().maxResultRows(maxResultRows);
        builder.params.putAll(params);
        return builder.param(name, value).build();
    }

    /**
     * Return a copy of these options with additional parameters.
     *
     * @param values parameter map; keys omit the leading {@code $}
     * @return updated query options
     */
    public QueryOptions withParams(Map<String, ?> values) {
        Builder builder = builder().maxResultRows(maxResultRows);
        builder.params.putAll(params);
        return builder.params(values).build();
    }

    /** Builder for {@link QueryOptions}. */
    public static final class Builder {
        private Long maxResultRows;
        private final Map<String, Object> params = new LinkedHashMap<>();

        /**
         * Create an empty query-options builder.
         *
         * <p>{@link QueryOptions#builder()} is the preferred entry point.
         */
        public Builder() {}

        /**
         * Set the maximum number of rows returned by each result table.
         *
         * @param maxResultRows maximum row count, or {@code null} for uncapped execution
         * @return this builder
         */
        public Builder maxResultRows(Long maxResultRows) {
            if (maxResultRows != null && maxResultRows < 0) {
                throw new IllegalArgumentException("maxResultRows cannot be negative");
            }
            this.maxResultRows = maxResultRows;
            return this;
        }

        /**
         * Set the maximum number of rows returned by each result table.
         *
         * @param maxResultRows maximum row count
         * @return this builder
         */
        public Builder maxResultRows(long maxResultRows) {
            return maxResultRows(Long.valueOf(maxResultRows));
        }

        /**
         * Add or replace one query parameter.
         *
         * @param name parameter name without a leading {@code $}
         * @param value parameter value
         * @return this builder
         */
        public Builder param(String name, Object value) {
            validateName(name);
            params.put(name, value);
            return this;
        }

        /**
         * Add query parameters from a map.
         *
         * @param values parameter map; keys omit the leading {@code $}
         * @return this builder
         */
        public Builder params(Map<String, ?> values) {
            if (values == null) {
                return this;
            }
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                param(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Build immutable query options.
         *
         * @return query options
         */
        public QueryOptions build() {
            if (maxResultRows == null && params.isEmpty()) {
                return DEFAULT;
            }
            return new QueryOptions(maxResultRows, params);
        }

        private static void validateName(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("query parameter name cannot be empty");
            }
            if (name.charAt(0) == '$') {
                throw new IllegalArgumentException("query parameter name should omit leading $");
            }
        }
    }
}

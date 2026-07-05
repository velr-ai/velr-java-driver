package ai.velr;

/** Graph entity kind for vector embedding inputs. */
public enum VectorEntityKind {
    /** A node entity. */
    NODE,
    /** A relationship entity. */
    RELATIONSHIP;

    static VectorEntityKind fromNative(int value) {
        switch (value) {
            case 0:
                return null;
            case 1:
                return NODE;
            case 2:
                return RELATIONSHIP;
            default:
                throw new IllegalArgumentException("unknown vector entity kind: " + value);
        }
    }
}

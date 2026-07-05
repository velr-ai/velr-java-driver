package ai.velr;

interface ChildHandle extends AutoCloseable {
    @Override
    void close();
}

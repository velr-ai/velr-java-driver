package ai.velr;

interface ParentHandle {
    void registerChild(ChildHandle child);

    void unregisterChild(ChildHandle child);
}

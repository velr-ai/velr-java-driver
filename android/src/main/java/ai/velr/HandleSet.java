package ai.velr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class HandleSet {
    private final Set<ChildHandle> children =
            Collections.newSetFromMap(new IdentityHashMap<ChildHandle, Boolean>());

    synchronized void register(ChildHandle child) {
        children.add(child);
    }

    synchronized void unregister(ChildHandle child) {
        children.remove(child);
    }

    void closeAll() {
        List<ChildHandle> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(children);
            children.clear();
        }
        Collections.reverse(snapshot);
        for (ChildHandle child : snapshot) {
            child.close();
        }
    }
}

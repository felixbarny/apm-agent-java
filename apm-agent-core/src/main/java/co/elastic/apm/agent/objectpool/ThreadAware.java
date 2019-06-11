package co.elastic.apm.agent.objectpool;

import javax.annotation.Nullable;

public interface ThreadAware extends Recyclable {

    void setThread(Thread thread);

    @Nullable
    Thread getThread();
}

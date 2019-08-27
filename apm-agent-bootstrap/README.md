# apm-agent-bootstrap

🚧 this does not resemble the current state but how the agent should work in the future


## Detachable agents
This agent supports being detached from a JVM.
That means it re-transforms previously transformed classes in order to undo the instrumentation.
It also makes sure that the classes from the Agent CL are not referenced anymore,
so that the entire Agent CL can be garbage collected.

It does so by clearing all references in the Bootstrap CL (for example in `Dispatcher` or `GlobalTracer`)
and by clearing all references in all Helper CL.

The only leftovers are the contents of `apm-agent-bootstrap.jar` which is one of the reasons to keep it very minimal,
with just a few interfaces and even fewer classes.

## Class Loader Hierarchy

```
    Bootstrap CL <-------------------- Agent CL (created by AgentMain)
      ^  - apm-agent-bootstrap.jar     - apm-agent-core.jar
      |    - Dispatcher                  - ElasticApmAgent
      |    - GlobalTracer                - TracerImpl, TransactionImpl, SpanImpl, ...
      |    - Tracer                    - apm-*-plugin.jar
      |    - Transaction, Span, ...    - byte-buddy.jar
      |    - WeakConcurrentMap         - disruptor.jar
      |                                - slf4j-api.jar
    Ext/Platform CL                            ^
      ^                                        | 
      |                                        | 
    System CL                                  |
      ^  - elastic-apm-agent.jar               |
      |    - AgentMain                         |
      |    - bootstrap/apm-agent-bootstrap.jar |
      |    - agent/apm-agent-core.jar          |
      |    - agent/apm-*-plugin.jar            |
      |    - agent/byte-buddy.jar              |
      |    - agent/slf4j-api.jar               |
      |                                        |
    Common - servlet-api                       |
   /      \                                    |
WebApp1  WebApp2 <-------------------- Helper CL (created by HelperClassManager)
          - spring-webmvc.jar          - ApmAsyncListener
          - apache-httpclient.jar      - CallbackWrapperCreator
          - co.elastic.apm.agent.Dispatcher
```

### System CL

When attaching a java agent, it is always added to the System CL.
The actual agent jar only contains one class, which implements the `premain` and `agentmain` methods.
It hosts the bootstrap jar and all jars which are loaded by the Agent CL.

### Bootstrap CL
Loads core Java classes.

The agent injects only a few classes to the bootstrap CL.
This contains the tracer API as well as the `Dispatcher`.

Those classes act as a delegate from the normal class loader hierarchy (which is visible for the application) to the Agent CL (which is invisible for the application).

Keep the contents of this file small
- Classes are leaked when un-loading the agent
- The bulk of the agent is loaded from a class loader which is not visible from the application
  - Advantage: classpath scanning tools don't see the agent code
    - Prevents slowdowns
    - Prevents errors. For example when they can't parse `module-info.class`

All those classes are within the `java.lang.elasticapm._${git.hash}` package.

#### Why within `java.lang`?

All class loaders have access to classes defined by the bootstrap CL.
Well, not all...
One small village of indomitable OSGi class loader still holds out against the boot delegation.

OSGi class loader white-list certain packages which are allowed to be looked up by the Bootstrap CL.
They typically have a static white-list for `java.*` and can be configured via the
`org.osgi.framework.bootdelegation` to enable bootdelegation for other packages.
Setting this property after the class loaders have initialized has no effect, however.
That means it's not a feasible solution for runtime-attaching agents.

There can be also be non-OSGi filtering class loaders where the bootdelegation works entirely different.

One thing all these filtering class loaders have in common though is that they always allow delegation for `java.lang.*` classes.

#### Why does the package name contain the git hash?

That allows the interfaces of the classes within the `apm-agent-bootstrap.jar` to change when attaching the agent to a JVM where an older version of the agent had been previously attached.

Example:
- JVM starts up without agent
- Attach agent 1.0. Creates bootstrap CL-loaded package `java.lang.elasticapm._97dd2ae0`.
- Detach agent. Bootstrap CL-loaded classes remain.
- Attach agent 1.1. Creates another bootstrap CL-loaded package `java.lang.elasticapm._908ee9ae`.

#### Alternatives

An alternative, which would not accumulate leaked classes after multiple attachments would be to only inject one class,
the `Dispatcher`, into the bootstrap CL.

This would mean the advices have to extract core Java classes from the framework-specific types and invoke a `MethodHandle` which is registered in the `Dispatcher`.
The registered `MethodHandles` call a static helper class which is loaded from the Agent CL.

It would still be possible to call methods on framework-specific classes using reflection or `MethodHandle`s.
It's just a bit more cumbersome.

### Agent CL

Loads the core agent, the instrumentation plugins and the agent's dependencies.

Because it creates a completely separate class loader hierarchy,
the classes of dependencies don't have to be relocated to a different namespace to avoid conflicts with application classes.

### Application CL

Class loaders which load the actual application, such as a WebAppClassLoader which loads a `.war` file.
Those CLs have access to the classes in the `apm-agent-bootstrap.jar` but not to the classes loaded by the Agent CL. 

### Helper CL

Loads classes which need access to the classpath of the application CL.
One example of that is in order to be able to implement a `javax.servlet.AsyncListener`.

Currently, these helpers need an interface which is available both, for the application CL, and the agent CL.
This would mean that the `apm-agent-bootstrap.jar` would contain technology-specific interfaces, which should be avoided.

We have several options:

1. Directly inject the classes in the target CL, without creating a child CL

   Pros: Relatively simple using the ClassInjector API. Instrumentations can directly reference helpers.
   Cons: Helpers can't directly reference the agent API
   
2. Also inject a `Dispatcher` into the application CL.

   As "usual", a Helper CL is created as a child of the application CL.
   A `MethodHandle` points from the `Dispatcher`, which resides in the application class loader, to a static class in the Helper CL. 
   That dispatcher doesn't have to be within the `java.lang` package,
   because it's directly loaded from the application CL thus loading the class doesn't have to be delegated to a parent CL.
   Note that one caveat is that the Helper CL can't load classes from the Agent CL.
   One alternative is to use another `MethodHandle`, in the bootstrap CL-injected `Dispatcher`. But that would be quite cumbersome.
   
3. 

Another one would be to use `net.bytebuddy.dynamic.loading.MultipleParentClassLoader` for the parent CL
so that the Helper CL has both, the Application CL, and the Agent CL as a parent. 





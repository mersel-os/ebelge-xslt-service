package io.mersel.services.xslt.infrastructure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sandbox testlerinde Saxon'a adıyla verilen sınıf. Örneklenip örneklenmediği yalnızca kendi
 * sayacından okunur; başka bir yan etkisi yoktur.
 */
public class SandboxInstantiationProbe {

    static final AtomicInteger CREATED = new AtomicInteger();

    public SandboxInstantiationProbe() {
        CREATED.incrementAndGet();
    }
}

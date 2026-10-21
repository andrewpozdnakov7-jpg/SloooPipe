// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;
import java.util.concurrent.atomic.AtomicLong;
/** Late completion of A cannot commit after B or cancellation. */
public final class LatestOperation {
    private final AtomicLong ticket = new AtomicLong();
    public long next() { return ticket.incrementAndGet(); }
    public boolean isCurrent(long expected) { return ticket.get() == expected; }
}

package dev.packarms.core;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deduplicated CPU asset loading. Values, null/missing values and failures all occupy the same
 * per-key future and are cached for this generation. No rendering or game API is used here.
 * Callers must treat successfully published values as immutable.
 */
public final class LazyAssetCache<K, V> implements AutoCloseable {
    @FunctionalInterface public interface Loader<K, V> { V load(K key) throws Exception; }

    public record Stats(long generation, long requests, long loads, long hits, long misses,
                        long failures, long pending, int entries) { }

    private static final AtomicLong THREAD_IDS = new AtomicLong();
    private final Loader<? super K, ? extends V> loader;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<State<K, V>> state = new AtomicReference<>(new State<>(1));
    private final Set<CompletableFuture<V>> outstanding = ConcurrentHashMap.newKeySet();

    private static final class State<K, V> {
        final long generation;
        final ConcurrentHashMap<K, CompletableFuture<V>> values = new ConcurrentHashMap<>();
        final AtomicLong requests = new AtomicLong();
        final AtomicLong loads = new AtomicLong();
        final AtomicLong hits = new AtomicLong();
        final AtomicLong misses = new AtomicLong();
        final AtomicLong failures = new AtomicLong();
        State(long generation) { this.generation = generation; }
    }

    /** A private single daemon thread prewarms assets. Sync loads do not hop to this executor. */
    public LazyAssetCache(Loader<? super K, ? extends V> loader) {
        this(loader, createExecutor("packarms-assets", 1), true);
    }

    /** The caller owns this executor and its shutdown/thread policy. */
    public LazyAssetCache(Loader<? super K, ? extends V> loader, Executor executor) {
        this(loader, executor, false);
    }

    public LazyAssetCache(Loader<? super K, ? extends V> loader, int workers, String threadName) {
        this(loader, createExecutor(threadName, workers), true);
    }

    private LazyAssetCache(Loader<? super K, ? extends V> loader, Executor executor, boolean ownsExecutor) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownedExecutor = ownsExecutor ? (ExecutorService) executor : null;
    }

    private static ExecutorService createExecutor(String name, int workers) {
        Objects.requireNonNull(name, "threadName");
        if (workers < 1) throw new IllegalArgumentException("workers must be positive");
        return Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task, name + "-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<V> request(K key) { return obtain(key, true); }
    /** Synchronous entry for offline tools; if prewarming already owns this key it waits for that future. */
    public V get(K key) { return obtain(key, false).join(); }
    public V load(K key) { return get(key); }

    private CompletableFuture<V> obtain(K key, boolean background) {
        Objects.requireNonNull(key, "key");
        if (closed.get()) throw new IllegalStateException("Asset cache is closed");
        State<K, V> current = state.get();
        current.requests.incrementAndGet();
        CompletableFuture<V> cached = current.values.get(key);
        if (cached != null) {
            current.hits.incrementAndGet();
            return cached;
        }
        CompletableFuture<V> candidate = new CompletableFuture<>();
        CompletableFuture<V> existing = current.values.putIfAbsent(key, candidate);
        if (existing != null) {
            current.hits.incrementAndGet();
            return existing;
        }
        outstanding.add(candidate);
        candidate.whenComplete((value, error) -> outstanding.remove(candidate));
        if (closed.get()) {
            candidate.completeExceptionally(new IllegalStateException("Asset cache is closed"));
            return candidate;
        }
        Runnable task = () -> {
            if (candidate.isDone()) return;
            if (state.get() != current) {
                // A reload retired this queued request before it started. Do not spend time reading
                // old sources (which their owner may already have closed). Already running loads
                // can finish for existing callers, but only in their original generation map.
                candidate.completeExceptionally(new IllegalStateException("Asset request belongs to a retired generation"));
                return;
            }
            current.loads.incrementAndGet();
            try {
                V value = loader.load(key);
                if (value == null) current.misses.incrementAndGet();
                candidate.complete(value);
            } catch (Throwable error) {
                current.failures.incrementAndGet();
                candidate.completeExceptionally(error);
            }
        };
        if (background) {
            try { executor.execute(task); }
            catch (RuntimeException error) {
                current.failures.incrementAndGet();
                candidate.completeExceptionally(error);
            }
        } else task.run();
        return candidate;
    }

    /** Nonblocking; empty covers absent, pending, missing and failed values. */
    public Optional<V> peek(K key) {
        Objects.requireNonNull(key, "key");
        CompletableFuture<V> value = state.get().values.get(key);
        if (value == null || !value.isDone() || value.isCompletedExceptionally() || value.isCancelled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(value.getNow(null));
    }

    public boolean isResolved(K key) {
        CompletableFuture<V> value = state.get().values.get(Objects.requireNonNull(key));
        return value != null && value.isDone();
    }

    public Optional<Throwable> failure(K key) {
        CompletableFuture<V> value = state.get().values.get(Objects.requireNonNull(key));
        if (value == null || !value.isCompletedExceptionally()) return Optional.empty();
        try { value.join(); return Optional.empty(); }
        catch (CompletionException error) { return Optional.ofNullable(error.getCause()); }
        catch (CancellationException error) { return Optional.of(error); }
    }

    /** Atomically installs a fresh map; old workers only complete futures in the retired map. */
    public void clear() {
        if (closed.get()) throw new IllegalStateException("Asset cache is closed");
        state.updateAndGet(previous -> new State<>(previous.generation + 1));
    }

    public Stats stats() {
        State<K, V> current = state.get();
        long pending = current.values.values().stream().filter(future -> !future.isDone()).count();
        return new Stats(current.generation, current.requests.get(), current.loads.get(), current.hits.get(),
                current.misses.get(), current.failures.get(), pending, current.values.size());
    }

    /** Stops new requests and completes outstanding futures; caller-provided executors stay untouched. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        state.getAndUpdate(old -> new State<>(old.generation + 1));
        // Include retired generations: shutdownNow may discard their queued tasks before the
        // retirement guard runs, and their original callers must not be left waiting forever.
        java.util.List.copyOf(outstanding).forEach(future -> future.completeExceptionally(
                new IllegalStateException("Asset cache is closed")));
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }
}

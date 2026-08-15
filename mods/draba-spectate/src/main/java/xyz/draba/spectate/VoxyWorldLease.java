package xyz.draba.spectate;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Keeps an already-open Voxy world engine alive during a cross-dimension watch session. */
public final class VoxyWorldLease implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("draba_spectate/voxy_lease");
    private static final Access ACCESS = loadAccess();
    private static final VoxyWorldLease EMPTY = new VoxyWorldLease(null, null);

    private final Access access;
    private Object engine;

    private VoxyWorldLease(Access access, Object engine) {
        this.access = access;
        this.engine = engine;
    }

    public static VoxyWorldLease acquire(Level level) {
        if (level == null || ACCESS == null) {
            return EMPTY;
        }
        try {
            Object identifier = ACCESS.worldIdentifierOf.invoke(null, level);
            if (identifier == null) {
                return EMPTY;
            }
            Object engine = ACCESS.getNullable.invoke(identifier);
            if (engine == null) {
                return EMPTY;
            }
            ACCESS.acquireRef.invoke(engine);
            return new VoxyWorldLease(ACCESS, engine);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Could not retain the current Voxy world for a spectate return", unwrap(exception));
            return EMPTY;
        }
    }

    @Override
    public void close() {
        Object retained = engine;
        engine = null;
        if (retained == null || access == null) {
            return;
        }
        try {
            access.releaseRef.invoke(retained);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Could not release a retained Voxy spectate world", unwrap(exception));
        }
    }

    static boolean integrationAvailable() {
        return ACCESS != null;
    }

    private static Access loadAccess() {
        if (!FabricLoader.getInstance().isModLoaded("voxy")) {
            return null;
        }
        try {
            Class<?> identifierClass = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
            Class<?> engineClass = Class.forName("me.cortex.voxy.common.world.WorldEngine");
            return new Access(
                    identifierClass.getMethod("of", Level.class),
                    identifierClass.getMethod("getNullable"),
                    engineClass.getMethod("acquireRef"),
                    engineClass.getMethod("releaseRef"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Voxy is present but its optional spectate-return integration is unavailable", exception);
            return null;
        }
    }

    private static Throwable unwrap(Exception exception) {
        if (exception instanceof InvocationTargetException invocation
                && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return exception;
    }

    private record Access(
            Method worldIdentifierOf,
            Method getNullable,
            Method acquireRef,
            Method releaseRef) {
    }
}

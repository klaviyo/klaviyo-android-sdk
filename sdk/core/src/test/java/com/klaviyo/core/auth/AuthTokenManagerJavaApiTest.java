package com.klaviyo.core.auth;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.junit.Test;

public class AuthTokenManagerJavaApiTest {

    @Test
    public void invalidationMethodsAreCallableFromJava() {
        AtomicInteger calls = new AtomicInteger();
        AuthTokenManager manager = (AuthTokenManager) Proxy.newProxyInstance(
                AuthTokenManager.class.getClassLoader(),
                new Class<?>[] {AuthTokenManager.class},
                (proxy, method, args) -> {
                    calls.incrementAndGet();
                    return null;
                });
        Function0<Unit> observer = () -> Unit.INSTANCE;

        manager.onTokenInvalidated(observer);
        manager.offTokenInvalidated(observer);
        manager.rejectCurrentToken();

        assertEquals(3, calls.get());
    }
}

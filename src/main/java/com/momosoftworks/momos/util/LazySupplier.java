package com.momosoftworks.momos.util;

import java.util.function.Supplier;

public class LazySupplier<T>
{
    private final Supplier<T> supplier;
    private volatile T instance;

    public LazySupplier(Supplier<T> supplier)
    {   this.supplier = supplier;
    }

    public T get()
    {
        T result = instance;
        if (result == null)
        {
            synchronized (this)
            {
                result = instance;
                if (result == null)
                {   instance = result = supplier.get();
                }
            }
        }
        return result;
    }
}

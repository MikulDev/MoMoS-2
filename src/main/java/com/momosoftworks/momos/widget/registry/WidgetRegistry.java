package com.momosoftworks.momos.widget.registry;

import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.LazySupplier;
import com.momosoftworks.momos.widget.app_launcher.AppLauncherWidget;
import com.momosoftworks.momos.widget.bar.BarWidget;
import com.momosoftworks.momos.widget.Widget;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class WidgetRegistry
{
    private static final Map<Identifier, LazySupplier<? extends Widget>> REGISTRY = new HashMap<>();

    public static final LazySupplier<BarWidget> BAR_LEFT = register("bar_left", () -> new BarWidget("LEFT"));
    public static final LazySupplier<BarWidget> BAR_RIGHT = register("bar_right", () -> new BarWidget("RIGHT"));
    public static final LazySupplier<AppLauncherWidget> APP_MENU = register("app_menu", AppLauncherWidget::new);

    public static <T extends Widget> LazySupplier<T> register(Identifier id, Supplier<T> widgetSupplier)
    {
        LazySupplier<T> supplier = new LazySupplier<>(widgetSupplier);
        REGISTRY.put(id, supplier);
        return supplier;
    }

    private static  <T extends Widget> LazySupplier<T> register(String id, Supplier<T> widgetSupplier)
    {   return register(Identifier.of(MomosApp.DEFAULT_NAMESPACE, id), widgetSupplier);
    }

    public static Widget getWidget(Identifier id)
    {
        // if (widget disabled) return null;
        LazySupplier<? extends Widget> supplier = REGISTRY.get(id);
        if (supplier != null)
        {   return supplier.get();
        }
        return null;
    }

    public static Map<Identifier, LazySupplier<? extends Widget>> getRegistry()
    {   return Collections.unmodifiableMap(REGISTRY);
    }
}

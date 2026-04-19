package com.momosoftworks.momos.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import javafx.scene.image.Image;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class JsonCodec<T>
{
    private final Class<T> type;

    protected JsonCodec(Class<T> type)
    {   this.type = type;
    }

    public Class<T> getType()
    {   return type;
    }

    public abstract JsonElement serialize(T obj);
    public abstract T deserialize(JsonElement json);

    public static <T> JsonCodec<T> create(Class<T> type, Function<T, JsonElement> encoder, Function<JsonElement, T> decoder)
    {
        return new JsonCodec<>(type)
        {
            @Override
            public JsonElement serialize(T obj)
            {   return encoder.apply(obj);
            }

            @Override
            public T deserialize(JsonElement json)
            {   return decoder.apply(json);
            }
        };
    }

    public static JsonCodec<Integer> INT     = create(Integer.class, JsonPrimitive::new, JsonElement::getAsInt);
    public static JsonCodec<String>  STRING  = create(String.class,  JsonPrimitive::new, JsonElement::getAsString);
    public static JsonCodec<Boolean> BOOLEAN = create(Boolean.class, JsonPrimitive::new, JsonElement::getAsBoolean);
    public static JsonCodec<Double>  DOUBLE  = create(Double.class,  JsonPrimitive::new, JsonElement::getAsDouble);
    public static JsonCodec<Image>   IMAGE   = create(Image.class, img -> new JsonPrimitive(img.getUrl()), json -> new Image(json.getAsString()));

    public static <T> JsonCodec<T> unit(Supplier<T> supplier)
    {   return create(null, obj -> new JsonObject(), json -> supplier.get());
    }

    public static <T extends Enum<T>> JsonCodec<T> ofEnum(Class<T> enumClass)
    {   return create(enumClass, obj -> new JsonPrimitive(obj.name()), json -> Enum.valueOf(enumClass, json.getAsString()));
    }

    public static <T> Builder<T> construct(Class<T> clazz)
    {   return new Builder<>(clazz);
    }

    public static class Builder<T>
    {
        public record Arg<T, V>(String key, JsonCodec<V> codec, Function<T, V> getter) {}

        Class<T> clazz;
        List<Arg<T, ?>> args = new ArrayList<>();
        List<Consumer<T>> postOperators = new ArrayList<>();

        public Builder(Class<T> clazz)
        {   this.clazz = clazz;
        }

        public <V> Builder<T> arg(String key, JsonCodec<V> codec, Function<T, V> getter)
        {   args.add(new Arg<>(key, codec, getter));
            return this;
        }

        public Builder<T> post(Consumer<T> operator)
        {   postOperators.add(operator);
            return this;
        }

        public JsonCodec<T> build()
        {
            Constructor<T> constructor;
            Class<?>[] argTypes = new Class<?>[args.size()];
            try
            {
                for (int i = 0; i < args.size(); i++)
                {
                    Class<?> t = args.get(i).codec.getType();
                    if (t == null)
                        throw new RuntimeException("Codec for arg '" + args.get(i).key + "' has no type info (e.g. unit codec)");
                    argTypes[i] = t;
                }
                constructor = this.clazz.getDeclaredConstructor(argTypes);
                constructor.setAccessible(true);
            }
            catch (NoSuchMethodException e)
            {   throw new RuntimeException(String.format("Failed to find matching constructor for class %s with argument types %s", clazz.getName(), List.of(argTypes)), e);
            }
            return new JsonCodec<>(clazz)
            {
                @Override
                public JsonElement serialize(T obj)
                {
                    var jsonObj = new JsonObject();
                    for (Arg<T, ?> arg : args)
                    {   jsonObj.add(arg.key, ((JsonCodec) arg.codec).serialize(arg.getter.apply(obj)));
                    }
                    return jsonObj;
                }

                @Override
                public T deserialize(JsonElement json)
                {
                    var jsonObj = json.getAsJsonObject();
                    try
                    {
                        Object[] argValues = new Object[args.size()];
                        for (int i = 0; i < args.size(); i++)
                        {
                            Arg<T, ?> arg = args.get(i);
                            JsonElement element = jsonObj.get(arg.key);
                            if (element == null)
                            {   throw new RuntimeException("Missing required field: " + arg.key);
                            }
                            argValues[i] = ((JsonCodec) arg.codec).deserialize(element);
                        }
                        T obj = constructor.newInstance(argValues);
                        for (Consumer<T> postOperator : postOperators)
                        {   postOperator.accept(obj);
                        }
                        return obj;
                    }
                    catch (Exception e)
                    {   throw new RuntimeException("Failed to deserialize JSON", e);
                    }
                }
            };
        }
    }
}
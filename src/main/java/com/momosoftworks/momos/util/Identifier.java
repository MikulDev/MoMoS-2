package com.momosoftworks.momos.util;

public record Identifier(String namespace, String path)
{
    public static Identifier of(String namespace, String path)
    {   return new Identifier(namespace, path);
    }

    public Identifier resolve(String subPath)
    {   return new Identifier(this.namespace, this.path + "/" + subPath);
    }

    @Override
    public String toString()
    {   return namespace + ":" + path;
    }
}
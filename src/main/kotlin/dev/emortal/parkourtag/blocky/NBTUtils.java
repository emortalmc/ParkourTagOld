package dev.emortal.parkourtag.blocky;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTByte;
import org.jglrxavpok.hephaistos.nbt.NBTByteArray;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTDouble;
import org.jglrxavpok.hephaistos.nbt.NBTFloat;
import org.jglrxavpok.hephaistos.nbt.NBTInt;
import org.jglrxavpok.hephaistos.nbt.NBTIntArray;
import org.jglrxavpok.hephaistos.nbt.NBTList;
import org.jglrxavpok.hephaistos.nbt.NBTLong;
import org.jglrxavpok.hephaistos.nbt.NBTLongArray;
import org.jglrxavpok.hephaistos.nbt.NBTShort;
import org.jglrxavpok.hephaistos.nbt.NBTString;
import org.krystilize.blocky.data.NbtData;

public class NBTUtils {
    private static final Map<Class<? extends NBT>, Function<NBT, Object>> NBT_TO_JAVA;

    @NotNull
    private static List<Object> toJavaList(NBTList<? extends NBT> list) {
        List<Object> javaList = new ArrayList();
        for (NBT nbt : list)
            javaList.add(getJavaObject(nbt));
        return javaList;
    }

    static {
        NBT_TO_JAVA = Map.ofEntries((Map.Entry<? extends Class<? extends NBT>, ? extends Function<NBT, Object>>[])new Map.Entry[] {
                Map.entry(NBTByte.class, nbt -> Byte.valueOf(((NBTByte)nbt).getValue())),
                Map.entry(NBTByteArray.class, nbt -> ((NBTByteArray)nbt).getValue()),
                Map.entry(NBTCompound.class, nbt -> new NbtData((NBTCompound)nbt)),
                Map.entry(NBTDouble.class, nbt -> Double.valueOf(((NBTDouble)nbt).getValue())),
                Map.entry(NBTFloat.class, nbt -> Float.valueOf(((NBTFloat)nbt).getValue())),
                Map.entry(NBTInt.class, nbt -> Integer.valueOf(((NBTInt)nbt).getValue())),
                Map.entry(NBTIntArray.class, nbt -> ((NBTIntArray)nbt).getValue()),
                Map.entry(NBTList.class, nbt -> toJavaList((NBTList<? extends NBT>)nbt)),
                Map.entry(NBTLong.class, nbt -> Long.valueOf(((NBTLong)nbt).getValue())),
                Map.entry(NBTLongArray.class, nbt -> ((NBTLongArray)nbt).getValue()),
                Map.entry(NBTShort.class, nbt -> Short.valueOf(((NBTShort)nbt).getValue())),
                Map.entry(NBTString.class, nbt -> ((NBTString)nbt).getValue()) });
    }

    public static <T> T getJavaObject(@Nullable NBT nbt) {
        if (nbt == null)
            return null;
        Class<? extends NBT> clazz = (Class)nbt.getClass();
        Class<? extends NBT> key = NBT_TO_JAVA.keySet().stream().filter(c -> c.isAssignableFrom(clazz)).findAny().orElse(null);
        Function<NBT, Object> func = NBT_TO_JAVA.get(key);
        if (func == null)
            throw new IllegalArgumentException("Unsupported NBT type: " + clazz.getName());
        return (T)func.apply(nbt);
    }
}

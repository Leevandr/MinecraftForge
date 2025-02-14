/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import com.mojang.logging.LogUtils;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/*
 * A class that attempts to parse command line arguments into key value pairs to allow addition and editing.
 * Can not use JOptSimple as that doesn't parse out the values for keys unless the spec says it has a value.
 */
@ApiStatus.Internal
class ArgumentList {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<Supplier<String[]>> entries = new ArrayList<>();
    private final Map<String, EntryValue> values = new HashMap<>();

    public static ArgumentList from(String... args) {
        ArgumentList ret = new ArgumentList();

        boolean ended = false;
        for (int x = 0; x < args.length; x++) {
            if (!ended) {
                if ("--".equals(args[x])) { // '--' by itself means there are no more arguments
                    ended = true;
                } else if ("-".equals(args[x])) {
                    ret.addRaw(args[x]);
                } else if (args[x].startsWith("-")) {
                    int idx = args[x].indexOf('=');
                    String key = idx == -1 ? args[x] : args[x].substring(0, idx);
                    String value = idx == -1 ? null : idx == args[x].length() - 1 ? "" : args[x].substring(idx + 1);

                    if (idx == -1 && x + 1 < args.length && !args[x+1].startsWith("-")) { //Not in --key=value, so try and grab the next argument.
                        ret.addArg(true, key, args[x+1]); //Assume that if the next value is a "argument" then don't use it as a value.
                        x++; // This isn't perfect, but the best we can do without knowing all of the spec.
                    } else {
                        ret.addArg(false, key, value);
                    }
                } else {
                    ret.addRaw(args[x]);
                }
            } else {
                ret.addRaw(args[x]);
            }
        }
        return ret;
    }

    public void addRaw(final String arg) {
        entries.add(() -> new String[] { arg });
    }

    public void addArg(boolean split, String raw, String value) {
        int idx = raw.startsWith("--") ? 2 : 1;
        String prefix = raw.substring(0, idx);
        String key = raw.substring(idx);
        EntryValue entry = new EntryValue(split, prefix, key, value);
        if (values.containsKey(key)) {
            LOGGER.info("Duplicate entries for " + key + " Unindexable");
        } else {
            values.put(key, entry);
        }
        entries.add(entry);
    }

    public String[] getArguments() {
        return entries.stream()
            .flatMap(e -> Arrays.stream(e.get()))
            .toArray(String[]::new);
    }

    public boolean has(String key) {
        return this.values.containsKey(key);
    }

    public boolean hasValue(String key) {
        return getOrDefault(key, null) != null;
    }

    public String get(String key) {
        EntryValue ent = values.get(key);
        return ent == null ? null : ent.getValue();
    }

    public String getOrDefault(String key, String value) {
        EntryValue ent = values.get(key);
        return ent == null ? value : ent.getValue() == null ? value : ent.getValue();
    }

    public void put(String key, String value) {
        EntryValue entry = values.get(key);
        if (entry == null) {
            entry = new EntryValue(true, "--", key, value);
            values.put(key, entry);
            entries.add(entry);
        } else {
            entry.setValue(value);
        }
    }

    public void putLazy(String key, String value) {
        EntryValue ent = values.get(key);
        if (ent == null)
            addArg(true, "--" + key, value);
        else if (ent.getValue() == null)
            ent.setValue(value);
    }

    public String remove(String key) {
        EntryValue ent = values.remove(key);
        if (ent == null)
            return null;
        entries.remove(ent);
        return ent.getValue();
    }

    private static final class EntryValue implements Supplier<String[]> {
        private final String prefix;
        private final String key;
        private final boolean split;
        private String value;

        public EntryValue(boolean split, String prefix, String key, String value) {
            this.split = split;
            this.prefix = prefix;
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String[] get() {
            if (getValue() == null)
                return new String[] { prefix + getKey() };
            if (split)
                return new String[] { prefix + getKey(), getValue() };
            return new String[] { prefix + getKey() + '=' + getValue() };
        }

        @Override
        public String toString() {
            return String.join(", ", get());
        }
    }
}

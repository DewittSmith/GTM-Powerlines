package com.github.dewittsmith.gtm_powerlines;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3i;

import java.util.*;
import java.util.stream.Collector;

import static com.gregtechceu.gtceu.api.pattern.Predicates.custom;

public class GTPatternMerger {
    public static FactoryBlockPattern merge(FactoryBlockPattern blockPattern, Map<Character, TraceabilityPredicate> predicateMap, String[][]... patterns) {
        var totalWidth = 0;
        var totalHeight = 0;
        var totalDepth = 0;

        var availableChars = new ArrayList<Character>();
        final var alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < alphabet.length(); i++) {
            availableChars.add(alphabet.charAt(i));
        }

        for (String[][] pattern : patterns) {
            var height = pattern.length;
            var depth = pattern[0].length;
            var width = pattern[0][0].length();

            totalWidth = Math.max(width, totalWidth);
            totalHeight = Math.max(height, totalHeight);
            totalDepth = Math.max(depth, totalDepth);
        }

        var candidates = new WeakHashMap<Vector3i, Set<Character>>();
        for (String[][] pattern : patterns) {
            var height = pattern.length;
            var depth = pattern[0].length;
            var width = pattern[0][0].length();

            for (int y = 0; y < totalHeight; ++y) {
                for (int z = 0; z < totalDepth; ++z) {
                    for (int x = 0; x < totalWidth; ++x) {
                        var pos = new Vector3i(x, y, z);
                        if (!candidates.containsKey(pos)) {
                            candidates.put(pos, new HashSet<>());
                        }

                        char character = '*';
                        if (x < width && y < height && z < depth) {
                            character = pattern[y][z].charAt(x);
                        }

                        availableChars.remove((Object) character);
                        candidates.get(pos).add(character);
                    }
                }
            }
        }

        var merged = new String[totalHeight][totalDepth];

        var remap = new HashMap<Character, String>();
        var remapInv = new HashMap<String, Character>();
        for (int y = 0; y < totalHeight; ++y) {
            for (int z = 0; z < totalDepth; ++z) {
                var line = new StringBuilder();
                for (int x = 0; x < totalWidth; ++x) {
                    var pos = new Vector3i(x, y, z);
                    var c = candidates.get(pos);

                    var ch = c.stream().findFirst().get();
                    if (c.size() > 1) {
                        var asStr = c.stream().collect(Collector.of(
                                StringBuilder::new,
                                StringBuilder::append,
                                StringBuilder::append,
                                StringBuilder::toString
                        ));

                        ch = remapInv.getOrDefault(asStr, null);
                        if (ch == null) {
                            ch = availableChars.get(0);
                            availableChars.remove(0);

                            remap.put(ch, asStr);
                            remapInv.put(asStr, ch);
                        }
                    }

                    line.append(ch);
                }

                merged[y][z] = line.toString();
            }
        }

        for (String[] aisle : merged) {
            blockPattern = blockPattern.aisle(aisle);
        }

        for (Character ch : predicateMap.keySet()) {
            blockPattern = blockPattern.where(ch, predicateMap.get(ch));
        }

        for (Character ch : remap.keySet()) {
            var m = remap.get(ch);

            var mergedPredicate = predicateMap.get(m.charAt(0));
            for (int i = 1; i < m.length(); ++i) {
                mergedPredicate = mergedPredicate.or(predicateMap.get(m.charAt(1)));
            }

            blockPattern.where(ch, mergedPredicate);
        }

        return blockPattern;
    }

    public static TraceabilityPredicate track(String name, BlockState blockState, Property<?>... props) {
        return custom(state -> {
                    var bs = state.getBlockState();
                    if (!bs.is(blockState.getBlock())) return false;
                    for (Property<?> prop : props) {
                        if (!bs.hasProperty(prop)) {
                            return false;
                        }

                        if (bs.getValue(prop) != blockState.getValue(prop)) {
                            return false;
                        }
                    }

                    state.getMatchContext()
                            .getOrCreate(name, () -> new ArrayList<BlockPos>())
                            .add(state.getPos());
                    return true;
                },
                () -> new BlockInfo[]{new BlockInfo(blockState)});
    }
}

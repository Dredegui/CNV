package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class BasicBlockCache extends AbstractJavassistTool {

    private static Map<Integer, Integer> cache = new HashMap<>();

    private static Integer cache_hits = 0;
    private static Integer cache_misses = 0;
    private static Integer nblocks = 0;
    private static final Integer CACHE_SIZE = 1;

    public BasicBlockCache(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void addBlockToCache(int block) {
        if (!cache.containsKey(block)) {
            cache.put(block, nblocks);
            cache_misses++;
        } else {
            if (cache.get(block) + CACHE_SIZE < nblocks) {
                cache_misses++;
            } else {
                cache_hits++;
            }

            cache.replace(block, nblocks);
        }

        nblocks++;
    }

    public static void printStatistics() {
        System.out.println(String.format("[%s] Cache hits: %s", BasicBlockCache.class.getSimpleName(), cache_hits));
        System.out.println(String.format("[%s] Cache misses: %s", BasicBlockCache.class.getSimpleName(), cache_misses));
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        
        if (behavior.getName().equals("main")) {
            behavior.insertAfter(String.format("%s.printStatistics();", BasicBlockCache.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.addBlockToCache(%s);", BasicBlockCache.class.getName(), block.getPosition()));
    }

}
